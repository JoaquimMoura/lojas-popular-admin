#!/usr/bin/env python3
"""Validação por API da Etapa 2 (atendimento e pós-venda): saída com baixa de estoque, entrega, montagem,
comprovantes privados, encomendas, inventário, pós-venda e agenda.

Ambiente: SOMENTE o local descartável (ver README.md). Requer LP_ADMIN_EMAIL e LP_ADMIN_PASSWORD.
A configuração comercial é salva no início e RESTAURADA no final (bloco finally). A decisão D05
(pagamento exigido para expedir) é alternada com valores de TESTE (pendente, falso e verdadeiro).
Código de saída: 0 = tudo OK; 1 = alguma verificação falhou; 2 = configuração/ambiente ausente.
"""
import urllib.parse

import lib as L
from lib import call, check, g, info, secao

ctx = {}


def principal():
    adm = L.preparar()
    ctx["snap"] = L.config_salvar(adm)
    L.rotulo_teste()
    info("D05 (pagamento exigido para expedir) será alternado entre pendente / falso / verdadeiro (valores de TESTE).")
    ctx["criadas"] = L.condicoes_teste(adm, ctx["snap"])
    ger = L.novo_usuario(adm, "GERENTE", "ger")
    v1 = L.novo_usuario(adm, "VENDEDOR", "vend")
    v2 = L.novo_usuario(adm, "VENDEDOR", "vend2")
    cliente = L.criar_cliente(ger["token"], nome="Cliente Etapa2 %s" % L.SUFIXO)
    pr = {
        "A": L.criar_produto(adm, "Pronta A", 500.00, estoque=30),
        "B": L.criar_produto(adm, "Pronta B", 300.00, variacoes=[
            {"cor": "Cinza", "tamanho": "P", "adicionalPreco": 0, "estoque": 10},
            {"cor": "Azul", "tamanho": "G", "adicionalPreco": 100, "estoque": 10}]),
        "E": L.criar_produto(adm, "Encomenda E", 800.00, estoque=0, modalidade="ENCOMENDA", prazo=15),
        "N": L.criar_produto(adm, "Sem saldo N", 200.00, variacoes=[{"cor": "Preto", "tamanho": "U", "adicionalPreco": 0, "estoque": None}]),
    }
    info("produtos de teste: " + ", ".join("%s=%s" % (k, p["id"]) for k, p in pr.items()))
    c = {"adm": adm, "ger": ger, "v1": v1, "v2": v2, "cliente": cliente, "pr": pr}

    secao("1. D05 pendente bloqueia a saída")
    L.config_aplicar(adm, L.LIMITE_TESTE, L.ARRED_TESTE, ["ADMIN", "GERENTE"], None)
    d05_pendente(c)
    secao("2. Fluxo de pronta entrega (D05 = falso)")
    L.config_aplicar(adm, L.LIMITE_TESTE, L.ARRED_TESTE, ["ADMIN", "GERENTE"], False)
    pronta_entrega(c)
    secao("3. Montagem")
    montagem(c)
    secao("4. D05 = verdadeiro exige pagamento quitado")
    L.config_aplicar(adm, L.LIMITE_TESTE, L.ARRED_TESTE, ["ADMIN", "GERENTE"], True)
    d05_verdadeiro(c)
    L.config_aplicar(adm, L.LIMITE_TESTE, L.ARRED_TESTE, ["ADMIN", "GERENTE"], False)
    secao("5. Encomendas")
    encomendas(c)
    secao("6. Inventário")
    inventario(c)
    secao("7. Pós-venda")
    pos_venda(c)
    secao("8. Agenda")
    agenda(c)


# ---------------------------------------------------------------------------

def confirmada(c, itens, tipo="ENTREGA", quem="ger", **kw):
    """Registra e confirma uma venda; devolve o detalhe."""
    tok = c[quem]["token"]
    s, v = L.registrar_venda(tok, c["cliente"], itens, tipo=tipo, **kw)
    if s != 200:
        raise RuntimeError("registrar venda: %s %s" % (s, v))
    s, v = L.confirmar(tok, v["id"])
    if s != 200:
        raise RuntimeError("confirmar venda: %s %s" % (s, v))
    return v


def detalhe(c, vid):
    return call("GET", "/vendas/%s" % vid, c["ger"]["token"])[1]


def agendar(c, vid, dias=2, periodo="MANHA"):
    return call("POST", "/vendas/%s/entrega/agendar" % vid, c["ger"]["token"],
                {"data": L.amanha(dias), "periodo": periodo, "equipe": "Equipe Teste", "observacao": "agendado pelo roteiro"})


def saida(c, vid, k):
    return call("POST", "/vendas/%s/saida" % vid, c["ger"]["token"], headers={"Idempotency-Key": k})


def movs(c, vid):
    return g(call("GET", "/estoque/movimentacoes?pedidoId=%s&tamanho=100" % vid, c["ger"]["token"])[1], "conteudo", default=[])


def fis(c, produto, variacao=None):
    sd = L.saldo(c["adm"], produto, variacao)
    return (sd["fisico"], sd["reservado"], sd["disponivel"]) if sd else None


# ---------------------------------------------------------------------------

def d05_pendente(c):
    gt, vt, pa = c["ger"]["token"], c["v1"]["token"], c["pr"]["A"]
    v = confirmada(c, [L.item(pa, 2)])
    vid = v["id"]
    s, b = saida(c, vid, L.chave())
    check("saída sem entrega agendada é recusada (400)", s == 400 and "Agende" in str(g(b, "message")), (s, b))
    s, b = agendar(c, vid)
    check("gerente agenda a entrega (statusEntrega AGENDADA)", s == 200 and g(b, "statusEntrega") == "AGENDADA", (s, b))
    s, b = call("POST", "/vendas/%s/entrega/agendar" % vid, vt, {"data": L.amanha(2), "periodo": "MANHA"})
    check("vendedor não agenda entrega (403)", s == 403, (s, b))
    s, b = agendar(c, vid)
    check("agendar de novo exige reagendamento (400)", s == 400 and "reagend" in str(g(b, "message")).lower(), (s, b))
    s, b = call("POST", "/vendas/%s/entrega/reagendar" % vid, gt, {"data": "2000-01-01", "periodo": "TARDE", "motivo": "teste"})
    check("data no passado é recusada (400)", s == 400 and "passado" in str(g(b, "message")), (s, b))
    antes = fis(c, pa)
    s, b = saida(c, vid, L.chave())
    check("D05 pendente: saída bloqueada (422 CONFIGURACAO_PENDENTE citando D05)",
          s == 422 and g(b, "code") == "CONFIGURACAO_PENDENTE" and "D05" in str(g(b, "message")), (s, b))
    d = detalhe(c, vid)
    check("D05 pendente: detalhe traz acoes.bloqueios.saida com D05", "D05" in str(g(d, "acoes", "bloqueios", "saida")), g(d, "acoes"))
    check("D05 pendente: nada foi baixado do estoque", fis(c, pa) == antes, (antes, fis(c, pa)))
    s, cfg = call("GET", "/vendas/configuracao", vt)
    check("D05 aparece em pendencias para o vendedor", "D05" in {p["codigo"] for p in g(cfg, "pendencias", default=[])}, cfg)
    ctx["venda_pendente"] = vid
    call("POST", "/vendas/%s/cancelar" % vid, gt, {"motivo": "limpeza D05"})


def pronta_entrega(c):
    gt, vt, pa = c["ger"]["token"], c["v1"]["token"], c["pr"]["A"]
    v = confirmada(c, [L.item(pa, 2)], quem="v1")  # venda do vendedor 1; a operação é do gerente
    vid = v["id"]
    ctx["entregue"] = vid
    s, b = agendar(c, vid)
    check("agendar entrega (data futura)", s == 200 and g(b, "entrega", "status") == "AGENDADA", (s, b))
    s, b = call("POST", "/vendas/%s/montagem/agendar" % vid, gt, {"data": L.amanha(3), "periodo": "TARDE", "responsavel": "Montador Teste"})
    check("agendar montagem junto da entrega", s == 200 and g(b, "statusMontagem") == "AGENDADA", (s, b))
    ctx["agenda_venda"] = vid
    f0, r0, d0 = fis(c, pa)
    s, b = call("POST", "/vendas/%s/saida" % vid, gt)
    check("saída sem Idempotency-Key é recusada (400)", s == 400 and "Idempotency" in str(g(b, "message")), (s, b))
    k1 = L.chave()
    s, b = saida(c, vid, k1)
    check("saída registrada (statusEntrega SAIU, baixa realizada)", s == 200 and g(b, "statusEntrega") == "SAIU" and g(b, "entrega", "baixaRealizada") is True, (s, g(b, "statusEntrega")))
    f1, r1, d1 = fis(c, pa)
    check("baixa: físico -2 e reservado -2 (disponível inalterado)", (f1, r1, d1) == (f0 - 2, r0 - 2, d0), ((f0, r0, d0), (f1, r1, d1)))
    s, b = saida(c, vid, k1)
    check("repetir a mesma chave: 200 e SEM nova baixa", s == 200 and fis(c, pa)[0] == f1, (s, fis(c, pa)))
    s, b = saida(c, vid, L.chave())
    check("outra chave depois da saída é recusada (400)", s == 400, (s, b))
    ms = [m for m in movs(c, vid) if m["tipo"] == "SAIDA_VENDA"]
    check("movimentações: exatamente 1 SAIDA_VENDA de -2 com saldo anterior/posterior",
          len(ms) == 1 and ms[0]["quantidade"] == -2 and ms[0]["saldoAnterior"] == f0 and ms[0]["saldoPosterior"] == f0 - 2, ms)
    s, b = call("POST", "/vendas/%s/cancelar" % vid, gt, {"motivo": "tentativa"})
    check("cancelar venda que já saiu é bloqueado (400, devolução em Pós-venda)", s == 400 and "saiu" in str(g(b, "message")), (s, b))
    d = detalhe(c, vid)
    check("detalhe: acoes.bloqueios.cancelar informa o motivo", "cancelar" in g(d, "acoes", "bloqueios", default={}), g(d, "acoes"))
    s, b = call("POST", "/vendas/%s/montagem/concluir" % vid, gt, campos={"observacao": "montei"})
    check("concluir montagem antes da entrega é recusado (400)", s == 400 and "depois da entrega" in str(g(b, "message")), (s, b))

    s, b = call("POST", "/vendas/%s/entrega/tentativa-frustrada" % vid, gt, {"motivo": ""})
    check("tentativa frustrada exige motivo (400)", s == 400, (s, b))
    s, b = call("POST", "/vendas/%s/entrega/tentativa-frustrada" % vid, gt, {"motivo": "cliente ausente"})
    check("tentativa frustrada registrada (TENTATIVA_FRUSTRADA)", s == 200 and g(b, "statusEntrega") == "TENTATIVA_FRUSTRADA", (s, b))
    s, b = saida(c, vid, L.chave())
    check("nova saída sem reagendar é recusada (400)", s == 400 and "reagende" in str(g(b, "message")), (s, b))
    s, b = call("POST", "/vendas/%s/entrega/concluir" % vid, gt, campos={"recebedor": "Fulano"})
    check("concluir entrega que não está em rota é recusado (400)", s == 400, (s, b))
    s, b = call("POST", "/vendas/%s/entrega/reagendar" % vid, gt, {"data": L.amanha(4), "periodo": "DIA_INTEIRO", "motivo": ""})
    check("reagendar exige motivo (400)", s == 400, (s, b))
    s, b = call("POST", "/vendas/%s/entrega/reagendar" % vid, gt, {"data": L.amanha(4), "periodo": "DIA_INTEIRO", "motivo": "cliente pediu"})
    check("reagendar (AGENDADA de novo)", s == 200 and g(b, "statusEntrega") == "AGENDADA", (s, b))
    s, b = saida(c, vid, L.chave())
    check("nova saída após reagendar: 200", s == 200 and g(b, "statusEntrega") == "SAIU", (s, b))
    check("nova saída NÃO baixa de novo (físico igual ao da 1ª saída)", fis(c, pa)[0] == f1, (f1, fis(c, pa)))
    ms = [m for m in movs(c, vid) if m["tipo"] == "SAIDA_VENDA"]
    check("movimentações continuam com 1 única SAIDA_VENDA", len(ms) == 1, ms)
    d = detalhe(c, vid)
    check("histórico de eventos da entrega: agendada, saída, frustrada, reagendada, saída",
          [e["tipo"] for e in g(d, "entrega", "eventos", default=[])][:5] == ["AGENDADA", "SAIDA", "TENTATIVA_FRUSTRADA", "REAGENDADA", "SAIDA"],
          [e["tipo"] for e in g(d, "entrega", "eventos", default=[])])

    # conclusão com comprovação
    s, b = call("POST", "/vendas/%s/entrega/concluir" % vid, gt, campos={"observacao": "sem comprovação"})
    check("concluir entrega sem recebedor e sem arquivo é recusado (400)", s == 400 and "comprovação" in str(g(b, "message")), (s, b))
    s, b = call("POST", "/vendas/%s/entrega/concluir" % vid, gt, campos={"recebedor": "Fulano"},
                arquivos={"arquivo": ("malicioso.png", b"isto nao e uma imagem png", "image/png")})
    check("arquivo cujo conteúdo não é do tipo informado é recusado (400)", s == 400, (s, b))
    s, b = call("POST", "/vendas/%s/entrega/concluir" % vid, gt, campos={"recebedor": "Fulano"},
                arquivos={"arquivo": ("script.exe", b"MZ\x90\x00\x03\x00\x00\x00", "application/octet-stream")})
    check("extensão não aceita é recusada (400)", s == 400, (s, b))
    png = L.png_minimo(2, 2)
    s, b = call("POST", "/vendas/%s/entrega/concluir" % vid, gt, campos={"recebedor": "Fulano de Tal", "observacao": "entregue na portaria"},
                arquivos={"arquivo": ("comprovante.png", png, "image/png")})
    check("concluir entrega com recebedor + arquivo PNG (ENTREGUE)", s == 200 and g(b, "statusEntrega") == "ENTREGUE", (s, b))
    caminho = g(b, "entrega", "comprovanteArquivo")
    check("caminho do comprovante fica na área privada", str(caminho).startswith("privado/entregas/%s/" % vid), caminho)
    q = "/arquivos?caminho=" + urllib.parse.quote(str(caminho), safe="/")
    st, corpo, cab = L.bruto("GET", "/api/v1" + q, gt)
    check("comprovante com token: 200, image/png e bytes idênticos", st == 200 and corpo == png and "image/png" in str(cab.get("content-type")), (st, len(corpo), cab.get("content-type")))
    st = L.bruto("GET", "/api/v1" + q, vt)[0]
    check("comprovante: vendedor RESPONSÁVEL pela venda abre (200)", st == 200, st)
    st = L.bruto("GET", "/api/v1" + q, c["v2"]["token"])[0]
    check("comprovante: outro vendedor NÃO abre (404)", st == 404, st)
    check("comprovante SEM token: 401", L.bruto("GET", "/api/v1" + q)[0] == 401)
    check("GET /uploads/<comprovante> público é negado (401/403)", L.bruto("GET", "/" + "uploads/" + caminho)[0] in (401, 403))
    check("GET /uploads/<comprovante> mesmo com token é negado (403)", L.bruto("GET", "/uploads/" + str(caminho), gt)[0] in (401, 403))
    st = L.bruto("GET", "/api/v1/arquivos?caminho=" + urllib.parse.quote("privado/../../etc/passwd", safe="/"), gt)[0]
    check("path traversal em /arquivos é negado (404)", st in (400, 404), st)
    check("caminho fora da área privada é negado (404)", L.bruto("GET", "/api/v1/arquivos?caminho=produtos/exemplo/exemplo.png", gt)[0] == 404)
    check("imagem de produto segue pública em /uploads (200)", L.bruto("GET", "/uploads/produtos/exemplo/exemplo.png")[0] == 200)
    d = detalhe(c, vid)
    check("detalhe: entrega concluída com recebedor", g(d, "entrega", "recebedorNome") == "Fulano de Tal" and g(d, "entrega", "tentativasFrustradas") == 1, g(d, "entrega"))
    s, b = saida(c, vid, L.chave())
    check("saída depois de entregue é recusada (400)", s == 400, (s, b))


def montagem(c):
    gt, pa = c["ger"]["token"], c["pr"]["A"]
    vid = ctx["entregue"]
    s, b = call("POST", "/vendas/%s/montagem/concluir" % vid, gt, campos={})
    check("concluir montagem sem evidência (nem foto nem texto) é recusado (400)", s == 400 and "evidência" in str(g(b, "message")), (s, b))
    s, b = call("POST", "/vendas/%s/montagem/concluir" % vid, gt, campos={"observacao": "montagem concluída sem problemas"},
                arquivos={"arquivo": ("montagem.png", L.png_minimo(), "image/png")})
    check("concluir montagem com observação + foto (CONCLUIDA)", s == 200 and g(b, "statusMontagem") == "CONCLUIDA", (s, b))
    caminho = g(b, "montagem", "evidenciaArquivo")
    check("evidência da montagem é privada e acessível com token",
          str(caminho).startswith("privado/montagens/") and L.bruto("GET", "/api/v1/arquivos?caminho=" + urllib.parse.quote(str(caminho), safe="/"), gt)[0] == 200, caminho)
    s, b = call("POST", "/vendas/%s/montagem/agendar" % vid, gt, {"data": L.amanha(3), "periodo": "MANHA"})
    check("montagem concluída não pode ser reagendada (400)", s == 400, (s, b))

    # montagem 'não necessária' e agendamento posterior
    v = confirmada(c, [L.item(pa, 1)], tipo="RETIRADA")
    s, b = call("POST", "/vendas/%s/montagem/nao-necessaria" % v["id"], gt, {"motivo": ""})
    check("montagem 'não necessária' exige motivo (400)", s == 400, (s, b))
    s, b = call("POST", "/vendas/%s/montagem/nao-necessaria" % v["id"], gt, {"motivo": "produto vem montado"})
    check("montagem 'não necessária' com motivo (NAO_NECESSARIA)", s == 200 and g(b, "statusMontagem") == "NAO_NECESSARIA" and g(b, "montagem", "motivoDispensa") == "produto vem montado", (s, b))
    s, b = call("POST", "/vendas/%s/montagem/agendar" % v["id"], gt, {"data": L.amanha(3), "periodo": "MANHA"})
    check("não é possível agendar montagem dispensada (400)", s == 400, (s, b))
    s, b = call("POST", "/vendas/%s/montagem/nao-necessaria" % vid, gt, {"motivo": "tarde demais"})
    check("não é possível dispensar montagem já concluída (400)", s == 400, (s, b))
    call("POST", "/vendas/%s/cancelar" % v["id"], gt, {"motivo": "limpeza"})

    # montagem agendada some do agendamento se a venda for cancelada antes da saída
    v = confirmada(c, [L.item(pa, 1)])
    agendar(c, v["id"])
    call("POST", "/vendas/%s/montagem/agendar" % v["id"], gt, {"data": L.amanha(3), "periodo": "MANHA"})
    s, b = call("POST", "/vendas/%s/cancelar" % v["id"], gt, {"motivo": "cliente desistiu"})
    check("cancelar antes da saída encerra entrega (CANCELADA) e montagem (NAO_NECESSARIA)",
          s == 200 and g(b, "entrega", "status") == "CANCELADA" and g(b, "montagem", "status") == "NAO_NECESSARIA", (s, g(b, "entrega"), g(b, "montagem")))


def d05_verdadeiro(c):
    gt, pa = c["ger"]["token"], c["pr"]["A"]
    v = confirmada(c, [L.item(pa, 1)])
    vid = v["id"]
    agendar(c, vid)
    antes = fis(c, pa)
    s, b = saida(c, vid, L.chave())
    check("D05=verdadeiro: venda não paga tem a saída recusada (400, exige pagamento quitado)",
          s == 400 and "pagamento" in str(g(b, "message")).lower(), (s, b))
    d = detalhe(c, vid)
    check("D05=verdadeiro: detalhe informa o bloqueio da saída", "pagamento" in str(g(d, "acoes", "bloqueios", "saida")).lower(), g(d, "acoes"))
    check("D05=verdadeiro: nada foi baixado", fis(c, pa) == antes, (antes, fis(c, pa)))
    call("POST", "/vendas/%s/cancelar" % vid, gt, {"motivo": "limpeza"})
    info("a quitação de uma venda só ocorre pelo fluxo de pagamento (Etapa 3): não é simulada por este roteiro.")


def encomendas(c):
    adm, gt, vt = c["adm"], c["ger"]["token"], c["v1"]["token"]
    pa, pe = c["pr"]["A"], c["pr"]["E"]
    check("produto ENCOMENDA não é vendido como pronta entrega (400)",
          L.registrar_venda(gt, c["cliente"], [L.item(pe, 1)])[0] == 400)
    pa0 = fis(c, pa)
    v1 = confirmada(c, [L.item(pe, 2, modalidade="ENCOMENDA"), L.item(pa, 1)])
    vid = v1["id"]
    check("confirmação reserva só o item de pronta entrega (1 reserva)", len(v1["reservas"]) == 1, v1["reservas"])
    s, lista = call("GET", "/encomendas", gt)
    mine = [e for e in lista if e["pedidoId"] == vid] if s == 200 else []
    check("GET /encomendas lista a encomenda da venda (AGUARDANDO_PEDIDO, prazo padrão 15 dias)",
          len(mine) == 1 and mine[0]["status"] == "AGUARDANDO_PEDIDO" and mine[0]["prazoPadrao"] == "15 dias", mine)
    enc = mine[0]
    s, b = call("PUT", "/encomendas/%s" % enc["id"], gt, {"fornecedor": "Fornecedor Teste", "referenciaFornecedor": "REF-%s" % L.SUFIXO, "previsaoChegada": "2000-01-01"})
    check("atualizar encomenda com previsão vencida: PEDIDO_REALIZADO e atrasada=true", s == 200 and g(b, "status") == "PEDIDO_REALIZADO" and g(b, "atrasada") is True, (s, b))
    s, b = call("PUT", "/encomendas/%s" % enc["id"], gt, {"fornecedor": "Fornecedor Teste", "referenciaFornecedor": "REF-%s" % L.SUFIXO, "previsaoChegada": L.amanha(5)})
    check("atualizar previsão para o futuro: atrasada=false", s == 200 and g(b, "atrasada") is False and g(b, "previsaoChegada") == L.amanha(5), (s, b))
    check("vendedor não atualiza encomenda (403)", call("PUT", "/encomendas/%s" % enc["id"], vt, {"fornecedor": "x"})[0] == 403)
    s, b = call("POST", "/encomendas/%s/receber" % enc["id"], gt, {"quantidade": 2})
    check("receber sem Idempotency-Key é recusado (400)", s == 400 and "Idempotency" in str(g(b, "message")), (s, b))
    check("receber quantidade zero é recusado (400)", call("POST", "/encomendas/%s/receber" % enc["id"], gt, {"quantidade": 0}, {"Idempotency-Key": L.chave()})[0] == 400)
    # Recebimento PARCIAL do fornecedor é aceito (Etapa 3): a proibição vale só para a entrega parcial ao cliente
    pe_ini = fis(c, pe)
    kp = L.chave()
    s, b = call("POST", "/encomendas/%s/receber" % enc["id"], gt, {"quantidade": 1}, {"Idempotency-Key": kp})
    check("recebimento parcial do fornecedor: PARCIALMENTE_RECEBIDA, 1 reservada e 1 faltante",
          s == 200 and g(b, "status") == "PARCIALMENTE_RECEBIDA" and g(b, "quantidadeReservada") == 1 and g(b, "quantidadeFaltante") == 1, (s, b))
    check("recebimento parcial: físico +1 e reservado +1 (a unidade chegada não é vendida a outro)",
          fis(c, pe) == (pe_ini[0] + 1, pe_ini[1] + 1, pe_ini[2]), (pe_ini, fis(c, pe)))
    s, b = call("POST", "/encomendas/%s/receber" % enc["id"], gt, {"quantidade": 1}, {"Idempotency-Key": kp})
    check("repetir o recebimento parcial (mesma chave): sem nova entrada", s == 200 and fis(c, pe)[0] == pe_ini[0] + 1, (s, fis(c, pe)))

    # saída exige TODOS os itens reservados
    agendar(c, vid)
    d = detalhe(c, vid)
    d = detalhe(c, vid)
    check("detalhe: saída bloqueada enquanto a encomenda não foi recebida por completo",
          g(d, "acoes", "podeRegistrarSaida") is False and "não há entrega parcial" in str(g(d, "acoes", "bloqueios", "saida")).lower(), g(d, "acoes"))
    s, b = saida(c, vid, L.chave())
    check("saída com encomenda só parcialmente recebida é recusada (400, sem ENTREGA parcial ao cliente)",
          s == 400 and "não há entrega parcial" in str(g(b, "message")).lower() and "encomenda ainda não recebida por completo" in str(g(b, "message")), (s, b))
    check("nada foi baixado do item pronto", fis(c, pa)[0] == pa0[0], (pa0, fis(c, pa)))

    # recebimento completo
    pe0 = pe_ini
    k = L.chave()
    s, b = call("POST", "/encomendas/%s/receber" % enc["id"], gt, {"quantidade": 1}, {"Idempotency-Key": k})
    check("receber o restante: RECEBIDA e reserva ATIVA", s == 200 and g(b, "status") == "RECEBIDA" and g(b, "reserva") == "ATIVA", (s, b))
    check("recebimento: físico +2 e reservado +2 do produto encomendado", fis(c, pe) == (pe0[0] + 2, pe0[1] + 2, pe0[2]), (pe0, fis(c, pe)))
    mv = [m for m in movs(c, vid) if m["tipo"] == "ENTRADA_ENCOMENDA"]
    check("duas movimentações ENTRADA_ENCOMENDA (+1 e +1) registradas", len(mv) == 2 and sorted(m["quantidade"] for m in mv) == [1, 1], mv)
    s, b = call("POST", "/encomendas/%s/receber" % enc["id"], gt, {"quantidade": 1}, {"Idempotency-Key": k})
    check("repetir o recebimento com a mesma chave: 200 sem nova entrada", s == 200 and fis(c, pe)[0] == pe0[0] + 2, (s, fis(c, pe)))
    s, b = call("POST", "/encomendas/%s/receber" % enc["id"], gt, {"quantidade": 1}, {"Idempotency-Key": L.chave()})
    check("receber de novo com outra chave é recusado (400)", s == 400, (s, b))
    s, b = call("PUT", "/encomendas/%s" % enc["id"], gt, {"fornecedor": "Outro"})
    check("encomenda recebida não pode mais ser alterada (400)", s == 400, (s, b))
    s, lista = call("GET", "/encomendas?status=RECEBIDA", gt)
    check("filtro por status em /encomendas", s == 200 and any(e["id"] == enc["id"] for e in lista), s)

    pa1 = fis(c, pa)
    s, b = saida(c, vid, L.chave())
    check("saída com todos os itens reservados: 200", s == 200 and g(b, "statusEntrega") == "SAIU", (s, b))
    check("saída baixou o item pronto (-1) e o encomendado (-2)",
          fis(c, pa)[0] == pa1[0] - 1 and fis(c, pe)[0] == pe0[0] + 2 - 2, (pa1, fis(c, pa), fis(c, pe)))
    check("2 movimentações SAIDA_VENDA na venda", len([m for m in movs(c, vid) if m["tipo"] == "SAIDA_VENDA"]) == 2, movs(c, vid))

    # recebimento maior que o vendido e cancelamento
    v2 = confirmada(c, [L.item(pe, 1, modalidade="ENCOMENDA")])
    e2 = [e for e in call("GET", "/encomendas", gt)[1] if e["pedidoId"] == v2["id"]][0]
    f_antes = fis(c, pe)
    s, b = call("POST", "/encomendas/%s/receber" % e2["id"], gt, {"quantidade": 3}, {"Idempotency-Key": L.chave()})
    check("recebimento acima do vendido é aceito (entrada = quantidade recebida, reserva = vendida)",
          s == 200 and fis(c, pe) == (f_antes[0] + 3, f_antes[1] + 1, f_antes[2] + 2), (s, f_antes, fis(c, pe)))
    s, b = call("POST", "/vendas/%s/cancelar" % v2["id"], gt, {"motivo": "cliente desistiu"})
    check("cancelar venda com encomenda recebida libera a reserva (o físico recebido permanece)",
          s == 200 and fis(c, pe) == (f_antes[0] + 3, f_antes[1], f_antes[2] + 3), (s, f_antes, fis(c, pe)))
    v3 = confirmada(c, [L.item(pe, 1, modalidade="ENCOMENDA")])
    call("POST", "/vendas/%s/cancelar" % v3["id"], gt, {"motivo": "cliente desistiu"})
    s, lista = call("GET", "/encomendas?status=CANCELADA", gt)
    check("cancelar venda cancela o acompanhamento da encomenda não recebida", s == 200 and any(e["pedidoId"] == v3["id"] for e in lista), s)
    ctx["venda_encomenda"] = vid


def inventario(c):
    gt, vt = c["ger"]["token"], c["v1"]["token"]
    pb, pn = c["pr"]["B"], c["pr"]["N"]
    var1, var2 = pb["variacoes"]
    ajuste = lambda corpo, k=None, tok=None: call("POST", "/estoque/ajustes", tok or gt, corpo, {"Idempotency-Key": k} if k else None)
    f0, r0, _ = fis(c, pb, var1)
    corpo = {"produtoId": pb["id"], "variacaoId": var1["id"], "contado": f0 + 2, "motivo": "contagem de inventário do roteiro"}
    s, b = ajuste(corpo)
    check("ajuste sem Idempotency-Key é recusado (400)", s == 400 and "Idempotency" in str(g(b, "message")), (s, b))
    s, b = ajuste(dict(corpo, motivo=""), L.chave())
    check("ajuste sem motivo é recusado (400)", s == 400, (s, b))
    k = L.chave()
    s, b = ajuste(corpo, k)
    check("ajuste de inventário (+2): AJUSTE_INVENTARIO com saldo anterior/posterior",
          s == 200 and g(b, "tipo") == "AJUSTE_INVENTARIO" and g(b, "quantidade") == 2 and g(b, "saldoAnterior") == f0 and g(b, "saldoPosterior") == f0 + 2, (s, b))
    s2, b2 = ajuste(corpo, k)
    check("mesma chave: devolve o mesmo ajuste e NÃO ajusta de novo", s2 == 200 and g(b2, "id") == g(b, "id") and fis(c, pb, var1)[0] == f0 + 2, (s2, b2))
    s, b = ajuste(corpo, L.chave())
    check("contagem igual ao saldo do sistema é recusada ('nada a ajustar')", s == 400 and "nada a ajustar" in str(g(b, "message")), (s, b))
    s, b = ajuste(dict(corpo, contado=-1), L.chave())
    check("contagem negativa é recusada (400)", s == 400, (s, b))
    s, b = ajuste({"produtoId": pb["id"], "contado": 3, "motivo": "x"}, L.chave())
    check("produto com variações exige a variação (400)", s == 400 and "variação" in str(g(b, "message")), (s, b))
    s, b = ajuste(dict(corpo, produtoId=999999999), L.chave())
    check("produto inexistente é recusado (400)", s == 400, (s, b))
    # não abaixo do reservado
    v = confirmada(c, [L.item(pb, 3, variacao=var1)])
    f, r, _ = fis(c, pb, var1)
    check("reserva de 3 unidades da variação", r == r0 + 3, (r0, r))
    s, b = ajuste(dict(corpo, contado=r - 1), L.chave())
    check("contagem abaixo do reservado é recusada ('menor que o total já reservado')", s == 400 and "reservado" in str(g(b, "message")), (s, b))
    s, b = ajuste(dict(corpo, contado=r), L.chave())
    check("contagem igual ao reservado é aceita (disponível 0)", s == 200 and fis(c, pb, var1)[2] == 0, (s, b))
    call("POST", "/vendas/%s/cancelar" % v["id"], gt, {"motivo": "limpeza"})
    h = g(call("GET", "/estoque/movimentacoes?produtoId=%s&variacaoId=%s" % (pb["id"], var1["id"]), gt)[1], "conteudo", default=[])
    check("histórico em /estoque/movimentacoes traz os ajustes da variação", len([m for m in h if m["tipo"] == "AJUSTE_INVENTARIO"]) == 2, [m["tipo"] for m in h])
    check("vendedor lê o histórico de movimentações (200)", call("GET", "/estoque/movimentacoes", vt)[0] == 200)
    # primeira contagem de uma variação sem saldo
    v_n = pn["variacoes"][0]
    s, b = ajuste({"produtoId": pn["id"], "variacaoId": v_n["id"], "contado": 4, "motivo": "primeira contagem"}, L.chave())
    check("primeira contagem de saldo nulo define o saldo (quantidade 0, anterior nulo)",
          s == 200 and g(b, "quantidade") == 0 and g(b, "saldoAnterior") is None and g(b, "saldoPosterior") == 4, (s, b))
    check("após a contagem o alerta de saldo some e a variação pode ser vendida",
          fis(c, pn, v_n) == (4, 0, 4), fis(c, pn, v_n))
    s, vn = L.registrar_venda(gt, c["cliente"], [L.item(pn, 1, variacao=v_n)])
    s2, _ = L.confirmar(gt, g(vn, "id")) if s == 200 else (s, None)
    check("variação com saldo definido por contagem é vendida (confirmação 200)", s2 == 200, (s, s2))
    if s == 200:
        call("POST", "/vendas/%s/cancelar" % g(vn, "id"), gt, {"motivo": "limpeza"})


def pos_venda(c):
    adm, gt, vt, pa, pb = c["adm"], c["ger"]["token"], c["v1"]["token"], c["pr"]["A"], c["pr"]["B"]
    # abertura só após a entrega
    nv = confirmada(c, [L.item(pa, 1)])
    s, b = call("POST", "/vendas/%s/ocorrencias" % nv["id"], gt, {"tipo": "ASSISTENCIA", "descricao": "cedo demais"})
    check("pós-venda não abre antes da entrega (400)", s == 400 and "entregues" in str(g(b, "message")), (s, b))
    call("POST", "/vendas/%s/cancelar" % nv["id"], gt, {"motivo": "limpeza"})
    vid = ctx["entregue"]  # PA x2, já entregue
    d = detalhe(c, vid)
    item_id = d["itens"][0]["id"]
    check("vendedor não abre ocorrência (403)", call("POST", "/vendas/%s/ocorrencias" % vid, vt, {"tipo": "ASSISTENCIA", "descricao": "x"})[0] == 403)
    check("detalhe: podeAbrirOcorrencia=true para o gerente", g(d, "acoes", "podeAbrirOcorrencia") is True, g(d, "acoes"))
    # devolução apta
    s, o1 = call("POST", "/vendas/%s/ocorrencias" % vid, gt, {"tipo": "DEVOLUCAO", "descricao": "não gostou", "itemId": item_id, "quantidade": 1})
    check("abrir devolução de 1 un. (ABERTA) com bloqueio financeiro D09 informado",
          s == 200 and g(o1, "status") == "ABERTA" and "D09" in str(g(o1, "bloqueios", "solucaoFinanceira")), (s, o1))
    s, b = call("POST", "/vendas/%s/ocorrencias" % vid, gt, {"tipo": "DEVOLUCAO", "descricao": "demais", "itemId": item_id, "quantidade": 2})
    check("cota: devolver mais do que o vendido é recusado ('Quantidade acima do vendido')", s == 400 and "acima do vendido" in str(g(b, "message")), (s, b))
    s, b = call("POST", "/vendas/%s/ocorrencias" % vid, gt, {"tipo": "DEVOLUCAO", "descricao": "sem item"})
    check("devolução sem item/quantidade é recusada (400)", s == 400, (s, b))
    s, o2 = call("POST", "/vendas/%s/ocorrencias" % vid, gt, {"tipo": "DEVOLUCAO", "descricao": "defeito", "itemId": item_id, "quantidade": 1})
    check("cota: a 2ª unidade ainda cabe", s == 200, (s, o2))
    s, b = call("POST", "/vendas/%s/ocorrencias" % vid, gt, {"tipo": "DEVOLUCAO", "descricao": "3a", "itemId": item_id, "quantidade": 1})
    check("cota: a 3ª unidade (acima do vendido) é recusada", s == 400, (s, b))
    s, b = call("POST", "/ocorrencias/%s/resolver" % o1["id"], gt, {"solucao": "cedo"})
    check("resolver devolução antes de receber é recusado ('Receba e avalie')", s == 400 and "Receba e avalie" in str(g(b, "message")), (s, b))
    f0 = fis(c, pa)[0]
    rec = lambda oid, corpo, k=None: call("POST", "/ocorrencias/%s/receber-devolucao" % oid, gt, corpo, {"Idempotency-Key": k} if k else None)
    s, b = rec(o1["id"], {"condicao": "APTA_REVENDA"})
    check("receber devolução sem Idempotency-Key é recusado (400)", s == 400 and "Idempotency" in str(g(b, "message")), (s, b))
    k = L.chave()
    s, b = rec(o1["id"], {"condicao": "APTA_REVENDA", "avaliacao": "perfeito estado"}, k)
    check("devolução APTA_REVENDA: DEVOLUCAO_RECEBIDA e estoqueReposto=true", s == 200 and g(b, "status") == "DEVOLUCAO_RECEBIDA" and g(b, "estoqueReposto") is True, (s, b))
    check("devolução apta volta ao estoque (físico +1)", fis(c, pa)[0] == f0 + 1, (f0, fis(c, pa)))
    s, b = rec(o1["id"], {"condicao": "APTA_REVENDA"}, k)
    check("repetir a chave: 200 sem nova entrada", s == 200 and fis(c, pa)[0] == f0 + 1, (s, fis(c, pa)))
    s, b = rec(o1["id"], {"condicao": "APTA_REVENDA"}, L.chave())
    check("receber a mesma devolução com outra chave é recusado (400)", s == 400, (s, b))
    mv = [m for m in movs(c, vid) if m["tipo"] == "ENTRADA_DEVOLUCAO"]
    check("movimentação ENTRADA_DEVOLUCAO de +1 registrada", len(mv) == 1 and mv[0]["quantidade"] == 1, mv)
    # não apta
    s, b = rec(o2["id"], {"condicao": "NAO_APTA"}, L.chave())
    check("devolução NAO_APTA exige avaliação (400)", s == 400 and "avaliação" in str(g(b, "message")), (s, b))
    s, b = rec(o2["id"], {"condicao": "NAO_APTA", "avaliacao": "madeira rachada, sem condição de revenda"}, L.chave())
    check("devolução NAO_APTA: recebida, estoqueReposto=false", s == 200 and g(b, "status") == "DEVOLUCAO_RECEBIDA" and g(b, "estoqueReposto") is False, (s, b))
    check("devolução não apta NÃO volta ao estoque (físico igual)", fis(c, pa)[0] == f0 + 1, (f0, fis(c, pa)))
    check("só 1 movimentação ENTRADA_DEVOLUCAO (a apta)", len([m for m in movs(c, vid) if m["tipo"] == "ENTRADA_DEVOLUCAO"]) == 1, movs(c, vid))
    s, b = call("POST", "/ocorrencias/%s/cancelar" % o2["id"], gt, {"motivo": "tarde"})
    check("não cancela ocorrência com devolução já recebida (400)", s == 400, (s, b))
    s, b = call("POST", "/ocorrencias/%s/resolver" % o1["id"], gt, {"solucao": ""})
    check("resolver exige a descrição da solução (400)", s == 400, (s, b))
    s, b = call("POST", "/ocorrencias/%s/resolver" % o1["id"], gt, {"solucao": "cliente informado; solução financeira depende de D09"})
    check("resolver devolução recebida (RESOLVIDA)", s == 200 and g(b, "status") == "RESOLVIDA", (s, b))

    # troca com diferença calculada
    vb = confirmada(c, [L.item(pb, 2, variacao=pb["variacoes"][0])])
    agendar(c, vb["id"])
    s, b = saida(c, vb["id"], L.chave())
    s, b = call("POST", "/vendas/%s/entrega/concluir" % vb["id"], gt, campos={"recebedor": "Cliente"})
    check("venda B entregue para o teste de troca", s == 200 and g(b, "statusEntrega") == "ENTREGUE", (s, b))
    ib = b["itens"][0]["id"]
    var1, var2 = pb["variacoes"]
    s, b = call("POST", "/vendas/%s/ocorrencias" % vb["id"], gt, {"tipo": "TROCA", "descricao": "quer a cor azul", "itemId": ib, "quantidade": 1})
    check("troca sem o produto de troca é recusada (400)", s == 400 and "produto da troca" in str(g(b, "message")), (s, b))
    s, b = call("POST", "/vendas/%s/ocorrencias" % vb["id"], gt, {"tipo": "TROCA", "descricao": "x", "itemId": ib, "quantidade": 1,
                                                                    "troca": {"produtoId": pb["id"], "quantidade": 1}})
    check("troca de produto com variações exige a variação (400)", s == 400 and "variação" in str(g(b, "message")), (s, b))
    s, t = call("POST", "/vendas/%s/ocorrencias" % vb["id"], gt, {"tipo": "TROCA", "descricao": "quer a cor azul", "itemId": ib, "quantidade": 1,
                                                                   "troca": {"produtoId": pb["id"], "variacaoId": var2["id"], "quantidade": 1}})
    # unitário original 300 * 0,95 = 285,00; novo (300+100) * 0,95 = 380,00; diferença = +95,00
    check("troca: diferença calculada = 380,00 - 285,00 = 95.00", s == 200 and g(t, "diferencaCalculada") == 95.0, (s, g(t, "diferencaCalculada")))
    check("troca: bloqueio financeiro cita D09 (nenhum valor é movimentado)", "D09" in str(g(t, "bloqueios", "solucaoFinanceira")), g(t, "bloqueios"))
    fv1 = fis(c, pb, var1)[0]
    s, b = rec(t["id"], {"condicao": "APTA_REVENDA"}, L.chave())
    check("troca: item devolvido apto volta ao estoque da variação original (+1)", s == 200 and fis(c, pb, var1)[0] == fv1 + 1, (s, fv1, fis(c, pb, var1)))
    s, b = call("POST", "/ocorrencias/%s/resolver" % t["id"], gt, {"solucao": "troca autorizada; diferença pendente de D09"})
    check("troca resolvida (RESOLVIDA)", s == 200 and g(b, "status") == "RESOLVIDA", (s, b))

    # assistência com evidência
    s, a = call("POST", "/vendas/%s/ocorrencias" % vb["id"], gt, {"tipo": "ASSISTENCIA", "descricao": "porta desalinhada", "itemId": ib})
    check("abrir assistência (ABERTA, sem bloqueio financeiro)", s == 200 and g(a, "status") == "ABERTA" and not g(a, "bloqueios"), (s, a))
    s, b = rec(a["id"], {"condicao": "APTA_REVENDA"}, L.chave())
    check("assistência não envolve devolução física (400)", s == 400 and "Assistência" in str(g(b, "message")), (s, b))
    s, b = call("POST", "/ocorrencias/%s/evidencias" % a["id"], gt, campos={"descricao": "foto do defeito"},
                arquivos={"arquivo": ("defeito.png", L.png_minimo(3, 3), "image/png")})
    check("anexar evidência (upload PNG) à assistência", s == 200 and len(g(b, "evidencias", default=[])) == 1, (s, b))
    ev = g(b, "evidencias", 0, "arquivo")
    qev = "/api/v1/arquivos?caminho=" + urllib.parse.quote(str(ev), safe="/")
    check("evidência da ocorrência fica na área privada e abre com token de gerente",
          str(ev).startswith("privado/ocorrencias/%s/" % a["id"]) and L.bruto("GET", qev, gt)[0] == 200, ev)
    check("evidência de ocorrência não é aberta por vendedor (404)", L.bruto("GET", qev, vt)[0] == 404)
    s, b = call("POST", "/ocorrencias/%s/evidencias" % a["id"], gt, campos={}, arquivos={"arquivo": ("x.gif", b"GIF89a....", "image/gif")})
    check("evidência com tipo não aceito é recusada (400)", s == 400, (s, b))
    s, b = call("POST", "/ocorrencias/%s/resolver" % a["id"], gt, {"solucao": "ajuste de dobradiça realizado"})
    check("resolver assistência (RESOLVIDA)", s == 200 and g(b, "status") == "RESOLVIDA", (s, b))
    # cancelamento de ocorrência aberta
    s, x = call("POST", "/vendas/%s/ocorrencias" % vb["id"], gt, {"tipo": "ASSISTENCIA", "descricao": "engano"})
    s, b = call("POST", "/ocorrencias/%s/cancelar" % x["id"], gt, {"motivo": ""})
    check("cancelar ocorrência exige motivo (400)", s == 400, (s, b))
    s, b = call("POST", "/ocorrencias/%s/cancelar" % x["id"], gt, {"motivo": "aberta por engano"})
    check("cancelar ocorrência aberta (CANCELADA)", s == 200 and g(b, "status") == "CANCELADA", (s, b))
    s, b = call("POST", "/ocorrencias/%s/evidencias" % x["id"], gt, campos={}, arquivos={"arquivo": ("a.png", L.png_minimo(), "image/png")})
    check("não anexa evidência a ocorrência cancelada (400)", s == 400, (s, b))
    # consultas
    s, lista = call("GET", "/ocorrencias?tipo=TROCA", gt)
    check("GET /ocorrencias?tipo=TROCA lista a troca", s == 200 and any(o["id"] == t["id"] for o in lista) and all(o["tipo"] == "TROCA" for o in lista), s)
    s, lista = call("GET", "/ocorrencias?status=RESOLVIDA", gt)
    check("GET /ocorrencias?status=RESOLVIDA filtra", s == 200 and all(o["status"] == "RESOLVIDA" for o in lista) and len(lista) >= 3, s)
    check("GET /ocorrencias/{id}", g(call("GET", "/ocorrencias/%s" % a["id"], gt)[1], "id") == a["id"])
    check("GET /ocorrencias/{id} inexistente = 404", call("GET", "/ocorrencias/999999999", gt)[0] == 404)
    d = detalhe(c, vb["id"])
    check("detalhe da venda traz as ocorrências abertas e o histórico de movimentações", len(g(d, "ocorrencias", default=[])) >= 3 and len(g(d, "movimentacoes", default=[])) >= 2, (len(g(d, "ocorrencias", default=[])), len(g(d, "movimentacoes", default=[]))))


def agenda(c):
    gt, vt = c["ger"]["token"], c["v1"]["token"]
    vid = ctx["agenda_venda"]
    de, ate = L.amanha(0), L.amanha(10)
    s, itens = call("GET", "/agenda?de=%s&ate=%s" % (de, ate), gt)
    mine = [i for i in itens if i["pedidoId"] == vid] if s == 200 else []
    tipos = sorted(i["tipo"] for i in mine)
    check("agenda mantém a entrega concluída da venda (status ENTREGUE)", s == 200 and [i["status"] for i in mine if i["tipo"] == "ENTREGA"] == ["ENTREGUE"], (s, mine))
    info("montagem já concluída %s da agenda (tipos da venda concluída: %s)" % ("sai" if "MONTAGEM" not in tipos else "continua", tipos))
    # venda agendada e ainda não saída, para conferir a entrega na agenda
    v = confirmada(c, [L.item(c["pr"]["A"], 1)])
    agendar(c, v["id"], dias=3, periodo="TARDE")
    call("POST", "/vendas/%s/montagem/agendar" % v["id"], gt, {"data": L.amanha(4), "periodo": "MANHA", "responsavel": "Montador"})
    s, itens = call("GET", "/agenda?de=%s&ate=%s" % (de, ate), gt)
    mine = {i["tipo"]: i for i in itens if i["pedidoId"] == v["id"]} if s == 200 else {}
    check("agenda: entrega (ENTREGA, TARDE, AGENDADA) e montagem (MONTAGEM, MANHA, AGENDADA) da venda agendada",
          g(mine, "ENTREGA", "data") == L.amanha(3) and g(mine, "ENTREGA", "periodo") == "TARDE" and g(mine, "ENTREGA", "status") == "AGENDADA"
          and g(mine, "MONTAGEM", "data") == L.amanha(4) and g(mine, "MONTAGEM", "responsavel") == "Montador", mine)
    check("agenda traz o endereço de entrega", "Rua de Teste" in str(g(mine, "ENTREGA", "endereco")), mine)
    datas = [(i["data"], i["periodo"]) for i in itens]
    check("agenda ordenada por data", [d for d, _ in datas] == sorted(d for d, _ in datas), datas)
    s, vis = call("GET", "/agenda?de=%s&ate=%s" % (de, ate), vt)
    check("vendedor LÊ a agenda (200)", s == 200, s)
    check("vendedor não opera: reagendar (403), saída (403), concluir entrega (403)",
          call("POST", "/vendas/%s/entrega/reagendar" % v["id"], vt, {"data": L.amanha(5), "periodo": "MANHA", "motivo": "x"})[0] == 403
          and call("POST", "/vendas/%s/saida" % v["id"], vt, headers={"Idempotency-Key": L.chave()})[0] == 403
          and call("POST", "/vendas/%s/entrega/concluir" % v["id"], vt, campos={"recebedor": "x"})[0] == 403, "")
    check("agenda sem token = 401", call("GET", "/agenda?de=%s&ate=%s" % (de, ate))[0] == 401)
    s, b = call("GET", "/agenda?de=%s&ate=%s" % (ate, de), gt)
    check("agenda com período invertido é recusada (400)", s == 400, (s, b))
    s, b = call("GET", "/agenda?de=%s&ate=%s" % (de, L.amanha(200)), gt)
    check("agenda com mais de 92 dias é recusada (400)", s == 400, (s, b))
    call("POST", "/vendas/%s/cancelar" % v["id"], gt, {"motivo": "limpeza"})


def finalizar():
    adm = L.login_admin()
    try:
        if "snap" in ctx:
            L.config_restaurar(adm, ctx["snap"], ctx.get("criadas", []))
    finally:
        L.limpar()


if __name__ == "__main__":
    L.exigir_config()
    L.executar(principal, finalizar)
