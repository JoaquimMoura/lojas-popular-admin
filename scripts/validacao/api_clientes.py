#!/usr/bin/env python3
"""Validação por API do vínculo cliente x venda e do histórico de compras: cadastro durante a venda, duas compras no
histórico, atualização do cadastro sem mexer no histórico, cancelamento fora dos indicadores, escopo do vendedor (acesso
direto à API), exclusão física bloqueada, duplicidade e troca/vínculo de cliente (D13 pendente = bloqueado).

Ambiente: SOMENTE o local descartável. Requer LP_ADMIN_EMAIL e LP_ADMIN_PASSWORD. As decisões D13 são restauradas ao final.
"""
import lib as L
from lib import call, check, g, secao

ctx = {}


def principal():
    adm = L.preparar()
    s, c0 = call("GET", "/config/comercial/clientes", adm)
    ctx["d13"] = {"vendedorVeHistoricoCompleto": c0.get("vendedorVeHistoricoCompleto"), "perfisTrocaCliente": c0.get("perfisTrocaCliente") or []}
    ctx["snap"] = L.config_salvar(adm)
    ctx["criadas"] = L.condicoes_teste(adm, ctx["snap"])
    L.config_aplicar(adm, L.LIMITE_TESTE, L.ARRED_TESTE, ["ADMIN", "GERENTE"], False)
    call("PUT", "/config/comercial/clientes", adm, {"vendedorVeHistoricoCompleto": None, "perfisTrocaCliente": []})
    ger = L.novo_usuario(adm, "GERENTE", "ger")
    va = L.novo_usuario(adm, "VENDEDOR", "vendA")
    vb = L.novo_usuario(adm, "VENDEDOR", "vendB")
    prod = L.criar_produto(adm, "Cômoda Histórico", 1000.00, estoque=100)

    def venda(quem, cliente, qtd=1):
        s, v = L.registrar_venda(quem["token"], cliente, [L.item(prod, qtd)], forma="PIX", parcelas=1, tipo="RETIRADA")
        assert s == 200, (s, v)
        s, v = L.confirmar(quem["token"], v["id"])
        assert s == 200, (s, v)
        return v

    secao("1. Cadastro durante a venda e vínculo")
    cli = L.criar_cliente(va["token"], nome="Cliente Histórico %s" % L.SUFIXO)
    v1 = venda(va, cli)
    v2 = venda(va, cli, 2)
    v3 = venda(vb, cli)
    check("a venda guarda o cliente comprador (separado de quem registrou e do vendedor)",
          g(v1, "cliente", "id") == cli["id"] and g(v1, "vendedor", "id") == va["id"], v1.get("cliente"))
    s, b = call("POST", "/vendas", va["token"], L.venda_body({"id": None}, [L.item(prod, 1)]))
    check("venda sem cliente é recusada no servidor (400)", s == 400, (s, b))

    secao("2. Histórico, indicadores e cancelamento")
    s, h = call("GET", "/clientes/%s/compras" % cli["id"], ger["token"])
    check("gerente vê as três compras, da mais recente para a mais antiga",
          s == 200 and [x["pedidoId"] for x in h["itens"]] == [v3["id"], v2["id"], v1["id"]], h)
    call("POST", "/vendas/%s/cancelar" % v3["id"], ger["token"], {"motivo": "Desistência"})
    s, r = call("GET", "/clientes/%s/resumo" % cli["id"], ger["token"])
    check("cancelada fica no histórico mas fora dos indicadores (2 compras, R$ 3.000 menos o ajuste do Pix)",
          s == 200 and r["compras"] == 2 and r["canceladas"] == 1 and "recebido" in r["observacao"], r)
    s, h2 = call("GET", "/clientes/%s/compras?situacao=CANCELADA" % cli["id"], ger["token"])
    check("filtro por situação", s == 200 and h2["total"] == 1, h2)
    s, h3 = call("GET", "/clientes/%s/compras?pagina=1&tamanho=2" % cli["id"], ger["token"])
    check("paginação", s == 200 and len(h3["itens"]) == 1 and h3["totalPaginas"] == 2, h3)

    secao("3. Cadastro alterado não muda o histórico")
    s, b = call("PUT", "/clientes/%s" % cli["id"], ger["token"], {"nome": "Nome Alterado %s" % L.SUFIXO, "telefone": "11999990000",
                                                                   "cpf": cli.get("cpf"), "confirmarDuplicidade": True})
    s, d = call("GET", "/vendas/%s" % v1["id"], ger["token"])
    check("a venda continua com o nome de quando foi feita", g(d, "cliente", "nome") == cli["nome"], g(d, "cliente"))

    secao("4. Escopo do vendedor (acesso direto à API)")
    s, hb = call("GET", "/clientes/%s/compras" % cli["id"], vb["token"])
    check("vendedor B vê só a própria compra", s == 200 and [x["pedidoId"] for x in hb["itens"]] == [v3["id"]]
          and hb["escopo"] == "SOMENTE_SUAS_VENDAS", hb)
    s, rb = call("GET", "/clientes/%s/resumo" % cli["id"], vb["token"])
    check("os indicadores também respeitam o escopo", s == 200 and rb["compras"] == 0 and rb["canceladas"] == 1, rb)
    s, b = call("GET", "/vendas/%s" % v1["id"], vb["token"])
    check("detalhe de venda alheia segue negado (404)", s == 404, (s, b))
    s, b = call("PUT", "/config/comercial/clientes", vb["token"], {"vendedorVeHistoricoCompleto": True})
    check("vendedor não altera a regra (403)", s == 403, (s, b))
    call("PUT", "/config/comercial/clientes", adm, {"vendedorVeHistoricoCompleto": True, "perfisTrocaCliente": []})
    s, hb2 = call("GET", "/clientes/%s/compras" % cli["id"], vb["token"])
    check("liberado pelo proprietário (D13), o vendedor vê todas as compras do cliente", s == 200 and hb2["total"] == 3, hb2)

    secao("5. Integridade")
    s, b = call("DELETE", "/clientes/%s" % cli["id"], adm)
    check("cliente com vendas não é excluído (400)", s == 400, (s, b))
    s, b = call("DELETE", "/clientes/%s" % cli["id"], ger["token"])
    check("gerente não exclui cliente (403)", s == 403, (s, b))
    livre = L.criar_cliente(ger["token"], nome="Sem Venda %s" % L.SUFIXO)
    s, b = call("DELETE", "/clientes/%s" % livre["id"], adm)
    check("cliente sem vendas pode ser excluído pelo proprietário", s == 200, (s, b))
    s, dup = call("POST", "/clientes", ger["token"], {"nome": "Outro Nome", "cpf": cli.get("cpf"), "telefone": "11988887777"})
    check("CPF repetido é recusado", s in (400, 409), (s, dup))

    secao("6. Troca e vínculo de cliente")
    outro = L.criar_cliente(ger["token"], nome="Cliente Correto %s" % L.SUFIXO)
    s, b = call("POST", "/vendas/%s/trocar-cliente" % v1["id"], adm, {"clienteId": outro["id"], "justificativa": "Cliente errado"})
    check("D13 pendente: troca de cliente bloqueada até para o proprietário (422)", s == 422 and "D13" in str(g(b, "message")), (s, b))
    call("PUT", "/config/comercial/clientes", adm, {"vendedorVeHistoricoCompleto": True, "perfisTrocaCliente": ["GERENTE"]})
    s, b = call("POST", "/vendas/%s/trocar-cliente" % v1["id"], va["token"], {"clienteId": outro["id"], "justificativa": "x"})
    check("vendedor não troca cliente (403)", s == 403, (s, b))
    s, b = call("POST", "/vendas/%s/trocar-cliente" % v1["id"], ger["token"], {"clienteId": outro["id"], "justificativa": ""})
    check("troca exige justificativa (400)", s == 400, (s, b))
    total = g(call("GET", "/vendas/%s" % v1["id"], adm)[1], "total")
    s, b = call("POST", "/vendas/%s/trocar-cliente" % v1["id"], ger["token"], {"clienteId": outro["id"], "justificativa": "Cliente errado na venda"})
    check("gerente autorizado troca o cliente, sem mudar valores", s == 200 and g(b, "cliente", "id") == outro["id"] and g(b, "total") == total, (s, b))
    s, b = call("POST", "/vendas/%s/vincular-cliente" % v1["id"], adm, {"clienteId": cli["id"], "justificativa": "x"})
    check("vincular só serve para venda sem cliente (400)", s == 400, (s, b))


def finalizar():
    adm = L.login_admin()
    if adm:
        call("PUT", "/config/comercial/clientes", adm, ctx.get("d13", {"vendedorVeHistoricoCompleto": None, "perfisTrocaCliente": []}))
        L.config_restaurar(adm, ctx["snap"], ctx.get("criadas", []))
    L.limpar()


if __name__ == "__main__":
    L.exigir_config()
    L.executar(principal, finalizar)
