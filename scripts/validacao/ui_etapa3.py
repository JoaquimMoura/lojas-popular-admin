#!/usr/bin/env python3
"""Validação no NAVEGADOR do financeiro (Etapa 3), em computador (1º viewport) e celular (layout das telas).

Ambiente: SOMENTE o local descartável (ver README.md). Requer Playwright e LP_ADMIN_EMAIL / LP_ADMIN_PASSWORD.
Variáveis opcionais: LP_BASE_URL, LP_VIEWPORTS (padrão "1366x900,390x844"), LP_SHOTS_DIR, LP_HEADFUL=1.
As decisões financeiras recebem valores de TESTE e são restauradas ao final; usuários de teste são desativados.
Código de saída: 0 = tudo OK; 1 = alguma verificação falhou; 2 = configuração/ambiente ausente.
"""
import os

import lib as L
from lib import call, check, g, info, secao
import ui_fluxo as U
from playwright.sync_api import sync_playwright

ctx = {}
FIN_TESTE = {"comissaoPercentual": 5, "comissaoAquisicao": "QUITACAO", "competenciaReceita": "CONFIRMACAO",
             "perfisReabertura": ["ADMIN"], "perfisRestituicao": ["ADMIN", "GERENTE"], "permiteRestituicao": True,
             "permiteCobrancaDiferenca": True, "metaDescontaDevolucoes": True, "fechamentoExigeSemPendencias": False}
FIN_VAZIO = {k: None for k in FIN_TESTE}
ABAS = ["caixa", "contas", "cartao", "comissoes", "metas", "fechamento", "restituicoes", "relatorios", "custos"]


def principal():
    adm = L.preparar()
    ctx["snap"] = L.config_salvar(adm)
    ctx["fin"] = ctx["snap"].get("financeiro") or dict(FIN_VAZIO)
    L.rotulo_teste()
    print("[VALORES DE TESTE FINANCEIROS] comissão 5%, quitação, competência na confirmação (restaurados ao final).")
    ctx["criadas"] = L.condicoes_teste(adm, ctx["snap"])
    L.config_aplicar(adm, L.LIMITE_TESTE, L.ARRED_TESTE, ["ADMIN", "GERENTE"], False)
    ger = L.novo_usuario(adm, "GERENTE", "ger")
    vend = L.novo_usuario(adm, "VENDEDOR", "vend")
    cliente = L.criar_cliente(ger["token"], nome="Cliente UI3 %s" % L.SUFIXO)
    produto = L.criar_produto(adm, "Guarda-roupa UI3", 1000.00, estoque=50)
    call("PUT", "/config/comercial/financeiro", adm, FIN_VAZIO)
    s0, perm0 = call("GET", "/config/comercial/permissoes-financeiras", adm)
    ops = ["consultar", "receber", "pagar", "estornar", "restituir"]
    ctx["perm"] = {k: (perm0.get(k) or []) for k in ops} if s0 == 200 else {k: [] for k in ops}
    call("PUT", "/config/comercial/permissoes-financeiras", adm, {k: ["GERENTE"] for k in ops})   # valores de TESTE
    ctx.update(adm=adm, ger=ger, vend=vend, cliente=cliente, produto=produto)

    vws = [tuple(int(x) for x in v.split("x")) for v in (os.environ.get("LP_VIEWPORTS") or "1366x900,390x844").split(",")]
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=not os.environ.get("LP_HEADFUL"))
        try:
            for i, (w, h) in enumerate(vws):
                secao("Viewport %dx%d" % (w, h))
                bc = browser.new_context(viewport={"width": w, "height": h}, has_touch=w < 700, is_mobile=w < 700)
                if i == 0:
                    completo(bc)
                layout(bc, "%dx%d" % (w, h))
                bc.close()
        finally:
            browser.close()


def venda_api(forma="PIX"):
    s, v = L.registrar_venda(ctx["vend"]["token"], ctx["cliente"], [L.item(ctx["produto"], 1)], forma=forma, parcelas=1,
                             tipo="RETIRADA")
    assert s == 200, (s, v)
    s, v = L.confirmar(ctx["vend"]["token"], v["id"])
    assert s == 200, (s, v)
    return v


def completo(bc):
    adm_email, adm_senha = L.ADMIN_EMAIL, L.ADMIN_PASSWORD
    pg = U.entrar(bc, adm_email, adm_senha)
    gt = ctx["ger"]["token"]

    secao("Decisões financeiras pendentes na configuração")
    U.ir(pg, "/gestao/config-comercial", "text=Decisões financeiras")
    t = U.texto(pg)
    check("a tela mostra 'Decisões financeiras' e as decisões como pendentes (D01, D02, D09, D10)",
          all(x in t for x in ("D01", "D02", "D09", "D10")) and "Pendente" in t, t[:300])
    U.shot(pg, "e3_config_pendente")

    secao("Pagamento no pedido (Pix)")
    v = venda_api()
    vid = v["id"]
    U.pedido(pg, vid)
    check("seção Pagamento com botão Registrar recebimento", pg.locator("button", has_text="Registrar recebimento").count() >= 1)
    pg.locator("button", has_text="Registrar recebimento").first.click()
    pg.wait_for_selector(".modal")
    check("o modal pede como o cliente pagou (Pix, dinheiro ou cartão)", "Como o cliente pagou" in U.texto(pg))
    btn = U.mbtn(pg, "Registrar recebimento")
    btn.dblclick()   # duplo clique: precisa gerar um único recebimento
    pg.wait_for_timeout(1500)
    s, d = call("GET", "/vendas/%s" % vid, gt)
    check("clique duplo registra um único recebimento e a venda fica PAGO",
          len(g(d, "pagamento", "recebimentos", default=[])) == 1 and g(d, "statusPagamento") == "PAGO",
          (len(g(d, "pagamento", "recebimentos", default=[])), g(d, "statusPagamento")))
    if pg.locator(".modal").count():
        pg.locator(".modal button", has_text="Fechar").first.click()
        U.fechou(pg)
    pg.reload()
    pg.wait_for_load_state("networkidle")
    check("selo de pagamento 'Pago' na tela", U.selo(pg, "Pago").count() >= 1)
    U.shot(pg, "e3_pedido_pago")
    pg.locator("button", has_text="Estornar").first.click()
    pg.wait_for_selector(".modal")
    check("estorno exige motivo (botão desabilitado sem texto)", U.mbtn(pg, "Estornar recebimento").is_disabled())
    pg.locator(".modal textarea").fill("Lançado na venda errada")
    U.mbtn(pg, "Estornar recebimento").click()
    U.fechou(pg)
    s, d = call("GET", "/vendas/%s" % vid, gt)
    check("estorno pela tela: recebimento ESTORNADO e pagamento volta a pendente",
          g(d, "pagamento", "recebimentos", 0, "status") == "ESTORNADO" and g(d, "statusPagamento") != "PAGO", g(d, "pagamento", "recebimentos"))

    secao("Telas do financeiro (computador)")
    for aba in ABAS:
        U.ir(pg, "/gestao/financeiro/" + aba)
        pg.wait_for_timeout(500)
        t = U.texto(pg)
        check("aba %s abre sem erro" % aba, "Erro" not in t[:200] and not pg.errs, (t[:160], pg.errs[-2:]))
    U.ir(pg, "/gestao/financeiro/comissoes")
    t = U.texto(pg)
    check("comissões: avisos de decisão pendente D01 visíveis", "D01" in t, t[:300])
    U.ir(pg, "/gestao/financeiro/fechamento")
    pg.wait_for_timeout(500)
    t = U.texto(pg)
    check("fechamento: resultado parcial/provisório, sem lucro definitivo, com o que falta",
          ("provisório" in t.lower() or "parcial" in t.lower()) and "Não apurado" in t, t[:400])
    check("fechamento: caixa físico e banco separados", "Caixa físico" in t and "Banco" in t)
    U.shot(pg, "e3_fechamento")

    secao("Caixa pela tela")
    U.ir(pg, "/gestao/financeiro/caixa")
    pg.wait_for_selector("text=Abrir o caixa")
    pg.locator(".card:has-text('Abrir o caixa') input").first.fill("100")
    pg.locator(".card:has-text('Abrir o caixa') button[type=submit]").click()
    pg.wait_for_selector("button:has-text('Fechar caixa')")
    s, a = call("GET", "/financeiro/caixa/atual", gt)
    check("caixa aberto pela tela com saldo inicial", g(a, "aberta") is True and abs(g(a, "sessao", "saldoInicial") - 100) < 0.005, a)
    pg.locator("button", has_text="Fechar caixa").first.click()
    pg.wait_for_selector(".modal")
    pg.locator(".modal input").first.fill("90")
    check("diferença no fechamento exige motivo (botão desabilitado)", U.mbtn(pg, "Fechar caixa").is_disabled())
    pg.locator(".modal textarea").first.fill("Troco a maior")
    U.mbtn(pg, "Fechar caixa").click()
    U.fechou(pg)
    s, a = call("GET", "/financeiro/caixa/atual", gt)
    check("caixa fechado com a diferença registrada", g(a, "aberta") is False)

    secao("Decisões financeiras e comissão pela tela")
    call("PUT", "/config/comercial/financeiro", ctx["adm"], FIN_TESTE)
    v2 = venda_api()
    s, r = call("POST", "/vendas/%s/recebimentos" % v2["id"], gt, {"valor": v2["total"]}, {"Idempotency-Key": L.chave()})
    U.ir(pg, "/gestao/financeiro/comissoes")
    pg.wait_for_timeout(500)
    t = U.texto(pg)
    check("comissões: previsão/devida da venda aparece e D01 deixou de ser pendente",
          ("#%s" % v2["id"]) in t and "D01" not in t.split("Avisos")[-1][:50], t[:500])
    U.shot(pg, "e3_comissoes")
    pg.close()

    secao("Vendedor")
    pv = U.entrar(bc, ctx["vend"]["email"], L.TEST_PASSWORD)
    U.ir(pv, "/gestao/pedidos")
    t = U.texto(pv)
    check("vendedor vê 'Minhas comissões' e 'Minha meta' e não vê o menu Financeiro",
          "Minhas comissões" in t and "Minha meta" in t and "Financeiro" not in t, t[:300])
    U.ir(pv, "/gestao/minhas-comissoes")
    check("vendedor abre a própria comissão", "Erro" not in U.texto(pv)[:200])
    U.ir(pv, "/gestao/financeiro/caixa")
    pv.wait_for_timeout(500)
    check("vendedor não acessa o Financeiro (redirecionado/negado)", "/financeiro" not in pv.url or "Acesso" in U.texto(pv), pv.url)
    pv.close()


def layout(bc, nome):
    pg = U.entrar(bc, L.ADMIN_EMAIL, L.ADMIN_PASSWORD)
    for aba in ABAS:
        U.ir(pg, "/gestao/financeiro/" + aba)
        pg.wait_for_timeout(400)
        U.sem_overflow(pg, "%s %s" % (nome, aba))
    U.ir(pg, "/gestao/config-comercial", "text=Decisões financeiras")
    U.sem_overflow(pg, "%s configuração" % nome)
    s, v = L.registrar_venda(ctx["vend"]["token"], ctx["cliente"], [L.item(ctx["produto"], 1)], forma="PIX", tipo="RETIRADA")
    U.pedido(pg, v["id"])
    U.sem_overflow(pg, "%s detalhe do pedido" % nome)
    U.shot(pg, "e3_layout_%s" % nome)
    check("%s: sem erros de JavaScript nem HTTP 5xx" % nome, not pg.errs, pg.errs[-3:])
    pg.close()


def finalizar():
    adm = L.login_admin()
    if not adm:
        return
    try:
        s, a = call("GET", "/financeiro/caixa/atual", adm)
        if s == 200 and g(a, "aberta"):
            call("POST", "/financeiro/caixa/fechar", adm, {"saldoContado": g(a, "sessao", "saldoEsperado")})
    except Exception as e:  # noqa: BLE001
        info("caixa: %s" % e)
    call("PUT", "/config/comercial/financeiro", adm, {k: ctx["fin"].get(k) for k in FIN_TESTE})
    call("PUT", "/config/comercial/permissoes-financeiras", adm, ctx.get("perm", {}))
    L.config_restaurar(adm, ctx["snap"], ctx.get("criadas", []))
    L.limpar()


if __name__ == "__main__":
    L.exigir_config()
    L.executar(principal, finalizar)
