#!/usr/bin/env python3
"""Validação no NAVEGADOR da gestão de vendas (Etapas 1 e 2), em computador e em celular, mais a vitrine pública.

Ambiente: SOMENTE o local descartável (ver README.md): `ambiente-pg.sh up` + `iniciar-app.sh`.
Requer: `pip install playwright && playwright install chromium`, e LP_ADMIN_EMAIL / LP_ADMIN_PASSWORD.
Configuração por variáveis de ambiente (nenhuma credencial é gravada no repositório):
  LP_BASE_URL      (padrão http://localhost:4180; só endereços locais)
  LP_VIEWPORTS     (padrão "1366x900,390x844"; o 1º recebe o fluxo completo, os demais só conferem o layout)
  LP_SHOTS_DIR     (padrão validacao-out/shots)  capturas de tela
  LP_HEADFUL=1     abre o navegador visível (depuração)
A configuração comercial é salva no início e RESTAURADA no final; usuários de teste são desativados.
Código de saída: 0 = tudo OK; 1 = alguma verificação falhou; 2 = configuração/ambiente ausente.
"""
import os
import re
import sys
import tempfile
import urllib.parse

import lib as L
from lib import call, check, g, info, secao

try:
    from playwright.sync_api import sync_playwright
except ImportError:
    sys.stderr.write("Playwright não instalado: pip install playwright && playwright install chromium\n")
    sys.exit(2)

SHOTS = os.environ.get("LP_SHOTS_DIR") or os.path.join(os.path.dirname(L.AQUI), "..", "validacao-out", "shots")
SHOTS = os.path.abspath(SHOTS)
os.makedirs(SHOTS, exist_ok=True)
PNG_PATH = os.path.join(tempfile.gettempdir(), "lp_val_comprovante_%s.png" % L.SUFIXO)
open(PNG_PATH, "wb").write(L.png_minimo())
ctx = {}


# ------------------------------------------------------------------ utilidades de navegador

def shot(pg, nome):
    pg.screenshot(path=os.path.join(SHOTS, nome + ".png"), full_page=True)


def entrar(browser_ctx, email, senha):
    pg = browser_ctx.new_page()
    pg.set_default_timeout(15000)
    pg.errs = []
    pg.on("pageerror", lambda e: pg.errs.append("pageerror: " + str(e)[:160]))
    pg.on("response", lambda r: pg.errs.append("HTTP %s %s" % (r.status, r.url)) if r.status >= 500 else None)
    pg.goto(L.BASE_URL + "/login")
    pg.wait_for_load_state("networkidle")
    pg.fill("input[type=email]", email)
    pg.fill("input[type=password]", senha)
    pg.click("form:has(input[type=password]) button[type=submit]")
    pg.wait_for_url(lambda u: "/login" not in u)
    pg.wait_for_load_state("networkidle")
    return pg


def ir(pg, rota, esperar=None):
    pg.goto(L.BASE_URL + rota)
    pg.wait_for_load_state("networkidle")
    if esperar:
        pg.wait_for_selector(esperar)


def pedido(pg, pid):
    ir(pg, "/gestao/pedidos/%s" % pid, "h3:has-text('Pedido #')")


def rotulo(pg, label):
    """Campo (select/input/textarea) logo após um <label> com o texto informado (formulários da página)."""
    return pg.locator("xpath=//label[normalize-space()='%s']/following-sibling::*[self::select or self::input or self::textarea][1]" % label)


def mcampo(pg, parte):
    """Campo dentro do modal aberto, a partir de parte do texto do rótulo."""
    return pg.locator("xpath=//div[contains(@class,'modal')]//label[contains(normalize-space(),'%s')]"
                      "/following-sibling::*[self::select or self::input or self::textarea][1]" % parte)


def mbtn(pg, texto):
    return pg.locator(".modal button[type=submit]", has_text=texto)


def fechou(pg):
    pg.wait_for_selector(".modal", state="detached")


def selo(pg, texto, t=8000):
    loc = pg.locator(".badge", has_text=texto).first
    try:
        loc.wait_for(state="visible", timeout=t)
    except Exception:  # noqa: BLE001 - a verificação seguinte reporta a ausência
        pass
    return loc


def sem_overflow(pg, nome):
    larg = pg.evaluate("[document.documentElement.scrollWidth, window.innerWidth]")
    check("%s: sem rolagem horizontal (%d<=%d)" % (nome, larg[0], larg[1]), larg[0] <= larg[1] + 1)


def texto(pg):
    return pg.locator("body").inner_text().replace("\xa0", " ")


def agendar(pg, botao, data, periodo="Manhã", equipe="Equipe 1"):
    pg.get_by_role("button", name=botao).first.click()
    pg.locator(".modal input[type=date]").fill(data)
    mcampo(pg, "Período").select_option(label=periodo)
    campos = pg.locator(".modal input[type=text], .modal input:not([type])")
    if campos.count() and equipe:
        campos.first.fill(equipe)
    mbtn(pg, "Agendar").click()
    fechou(pg)


def nova_venda_ui(pg, cliente_nome, produto_busca, qtd=1, desconto=None, justificativa=None):
    """Nova venda pela interface (4 etapas) e devolve o número do pedido (rascunho)."""
    ir(pg, "/gestao/vendas/nova")
    pg.fill("input[placeholder='Digite ao menos 2 caracteres']", cliente_nome)
    pg.locator(".list-group-item-action", has_text=cliente_nome).first.click()
    pg.get_by_role("button", name="Avançar").click()
    pg.fill("input[placeholder='Buscar produto pelo nome']", produto_busca)
    pg.locator(".list-group-item-action", has_text=produto_busca).first.click()
    sel = rotulo(pg, "Variação *")
    sel.select_option(label=[o for o in sel.locator("option").all_inner_texts() if "Branco" in o][0])
    rotulo(pg, "Quantidade").fill(str(qtd))
    pg.get_by_role("button", name="Adicionar à venda").click()
    pg.get_by_role("button", name="Avançar").click()
    rotulo(pg, "Canal *").select_option(label="Loja")
    rotulo(pg, "Tipo de entrega *").select_option(label="Retirada na loja")
    rotulo(pg, "Forma de pagamento *").select_option(label="Pix")
    rotulo(pg, "Parcelas *").select_option(label="1x")
    if desconto is not None:
        rotulo(pg, "Desconto (R$)").fill(str(desconto))
        if justificativa:
            rotulo(pg, "Justificativa do desconto").fill(justificativa)
    pg.get_by_role("button", name="Avançar").click()
    pg.get_by_role("button", name="Salvar rascunho").click()
    pg.wait_for_url(re.compile(r"/gestao/pedidos/\d+"))
    pg.wait_for_load_state("networkidle")
    return int(re.search(r"/pedidos/(\d+)", pg.url).group(1))


# ------------------------------------------------------------------ dados e leitura por API

def brl(valor):
    """Formata como a interface (pt-BR): 1234.5 -> 'R$ 1.234,50'."""
    return "R$ " + ("{:,.2f}".format(float(valor)).replace(",", "X").replace(".", ",").replace("X", "."))


def estoque_branco(tok, produto):
    s, e = call("GET", "/estoque?produtoId=%s" % produto["id"], tok)
    x = [r for r in e if r["variacaoDescricao"] and "Branco" in r["variacaoDescricao"]][0]
    return x["fisico"], x["reservado"], x["disponivel"]


# ------------------------------------------------------------------ roteiro

def principal():
    adm = L.preparar()
    ctx["snap"] = L.config_salvar(adm)
    L.rotulo_teste()
    ctx["criadas"] = L.condicoes_teste(adm, ctx["snap"])
    ger = L.novo_usuario(adm, "GERENTE", "ui-ger")
    ven = L.novo_usuario(adm, "VENDEDOR", "ui-ven")
    ven2 = L.novo_usuario(adm, "VENDEDOR", "ui-ven2")
    cli = L.criar_cliente(ger["token"], nome="Cliente UI %s" % L.SUFIXO,
                          enderecos=[{"logradouro": "Rua Teste", "numero": "1", "bairro": "Centro", "cidade": "São Paulo", "uf": "SP", "principal": True}])
    gr = L.criar_produto(adm, "Guarda-roupa UI", 1000.00, variacoes=[{"cor": "Branco", "tamanho": "Casal", "adicionalPreco": 0, "estoque": 30}])
    cama = L.criar_produto(adm, "Cama UI", 2000.00, estoque=0, modalidade="ENCOMENDA", prazo=None,
                           variacoes=[{"cor": "Nogal", "tamanho": "Queen", "adicionalPreco": 0, "estoque": 0}])
    ctx.update(adm=adm, ger=ger, ven=ven, ven2=ven2, cli=cli, gr=gr, cama=cama)
    item_gr = lambda q: L.item(gr, q, gr["variacoes"][0])                                   # noqa: E731
    item_cama = lambda q: L.item(cama, q, cama["variacoes"][0], "ENCOMENDA")                # noqa: E731
    cfg_ok = lambda exige: L.config_aplicar(adm, L.LIMITE_TESTE, L.ARRED_TESTE, ["ADMIN", "GERENTE"], exige)  # noqa: E731

    def venda_confirmada(itens):
        st, v = L.registrar_venda(ven["token"], cli, itens)
        if st != 200:
            raise RuntimeError("não foi possível registrar a venda de apoio: %s %s" % (st, v))
        st, c = L.confirmar(ven["token"], v["id"])
        if st != 200:
            raise RuntimeError("não foi possível confirmar a venda de apoio: %s %s" % (st, c))
        return v["id"]

    vps = []
    for par in (os.environ.get("LP_VIEWPORTS") or "1366x900,390x844").split(","):
        w, h = par.strip().lower().split("x")
        vps.append((int(w), int(h)))

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=not os.environ.get("LP_HEADFUL"))
        for idx, (w, h) in enumerate(vps):
            completo = idx == 0
            n = "[%dpx]" % w
            secao("Navegador %dx%d (%s)" % (w, h, "fluxo completo" if completo else "layout"))
            mk = lambda: browser.new_context(viewport={"width": w, "height": h}, is_mobile=(w < 500), has_touch=(w < 500))  # noqa: E731, B023
            cg, cv = mk(), mk()
            # estado inicial conhecido para cada viewport: tudo configurado, D05 pendente
            cfg_ok(None)
            pg_g = entrar(cg, ger["email"], L.TEST_PASSWORD)
            pg_v = entrar(cv, ven["email"], L.TEST_PASSWORD)

            if completo:
                # ---------- Configuração comercial pela interface (D03/D04/D05/D07 pendentes -> definidos)
                L.config_aplicar(adm, None, None, [], None)
                L.condicoes_ativar(adm, False)
                ca = mk()
                pg_a = entrar(ca, L.ADMIN_EMAIL, L.ADMIN_PASSWORD)
                ir(pg_a, "/gestao/config-comercial")
                itens_pend = " ".join(pg_a.locator(".alert-warning li").all_inner_texts())
                check("%s config: pendências D03, D04, D05 e D07 visíveis" % n, all(x in itens_pend for x in ("D03", "D04", "D05", "D07")), itens_pend)
                ir(pg_v, "/gestao/vendas/nova")
                check("%s vendedor vê o alerta de configuração pendente em Nova venda" % n, "pendente" in texto(pg_v).lower())
                pg_a.locator("input[type=number]").first.fill(str(L.LIMITE_TESTE))
                pg_a.locator("select").first.select_option(value=L.ARRED_TESTE)
                pg_a.locator("#perfil-ADMIN").check()
                pg_a.locator("#perfil-GERENTE").check()
                pg_a.locator("select", has=pg_a.locator("option", has_text="Não exigir pagamento")).select_option(label="Não definido (saída bloqueada)")
                pg_a.get_by_role("button", name="Salvar regras").click()
                pg_a.wait_for_timeout(1200)
                L.condicoes_teste(adm, L.config_salvar(adm))
                pg_a.reload()
                pg_a.wait_for_load_state("networkidle")
                itens_pend = " ".join(pg_a.locator(".alert-warning li").all_inner_texts())
                check("%s config: D03/D04/D07 definidos pela interface; D05 segue pendente" % n,
                      "D05" in itens_pend and not any(x in itens_pend for x in ("D03", "D04", "D07")), itens_pend)
                shot(pg_a, "config_comercial")

                # ---------- Etapa 1: venda com desconto acima do limite, aprovação, confirmação, reserva, cancelamento
                f0, r0, d0 = estoque_branco(adm, gr)
                pid = nova_venda_ui(pg_v, cli["nome"], gr["nome"], 1, desconto=150, justificativa="Cliente antigo")
                check("%s venda #%s com desconto acima do limite fica Aguardando aprovação" % (n, pid), selo(pg_v, "Aguardando aprovação").is_visible())
                check("%s confirmar bloqueado com explicação e sem botão de aprovar para o vendedor" % n,
                      pg_v.get_by_role("button", name="Confirmar venda").is_disabled()
                      and pg_v.get_by_role("button", name="Aprovar desconto").count() == 0)
                pedido(pg_g, pid)
                pg_g.get_by_role("button", name="Aprovar desconto").click()
                pg_g.locator("textarea").fill("Aprovado no teste")
                pg_g.locator(".modal").get_by_role("button", name="Aprovar").click()
                pg_g.wait_for_selector(".badge:has-text('Rascunho')")
                check("%s gerente aprova o desconto -> Rascunho" % n, selo(pg_g, "Rascunho").is_visible())
                pedido(pg_v, pid)
                pg_v.get_by_role("button", name="Confirmar venda").dblclick()
                pg_v.wait_for_selector(".badge:has-text('Confirmada')")
                f1, r1, d1 = estoque_branco(adm, gr)
                check("%s clique duplo em Confirmar gerou UMA reserva (reservado %s->%s)" % (n, r0, r1), r1 == r0 + 1 and d1 == d0 - 1)
                ir(pg_v, "/gestao/estoque")
                check("%s Estoque (UI) mostra o produto com a reserva" % n, "Branco" in texto(pg_v))
                pedido(pg_v, pid)
                check("%s vendedor não cancela (perfil não autorizado, com explicação)" % n,
                      pg_v.get_by_role("button", name="Cancelar venda").is_disabled() and pg_v.get_by_text("não está autorizado").first.is_visible())
                pedido(pg_g, pid)
                pg_g.get_by_role("button", name="Cancelar venda").click()
                check("%s cancelar exige motivo" % n, pg_g.locator(".modal").get_by_role("button", name="Cancelar venda").is_disabled())
                pg_g.locator("textarea").fill("Desistência no teste")
                pg_g.locator(".modal").get_by_role("button", name="Cancelar venda").click()
                pg_g.wait_for_selector(".badge:has-text('Cancelada')")
                f2, r2, d2 = estoque_branco(adm, gr)
                check("%s cancelamento liberou o estoque (reservado %s, disponível %s)" % (n, r2, d2), r2 == r0 and d2 == d0)
                shot(pg_g, "venda_cancelada")

                # ---------- D03 / D04 / D07 isolados na interface
                L.config_aplicar(adm, None, L.ARRED_TESTE, ["ADMIN", "GERENTE"], None)
                ir(pg_v, "/gestao/vendas/nova")
                pg_v.fill("input[placeholder='Digite ao menos 2 caracteres']", cli["nome"])
                pg_v.locator(".list-group-item-action", has_text=cli["nome"]).first.click()
                pg_v.get_by_role("button", name="Avançar").click()
                pg_v.fill("input[placeholder='Buscar produto pelo nome']", gr["nome"])
                pg_v.locator(".list-group-item-action", has_text=gr["nome"]).first.click()
                sel = rotulo(pg_v, "Variação *")
                sel.select_option(index=1)
                pg_v.get_by_role("button", name="Adicionar à venda").click()
                pg_v.get_by_role("button", name="Avançar").click()
                check("%s D03 pendente: desconto desabilitado e formas de pagamento liberadas" % n,
                      rotulo(pg_v, "Desconto (R$)").is_disabled() and not rotulo(pg_v, "Forma de pagamento *").is_disabled())
                L.config_aplicar(adm, L.LIMITE_TESTE, None, ["ADMIN", "GERENTE"], None)
                ir(pg_v, "/gestao/vendas/nova")
                pg_v.fill("input[placeholder='Digite ao menos 2 caracteres']", cli["nome"])
                pg_v.locator(".list-group-item-action", has_text=cli["nome"]).first.click()
                pg_v.get_by_role("button", name="Avançar").click()
                pg_v.fill("input[placeholder='Buscar produto pelo nome']", gr["nome"])
                pg_v.locator(".list-group-item-action", has_text=gr["nome"]).first.click()
                rotulo(pg_v, "Variação *").select_option(index=1)
                pg_v.get_by_role("button", name="Adicionar à venda").click()
                pg_v.get_by_role("button", name="Avançar").click()
                check("%s D04 pendente: pagamento desabilitado e 'Avançar' bloqueado" % n,
                      rotulo(pg_v, "Forma de pagamento *").is_disabled() and pg_v.get_by_role("button", name="Avançar").is_disabled())
                cfg_ok(None)
                vid = venda_confirmada([item_gr(1)])
                L.config_aplicar(adm, L.LIMITE_TESTE, L.ARRED_TESTE, [], None)
                pedido(pg_g, vid)
                check("%s D07 pendente: cancelar desabilitado e explica D07 (vender segue liberado)" % n,
                      pg_g.get_by_role("button", name="Cancelar venda").is_disabled() and pg_g.get_by_text("D07").first.is_visible())
                cfg_ok(None)
                call("POST", "/vendas/%s/cancelar" % vid, adm, {"motivo": "limpeza do roteiro"})
                ca.close()

            # ---------- Etapa 2: D05 pendente bloqueia a saída
            p1 = venda_confirmada([item_gr(1)])
            pedido(pg_g, p1)
            check("%s detalhe mostra frete gratuito e montagem sem cobrança" % n,
                  "Gratuito" in texto(pg_g) and "sem cobrança adicional" in texto(pg_g))
            agendar(pg_g, "Agendar retirada", L.amanha())
            check("%s entrega agendada" % n, selo(pg_g, "Agendada").is_visible())
            check("%s D05 pendente: Registrar saída desabilitado com explicação" % n,
                  pg_g.get_by_role("button", name="Registrar saída").is_disabled() and pg_g.get_by_text("D05").first.is_visible())
            shot(pg_g, "saida_bloqueada_d05_%d" % w)
            f0, r0, d0 = estoque_branco(adm, gr)

            if completo:
                cfg_ok(False)   # valor de TESTE para exercitar o caminho liberado
                pedido(pg_g, p1)
                pg_g.get_by_role("button", name="Registrar saída").first.click()
                mbtn(pg_g, "Registrar saída").dblclick()
                fechou(pg_g)
                check("%s saída registrada (Saiu para entrega)" % n, selo(pg_g, "Saiu").is_visible())
                f1, r1, _ = estoque_branco(adm, gr)
                check("%s clique duplo baixou UMA vez (físico %s->%s)" % (n, f0, f1), f1 == f0 - 1 and r1 == r0 - 1)
                check("%s cancelar bloqueado depois da saída, com explicação" % n,
                      pg_g.get_by_role("button", name="Cancelar venda").is_disabled() and pg_g.get_by_text("expedido").first.is_visible())
                pg_g.get_by_role("button", name="Tentativa frustrada").click()
                mcampo(pg_g, "Motivo").fill("Cliente ausente")
                mbtn(pg_g, "Registrar").click()
                fechou(pg_g)
                check("%s tentativa frustrada mantém a pendência" % n, selo(pg_g, "Tentativa frustrada").is_visible())
                pg_g.get_by_role("button", name="Reagendar").first.click()
                pg_g.locator(".modal input[type=date]").fill(str(L.amanha(2)))
                mcampo(pg_g, "Período").select_option(label="Tarde")
                mcampo(pg_g, "Motivo do reagendamento").fill("Cliente pediu outro dia")
                mbtn(pg_g, "Reagendar").click()
                fechou(pg_g)
                pg_g.get_by_role("button", name="Registrar saída").first.click()
                mbtn(pg_g, "Registrar saída").click()
                fechou(pg_g)
                check("%s nova saída após reagendar NÃO baixa de novo" % n, estoque_branco(adm, gr)[0] == f1)
                pg_g.get_by_role("button", name=re.compile("Concluir (retirada|entrega)")).first.click()
                check("%s concluir exige comprovação" % n, mbtn(pg_g, "Concluir").is_disabled())
                mcampo(pg_g, "Nome de quem recebeu").fill("Maria da Silva")
                pg_g.locator(".modal input[type=file]").set_input_files(PNG_PATH)
                mbtn(pg_g, "Concluir").click()
                fechou(pg_g)
                check("%s entrega concluída (Entregue)" % n, selo(pg_g, "Entregue").is_visible())
                with pg_g.context.expect_page() as nova, pg_g.expect_response(lambda r: "/api/v1/arquivos" in r.url) as resp:
                    pg_g.get_by_role("button", name="Abrir comprovante").first.click()
                check("%s comprovante abre pelo endpoint autenticado (HTTP %s)" % (n, resp.value.status), resp.value.status == 200)
                nova.value.close()
                shot(pg_g, "entregue")

                # montagem inclusa, depois da entrega
                agendar(pg_g, "Agendar montagem", str(L.amanha()), "Tarde", "Montador")
                check("%s montagem agendada" % n, selo(pg_g, "Agendada").is_visible())
                pg_g.get_by_role("button", name="Concluir montagem").first.click()
                check("%s concluir montagem exige evidência" % n, mbtn(pg_g, "Concluir montagem").is_disabled())
                mcampo(pg_g, "Observação").fill("Montado e nivelado")
                mbtn(pg_g, "Concluir montagem").click()
                fechou(pg_g)
                check("%s montagem concluída" % n, selo(pg_g, "Concluída").is_visible())
                total_p1 = call("GET", "/vendas/%s" % p1, adm)[1]["total"]
                check("%s total do pedido inalterado (frete/montagem sem cobrança: %s)" % (n, brl(total_p1)), brl(total_p1) in texto(pg_g))

                # pós-venda: devolução apta volta ao estoque; bloqueio financeiro D09
                pg_g.get_by_role("button", name="Abrir ocorrência").first.click()
                mcampo(pg_g, "Tipo").select_option(label="Devolução")
                mcampo(pg_g, "Descrição").fill("Arrependimento")
                mcampo(pg_g, "Item da venda").select_option(index=1)
                mcampo(pg_g, "Quantidade").first.fill("1")
                mbtn(pg_g, "Abrir ocorrência").click()
                fechou(pg_g)
                pg_g.reload()
                pg_g.wait_for_load_state("networkidle")
                pg_g.get_by_role("link", name=re.compile("Ocorrência #")).first.click()
                pg_g.wait_for_url(re.compile(r"/gestao/pos-venda/\d+"))
                pg_g.locator(".alert-warning", has_text="D09").first.wait_for()
                check("%s devolução exibe o bloqueio financeiro D09" % n, pg_g.locator(".alert-warning", has_text="D09").count() > 0)
                antes = estoque_branco(adm, gr)[0]
                pg_g.get_by_role("button", name="Receber devolução").click()
                mcampo(pg_g, "Condição").select_option(label="Apta para revenda")
                mbtn(pg_g, "Registrar recebimento").click()
                fechou(pg_g)
                check("%s devolução apta volta ao estoque (%s->%s)" % (n, antes, estoque_branco(adm, gr)[0]), estoque_branco(adm, gr)[0] == antes + 1)
                pg_g.get_by_role("button", name="Resolver").click()
                mcampo(pg_g, "Solução").fill("Devolução aceita; restituição a definir (D09)")
                mbtn(pg_g, "Resolver").click()
                fechou(pg_g)
                check("%s ocorrência resolvida" % n, selo(pg_g, "Resolvida").is_visible())

                # encomenda + sem entrega parcial
                p2 = venda_confirmada([item_gr(1), item_cama(2)])
                ir(pg_g, "/gestao/encomendas")
                card = pg_g.locator("tr, .card").filter(has_text="#%s" % p2).first
                card.get_by_role("button", name="Editar").click()
                mcampo(pg_g, "Fornecedor").fill("Fábrica de teste")
                mcampo(pg_g, "Referência do fornecedor").fill("PED-UI-1")
                pg_g.locator(".modal input[type=date]").fill(str(L.amanha(3)))
                mbtn(pg_g, "Salvar").click()
                fechou(pg_g)
                pg_g.wait_for_selector("text=Prazo padrão")
                check("%s encomenda: pedido realizado e prazo padrão 'A definir (D08)'" % n, "Pedido realizado" in texto(pg_g) and "D08" in texto(pg_g))
                pedido(pg_g, p2)
                agendar(pg_g, "Agendar retirada", str(L.amanha()))
                check("%s pedido misto: saída bloqueada (entrega parcial proibida)" % n,
                      pg_g.get_by_role("button", name="Registrar saída").first.is_disabled() and pg_g.get_by_text("não há entrega parcial").first.is_visible())
                ir(pg_g, "/gestao/encomendas")
                card = pg_g.locator("tr, .card").filter(has_text="#%s" % p2).first
                card.get_by_role("button", name="Receber").click()
                mcampo(pg_g, "Quantidade recebida").fill("1")
                mbtn(pg_g, "Registrar recebimento").click()
                pg_g.locator(".modal").get_by_text("cobrir a quantidade vendida").first.wait_for()
                check("%s recebimento menor que o vendido recusado, com mensagem no modal" % n, True)
                mcampo(pg_g, "Quantidade recebida").fill("2")
                mbtn(pg_g, "Registrar recebimento").click()
                fechou(pg_g)
                pedido(pg_g, p2)
                check("%s após o recebimento a saída fica liberada" % n, pg_g.get_by_role("button", name="Registrar saída").first.is_enabled())
                pg_g.get_by_role("button", name="Registrar saída").first.click()
                mbtn(pg_g, "Registrar saída").click()
                fechou(pg_g)
                check("%s pedido completo sai de uma vez" % n, selo(pg_g, "Saiu").is_visible())

                # inventário
                ir(pg_g, "/gestao/estoque")
                pg_g.fill("input[placeholder='Buscar por nome ou SKU']", gr["nome"])
                pg_g.wait_for_timeout(300)
                linha = pg_g.locator("tr, .card").filter(has_text="Branco").first
                linha.locator("button:visible", has_text=re.compile("Contar")).first.click()
                sys_fis = estoque_branco(adm, gr)[0]
                mcampo(pg_g, "contada").fill(str(sys_fis + 3))
                check("%s inventário exige motivo" % n, mbtn(pg_g, "Registrar ajuste").is_disabled())
                mcampo(pg_g, "Motivo").fill("Contagem de teste: sobraram 3")
                mbtn(pg_g, "Registrar ajuste").click()
                fechou(pg_g)
                check("%s inventário ajustou o físico (%s->%s)" % (n, sys_fis, estoque_branco(adm, gr)[0]), estoque_branco(adm, gr)[0] == sys_fis + 3)
                pg_g.locator("button:visible", has_text=re.compile("Movimenta")).first.click()
                pg_g.wait_for_timeout(800)
                check("%s histórico de movimentações mostra o ajuste" % n, "Ajuste de inventário" in texto(pg_g))
                shot(pg_g, "estoque_movimentacoes")

            # ---------- Agenda, escopo do vendedor e layout
            ir(pg_g, "/gestao/agenda")
            pg_g.wait_for_timeout(500)
            check("%s agenda lista entregas/retiradas" % n, "Retirada" in texto(pg_g) or "Entrega" in texto(pg_g))
            ir(pg_v, "/gestao/agenda")
            check("%s vendedor consulta a agenda (leitura)" % n, "Agenda" in texto(pg_v))
            pedido(pg_v, p1)
            check("%s vendedor NÃO vê ações de saída, entrega, montagem ou pós-venda" % n,
                  pg_v.get_by_role("button", name=re.compile("Registrar saída|Agendar|Concluir|Abrir ocorrência")).count() == 0)
            ir(pg_v, "/gestao/encomendas")
            check("%s vendedor não acessa Encomendas" % n, "/gestao/encomendas" not in pg_v.url)
            pg2 = entrar(mk(), ven2["email"], L.TEST_PASSWORD)
            pg2.goto("%s/gestao/pedidos/%s" % (L.BASE_URL, p1))
            pg2.wait_for_load_state("networkidle")
            check("%s outro vendedor não enxerga a venda alheia" % n, "não encontrada" in texto(pg2).lower() or "Pedido #" not in texto(pg2))
            if w < 500:
                for rota, nome in [("/gestao/pedidos/%s" % p1, "detalhe do pedido"), ("/gestao/encomendas", "encomendas"),
                                   ("/gestao/pos-venda", "pós-venda"), ("/gestao/estoque", "estoque"), ("/gestao/agenda", "agenda"),
                                   ("/gestao/pedidos", "lista de pedidos")]:
                    ir(pg_g, rota)
                    pg_g.wait_for_timeout(300)
                    sem_overflow(pg_g, "%s %s" % (n, nome))
                    shot(pg_g, "mobile_%s" % nome.replace(" ", "_"))
            check("%s sem erros 5xx nem exceções na sessão" % n, not (pg_g.errs + pg_v.errs + pg2.errs), pg_g.errs + pg_v.errs + pg2.errs)
            cg.close()
            cv.close()

        # ---------- Vitrine pública (anônimo)
        secao("Vitrine pública")
        anon = browser.new_context(viewport={"width": 1366, "height": 900})
        a = anon.new_page()
        a.set_default_timeout(15000)
        posts, erros = [], []
        a.on("request", lambda r: posts.append(r.url) if r.method == "POST" and "/pedidos" in r.url else None)
        a.on("response", lambda r: erros.append((r.status, r.url)) if r.status >= 400 and "/api/" in r.url else None)
        a.goto(L.BASE_URL + "/loja")
        a.wait_for_load_state("networkidle")
        check("vitrine /loja carrega produtos", a.locator("body").inner_text().strip() != "" and a.locator("a[href^='/produto/']").count() > 0)
        a.goto("%s/produto/%s" % (L.BASE_URL, gr["id"]))
        a.wait_for_load_state("networkidle")
        check("detalhe do produto exibe nome e preço", gr["nome"][:12] in a.locator("body").inner_text() and "1.000" in a.locator("body").inner_text().replace(" ", " "))
        a.get_by_text("Adicionar ao Carrinho").first.click()
        a.wait_for_timeout(500)
        a.goto(L.BASE_URL + "/carrinho")
        a.wait_for_load_state("networkidle")
        link = a.get_by_role("link", name=re.compile("Finalizar pelo WhatsApp"))
        href = link.get_attribute("href") if link.count() else ""
        check("carrinho: link do WhatsApp com a mensagem do pedido", "wa.me/" in (href or "") and "Guarda-roupa" in urllib.parse.unquote(href or ""), href)
        check("carrinho NÃO grava venda (nenhum POST em /pedidos)", not posts, posts)
        a.goto(L.BASE_URL + "/gestao/pedidos")
        a.wait_for_load_state("networkidle")
        check("anônimo em /gestao é levado ao login", "/login" in a.url)
        check("vitrine sem respostas 4xx/5xx de API", not erros, erros)
        browser.close()


def finalizar():
    adm = L.login_admin()
    if ctx.get("snap"):
        L.config_restaurar(adm, ctx["snap"], ctx.get("criadas", []))
    L.limpar()


if __name__ == "__main__":
    L.exigir_config()
    L.executar(principal, finalizar)
