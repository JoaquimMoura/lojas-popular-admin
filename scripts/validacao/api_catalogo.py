#!/usr/bin/env python3
"""Validação por API do catálogo dinâmico: materiais cadastráveis (sem duplicidade, compartilhados, inativáveis), categoria
com vários materiais e características configuráveis, produto com campos dinâmicos validados no servidor, troca de categoria
com impacto confirmado, opções em uso inativadas e permissões (vendedor preenche, mas não configura).

Ambiente: SOMENTE o local descartável. Requer LP_ADMIN_EMAIL e LP_ADMIN_PASSWORD. Usa nomes com o sufixo da execução.
"""
import lib as L
from lib import call, check, g, secao

ctx = {}


def car(nome, tipo, **kw):
    corpo = {"nome": nome, "tipo": tipo, "obrigatoria": False, "exibirNaVitrine": False, "ativa": True}
    corpo.update(kw)
    return corpo


def principal():
    adm = L.preparar()
    ger = L.novo_usuario(adm, "GERENTE", "ger")
    ven = L.novo_usuario(adm, "VENDEDOR", "vend")
    suf = L.SUFIXO

    secao("1. Materiais")
    s, lista = call("GET", "/materiais", ger["token"])
    nomes = {m["nome"] for m in (lista if s == 200 else [])}
    check("materiais antigos (MDF, MDP, Madeira, Ferro, Vidro, Plástico) estão no cadastro", {"MDF", "MDP", "Madeira", "Ferro", "Vidro", "Plástico"} <= nomes, nomes)
    s, esp = call("POST", "/materiais", ger["token"], {"nome": "Espuma %s" % suf})
    check("gerente cadastra material", s == 201 and esp["ativo"], (s, esp))
    s, b = call("POST", "/materiais", ger["token"], {"nome": "  ESPUMA   %s " % suf.upper()})
    check("duplicidade por maiúscula/espaço é recusada (400)", s == 400, (s, b))
    s, b = call("POST", "/materiais", ven["token"], {"nome": "Outro %s" % suf})
    check("vendedor não cadastra material (403)", s == 403, (s, b))
    s, lat = call("POST", "/materiais", ger["token"], {"nome": "Látex %s" % suf})
    s, b = call("POST", "/materiais", ger["token"], {"nome": "latex %s" % suf})
    check("duplicidade por acento é recusada (400)", s == 400, (s, b))
    s, b = call("GET", "/materiais?q=latex%20" + suf, ven["token"])
    check("busca sem acento encontra o material", s == 200 and any(m["id"] == lat["id"] for m in b), (s, b))

    secao("2. Categoria: simples, com materiais e características")
    s, simples = call("POST", "/categorias", ger["token"], {"nome": "Colchões %s" % suf})
    check("categoria simples salva sem material nem característica", s == 201 and simples["materiais"] == [] and simples["caracteristicas"] == [], (s, simples))
    cars = [car("Densidade", "SELECAO_UNICA", obrigatoria=True, exibirNaVitrine=True,
                opcoes=[{"valor": "D28"}, {"valor": "D33"}, {"valor": "D45"}]),
            car("Altura", "NUMERO", unidade="cm", exibirNaVitrine=True), car("Obs", "TEXTO")]
    s, cat = call("PUT", "/categorias/%s" % simples["id"], ger["token"],
                  {"nome": "Colchões %s" % suf, "materialIds": [esp["id"], lat["id"]], "caracteristicas": cars})
    check("edita a categoria: 2 materiais e 3 características", s == 200 and len(cat["materiais"]) == 2 and len(cat["caracteristicas"]) == 3, (s, cat))
    s, b = call("PUT", "/categorias/%s" % simples["id"], ven["token"], {"nome": "Colchões %s" % suf, "materialIds": [esp["id"]]})
    check("vendedor não configura materiais da categoria (403)", s == 403, (s, b))
    s, b = call("POST", "/categorias", ger["token"], {"nome": "colchões  %s" % suf})
    check("nome de categoria repetido (sem acento/maiúscula) é recusado (400)", s == 400, (s, b))
    dens = next(x for x in cat["caracteristicas"] if x["nome"] == "Densidade")
    alt = next(x for x in cat["caracteristicas"] if x["nome"] == "Altura")
    obs = next(x for x in cat["caracteristicas"] if x["nome"] == "Obs")
    d33 = next(o for o in dens["opcoes"] if o["valor"] == "D33")

    secao("3. Produto com campos dinâmicos")
    base = {"preco": 1000, "estoque": 5, "categoriaId": cat["id"], "diferenciais": [], "variacoes": []}
    s, b = call("POST", "/produtos", ven["token"], dict(base, nome="Colchão sem densidade %s" % suf, sku="C-%s-1" % suf, caracteristicas=[]))
    check("campo obrigatório ausente é recusado (400)", s == 400 and "Densidade" in str(g(b, "message")), (s, b))
    val = [{"caracteristicaId": dens["id"], "opcaoIds": [d33["id"]]}, {"caracteristicaId": alt["id"], "valorNumero": 25.5}]
    s, p = call("POST", "/produtos", ven["token"], dict(base, nome="Colchão B %s" % suf, sku="C-%s-2" % suf, caracteristicas=val, materialIds=[esp["id"]]))
    check("vendedor preenche o produto (material e características)", s == 200 and [m["id"] for m in p["materiais"]] == [esp["id"]]
          and {v["nome"]: v["exibicao"] for v in p["caracteristicas"]} == {"Densidade": "D33", "Altura": "25.5 cm"}, (s, p))
    s, b = call("POST", "/produtos", ven["token"], dict(base, nome="Colchão C %s" % suf, sku="C-%s-3" % suf, caracteristicas=val, materialIds=[1]))
    check("material fora da categoria é recusado (400)", s == 400, (s, b))
    s, b = call("POST", "/produtos", ven["token"], dict(base, nome="Colchão D %s" % suf, sku="C-%s-4" % suf,
                caracteristicas=[{"caracteristicaId": dens["id"], "opcaoIds": [d33["id"]]}, {"caracteristicaId": obs["id"], "valorNumero": 3}]))
    check("valor de tipo errado é recusado (400)", s == 400, (s, b))
    s, b = call("POST", "/produtos", ven["token"], dict(base, nome="Colchão E %s" % suf, sku="C-%s-5" % suf,
                caracteristicas=[{"caracteristicaId": dens["id"], "opcaoIds": [d33["id"]]}, {"caracteristicaId": 999999, "valorTexto": "x"}]))
    check("característica de outra categoria/inexistente é recusada (400)", s == 400, (s, b))

    secao("4. Troca de categoria, opção em uso e material inativo")
    s, outra = call("POST", "/categorias", ger["token"], {"nome": "Sofás %s" % suf})
    s, imp = call("GET", "/produtos/%s/impacto-categoria?categoriaId=%s" % (p["id"], outra["id"]), ven["token"])
    check("o impacto da troca é mostrado antes de salvar", s == 200 and imp["materiais"] == ["Espuma %s" % suf] and len(imp["caracteristicas"]) == 2, (s, imp))
    corpo = dict(base, nome=p["nome"], sku=p["sku"], categoriaId=outra["id"], version=p["version"])
    s, b = call("PUT", "/produtos/%s" % p["id"], ven["token"], dict(corpo, caracteristicas=[], materialIds=[]))
    check("trocar a categoria sem confirmar não descarta em silêncio (400)", s == 400 and "Confirme" in str(g(b, "message")), (s, b))
    s, b = call("PUT", "/produtos/%s" % p["id"], ven["token"], dict(corpo, caracteristicas=[], materialIds=[], confirmarDescarte=True))
    check("com a confirmação, descarta só o que não se aplica", s == 200 and b["materiais"] == [] and b["caracteristicas"] == [], (s, b))

    s, p2 = call("POST", "/produtos", ven["token"], dict(base, nome="Colchão F %s" % suf, sku="C-%s-6" % suf, caracteristicas=val))
    novas = [o for o in dens["opcoes"] if o["valor"] != "D33"]
    s, c2 = call("PUT", "/categorias/%s" % cat["id"], ger["token"], {"nome": "Colchões %s" % suf, "caracteristicas": [
        car("Densidade", "SELECAO_UNICA", id=dens["id"], obrigatoria=True, opcoes=novas), car("Altura", "NUMERO", id=alt["id"], unidade="cm"), car("Obs", "TEXTO", id=obs["id"])]})
    d33_atual = next(o for o in next(x for x in c2["caracteristicas"] if x["nome"] == "Densidade")["opcoes"] if o["id"] == d33["id"])
    check("opção em uso removida do formulário só é inativada", s == 200 and d33_atual["ativa"] is False, (s, d33_atual))
    s, p2b = call("GET", "/produtos/%s" % p2["id"], ger["token"])
    check("o produto mantém o valor D33", any(v["exibicao"] == "D33" for v in p2b["caracteristicas"]), p2b["caracteristicas"])
    s, b = call("POST", "/produtos", ven["token"], dict(base, nome="Colchão G %s" % suf, sku="C-%s-7" % suf, caracteristicas=val))
    check("nova seleção da opção inativa é recusada (400)", s == 400, (s, b))
    s, b = call("PUT", "/materiais/%s" % esp["id"], ger["token"], {"ativo": False})
    s, cat2 = call("GET", "/categorias/%s" % cat["id"], ger["token"])
    check("material inativado preserva os vínculos da categoria", s == 200 and any(m["id"] == esp["id"] for m in cat2["materiais"]), cat2["materiais"])
    s, pend = call("GET", "/categorias/%s/pendencias" % cat["id"], ger["token"])
    check("cadastros a complementar são consultáveis", s == 200 and isinstance(pend, list), (s, pend))
    s, v = call("GET", "/vendas/configuracao", ven["token"])
    check("vendas e vitrine seguem funcionando (configuração de venda)", s == 200, s)


def finalizar():
    L.limpar()


if __name__ == "__main__":
    L.exigir_config()
    L.executar(principal, finalizar)
