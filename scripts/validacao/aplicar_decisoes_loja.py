#!/usr/bin/env python3
"""Aplica, num ambiente LOCAL descartável, as decisões já respondidas pela loja (docs/gestao-vendas-decisoes-da-loja.md).

Fica de fora (continua pendente, de propósito): taxas/prazos das operadoras de cartão (D11). Requer LP_ADMIN_EMAIL e
LP_ADMIN_PASSWORD (o proprietário do ambiente). Cria um gerente e um vendedor de TESTE (senha de teste gerada, mostrada no final).
Nunca aponte para produção.
"""
import lib as L
from lib import call, check, secao

DECISOES_FIN = {"comissaoPercentual": 3, "comissaoAquisicao": "CONFIRMACAO", "competenciaReceita": "CONFIRMACAO",
                "perfisReabertura": ["ADMIN", "GERENTE"], "perfisRestituicao": ["ADMIN", "GERENTE"],
                "permiteRestituicao": True, "permiteCobrancaDiferenca": True, "metaDescontaDevolucoes": True,
                "fechamentoExigeSemPendencias": True}
PERM = {k: ["ADMIN", "GERENTE"] for k in ("consultar", "receber", "pagar", "estornar", "restituir")}
CONDICOES = [("PIX", 1), ("DINHEIRO", 1)] + [("CARTAO", n) for n in range(1, 7)]


def principal():
    adm = L.preparar()
    secao("Configuração comercial")
    s, b = call("PUT", "/config/comercial", adm, {"limiteDescontoPercentual": 0, "arredondamento": "HALF_UP",
                                                  "perfisCancelamento": ["ADMIN", "GERENTE"], "exigePagamentoExpedir": False})
    check("limite 0%, arredondamento meio para cima, cancelam gerente e proprietário, saída não exige pagamento", s == 200, (s, b))
    s, cfg = call("GET", "/config/comercial", adm)
    existentes = {(c["forma"], c["parcelas"]): c for c in cfg.get("condicoes", [])}
    for forma, parc in CONDICOES:
        c = existentes.get((forma, parc))
        if c:
            s, b = call("PUT", "/config/comercial/condicoes/%s" % c["id"], adm, {"ajustePercentual": "0.00", "ativa": True})
        else:
            s, b = call("POST", "/config/comercial/condicoes", adm, {"forma": forma, "parcelas": parc, "ajustePercentual": "0.00", "ativa": True})
        check("condição %s %dx = 0%%" % (forma, parc), s == 200, (s, b))
    secao("Regras financeiras e permissões do gerente")
    s, b = call("PUT", "/config/comercial/financeiro", adm, DECISOES_FIN)
    check("regras financeiras (comissão 3%, devida ao confirmar, competência na confirmação, D07/D09/D10)", s == 200, (s, b))
    s, b = call("PUT", "/config/comercial/permissoes-financeiras", adm, PERM)
    check("gerente pode consultar, receber, pagar, estornar e restituir", s == 200 and b.get("todasDecididas") is True, (s, b))
    secao("Usuários de teste")
    ger = L.novo_usuario(adm, "GERENTE", "gerente")
    ven = L.novo_usuario(adm, "VENDEDOR", "vendedor")
    s, cfg = call("GET", "/config/comercial", adm)
    pend = sorted({p["codigo"] for p in cfg.get("pendencias", [])})
    print("\nPendências restantes (esperado: D11 taxas do cartão):", ", ".join(pend) or "nenhuma")
    print("\nUsuários de TESTE (senha de teste):")
    print("  gerente : %s" % ger["email"])
    print("  vendedor: %s" % ven["email"])
    print("  senha   : %s" % L.TEST_PASSWORD)
    L._usuarios_criados.clear()   # não desativar ao sair: são para teste manual


if __name__ == "__main__":
    L.exigir_config()
    L.executar(principal)
