#!/usr/bin/env python3
"""Validação por API da Etapa 1 (gestão de vendas com reserva de estoque).

Cobre: autenticação (401 x 403), matriz de permissões, limite de usuários, estado de configuração pendente
(D03/D04/D07/D05) com bloqueios isolados, clientes (CPF e duplicidade), venda completa (desconto, aprovação,
confirmação idempotente, reserva, cancelamento e permissões por D07), escopo entre vendedores e catálogo
preservando ids de variação.

Ambiente: SOMENTE o local descartável (ver README.md). Requer LP_ADMIN_EMAIL e LP_ADMIN_PASSWORD.
A configuração comercial é salva no início e RESTAURADA no final (bloco finally).
Código de saída: 0 = tudo OK; 1 = alguma verificação falhou; 2 = configuração/ambiente ausente.
"""
import lib as L
from lib import call, check, g, info, secao

ctx = {}


def principal():
    adm = L.preparar()
    ctx["adm"] = adm
    ctx["snap"] = L.config_salvar(adm)
    L.rotulo_teste()
    info("pendências da configuração ao iniciar: %s" % sorted({p["codigo"] for p in ctx["snap"]["pendencias"]}))
    ctx["criadas"] = L.condicoes_teste(adm, ctx["snap"])

    secao("1. Autenticação")
    autenticacao(adm)
    secao("2. Usuários e perfis")
    ger, v1, v2 = usuarios(adm)
    secao("3. Matriz de permissões")
    permissoes(adm, ger, v1)
    secao("4. Clientes")
    cliente = clientes(adm, ger, v1)
    secao("5. Configuração pendente (D03/D04/D07/D05) com bloqueios isolados")
    pend = produtos_base(adm)
    pendencias(adm, ger, v1, cliente, pend)
    secao("6. Venda completa")
    venda_completa(adm, ger, v1, v2, cliente, pend)
    secao("7. Escopo entre vendedores")
    escopo(adm, ger, v1, v2, cliente, pend)
    secao("8. Catálogo preserva ids de variação")
    catalogo(adm, v1, cliente, pend)


# ---------------------------------------------------------------------------

def autenticacao(adm):
    check("login do proprietário", adm)
    s, b = call("POST", "/auth/login", body={"email": L.ADMIN_EMAIL, "senha": "senha-errada-" + L.SUFIXO})
    check("login com senha errada não emite token", s >= 400 and not g(b, "accessToken"), (s, b))
    if s != 401:
        info("OBSERVAÇÃO: login inválido respondeu %s (o esperado seria 401); ver 'achados' no relatório." % s)
    check("vendas sem token = 401", call("GET", "/vendas")[0] == 401)
    check("token malformado = 401", call("GET", "/vendas", "token.invalido.xyz")[0] == 401)
    email, senha = L.os.environ.get("LP_CLIENTE_EMAIL"), L.os.environ.get("LP_CLIENTE_PASSWORD")
    cli = L.login(email, senha) if email and senha else None
    if not cli:
        info("PULADO: usuário CLIENTE de teste não configurado (LP_CLIENTE_EMAIL/LP_CLIENTE_PASSWORD).")
        return
    for caminho in ("/vendas", "/clientes", "/estoque", "/vendas/configuracao", "/agenda?de=2030-01-01&ate=2030-01-02"):
        s, _ = call("GET", caminho, cli)
        check("usuário autenticado sem perfil operacional (CLIENTE) em %s = 403 (não 401)" % caminho.split("?")[0], s == 403, s)


def usuarios(adm):
    ger = L.novo_usuario(adm, "GERENTE", "ger")
    v1 = L.novo_usuario(adm, "VENDEDOR", "vend1")
    v2 = L.novo_usuario(adm, "VENDEDOR", "vend2")
    check("proprietário cria gerente e 2 vendedores (com login)", ger["token"] and v1["token"] and v2["token"])
    s, b = call("POST", "/usuarios", adm, {"nome": "Curta", "email": "lp-val-curta-%s@example.invalid" % L.SUFIXO,
                                          "senha": "123", "perfil": "VENDEDOR"})
    check("senha curta é recusada com mensagem (400)", s == 400 and "8" in str(g(b, "message")), (s, b))
    s, b = call("POST", "/usuarios", adm, {"nome": "Dup", "email": ger["email"], "senha": L.TEST_PASSWORD,
                                          "perfil": "VENDEDOR"})
    check("e-mail duplicado é recusado (400)", s == 400, (s, b))
    s, b = call("POST", "/usuarios", adm, {"nome": "Cli", "email": "lp-val-perfil-%s@example.invalid" % L.SUFIXO,
                                          "senha": L.TEST_PASSWORD, "perfil": "CLIENTE"})
    check("perfil CLIENTE não pode ser criado em Usuários (400)", s == 400, (s, b))
    s, us = call("GET", "/usuarios", adm)
    s2, b2 = call("POST", "/usuarios/%s/desativar" % _id_admin(us), adm)
    check("proprietário não desativa o próprio usuário (400)", s2 == 400, (s2, b2))

    # limite de 5 usuários operacionais ativos: preenche as vagas livres e confirma que a próxima é recusada
    ativos = sum(1 for u in us if u["ativo"] and set(u["perfis"]) & {"ADMIN", "GERENTE", "VENDEDOR"})
    extras = []
    for i in range(max(0, 5 - ativos)):
        extras.append(L.novo_usuario(adm, "VENDEDOR", "extra%d" % i))
    s, b = call("POST", "/usuarios", adm, {"nome": "Excedente", "email": "lp-val-excedente-%s@example.invalid" % L.SUFIXO,
                                          "senha": L.TEST_PASSWORD, "perfil": "VENDEDOR"})
    check("6º usuário operacional ativo é recusado (limite 5)", s == 400 and "Limite" in str(g(b, "message")), (s, b))
    if extras:
        alvo = extras[0]
        s, _ = call("POST", "/usuarios/%s/desativar" % alvo["id"], adm)
        check("desativar usuário libera vaga", s == 200)
        check("usuário desativado não faz login", L.login(alvo["email"], L.TEST_PASSWORD) is None)
        check("token emitido antes da desativação deixa de valer (401)", call("GET", "/vendas", alvo["token"])[0] == 401)
    return ger, v1, v2


def _id_admin(us):
    for u in us:
        if u["email"] == L.ADMIN_EMAIL:
            return u["id"]
    return 0


def permissoes(adm, ger, v1):
    gt, vt = ger["token"], v1["token"]
    check("vendedor não lista usuários (403)", call("GET", "/usuarios", vt)[0] == 403)
    check("gerente não lista usuários (403)", call("GET", "/usuarios", gt)[0] == 403)
    check("vendedor lê a lista de vendedores (200)", call("GET", "/usuarios/vendedores", vt)[0] == 200)
    check("vendedor não lê a configuração comercial (403)", call("GET", "/config/comercial", vt)[0] == 403)
    check("gerente lê a configuração comercial (200)", call("GET", "/config/comercial", gt)[0] == 200)
    check("vendedor lê a configuração para vender (200)", call("GET", "/vendas/configuracao", vt)[0] == 200)
    s, b = call("PUT", "/config/comercial", gt, {"limiteDescontoPercentual": 10, "arredondamento": "HALF_UP",
                                                 "perfisCancelamento": ["ADMIN", "VENDEDOR"], "exigePagamentoExpedir": None})
    check("gerente NÃO define perfis de cancelamento (403)", s == 403, (s, b))
    s, b = call("PUT", "/config/comercial", vt, {"limiteDescontoPercentual": 10})
    check("vendedor não altera a configuração (403)", s == 403, (s, b))
    check("vendedor não acessa encomendas (403)", call("GET", "/encomendas", vt)[0] == 403)
    check("vendedor não acessa ocorrências (403)", call("GET", "/ocorrencias", vt)[0] == 403)
    check("vendedor não registra ajuste de inventário (403)",
          call("POST", "/estoque/ajustes", vt, {"produtoId": 1, "contado": 1, "motivo": "x"}, {"Idempotency-Key": L.chave()})[0] == 403)
    check("gerente não lê a auditoria (403)", call("GET", "/auditoria", gt)[0] == 403)
    s, b = call("GET", "/auditoria", adm)
    check("proprietário não é barrado na auditoria (nem 401 nem 403)", s not in (401, 403), (s, b))
    if s != 200:
        info("OBSERVAÇÃO: GET /auditoria respondeu %s para o proprietário (esperado 200); ver 'achados' no relatório." % s)


def clientes(adm, ger, v1):
    vt = v1["token"]
    cpf = L.cpf_formatado(L.cpf_aleatorio())
    nome = "Maria Val %s" % L.SUFIXO
    tel = L.telefone_aleatorio()
    s, cliente = call("POST", "/clientes", vt, L.cliente_body(nome, cpf=cpf, telefone=tel))
    check("vendedor cadastra cliente com endereço (200)", s == 200 and g(cliente, "enderecos", 0, "id"), (s, cliente))
    s, b = call("POST", "/clientes", vt, {"nome": "Outra Pessoa " + L.SUFIXO, "cpf": cpf})
    check("CPF repetido bloqueia (409 CLIENTE_DUPLICADO)", s == 409 and g(b, "code") == "CLIENTE_DUPLICADO", (s, b))
    s, b = call("POST", "/clientes", vt, {"nome": "Outra Pessoa " + L.SUFIXO, "cpf": "111.111.111-11"})
    check("CPF inválido recusado (400)", s == 400, (s, b))
    s, b = call("POST", "/clientes", vt, {"nome": nome})
    check("nome repetido alerta (409 CLIENTE_POSSIVEL_DUPLICADO)", s == 409 and g(b, "code") == "CLIENTE_POSSIVEL_DUPLICADO", (s, b))
    s, b = call("POST", "/clientes", vt, {"nome": "Pessoa Diferente " + L.SUFIXO, "telefone": tel})
    check("telefone repetido alerta (409 CLIENTE_POSSIVEL_DUPLICADO)", s == 409 and g(b, "code") == "CLIENTE_POSSIVEL_DUPLICADO", (s, b))
    s, b = call("POST", "/clientes", vt, {"nome": nome, "confirmarDuplicidade": True})
    check("duplicidade confirmada cadastra (200)", s == 200, (s, b))
    s, lista = call("GET", "/clientes?q=" + cpf, vt)
    check("busca por CPF encontra o cliente", s == 200 and any(c["id"] == cliente["id"] for c in lista), (s, lista))
    s, dups = call("GET", "/clientes/duplicidades?nome=" + nome.replace(" ", "%20"), vt)
    check("consulta de duplicidades devolve os cadastros de mesmo nome", s == 200 and len(dups) >= 2, (s, dups))
    corpo = L.cliente_body(nome, cpf=cpf, telefone=tel)
    corpo["observacoes"] = "atualizado pelo roteiro"
    s, b = call("PUT", "/clientes/%s" % cliente["id"], vt, corpo)
    check("atualizar cliente que tem homônimo alerta (409 CLIENTE_POSSIVEL_DUPLICADO)", s == 409 and g(b, "code") == "CLIENTE_POSSIVEL_DUPLICADO", (s, b))
    corpo["confirmarDuplicidade"] = True
    s, b = call("PUT", "/clientes/%s" % cliente["id"], vt, corpo)
    check("atualizar cliente com duplicidade confirmada (200)", s == 200 and g(b, "observacoes") == "atualizado pelo roteiro", (s, b))
    check("vendedor não desativa cliente (403)", call("POST", "/clientes/%s/desativar" % cliente["id"], vt)[0] == 403)
    s, descartavel = call("POST", "/clientes", vt, L.cliente_body("Inativo Val %s" % L.SUFIXO))
    s, b = call("POST", "/clientes/%s/desativar" % descartavel["id"], ger["token"])
    check("gerente desativa cliente (200)", s == 200 and g(b, "ativo") is False, (s, b))
    ctx["cliente_inativo"] = descartavel
    return cliente


def produtos_base(adm):
    p_a = L.criar_produto(adm, "Produto A", 1000.00, estoque=20)
    p_v = L.criar_produto(adm, "Produto V", 1500.00, variacoes=[
        {"cor": "Cinza", "tamanho": "2L", "adicionalPreco": 0, "estoque": 5},
        {"cor": "Azul", "tamanho": "3L", "adicionalPreco": 200, "estoque": 5}])
    p_n = L.criar_produto(adm, "Produto N", 700.00, variacoes=[{"cor": "Verde", "tamanho": "U", "adicionalPreco": 0, "estoque": None}])
    info("produtos de teste: A=%s (sem variação), V=%s (2 variações), N=%s (variação sem saldo)" % (p_a["id"], p_v["id"], p_n["id"]))
    return {"A": p_a, "V": p_v, "N": p_n}


def pendencias(adm, ger, v1, cliente, pr):
    vt, gt = v1["token"], ger["token"]
    pa = pr["A"]
    # estado totalmente pendente
    L.config_aplicar(adm, None, None, [], None)
    L.condicoes_ativar(adm, False)
    s, cfg = call("GET", "/vendas/configuracao", vt)
    cods = sorted({p["codigo"] for p in g(cfg, "pendencias", default=[])})
    check("pendências listadas: D03, D04, D05 e D07", cods == ["D03", "D04", "D05", "D07"], cods)
    pend_cfg = g(call("GET", "/config/comercial", gt)[1], "pendencias", default=[])
    cods_cfg = sorted({p["codigo"] for p in pend_cfg if p.get("area") != "FINANCEIRO"})
    check("gerente vê as mesmas pendências de vendas e atendimento na configuração comercial (as financeiras vêm à parte)",
          cods_cfg == cods, cods_cfg)
    s, b = call("POST", "/clientes", vt, L.cliente_body("Pendente Val %s" % L.SUFIXO))
    check("cadastrar cliente NÃO é bloqueado pela configuração pendente", s == 200, (s, b))
    check("vitrine/produtos públicos seguem funcionando sem token", call("GET", "/produtos?nome=Produto")[0] == 200)
    s, b = L.registrar_venda(vt, cliente, [L.item(pa)])
    check("registrar venda bloqueado por D04 (422 CONFIGURACAO_PENDENTE)", s == 422 and g(b, "code") == "CONFIGURACAO_PENDENTE", (s, b))
    check("listar vendas NÃO é bloqueado", call("GET", "/vendas", vt)[0] == 200)
    s, b = call("GET", "/estoque", vt)
    check("estoque NÃO é bloqueado e mostra alerta de variação sem saldo",
          s == 200 and any(x.get("alerta") for x in b if x["produtoId"] == pr["N"]["id"]), s)
    s, b = call("POST", "/vendas/999999999/cancelar", gt, {"motivo": "x"})
    check("cancelar venda inexistente = 404 (não 500)", s == 404, (s, b))

    # D07 isolado: vender e confirmar funcionam; só cancelar bloqueia
    L.config_aplicar(adm, L.LIMITE_TESTE, L.ARRED_TESTE, [], False)
    L.condicoes_ativar(adm, True)
    s, v = L.registrar_venda(vt, cliente, [L.item(pa)])
    check("D07 pendente: registrar venda continua liberado", s == 200, (s, v))
    s, c = L.confirmar(vt, g(v, "id"))
    check("D07 pendente: confirmar continua liberado", s == 200, (s, c))
    s, b = call("POST", "/vendas/%s/cancelar" % g(v, "id"), adm, {"motivo": "teste D07"})
    check("D07 pendente: cancelar bloqueado (422 CONFIGURACAO_PENDENTE, D07)",
          s == 422 and g(b, "code") == "CONFIGURACAO_PENDENTE" and "D07" in str(g(b, "message")), (s, b))
    s, d = call("GET", "/vendas/%s" % g(v, "id"), adm)
    check("D07 pendente: detalhe informa acoes.bloqueios.cancelar e podeCancelar=false",
          "cancelar" in g(d, "acoes", "bloqueios", default={}) and g(d, "acoes", "podeCancelar") is False, g(d, "acoes"))
    ctx["venda_d07"] = g(v, "id")

    # D03 isolado: sem desconto vende e cancela; com desconto bloqueia
    L.config_aplicar(adm, None, L.ARRED_TESTE, ["ADMIN", "GERENTE"], False)
    s, b = L.registrar_venda(vt, cliente, [L.item(pa)], desconto=10)
    check("D03 pendente: venda COM desconto bloqueada (422, D03)", s == 422 and "D03" in str(g(b, "message")), (s, b))
    s, v = L.registrar_venda(vt, cliente, [L.item(pa)])
    check("D03 pendente: venda SEM desconto liberada", s == 200, (s, v))
    s, b = call("POST", "/vendas/%s/cancelar" % g(v, "id"), gt, {"motivo": "teste D03"})
    check("D03 pendente: cancelar liberado (D07 definido)", s == 200 and g(b, "statusComercial") == "CANCELADA", (s, b))
    s, b = call("POST", "/vendas/%s/cancelar" % ctx["venda_d07"], gt, {"motivo": "limpeza D07"})
    check("D07 definido: a venda antes bloqueada pode ser cancelada", s == 200, (s, b))

    # D04 isolado: arredondamento ausente
    L.config_aplicar(adm, L.LIMITE_TESTE, L.ARRED_TESTE, ["ADMIN", "GERENTE"], False)
    s, v_exist = L.registrar_venda(vt, cliente, [L.item(pa)])
    L.config_aplicar(adm, L.LIMITE_TESTE, None, ["ADMIN", "GERENTE"], False)
    s, b = L.registrar_venda(vt, cliente, [L.item(pa)])
    check("D04 (arredondamento) pendente: registrar bloqueado", s == 422 and "D04" in str(g(b, "message")), (s, b))
    check("D04 pendente: consultas e estoque liberados", call("GET", "/vendas", vt)[0] == 200 and call("GET", "/estoque", vt)[0] == 200)
    s, d = call("POST", "/vendas/%s/cancelar" % g(v_exist, "id"), gt, {"motivo": "limpeza D04"})
    check("D04 pendente: cancelar venda existente liberado", s == 200, (s, d))

    # D04: sem condições ativas
    L.config_aplicar(adm, L.LIMITE_TESTE, L.ARRED_TESTE, ["ADMIN", "GERENTE"], False)
    L.condicoes_ativar(adm, False)
    s, b = L.registrar_venda(vt, cliente, [L.item(pa)])
    check("D04 (sem condições ativas): registrar bloqueado", s == 422 and "D04" in str(g(b, "message")), (s, b))
    L.condicoes_ativar(adm, True)
    s, b = L.registrar_venda(vt, cliente, [L.item(pa)], forma="DINHEIRO")
    check("forma/parcelas não cadastrada (Dinheiro) NÃO é aceita (400)", s == 400 and "não cadastrada" in str(g(b, "message")), (s, b))
    s, v = L.registrar_venda(vt, cliente, [L.item(pa)])
    check("tudo configurado de novo: registrar liberado", s == 200, (s, v))
    call("POST", "/vendas/%s/cancelar" % g(v, "id"), gt, {"motivo": "limpeza"})


def venda_completa(adm, ger, v1, v2, cliente, pr):
    vt, gt = v1["token"], ger["token"]
    pa, pv = pr["A"], pr["V"]
    L.config_aplicar(adm, L.LIMITE_TESTE, L.ARRED_TESTE, ["ADMIN", "GERENTE"], False)

    # registro, preço calculado no servidor e idempotência do registro
    k = "val-reg-" + L.SUFIXO
    s, v = L.registrar_venda(vt, cliente, [L.item(pa)], chaveIdempotencia=k)
    check("registrar venda (200, RASCUNHO)", s == 200 and g(v, "statusComercial") == "RASCUNHO", (s, v))
    check("preço calculado no servidor: R$ 1000 com PIX -5% = 950.00 e frete 0",
          g(v, "total") == 950.0 and g(v, "frete") == 0, (g(v, "total"), g(v, "frete")))
    check("vendedor responsável = quem registrou", g(v, "vendedor", "id") == v1["id"], g(v, "vendedor"))
    s, v_rep = L.registrar_venda(vt, cliente, [L.item(pa)], chaveIdempotencia=k)
    check("registro repetido com a mesma chaveIdempotencia devolve a mesma venda", s == 200 and g(v_rep, "id") == g(v, "id"), (s, v_rep))
    s, b = L.registrar_venda(vt, cliente, [])
    check("venda sem itens: mensagem de validação (400)", s == 400 and "item" in str(g(b, "message")).lower(), (s, b))
    s, b = L.registrar_venda(vt, cliente, [L.item(pa, quantidade=0)])
    check("quantidade zero recusada (400)", s == 400, (s, b))
    s, b = L.registrar_venda(vt, cliente, [L.item(pv)])
    check("produto com variações exige a variação (400)", s == 400 and "variação" in str(g(b, "message")), (s, b))
    s, b = L.registrar_venda(vt, cliente, [L.item(pr["N"], variacao=pr["N"]["variacoes"][0])])
    s2, b2 = L.confirmar(vt, g(b, "id")) if s == 200 else (s, b)
    check("variação sem saldo não pode ser confirmada (400, 'saldo')", s2 == 400 and "saldo" in str(g(b2, "message")), (s2, b2))
    if s == 200:
        call("POST", "/vendas/%s/cancelar" % g(b, "id"), gt, {"motivo": "limpeza"})
    s, b = L.registrar_venda(vt, ctx["cliente_inativo"], [L.item(pa)])
    check("cliente inativo não pode comprar (400)", s == 400 and "inativo" in str(g(b, "message")), (s, b))
    s, b = L.registrar_venda(vt, cliente, [L.item(pa)], vendedorId=ger["id"])
    check("vendedor não registra em nome de outro (400)", s == 400, (s, b))
    s, b = L.registrar_venda(vt, cliente, [L.item(pa)], tipo="ENTREGA", enderecoId=987654321)
    check("endereço que não é do cliente é recusado (400)", s == 400, (s, b))

    # desconto: no limite, acima do limite, aprovação
    s, d_ok = call("PUT", "/vendas/%s" % g(v, "id"), vt, L.venda_body(cliente, [L.item(pa)], desconto=95.00))
    check("desconto no limite (10% de 950 = 95.00) não exige aprovação", s == 200 and g(d_ok, "statusComercial") == "RASCUNHO" and g(d_ok, "total") == 855.0, (s, g(d_ok, "statusComercial"), g(d_ok, "total")))
    s, d_alto = call("PUT", "/vendas/%s" % g(v, "id"), vt, L.venda_body(cliente, [L.item(pa)], desconto=200.00, justificativaDesconto="cliente antigo"))
    check("desconto acima do limite deixa a venda AGUARDANDO_APROVACAO", s == 200 and g(d_alto, "statusComercial") == "AGUARDANDO_APROVACAO", (s, g(d_alto, "statusComercial")))
    s, b = L.confirmar(vt, g(v, "id"))
    check("venda aguardando aprovação não confirma (400)", s == 400, (s, b))
    check("vendedor não aprova desconto (403)", call("POST", "/vendas/%s/desconto/aprovar" % g(v, "id"), vt, {})[0] == 403)
    s, b = call("POST", "/vendas/%s/desconto/rejeitar" % g(v, "id"), gt, {})
    check("rejeitar desconto exige motivo (400)", s == 400, (s, b))
    s, b = call("POST", "/vendas/%s/desconto/aprovar" % g(v, "id"), gt, {"motivo": "ok"})
    check("gerente aprova o desconto (venda volta a RASCUNHO)", s == 200 and g(b, "statusComercial") == "RASCUNHO", (s, b))
    s, b = call("PUT", "/vendas/%s" % g(v, "id"), vt, L.venda_body(cliente, [L.item(pa)], desconto=250.00))
    check("alterar o valor do desconto invalida a aprovação anterior", s == 200 and g(b, "statusComercial") == "AGUARDANDO_APROVACAO", (s, g(b, "statusComercial")))
    # auto-aprovação: gerente registra venda própria com desconto alto e tenta aprovar
    s, vg = L.registrar_venda(gt, cliente, [L.item(pa)], desconto=300.00)
    check("gerente registra venda com desconto alto (aguarda aprovação)", s == 200 and g(vg, "statusComercial") == "AGUARDANDO_APROVACAO", (s, g(vg, "statusComercial")))
    s, b = call("POST", "/vendas/%s/desconto/aprovar" % g(vg, "id"), gt, {})
    check("gerente NÃO aprova o próprio desconto (400)", s == 400 and "próprio" in str(g(b, "message")), (s, b))
    s, b = call("POST", "/vendas/%s/desconto/aprovar" % g(vg, "id"), adm, {})
    check("proprietário aprova o desconto do gerente", s == 200 and g(b, "statusComercial") == "RASCUNHO", (s, b))
    call("POST", "/vendas/%s/cancelar" % g(vg, "id"), gt, {"motivo": "limpeza"})

    # volta ao valor sem desconto e confirma
    s, v = call("PUT", "/vendas/%s" % g(v, "id"), vt, L.venda_body(cliente, [L.item(pa)]))
    check("venda editável em rascunho: remover desconto restaura 950.00", s == 200 and g(v, "total") == 950.0, (s, g(v, "total")))
    antes = L.saldo(adm, pa)
    s, b = call("POST", "/vendas/%s/confirmar" % g(v, "id"), vt)
    check("confirmar sem Idempotency-Key: mensagem clara (400)", s == 400 and "Idempotency" in str(g(b, "message")), (s, b))
    k = L.chave()
    s1, c1 = L.confirmar(vt, g(v, "id"), k)
    s2, c2 = L.confirmar(vt, g(v, "id"), k)
    check("confirmação repetida com a mesma chave: 200 e apenas 1 reserva",
          s1 == 200 and s2 == 200 and g(c2, "statusComercial") == "CONFIRMADA" and len(g(c2, "reservas", default=[])) == 1, (s1, s2))
    s3, c3 = L.confirmar(vt, g(v, "id"), "outra-" + L.chave())
    check("confirmar de novo com OUTRA chave é recusado (400)", s3 == 400, (s3, c3))
    depois = L.saldo(adm, pa)
    check("reserva: reservado +1, físico inalterado, disponível -1",
          depois and antes and depois["reservado"] == antes["reservado"] + 1 and depois["fisico"] == antes["fisico"]
          and depois["disponivel"] == antes["disponivel"] - 1, (antes, depois))
    s, b = call("PUT", "/vendas/%s" % g(v, "id"), vt, L.venda_body(cliente, [L.item(pa)]))
    check("venda confirmada não pode mais ser editada (400)", s == 400, (s, b))

    # atomicidade: um item com saldo e outro sem -> nada é reservado
    ant_a = L.saldo(adm, pa)
    s, vx = L.registrar_venda(vt, cliente, [L.item(pa), L.item(pv, quantidade=50, variacao=pv["variacoes"][0])])
    s2, b2 = L.confirmar(vt, g(vx, "id"))
    check("estoque insuficiente recusa a confirmação (400)", s2 == 400 and "insuficiente" in str(g(b2, "message")), (s2, b2))
    dep_a = L.saldo(adm, pa)
    check("confirmação atômica: nenhum item foi reservado", dep_a["reservado"] == ant_a["reservado"], (ant_a, dep_a))
    call("POST", "/vendas/%s/cancelar" % g(vx, "id"), gt, {"motivo": "limpeza"})

    # cancelamento e permissões (D07)
    check("vendedor não cancela com D07=[ADMIN,GERENTE] (403)", call("POST", "/vendas/%s/cancelar" % g(v, "id"), vt, {"motivo": "x"})[0] == 403)
    s, b = call("POST", "/vendas/%s/cancelar" % g(v, "id"), gt, {})
    check("cancelar sem motivo é recusado (400)", s == 400, (s, b))
    L.config_aplicar(adm, L.LIMITE_TESTE, L.ARRED_TESTE, ["ADMIN"], False)
    check("D07=[ADMIN]: gerente não cancela (403)", call("POST", "/vendas/%s/cancelar" % g(v, "id"), gt, {"motivo": "x"})[0] == 403)
    s, b = call("GET", "/vendas/%s" % g(v, "id"), gt)
    check("D07=[ADMIN]: detalhe do gerente informa o bloqueio de cancelar", "cancelar" in g(b, "acoes", "bloqueios", default={}), g(b, "acoes"))
    s, b = call("POST", "/vendas/%s/cancelar" % g(v, "id"), adm, {"motivo": "cancelamento de teste"})
    check("proprietário cancela (CANCELADA) e a reserva é liberada",
          s == 200 and g(b, "statusComercial") == "CANCELADA" and all(r["status"] == "LIBERADA" for r in g(b, "reservas", default=[])), (s, b))
    fim = L.saldo(adm, pa)
    check("cancelamento devolve o disponível (reservado volta ao valor anterior à confirmação)", fim["reservado"] == antes["reservado"], (antes, fim))
    s, b = call("POST", "/vendas/%s/cancelar" % g(v, "id"), adm, {"motivo": "de novo"})
    check("cancelar de novo é idempotente (200)", s == 200 and g(b, "statusComercial") == "CANCELADA", (s, b))
    s, b = L.confirmar(vt, g(v, "id"))
    check("venda cancelada não confirma (400)", s == 400, (s, b))
    L.config_aplicar(adm, L.LIMITE_TESTE, L.ARRED_TESTE, ["ADMIN", "GERENTE"], False)

    # gerente registra em nome de um vendedor
    s, vv = L.registrar_venda(gt, cliente, [L.item(pa)], vendedorId=v1["id"])
    check("gerente registra venda em nome do vendedor", s == 200 and g(vv, "vendedor", "id") == v1["id"], (s, vv))
    ctx["venda_escopo"] = g(vv, "id")


def escopo(adm, ger, v1, v2, cliente, pr):
    vid = ctx["venda_escopo"]
    t1, t2, tg = v1["token"], v2["token"], ger["token"]
    check("vendedor 1 vê a própria venda", call("GET", "/vendas/%s" % vid, t1)[0] == 200)
    check("vendedor 2 NÃO vê venda do vendedor 1 (404)", call("GET", "/vendas/%s" % vid, t2)[0] == 404)
    check("vendedor 2 não confirma venda alheia (404)", L.confirmar(t2, vid)[0] == 404)
    check("vendedor 2 não cancela venda alheia (404 ou 403)", call("POST", "/vendas/%s/cancelar" % vid, t2, {"motivo": "x"})[0] in (403, 404))
    s, lista = call("GET", "/vendas?tamanho=50", t2)
    check("lista do vendedor 2 não contém a venda do vendedor 1", s == 200 and all(x["id"] != vid for x in g(lista, "conteudo", default=[])), s)
    s, lista = call("GET", "/vendas?tamanho=50", t1)
    check("lista do vendedor 1 contém a venda", any(x["id"] == vid for x in g(lista, "conteudo", default=[])))
    check("gerente vê a venda de qualquer vendedor", call("GET", "/vendas/%s" % vid, tg)[0] == 200)
    check("proprietário vê a venda de qualquer vendedor", call("GET", "/vendas/%s" % vid, adm)[0] == 200)
    call("POST", "/vendas/%s/cancelar" % vid, tg, {"motivo": "limpeza"})


def catalogo(adm, v1, cliente, pr):
    pv = pr["V"]
    ids = [x["id"] for x in pv["variacoes"]]
    # vende a primeira variação para que ela esteja "vendida"
    s, vend = L.registrar_venda(v1["token"], cliente, [L.item(pv, variacao=pv["variacoes"][0])])
    check("venda da variação 1 registrada (para o teste de remoção)", s == 200, (s, vend))
    campos = ["cor", "tamanho", "sku", "adicionalPreco", "estoque", "imagemUrl"]

    def atual():
        return call("GET", "/produtos/%s" % pv["id"])[1]

    def put(p, variacoes):
        corpo = {k: p.get(k) for k in ["nome", "descricao", "preco", "precoOriginal", "estoque", "sku", "codigo", "largura",
                                       "altura", "profundidade", "peso", "volumes", "diferenciais", "version", "modalidade",
                                       "prazoEncomendaDias"]}
        corpo["categoriaId"] = p.get("categoriaId")
        corpo["variacoes"] = variacoes
        return call("PUT", "/produtos/%s" % pv["id"], adm, corpo)

    p = atual()
    com_id = [dict({k: x.get(k) for k in campos}, id=x["id"]) for x in p["variacoes"]]
    s, p2 = put(p, com_id)
    check("editar produto COM id preserva os ids das variações", s == 200 and [x["id"] for x in g(p2, "variacoes", default=[])] == ids, (s, p2))
    sem_id = [{k: x.get(k) for k in campos} for x in p2["variacoes"]]
    s, p3 = put(p2, sem_id)
    check("editar produto SEM id (cliente antigo) preserva os ids por SKU", s == 200 and [x["id"] for x in g(p3, "variacoes", default=[])] == ids, (s, p3))
    s, p4 = put(p3, sem_id[1:])
    check("remover variação já vendida é recusado com mensagem", s == 400 and "já foi vendida" in str(g(p4, "message")), (s, p4))
    s, b = call("DELETE", "/produtos/%s" % pv["id"], adm)
    check("excluir produto com vendas é recusado com mensagem", s == 400 and "vendas" in str(g(b, "message")), (s, b))
    nova = {"cor": "Cinza", "tamanho": "Casal", "sku": "%s-NOVA" % pv["sku"], "adicionalPreco": 0, "estoque": 3}
    s, p5 = put(p3, sem_id + [nova])
    check("adicionar variação nova mantém as antigas e cria a nova",
          s == 200 and [x["id"] for x in g(p5, "variacoes", default=[])][:2] == ids and len(g(p5, "variacoes", default=[])) == 3, (s, p5))
    s, p6 = put(atual(), [dict(v, estoque=v["estoque"]) for v in sem_id])
    check("edição com 'version' desatualizada não corrompe (200 ou 409)", s in (200, 409), (s, p6))
    call("POST", "/vendas/%s/cancelar" % g(vend, "id"), adm, {"motivo": "limpeza"})


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
