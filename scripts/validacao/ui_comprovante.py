#!/usr/bin/env python3
"""Validação no NAVEGADOR do comprovante de compra (pedido de venda, sem valor fiscal): impressão A4, PDF, celular.

Cenários: um produto (Pix pendente); vários produtos com variação, nomes e endereço longos (dinheiro parcial, entrega e
montagem agendadas, observação ao cliente); cartão parcelado pago; encomenda + pronta entrega; cancelado; pedido antigo
incompleto. Confere: aviso "não é documento fiscal", ausência de observações internas, de "quitado"/"recibo", uma
página A4 por pedido curto, menu fora da visualização e sem rolagem horizontal no celular.

Ambiente: SOMENTE o local descartável. Requer LP_ADMIN_EMAIL, LP_ADMIN_PASSWORD e Playwright. Variáveis opcionais:
LP_SHOTS_DIR (capturas e PDFs; padrão validacao-out/shots) e LP_PG_CONTAINER (padrão lp-val-pg, para criar o pedido antigo).
"""
import os
import re
import subprocess

import lib as L
from lib import call, check, secao
from playwright.sync_api import sync_playwright

SHOTS = os.path.abspath(os.environ.get("LP_SHOTS_DIR") or os.path.join(os.path.dirname(L.AQUI), "..", "validacao-out", "shots"))
os.makedirs(SHOTS, exist_ok=True)
ctx = {}

END_LONGO = {"apelido": "Casa", "cep": "06268-000", "numero": "1000", "bairro": "Baronesa", "cidade": "Osasco", "uf": "SP",
             "logradouro": "Avenida Presidente Médici com um nome de logradouro bastante comprido para testar a quebra de linha",
             "complemento": "Bloco B, apartamento 1204, torre norte, próximo ao mercado e à padaria da esquina", "principal": True}


def principal():
    adm = L.preparar()
    ctx["adm"] = adm
    suf = L.SUFIXO

    def cliente(nome, endereco=None):
        corpo = {"nome": "%s %s" % (nome, suf), "cpf": L.cpf_formatado(L.cpf_aleatorio()), "telefone": L.telefone_aleatorio(),
                 "confirmarDuplicidade": True}
        if endereco:
            corpo["enderecos"] = [endereco]
        s, c = call("POST", "/clientes", adm, corpo)
        assert s == 200, (s, c)
        return c

    def venda(cli, itens, **kw):
        s, v = L.registrar_venda(adm, cli, itens, **kw)
        assert s == 200, (s, v)
        s, v = L.confirmar(adm, v["id"])
        assert s == 200, (s, v)
        return v

    def receber(vid, valor, **extra):
        return call("POST", "/vendas/%s/recebimentos" % vid, adm, dict({"valor": valor}, **extra), {"Idempotency-Key": L.chave()})

    p1 = L.criar_produto(adm, "Guarda-roupa de seis portas com espelho e gavetas internas em MDF branco texturizado", 2899.90, estoque=50)
    p2 = L.criar_produto(adm, "Sofá retrátil", 1500.00, estoque=0, variacoes=[{"cor": "Cinza chumbo", "tamanho": "3 lugares", "adicionalPreco": 0, "estoque": 20}])
    p3 = L.criar_produto(adm, "Cômoda", 700.00, estoque=50)
    pe = L.criar_produto(adm, "Mesa sob encomenda", 1800.00, estoque=0, modalidade="ENCOMENDA", prazo=15)
    cen = {}

    cen["um_produto_pix_pendente"] = venda(cliente("Maria Simples"), [L.item(p3, 1)])["id"]

    c2 = cliente("Joao Multiplos", END_LONGO)
    ender = call("GET", "/clientes/%s" % c2["id"], adm)[1]["enderecos"][0]["id"]
    v = venda(c2, [L.item(p1, 2), L.item(p2, 1, variacao=p2["variacoes"][0]), L.item(p3, 3)], forma="DINHEIRO", tipo="ENTREGA", enderecoId=ender)
    if not call("GET", "/financeiro/caixa/atual", adm)[1].get("aberta"):
        call("POST", "/financeiro/caixa/abrir", adm, {"saldoInicial": 0})
    receber(v["id"], round(v["total"] / 2, 2))
    call("POST", "/vendas/%s/entrega/agendar" % v["id"], adm, {"data": L.amanha(4), "periodo": "TARDE", "equipe": "Equipe interna SIGILO", "observacao": "NOTA INTERNA SIGILO"})
    call("POST", "/vendas/%s/montagem/agendar" % v["id"], adm, {"data": L.amanha(5), "periodo": "MANHA", "responsavel": "Montador interno"})
    call("PUT", "/vendas/%s/observacao-cliente" % v["id"], adm, {"texto": "Entrada pelo portão lateral; avisar o porteiro com 30 minutos de antecedência."})
    cen["varios_dinheiro_parcial_agendados_endereco_longo"] = v["id"]

    v = venda(cliente("Ana Cartao"), [L.item(p2, 1, variacao=p2["variacoes"][0])], forma="CARTAO", parcelas=3)
    receber(v["id"], v["total"], operadora="OPERADORA X", tipoCartao="CREDITO")
    cen["cartao_3x_pago"] = v["id"]

    v = venda(cliente("Carlos Encomenda"), [L.item(pe, 1, modalidade="ENCOMENDA"), L.item(p3, 1)])
    receber(v["id"], v["total"])
    cen["encomenda_e_pronta_entrega_pago"] = v["id"]

    v = venda(cliente("Paula Cancelada"), [L.item(p3, 1)])
    call("POST", "/vendas/%s/cancelar" % v["id"], adm, {"motivo": "Desistência"})
    cen["cancelado"] = v["id"]

    cont = os.environ.get("LP_PG_CONTAINER", "lp-val-pg")
    subprocess.run(["docker", "exec", cont, "psql", "-U", "lpval", "-d", "lojas_val", "-qc",
                    "insert into pedidos (usuario_id, status, status_comercial, status_pagamento, status_entrega, status_montagem, total, "
                    "subtotal, desconto, criado_em, atualizado_em, revisao_legado, version) select id, 'PAGO', 'LEGADO', 'NAO_INFORMADO', "
                    "'NAO_INFORMADO', 'NAO_INFORMADO', 777.00, 777.00, 0, now(), now(), true, 0 from users where email = lower('%s')" % L.ADMIN_EMAIL],
                   check=False)
    s, lista = call("GET", "/vendas?status=LEGADO&tamanho=5", adm)
    leg = [x["id"] for x in (lista.get("conteudo") or [])] if s == 200 else []
    if leg:
        cen["pedido_antigo_incompleto"] = max(leg)

    secao("Rascunho e acesso direto")
    s, rasc = L.registrar_venda(adm, cliente("Rascunho"), [L.item(p3, 1)])
    s, b = call("GET", "/vendas/%s/comprovante" % rasc["id"], adm)
    check("rascunho não tem comprovante (400)", s == 400, (s, b))
    ven = L.novo_usuario(adm, "VENDEDOR", "vend")
    s, b = call("GET", "/vendas/%s/comprovante" % cen["cancelado"], ven["token"])
    check("vendedor não emite comprovante de venda alheia (404)", s == 404, (s, b))
    s, b = L.bruto("GET", "/api/v1/vendas/%s/comprovante" % cen["cancelado"])[:2]
    check("sem login não há comprovante (401)", s == 401, s)

    secao("Visualização e impressão")
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=not os.environ.get("LP_HEADFUL"))
        bc = browser.new_context(viewport={"width": 1100, "height": 900})
        pg = bc.new_page()
        pg.goto(L.BASE_URL + "/login"); pg.fill("input[type=email]", L.ADMIN_EMAIL); pg.fill("input[type=password]", L.ADMIN_PASSWORD)
        pg.click("form:has(input[type=password]) button[type=submit]"); pg.wait_for_url(lambda u: "/login" not in u)
        for nome, vid in cen.items():
            q = bc.new_page()
            q.goto(L.BASE_URL + "/gestao/pedidos/%s/imprimir" % vid); q.wait_for_load_state("networkidle"); q.wait_for_timeout(400)
            txt = q.locator("article.comprovante").inner_text()
            pdf = q.pdf(format="A4", prefer_css_page_size=True)
            open(os.path.join(SHOTS, "comprovante_%s.pdf" % nome), "wb").write(pdf)
            q.screenshot(path=os.path.join(SHOTS, "comprovante_%s.png" % nome), full_page=True)
            paginas = len(re.findall(rb"/Type\s*/Page[^s]", pdf))
            check("%s: 'Pedido de venda — não é documento fiscal', sem NF-e/DANFE como identificação" % nome,
                  "PEDIDO DE VENDA" in txt and "não é documento fiscal" in txt.lower(), txt[:200])
            check("%s: sem observação interna, sem 'quitado' nem 'recibo'" % nome, "SIGILO" not in txt and not re.search(r"quitad|recibo", txt, re.I))
            check("%s: cabe em %d página(s) A4 (menu fora da visualização)" % (nome, paginas), paginas <= 2 and not q.locator(".site-navbar").is_visible())
            if nome == "cancelado":
                check("cancelado: identificação clara", "PEDIDO CANCELADO" in txt)
            if nome == "pedido_antigo_incompleto":
                check("pedido antigo: 'Não informado' em vez de dados presumidos", "Não informado" in txt and "Retirada na loja" not in txt)
            if nome == "varios_dinheiro_parcial_agendados_endereco_longo":
                check("endereço desta entrega completo, entrega e montagem agendadas e observação ao cliente",
                      "Bloco B" in txt and "Agendada para" in txt and "portão lateral" in txt and "Parcial" in txt, txt[:300])
            if nome == "encomenda_e_pronta_entrega_pago":
                check("encomenda indica previsão de chegada separada", "previsão de chegada" in txt.lower())
            q.close()
        m = browser.new_context(viewport={"width": 390, "height": 844}, is_mobile=True, has_touch=True).new_page()
        m.goto(L.BASE_URL + "/login"); m.fill("input[type=email]", L.ADMIN_EMAIL); m.fill("input[type=password]", L.ADMIN_PASSWORD)
        m.click("form:has(input[type=password]) button[type=submit]"); m.wait_for_url(lambda u: "/login" not in u)
        m.goto(L.BASE_URL + "/gestao/pedidos/%s/imprimir" % cen["cartao_3x_pago"]); m.wait_for_load_state("networkidle")
        check("celular: sem rolagem horizontal", not m.evaluate("document.documentElement.scrollWidth > innerWidth"))
        # impressão não altera nada: situação do pedido igual antes e depois
        antes = call("GET", "/vendas/%s" % cen["cartao_3x_pago"], adm)[1]
        call("GET", "/vendas/%s/comprovante" % cen["cartao_3x_pago"], adm)
        depois = call("GET", "/vendas/%s" % cen["cartao_3x_pago"], adm)[1]
        check("reimpressão não altera o pedido (versão, pagamento e total)", antes["version"] == depois["version"]
              and antes["statusPagamento"] == depois["statusPagamento"] and antes["total"] == depois["total"])
        browser.close()


def finalizar():
    adm = L.login_admin()
    if adm:   # deixa o caixa como estava: fechado
        s, a = call("GET", "/financeiro/caixa/atual", adm)
        if s == 200 and a.get("aberta"):
            call("POST", "/financeiro/caixa/fechar", adm, {"saldoContado": a["sessao"]["saldoEsperado"]})
    L.limpar()


if __name__ == "__main__":
    L.exigir_config()
    L.executar(principal, finalizar)
