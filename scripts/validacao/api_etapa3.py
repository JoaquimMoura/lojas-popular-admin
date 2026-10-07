#!/usr/bin/env python3
"""Validação por API da Etapa 3 (financeiro): recebimentos (dinheiro, Pix, cartão), caixa, contas, cartão
(bruto/taxa/líquido/liquidação), comissões, metas, restituições (D09), fechamento mensal e bloqueio de período.

Ambiente: SOMENTE o local descartável (ver README.md). Requer LP_ADMIN_EMAIL e LP_ADMIN_PASSWORD.
As decisões financeiras (D01, D02, D06, D07, D09, D10, D11) recebem valores de TESTE durante o roteiro, são
alternadas para provar os bloqueios das decisões pendentes e RESTAURADAS ao final (bloco finally).
Código de saída: 0 = tudo OK; 1 = alguma verificação falhou; 2 = configuração/ambiente ausente.
"""
import datetime
import secrets
import threading

import lib as L
from lib import call, check, g, info, secao

ctx = {}

# Valores de TESTE (não são decisões de negócio).
FIN_TESTE = {"comissaoPercentual": 5, "comissaoAquisicao": "QUITACAO", "competenciaReceita": "CONFIRMACAO",
             "perfisReabertura": ["ADMIN"], "perfisRestituicao": ["ADMIN", "GERENTE"], "permiteRestituicao": True,
             "permiteCobrancaDiferenca": True, "metaDescontaDevolucoes": True, "fechamentoExigeSemPendencias": False}
FIN_VAZIO = {k: None for k in FIN_TESTE}


def principal():
    adm = L.preparar()
    ctx["snap"] = L.config_salvar(adm)
    ctx["fin_snap"] = ctx["snap"].get("financeiro") or dict(FIN_VAZIO)
    L.rotulo_teste()
    print("[VALORES DE TESTE FINANCEIROS] comissão=5%%, aquisição=QUITACAO, competência=CONFIRMACAO, taxa de cartão 4%% em 3x "
          "(30/30 dias). Nenhum deles é decisão de negócio: D01, D02, D06, D07, D09, D10 e D11 seguem pendentes para a loja.")
    ctx["criadas"] = L.condicoes_teste(adm, ctx["snap"])
    L.config_aplicar(adm, L.LIMITE_TESTE, L.ARRED_TESTE, ["ADMIN", "GERENTE"], False)
    ger = L.novo_usuario(adm, "GERENTE", "ger")
    vend = L.novo_usuario(adm, "VENDEDOR", "vend")
    cliente = L.criar_cliente(ger["token"], nome="Cliente Etapa3 %s" % L.SUFIXO)
    produto = L.criar_produto(adm, "Guarda-roupa Etapa3", 1000.00, estoque=200)
    c = {"adm": adm, "ger": ger, "vend": vend, "cliente": cliente, "produto": produto,
         "op": "OP-%s" % L.SUFIXO[:6].upper()}
    fechar_caixa_aberto(c)

    secao("1. Decisões financeiras pendentes e permissões")
    aplicar_fin(c, FIN_VAZIO)
    pendencias(c)
    secao("2. Pix: recebimento, repetição, parcial, estorno")
    aplicar_fin(c, FIN_TESTE)
    pix(c)
    secao("3. Saída condicionada à quitação (D05 = verdadeiro)")
    saida_quitacao(c)
    secao("4. Dinheiro e caixa físico")
    dinheiro_caixa(c)
    secao("5. Cartão: bruto, taxa, líquido, liquidação")
    cartao(c)
    secao("6. Concorrência")
    concorrencia(c)
    secao("7. Contas a pagar e a receber")
    contas(c)
    secao("8. Comissões")
    comissoes(c)
    secao("9. Restituição e devolução (D09)")
    restituicao(c)
    secao("10. Metas")
    metas(c)
    secao("11. Fechamento mensal e bloqueio de período")
    fechamento(c)


def finalizar():
    adm = L.login_admin()
    if not adm:
        return
    try:
        fechar_caixa_aberto({"adm": adm, "ger": {"token": adm}})
    except Exception as e:  # pragma: no cover
        info("não foi possível fechar o caixa de teste: %s" % e)
    s, b = call("PUT", "/config/comercial/financeiro", adm, {k: ctx["fin_snap"].get(k) for k in FIN_TESTE})
    info("decisões financeiras restauradas (%s)" % s)
    L.config_restaurar(adm, ctx["snap"], ctx.get("criadas", []))
    L.limpar()


# ---------------------------------------------------------------------------

def aplicar_fin(c, valores):
    s, b = call("PUT", "/config/comercial/financeiro", c["adm"], valores)
    if s != 200:
        raise RuntimeError("não foi possível gravar a configuração financeira: %s %s" % (s, b))
    return b


def fechar_caixa_aberto(c):
    s, a = call("GET", "/financeiro/caixa/atual", c["ger"]["token"])
    if s == 200 and g(a, "aberta"):
        call("POST", "/financeiro/caixa/fechar", c["ger"]["token"],
             {"saldoContado": g(a, "sessao", "saldoEsperado"), "motivoDiferenca": None})


def venda(c, qtd=1, forma="PIX", parcelas=1, confirmar=True):
    s, v = L.registrar_venda(c["vend"]["token"], c["cliente"], [L.item(c["produto"], qtd)], forma=forma, parcelas=parcelas,
                             tipo="RETIRADA")
    if s != 200:
        raise RuntimeError("registrar venda: %s %s" % (s, v))
    if confirmar:
        s, v = L.confirmar(c["vend"]["token"], v["id"])
        if s != 200:
            raise RuntimeError("confirmar venda: %s %s" % (s, v))
    return v


def detalhe(c, vid):
    return call("GET", "/vendas/%s" % vid, c["ger"]["token"])[1]


def receber(c, vid, valor, k=None, extra=None, quem="ger"):
    corpo = {"valor": valor}
    corpo.update(extra or {})
    return call("POST", "/vendas/%s/recebimentos" % vid, c[quem]["token"], corpo, {"Idempotency-Key": k or L.chave()})


def estornar(c, rid, k=None, motivo="Lançamento feito por engano"):
    return call("POST", "/recebimentos/%s/estornar" % rid, c["ger"]["token"], {"motivo": motivo},
                {"Idempotency-Key": k or L.chave()})


def lancs(c, vid):
    s, b = call("GET", "/financeiro/lancamentos?de=2000-01-01&ate=2999-12-31", c["adm"])
    return [x for x in (b if s == 200 else []) if x.get("pedidoId") == vid]


def quitar(c, vid, forma="PIX"):
    d = detalhe(c, vid)
    extra = {"operadora": c["op"]} if forma == "CARTAO" else None
    return receber(c, vid, g(d, "pagamento", "saldo"), extra=extra)


def pagamento(c, vid):
    return g(detalhe(c, vid), "pagamento", default={})


def comissao_da_venda(c, vid, tipo="PREVISAO"):
    s, r = call("GET", "/financeiro/comissoes?vendedorId=%s" % c["vend"]["id"], c["adm"])
    return next((i for i in g(r, "itens", default=[]) if i["pedidoId"] == vid and i["tipo"] == tipo), None)


# ---------------------------------------------------------------------------

def pendencias(c):
    gt, vt, at = c["ger"]["token"], c["vend"]["token"], c["adm"]
    s, cfg = call("GET", "/config/comercial", at)
    codigos = {p["codigo"] for p in g(cfg, "pendencias", default=[]) if p.get("area") == "FINANCEIRO"}
    check("config lista D01, D02, D06, D07, D09 e D10 como pendências financeiras",
          {"D01", "D02", "D06", "D07", "D09", "D10"} <= codigos, codigos)
    s, b = call("PUT", "/config/comercial/financeiro", gt, FIN_TESTE)
    check("gerente não altera decisões financeiras (403)", s == 403, (s, b))
    s, b = call("PUT", "/config/comercial/financeiro", at, dict(FIN_TESTE, comissaoPercentual=101))
    check("percentual de comissão acima de 100 é recusado (400)", s == 400, (s, b))
    s, b = call("PUT", "/config/comercial/financeiro", at, dict(FIN_TESTE, perfisReabertura=["VENDEDOR"]))
    check("vendedor não pode ser perfil autorizador (400)", s == 400, (s, b))
    s, b = call("GET", "/financeiro/caixa/atual", vt)
    check("vendedor não acessa o financeiro (403)", s == 403, (s, b))
    s, b = call("GET", "/vendas/configuracao", vt)
    check("pendências financeiras não vazam para a tela de venda do vendedor",
          all(p.get("area") != "FINANCEIRO" for p in g(b, "pendencias", default=[])), b)
    v = venda(c)
    s, r = call("GET", "/financeiro/comissoes?vendedorId=%s" % c["vend"]["id"], at)
    check("D01 pendente: nenhuma comissão calculada (nem zero nem estimativa)",
          g(r, "percentualDefinido") is False and comissao_da_venda(c, v["id"]) is None, r)
    s, b = call("POST", "/financeiro/comissoes/gerar-previsoes", at)
    check("D01 pendente: gerar previsões é bloqueado (422 citando D01)", s == 422 and "D01" in str(g(b, "message")), (s, b))
    call("POST", "/vendas/%s/cancelar" % v["id"], gt, {"motivo": "limpeza D01"})


def pix(c):
    gt = c["ger"]["token"]
    v = venda(c)
    vid, total = v["id"], v["total"]
    s, b = call("POST", "/vendas/%s/recebimentos" % vid, gt, {"valor": total})
    check("recebimento sem Idempotency-Key é recusado (400)", s == 400 and "Idempotency" in str(g(b, "message")), (s, b))
    s, b = receber(c, vid, total + 0.01)
    check("valor acima do saldo é recusado (400)", s == 400 and "excede" in str(g(b, "message")), (s, b))
    s, b = receber(c, vid, 0)
    check("valor zero é recusado (400)", s == 400, (s, b))
    s, b = receber(c, vid, round(total / 2, 2), quem="vend")
    check("vendedor não registra recebimento (403)", s == 403, (s, b))
    s, b = receber(c, vid, round(total * 0.4, 2))
    check("recebimento parcial: statusPagamento PARCIAL", s == 200 and g(b, "statusPagamento") == "PARCIAL", (s, b))
    k = L.chave()
    resto = round(total - round(total * 0.4, 2), 2)
    s1, b1 = receber(c, vid, resto, k=k)
    s2, b2 = receber(c, vid, resto, k=k)
    check("saldo restante: PAGO", s1 == 200 and g(b1, "statusPagamento") == "PAGO", (s1, b1))
    check("repetição com a mesma chave não duplica o recebimento",
          s2 == 200 and len(g(b2, "pagamento", "recebimentos", default=[])) == 2 and len(lancs(c, vid)) == 2,
          (s2, len(g(b2, "pagamento", "recebimentos", default=[]))))
    check("Pix entra no BANCO e não no caixa físico", {x["conta"] for x in lancs(c, vid)} == {"BANCO"}, lancs(c, vid))
    s, b = receber(c, vid, 1)
    check("venda quitada não recebe mais (400)", s == 400, (s, b))
    s, b = call("POST", "/vendas/%s/cancelar" % vid, gt, {"motivo": "tentativa"})
    check("venda com recebimento ativo não é cancelada por este caminho (400)", s == 400 and "recebimentos" in str(g(b, "message")), (s, b))
    rid = g(b2, "pagamento", "recebimentos")[1]["id"]
    s, b = call("POST", "/recebimentos/%s/estornar" % rid, gt, {"motivo": " "}, {"Idempotency-Key": L.chave()})
    check("estorno sem motivo é recusado (400)", s == 400, (s, b))
    ke = L.chave()
    s1, b1 = estornar(c, rid, k=ke)
    s2, b2 = estornar(c, rid, k=ke)
    check("estorno: statusPagamento volta a PARCIAL", s1 == 200 and g(b1, "statusPagamento") == "PARCIAL", (s1, b1))
    check("estorno repetido (mesma chave) é idempotente e gera um único lançamento oposto",
          s2 == 200 and len(lancs(c, vid)) == 3, (s2, len(lancs(c, vid))))
    s, b = estornar(c, rid)
    check("estornar de novo com outra chave é recusado (400)", s == 400 and "já foi estornado" in str(g(b, "message")), (s, b))
    s, b = receber(c, vid, resto)
    check("após o estorno é possível receber novamente", s == 200 and g(b, "statusPagamento") == "PAGO", (s, b))
    ctx["venda_pix"] = vid


def saida_quitacao(c):
    gt, at = c["ger"]["token"], c["adm"]
    L.config_aplicar(at, L.LIMITE_TESTE, L.ARRED_TESTE, ["ADMIN", "GERENTE"], True)
    v = venda(c)
    vid, total = v["id"], v["total"]
    s, b = call("POST", "/vendas/%s/entrega/agendar" % vid, gt, {"data": L.amanha(2), "periodo": "MANHA"})
    check("agendar entrega", s == 200, (s, b))
    s, b = call("POST", "/vendas/%s/saida" % vid, gt, headers={"Idempotency-Key": L.chave()})
    check("D05 verdadeiro: saída sem pagamento é recusada (400 'pagamento quitado')",
          s == 400 and "pagamento quitado" in str(g(b, "message")), (s, b))
    receber(c, vid, round(total / 2, 2))
    s, b = call("POST", "/vendas/%s/saida" % vid, gt, headers={"Idempotency-Key": L.chave()})
    check("D05 verdadeiro: pagamento parcial não libera a saída (400)", s == 400, (s, b))
    quitar(c, vid)
    s, b = call("POST", "/vendas/%s/saida" % vid, gt, headers={"Idempotency-Key": L.chave()})
    check("D05 verdadeiro: com o pagamento quitado a saída é registrada", s == 200 and g(b, "statusEntrega") == "SAIU", (s, b))
    L.config_aplicar(at, L.LIMITE_TESTE, L.ARRED_TESTE, ["ADMIN", "GERENTE"], False)


def dinheiro_caixa(c):
    gt = c["ger"]["token"]
    v = venda(c, forma="DINHEIRO") if ("DINHEIRO", 1) in L.CONDICOES_TESTE else None
    if v is None:
        # a condição de dinheiro não é de teste: cria/ativa uma neutra apenas durante o roteiro
        s, nova = call("POST", "/config/comercial/condicoes", c["adm"],
                       {"forma": "DINHEIRO", "parcelas": 1, "ajustePercentual": "0.00", "ativa": True})
        if s == 200:
            ctx["criadas"].append(nova["id"])
        else:
            s2, cfg = call("GET", "/config/comercial", c["adm"])
            ex = next((x for x in cfg["condicoes"] if x["forma"] == "DINHEIRO" and x["parcelas"] == 1), None)
            if ex:
                call("PUT", "/config/comercial/condicoes/%s" % ex["id"], c["adm"], {"ajustePercentual": "0.00", "ativa": True})
        v = venda(c, forma="DINHEIRO")
    vid, total = v["id"], v["total"]
    s, b = receber(c, vid, total)
    check("dinheiro sem caixa aberto é recusado (400 'abra o caixa')", s == 400 and "abra o caixa" in str(g(b, "message")).lower(), (s, b))
    s, a = call("POST", "/financeiro/caixa/abrir", gt, {"saldoInicial": 100})
    check("abrir caixa com saldo inicial", s == 200 and g(a, "status") == "ABERTA", (s, a))
    s, b = call("POST", "/financeiro/caixa/abrir", gt, {"saldoInicial": 0})
    check("não abre dois caixas ao mesmo tempo (400)", s == 400, (s, b))
    s, b = receber(c, vid, total)
    check("dinheiro com caixa aberto: PAGO", s == 200 and g(b, "statusPagamento") == "PAGO", (s, b))
    check("dinheiro entra no CAIXA", {x["conta"] for x in lancs(c, vid)} == {"CAIXA"}, lancs(c, vid))
    km = L.chave()
    s1, b1 = call("POST", "/financeiro/caixa/movimentos", gt, {"tipo": "SUPRIMENTO", "valor": 50, "motivo": "troco"}, {"Idempotency-Key": km})
    s2, b2 = call("POST", "/financeiro/caixa/movimentos", gt, {"tipo": "SUPRIMENTO", "valor": 50, "motivo": "troco"}, {"Idempotency-Key": km})
    check("suprimento repetido (mesma chave) não duplica", s1 == 200 and s2 == 200 and g(b1, "saldoEsperado") == g(b2, "saldoEsperado"), (b1, b2))
    s, b = call("POST", "/financeiro/caixa/movimentos", gt, {"tipo": "RETIRADA", "valor": 99999, "motivo": "teste"}, {"Idempotency-Key": L.chave()})
    check("retirada acima do saldo é recusada (400)", s == 400, (s, b))
    s, atual = call("GET", "/financeiro/caixa/atual", gt)
    esperado = g(atual, "sessao", "saldoEsperado")
    check("saldo esperado = inicial + entradas + suprimentos", abs(esperado - (100 + total + 50)) < 0.005, (esperado, total))
    s, b = call("POST", "/financeiro/caixa/fechar", gt, {"saldoContado": esperado - 10})
    check("fechar com diferença exige motivo (400)", s == 400 and "motivo" in str(g(b, "message")).lower(), (s, b))
    s, b = call("POST", "/financeiro/caixa/fechar", gt, {"saldoContado": esperado - 10, "motivoDiferenca": "Troco dado a maior"})
    check("fechamento diário registra a diferença", s == 200 and g(b, "status") == "FECHADA" and abs(g(b, "diferenca") + 10) < 0.005, (s, b))
    s, a = call("GET", "/financeiro/caixa/atual", gt)
    check("não há caixa aberto depois do fechamento", g(a, "aberta") is False, a)


def cartao(c):
    gt, at = c["ger"]["token"], c["adm"]
    s, b = call("POST", "/financeiro/taxas-cartao", at, {"operadora": c["op"], "parcelas": 3, "taxaPercentual": "4.0000",
                                                       "prazoPrimeiraParcelaDias": 30, "intervaloDias": 30, "ativa": True})
    check("cadastrar taxa de cartão (D11) da operadora de teste", s == 200, (s, b))
    v = venda(c, forma="CARTAO", parcelas=3)
    vid, total = v["id"], v["total"]
    s, b = receber(c, vid, total, extra={"operadora": "OPERADORA-SEM-TAXA"})
    check("operadora sem taxa cadastrada bloqueia (422 D11)", s == 422 and "D11" in str(g(b, "message")), (s, b))
    s, b = receber(c, vid, round(total / 2, 2), extra={"operadora": c["op"]})
    check("cartão exige o valor total da venda (400)", s == 400 and "valor total" in str(g(b, "message")), (s, b))
    s, b = receber(c, vid, total, extra={"operadora": c["op"], "referencia": "NSU-%s" % L.SUFIXO})
    rec = g(b, "pagamento", "recebimentos", default=[{}])[0]
    parcelas = rec.get("recebiveis", [])
    check("cartão: venda PAGO e 3 recebíveis da operadora", s == 200 and g(b, "statusPagamento") == "PAGO" and len(parcelas) == 3, (s, b))
    bruto = round(sum(p["valorBruto"] for p in parcelas), 2)
    check("soma dos brutos = total cobrado", abs(bruto - total) < 0.005, (bruto, total))
    check("líquido = bruto - taxa em cada parcela",
          all(abs(p["valorLiquido"] - (p["valorBruto"] - p["valorTaxa"])) < 0.005 for p in parcelas), parcelas)
    check("previsões 30/60/90 dias", [p["dataPrevista"] for p in parcelas] ==
          [(datetime.date.today() + datetime.timedelta(days=d)).isoformat() for d in (30, 60, 90)], [p["dataPrevista"] for p in parcelas])
    check("cartão não gera entrada de dinheiro antes da liquidação (sem duplicar receita)", lancs(c, vid) == [], lancs(c, vid))
    pid = parcelas[0]["id"]
    kl = L.chave()
    s1, l1 = call("POST", "/financeiro/recebiveis/%s/liquidar" % pid, gt, {}, {"Idempotency-Key": kl})
    s2, l2 = call("POST", "/financeiro/recebiveis/%s/liquidar" % pid, gt, {}, {"Idempotency-Key": kl})
    check("liquidação: recebível LIQUIDADO e uma única entrada no BANCO",
          s1 == 200 and s2 == 200 and g(l1, "status") == "LIQUIDADO" and [x["conta"] for x in lancs(c, vid)] == ["BANCO"], (s1, s2, lancs(c, vid)))
    s, b = call("POST", "/financeiro/recebiveis/%s/liquidar" % pid, gt, {}, {"Idempotency-Key": L.chave()})
    check("liquidar de novo com outra chave é recusado (400)", s == 400, (s, b))
    s, b = call("POST", "/financeiro/recebiveis/%s/liquidar" % parcelas[1]["id"], gt, {"valorLiquidado": 999999}, {"Idempotency-Key": L.chave()})
    check("valor liquidado acima do bruto é recusado (400)", s == 400, (s, b))
    s, b = call("POST", "/financeiro/recebiveis/%s/liquidar" % parcelas[1]["id"], gt,
                {"valorLiquidado": round(parcelas[1]["valorLiquido"] - 1, 2)}, {"Idempotency-Key": L.chave()})
    check("liquidação divergente registra a diferença sem inventar valor", s == 200 and abs(g(b, "diferencaLiquidacao") + 1) < 0.005, (s, b))
    s, b = estornar(c, rec["id"])
    check("recebimento com parcela liquidada não é estornado (400)", s == 400 and "liquidad" in str(g(b, "message")).lower(), (s, b))
    ke = L.chave()
    s1, b1 = call("POST", "/financeiro/recebiveis/%s/estornar-liquidacao" % pid, gt, {"motivo": "Liquidação lançada por engano"}, {"Idempotency-Key": ke})
    s2, b2 = call("POST", "/financeiro/recebiveis/%s/estornar-liquidacao" % pid, gt, {"motivo": "Liquidação lançada por engano"}, {"Idempotency-Key": ke})
    check("estorno da liquidação é idempotente e gera um lançamento oposto",
          s1 == 200 and s2 == 200 and g(b1, "status") == "PREVISTO" and len([x for x in lancs(c, vid) if x["estornaId"]]) == 1, (s1, s2, lancs(c, vid)))
    s, b = call("GET", "/financeiro/recebiveis?status=PREVISTO&operadora=%s" % c["op"], gt)
    check("listagem de recebíveis por operadora/status", s == 200 and len(b) >= 2, (s, b))


def concorrencia(c):
    gt = c["ger"]["token"]

    def em_paralelo(fn, n=2):
        saidas, ts = [], []

        def alvo():
            saidas.append(fn())
        for _ in range(n):
            t = threading.Thread(target=alvo)
            ts.append(t)
        for t in ts:
            t.start()
        for t in ts:
            t.join()
        return saidas

    v = venda(c)
    vid, total = v["id"], v["total"]
    k = L.chave()
    r = em_paralelo(lambda: receber(c, vid, total, k=k))
    check("dois recebimentos simultâneos com a mesma chave: um só lançamento",
          all(s == 200 for s, _ in r) and len(lancs(c, vid)) == 1 and len(pagamento(c, vid)["recebimentos"]) == 1, ([s for s, _ in r], lancs(c, vid)))
    v = venda(c)
    vid = v["id"]
    r = em_paralelo(lambda: receber(c, vid, total))
    check("dois recebimentos simultâneos (chaves diferentes) não excedem o saldo",
          sorted(s for s, _ in r) == [200, 400] and abs(pagamento(c, vid)["recebido"] - total) < 0.005, ([s for s, _ in r]))
    rid = pagamento(c, vid)["recebimentos"][0]["id"]
    r = em_paralelo(lambda: estornar(c, rid))
    check("dois estornos simultâneos: apenas um é efetivado",
          sorted(s for s, _ in r) == [200, 400] and len(lancs(c, vid)) == 2, ([s for s, _ in r], len(lancs(c, vid))))
    s, ct = call("POST", "/financeiro/contas", gt, {"tipo": "PAGAR", "descricao": "Conta concorrente", "categoria": "Teste",
                                                    "competencia": datetime.date.today().isoformat(), "valor": 80,
                                                    "vencimento": L.amanha(5)})
    r = em_paralelo(lambda: call("POST", "/financeiro/contas/%s/baixar" % ct["id"], gt, {"meio": "BANCO"}, {"Idempotency-Key": L.chave()}))
    check("duas baixas simultâneas da mesma conta: apenas uma é efetivada", sorted(s for s, _ in r) == [200, 400], [s for s, _ in r])


def contas(c):
    gt = c["ger"]["token"]
    hoje = datetime.date.today().isoformat()
    corpo = {"tipo": "PAGAR", "descricao": "Aluguel (teste)", "categoria": "Aluguel", "competencia": hoje, "valor": 250,
             "vencimento": L.amanha(5)}
    s, b = call("POST", "/financeiro/contas", c["vend"]["token"], corpo)
    check("vendedor não cria contas (403)", s == 403, (s, b))
    s, ct = call("POST", "/financeiro/contas", gt, corpo)
    check("criar conta a pagar", s == 200 and g(ct, "situacao") == "ABERTA", (s, ct))
    s, b = call("POST", "/financeiro/contas/%s/baixar" % ct["id"], gt, {"meio": "CAIXA"}, {"Idempotency-Key": L.chave()})
    check("baixa pelo CAIXA sem caixa aberto é recusada (400)", s == 400, (s, b))
    kb = L.chave()
    s1, p1 = call("POST", "/financeiro/contas/%s/baixar" % ct["id"], gt, {"meio": "BANCO"}, {"Idempotency-Key": kb})
    s2, p2 = call("POST", "/financeiro/contas/%s/baixar" % ct["id"], gt, {"meio": "BANCO"}, {"Idempotency-Key": kb})
    check("conta paga uma vez (repetição com a mesma chave é idempotente)", s1 == 200 and s2 == 200 and g(p1, "situacao") == "PAGA", (s1, s2))
    s, b = call("POST", "/financeiro/contas/%s/baixar" % ct["id"], gt, {"meio": "BANCO"}, {"Idempotency-Key": L.chave()})
    check("pagar de novo é recusado (400)", s == 400 and "já foi baixada" in str(g(b, "message")), (s, b))
    ke = L.chave()
    s1, e1 = call("POST", "/financeiro/contas/%s/estornar" % ct["id"], gt, {"motivo": "Pagamento em duplicidade"}, {"Idempotency-Key": ke})
    s2, e2 = call("POST", "/financeiro/contas/%s/estornar" % ct["id"], gt, {"motivo": "Pagamento em duplicidade"}, {"Idempotency-Key": ke})
    check("estorno da baixa: conta volta a ABERTA e o histórico guarda tudo",
          s1 == 200 and s2 == 200 and g(e1, "situacao") == "ABERTA" and [x["tipo"] for x in g(e2, "eventos")] == ["CRIADA", "PAGA", "ESTORNADA"],
          (s1, s2, [x["tipo"] for x in g(e2, "eventos", default=[])]))
    s, b = call("POST", "/financeiro/contas/%s/cancelar" % ct["id"], gt, {"motivo": "Contrato encerrado"})
    check("conta em aberto pode ser cancelada com motivo", s == 200 and g(b, "situacao") == "CANCELADA", (s, b))
    s, rc = call("POST", "/financeiro/contas", gt, dict(corpo, tipo="RECEBER", descricao="Venda de sucata (teste)"))
    s, b = call("POST", "/financeiro/contas/%s/baixar" % rc["id"], gt, {"meio": "BANCO"}, {"Idempotency-Key": L.chave()})
    check("conta a receber baixada entra no banco", s == 200 and g(b, "situacao") == "PAGA" and g(b, "tipo") == "RECEBER", (s, b))
    s, lista = call("GET", "/financeiro/contas?situacao=ABERTA", gt)
    check("listagem de contas por situação", s == 200 and isinstance(lista, list), (s, lista))


def comissoes(c):
    at, gt = c["adm"], c["ger"]["token"]
    v = venda(c)
    vid, total = v["id"], v["total"]
    p = comissao_da_venda(c, vid)
    check("D01 definido: previsão de comissão (PREVISTA) sobre o total cobrado",
          p and p["status"] == "PREVISTA" and abs(p["base"] - total) < 0.005 and abs(p["valor"] - round(total * 0.05, 2)) < 0.01, p)
    receber(c, vid, round(total / 2, 2))
    check("pagamento parcial ainda não adquire a comissão", comissao_da_venda(c, vid)["status"] == "PREVISTA")
    quitar(c, vid)
    p = comissao_da_venda(c, vid)
    check("D02 = quitação: comissão DEVIDA após quitar", p["status"] == "DEVIDA", p)
    aplicar_fin(c, dict(FIN_TESTE, comissaoPercentual=8))
    check("regra histórica: a venda antiga mantém 5%", abs(comissao_da_venda(c, vid)["percentual"] - 5) < 0.001)
    v2 = venda(c)
    check("a venda nova usa o percentual vigente (8%)", abs(comissao_da_venda(c, v2["id"])["percentual"] - 8) < 0.001)
    aplicar_fin(c, dict(FIN_TESTE))
    s, b = call("GET", "/financeiro/comissoes/minhas", c["vend"]["token"])
    check("vendedor lê a própria comissão", s == 200 and any(i["pedidoId"] == vid for i in g(b, "itens", default=[])), (s, b))
    s, b = call("GET", "/financeiro/comissoes", c["vend"]["token"])
    check("vendedor não lê a comissão de outros (403)", s == 403, (s, b))
    corpo = {"vendedorId": c["vend"]["id"], "vencimento": L.amanha(7)}
    s, b = call("POST", "/financeiro/comissoes/pagamento", gt, corpo)
    check("gerente não gera pagamento de comissão (403)", s == 403, (s, b))
    s, ct = call("POST", "/financeiro/comissoes/pagamento", at, corpo)
    check("proprietário gera a conta a pagar das comissões devidas", s == 200 and ct["tipo"] == "PAGAR" and ct["origem"] == "COMISSAO", (s, ct))
    check("comissão passa a EM_CONTA", comissao_da_venda(c, vid)["status"] == "EM_CONTA")
    call("POST", "/financeiro/contas/%s/baixar" % ct["id"], gt, {"meio": "BANCO"}, {"Idempotency-Key": L.chave()})
    check("conta paga: comissão PAGA", comissao_da_venda(c, vid)["status"] == "PAGA")
    # reversão por cancelamento (venda sem recebimentos)
    v3 = venda(c)
    s, b = call("POST", "/vendas/%s/cancelar" % v3["id"], gt, {"motivo": "Desistência"})
    rev = comissao_da_venda(c, v3["id"], "REVERSAO")
    prev = comissao_da_venda(c, v3["id"], "PREVISAO")
    check("cancelamento reverte a previsão (REVERTIDA + REVERSAO compensada)",
          s == 200 and prev["status"] == "REVERTIDA" and rev and rev["valor"] < 0 and rev["status"] == "COMPENSADA", (prev, rev))
    # D02 pendente: nada é adquirido nem pago
    aplicar_fin(c, dict(FIN_TESTE, comissaoAquisicao=None))
    v4 = venda(c)
    quitar(c, v4["id"])
    check("D02 pendente: a comissão permanece PREVISTA mesmo com a venda quitada", comissao_da_venda(c, v4["id"])["status"] == "PREVISTA")
    s, r = call("GET", "/financeiro/comissoes?vendedorId=%s" % c["vend"]["id"], at)
    check("aviso D02 visível na tela de comissões", any("D02" in a for a in g(r, "avisos", default=[])), g(r, "avisos"))
    aplicar_fin(c, FIN_TESTE)


def restituicao(c):
    gt, at = c["ger"]["token"], c["adm"]
    v = venda(c, qtd=2)
    vid, total = v["id"], v["total"]
    quitar(c, vid)
    call("POST", "/vendas/%s/entrega/agendar" % vid, gt, {"data": L.amanha(2), "periodo": "MANHA"})
    call("POST", "/vendas/%s/saida" % vid, gt, headers={"Idempotency-Key": L.chave()})
    s, b = call("POST", "/vendas/%s/entrega/concluir" % vid, gt, campos={"recebedor": "Cliente"})
    check("venda entregue para a devolução", s == 200, (s, b))
    item_id = g(detalhe(c, vid), "itens")[0]["id"]
    s, oc = call("POST", "/vendas/%s/ocorrencias" % vid, gt, {"tipo": "DEVOLUCAO", "descricao": "Não gostou", "itemId": item_id, "quantidade": 1})
    check("abrir ocorrência de devolução de 1 unidade", s == 200, (s, oc))
    oid = oc["id"]
    aplicar_fin(c, dict(FIN_TESTE, permiteRestituicao=None))
    s, b = call("POST", "/ocorrencias/%s/restituicoes" % oid, gt, {"valor": 100, "motivo": "Devolução"})
    check("D09 pendente: restituição bloqueada (422 D09)", s == 422 and "D09" in str(g(b, "message")), (s, b))
    aplicar_fin(c, dict(FIN_TESTE, permiteRestituicao=False))
    s, b = call("POST", "/ocorrencias/%s/restituicoes" % oid, gt, {"valor": 100, "motivo": "Devolução"})
    check("D09 = não permite: restituição recusada (400)", s == 400 and "não permite" in str(g(b, "message")), (s, b))
    aplicar_fin(c, FIN_TESTE)
    s, b = call("POST", "/ocorrencias/%s/restituicoes" % oid, gt, {"valor": 100, "motivo": "Devolução"})
    check("sem devolução física recebida não há restituição (400)", s == 400 and "devolução física" in str(g(b, "message")), (s, b))
    s, b = call("POST", "/ocorrencias/%s/receber-devolucao" % oid, gt, {"condicao": "APTA_REVENDA", "avaliacao": "Sem avarias"},
                {"Idempotency-Key": L.chave()})
    check("receber a devolução física não restitui dinheiro sozinho",
          s == 200 and g(call("GET", "/financeiro/restituicoes", gt), 1) is not None and len(lancs(c, vid)) == 1, (s, lancs(c, vid)))
    unit = total / 2
    s, b = call("POST", "/ocorrencias/%s/restituicoes" % oid, gt, {"valor": round(unit + 0.01, 2), "motivo": "Acima do item"})
    check("valor acima do item devolvido é recusado (400)", s == 400, (s, b))
    s, r = call("POST", "/ocorrencias/%s/restituicoes" % oid, gt, {"valor": round(unit, 2), "motivo": "Devolução de 1 un."})
    check("solicitar restituição (SOLICITADA)", s == 200 and g(r, "status") == "SOLICITADA", (s, r))
    s, b = call("POST", "/restituicoes/%s/efetivar" % r["id"], gt, {}, {"Idempotency-Key": L.chave()})
    check("efetivar sem autorização é recusado (400)", s == 400, (s, b))
    s, b = call("POST", "/restituicoes/%s/autorizar" % r["id"], gt)
    check("solicitante não autoriza a própria restituição (400)", s == 400 and "própria" in str(g(b, "message")), (s, b))
    s, b = call("POST", "/restituicoes/%s/autorizar" % r["id"], at)
    check("proprietário autoriza", s == 200 and g(b, "status") == "AUTORIZADA", (s, b))
    k = L.chave()
    s1, e1 = call("POST", "/restituicoes/%s/efetivar" % r["id"], gt, {}, {"Idempotency-Key": k})
    s2, e2 = call("POST", "/restituicoes/%s/efetivar" % r["id"], gt, {}, {"Idempotency-Key": k})
    check("efetivar: saída do BANCO uma única vez (repetição idempotente)",
          s1 == 200 and s2 == 200 and g(e1, "status") == "EFETIVADA" and len(lancs(c, vid)) == 2, (s1, s2, lancs(c, vid)))
    s, b = call("POST", "/restituicoes/%s/efetivar" % r["id"], gt, {}, {"Idempotency-Key": L.chave()})
    check("efetivar de novo com outra chave é recusado (400)", s == 400 and "já foi efetivada" in str(g(b, "message")), (s, b))
    pg = pagamento(c, vid)
    check("detalhe da venda mostra o valor restituído", abs(pg["restituido"] - round(unit, 2)) < 0.005, pg.get("restituido"))
    rid = pg["recebimentos"][0]["id"]
    s, b = estornar(c, rid)
    check("não estorna recebimento cujo valor já foi restituído (400)", s == 400 and "restituídos" in str(g(b, "message")), (s, b))
    rev = comissao_da_venda(c, vid, "REVERSAO")
    check("restituição gera reversão proporcional da comissão (ligada à venda)", rev is not None and rev["valor"] < 0, rev)
    # troca com diferença
    item2 = g(detalhe(c, vid), "itens")[0]["id"]
    s, o2 = call("POST", "/vendas/%s/ocorrencias" % vid, gt, {"tipo": "TROCA", "descricao": "Modelo maior", "itemId": item2, "quantidade": 1,
                                                              "troca": {"produtoId": c["produto"]["id"], "quantidade": 1}})
    if s == 200:
        check("troca por produto de mesmo valor não gera diferença", abs(g(o2, "diferencaCalculada", default=0)) < 0.005, o2)


def metas(c):
    gt, vt, at = c["ger"]["token"], c["vend"]["token"], c["adm"]
    mes = datetime.date.today().strftime("%Y-%m")
    s, b = call("PUT", "/financeiro/metas", vt, {"vendedorId": c["vend"]["id"], "mes": mes, "valor": 5000})
    check("vendedor não define meta (403)", s == 403, (s, b))
    s, b = call("PUT", "/financeiro/metas", gt, {"vendedorId": c["vend"]["id"], "mes": mes, "valor": 5000})
    check("gerente define a meta mensal do vendedor", s == 200 and abs(g(b, "meta") - 5000) < 0.005, (s, b))
    s, lista = call("GET", "/financeiro/metas?mes=%s" % mes, gt)
    m = next((x for x in g(lista, default=[]) if x["vendedorId"] == c["vend"]["id"]), None)
    check("meta acompanha o vendido (cancelamentos não contam)", m is not None and m["vendido"] > 0, m)
    aplicar_fin(c, dict(FIN_TESTE, metaDescontaDevolucoes=None))
    s, lista = call("GET", "/financeiro/metas?mes=%s" % mes, gt)
    m = next((x for x in g(lista, default=[]) if x["vendedorId"] == c["vend"]["id"]), None)
    check("D09 pendente: atingimento PROVISÓRIO com a política declarada pendente",
          m and m["provisorio"] is True and m["politicaDevolucoes"] == "PENDENTE_D09" and "D09" in str(m["observacao"]), m)
    aplicar_fin(c, FIN_TESTE)
    s, b = call("GET", "/financeiro/metas/minha?mes=%s" % mes, vt)
    check("vendedor lê apenas a própria meta", s == 200 and abs(g(b, "meta") - 5000) < 0.005, (s, b))
    s, b = call("GET", "/financeiro/metas?mes=%s" % mes, vt)
    check("vendedor não lista metas de todos (403)", s == 403, (s, b))


def fechamento(c):
    gt, at = c["ger"]["token"], c["adm"]
    hoje = datetime.date.today()
    mes_atual = hoje.strftime("%Y-%m")
    s, p = call("GET", "/financeiro/fechamento/previa?mes=%s" % mes_atual, gt)
    r = g(p, "resultado", default={})
    check("prévia do mês corrente: resultado NÃO é definitivo e não traz lucro apurado",
          s == 200 and r.get("definitivo") is False and r.get("lucroApurado") is None, (s, r))
    check("prévia lista o que falta (custos e critério de competência)", r.get("itensSemCusto", 0) > 0 and any("custo" in f for f in r.get("faltantes", [])), r)
    check("caixa e banco aparecem separados na prévia", "entradasCaixa" in g(p, "caixa", default={}) and "entradasBanco" in g(p, "caixa", default={}))
    check("mês corrente não pode ser aprovado", g(p, "podeAprovar") is False and any("ainda não terminou" in b for b in g(p, "bloqueiosAprovacao", default=[])), p)
    s, b = call("POST", "/financeiro/fechamento/aprovar", at, {"mes": mes_atual})
    check("aprovar mês em andamento é recusado (400)", s == 400, (s, b))

    ant = (hoje.replace(day=1) - datetime.timedelta(days=30 * (3 + secrets.randbelow(40)))).replace(day=1)
    mes = ant.strftime("%Y-%m")
    dia = ant.replace(day=15).isoformat()
    s, p0 = call("GET", "/financeiro/fechamento/previa?mes=%s" % mes, gt)
    if g(p0, "fechado"):
        call("POST", "/financeiro/fechamento/reabrir", at, {"mes": mes, "justificativa": "Reabertura para repetir o roteiro de validação"})
    v = venda(c)
    s, b = receber(c, v["id"], round(v["total"] / 2, 2), extra={"dataPagamento": dia})
    check("lançamento retroativo em mês ainda aberto é aceito", s == 200, (s, b))
    s, p = call("GET", "/financeiro/fechamento/previa?mes=%s" % mes, gt)
    check("prévia do mês passado traz a entrada no BANCO (Pix) e nada no caixa",
          g(p, "caixa", "entradasBanco") >= round(v["total"] / 2, 2) - 0.01 and g(p, "caixa", "entradasCaixa") == 0, g(p, "caixa"))
    aplicar_fin(c, dict(FIN_TESTE, fechamentoExigeSemPendencias=None))
    s, b = call("POST", "/financeiro/fechamento/aprovar", at, {"mes": mes})
    check("D10 pendente: aprovação bloqueada (422 D10)", s == 422 and "D10" in str(g(b, "message")), (s, b))
    aplicar_fin(c, FIN_TESTE)
    s, b = call("POST", "/financeiro/fechamento/aprovar", gt, {"mes": mes})
    check("gerente não aprova o fechamento (403)", s == 403, (s, b))
    s, ap = call("POST", "/financeiro/fechamento/aprovar", at, {"mes": mes})
    check("proprietário aprova o fechamento (fechado, versão registrada)", s == 200 and g(ap, "fechado") is True and g(ap, "versao", default=0) >= 1, (s, ap))
    s, b = call("POST", "/financeiro/fechamento/aprovar", at, {"mes": mes})
    check("aprovar de novo é recusado (400)", s == 400 and "já está fechado" in str(g(b, "message")), (s, b))
    s, b = receber(c, v["id"], 10, extra={"dataPagamento": dia})
    check("período fechado: recebimento retroativo é bloqueado (400)", s == 400 and "fechado" in str(g(b, "message")), (s, b))
    s, b = call("POST", "/financeiro/contas", gt, {"tipo": "PAGAR", "descricao": "Retroativa", "categoria": "Teste", "competencia": dia,
                                                   "valor": 10, "vencimento": L.amanha(3)})
    check("período fechado: criar conta na competência é bloqueado (400)", s == 400 and "fechado" in str(g(b, "message")), (s, b))
    s, b = receber(c, v["id"], 10)
    check("lançamento com a data atual continua possível", s == 200, (s, b))
    s, b = call("POST", "/financeiro/fechamento/reabrir", gt, {"mes": mes, "justificativa": "Correção"})
    check("gerente não reabre (D07 = só o proprietário) (403)", s == 403, (s, b))
    s, b = call("POST", "/financeiro/fechamento/reabrir", at, {"mes": mes, "justificativa": " "})
    check("reabertura sem justificativa é recusada (400)", s == 400, (s, b))
    aplicar_fin(c, dict(FIN_TESTE, perfisReabertura=None))
    s, b = call("POST", "/financeiro/fechamento/reabrir", at, {"mes": mes, "justificativa": "Correção de data"})
    check("D07 pendente: reabertura bloqueada (422 D07)", s == 422 and "D07" in str(g(b, "message")), (s, b))
    aplicar_fin(c, FIN_TESTE)
    s, b = call("POST", "/financeiro/fechamento/reabrir", at, {"mes": mes, "justificativa": "Pix lançado na data errada"})
    check("reabrir com justificativa", s == 200 and g(b, "fechado") is False, (s, b))
    s, b = receber(c, v["id"], 10, extra={"dataPagamento": dia})
    check("após reabrir o período aceita lançamentos de novo", s == 200, (s, b))
    s, ap2 = call("POST", "/financeiro/fechamento/aprovar", at, {"mes": mes})
    check("nova aprovação cria a versão seguinte e mantém a anterior",
          s == 200 and g(ap2, "versao") == g(ap, "versao") + 1 and len(g(ap2, "versoes", default=[])) >= 2, (s, ap2))
    call("POST", "/financeiro/fechamento/reabrir", at, {"mes": mes, "justificativa": "Fim do roteiro de validação"})


if __name__ == "__main__":
    L.executar(principal, finalizar)
