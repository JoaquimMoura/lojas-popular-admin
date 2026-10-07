"""Módulo comum dos roteiros de validação da gestão de vendas (somente biblioteca padrão do Python).

Uso exclusivo em AMBIENTE LOCAL DESCARTÁVEL (ver README.md). Nenhum segredo é gravado aqui: a configuração
vem de variáveis de ambiente LP_* (ou do arquivo scripts/validacao/.ambiente.env, não versionado, gerado por
ambiente-pg.sh) e as senhas dos usuários de teste são geradas a cada execução.
"""
import json
import os
import random
import secrets
import struct
import sys
import time
import traceback
import urllib.error
import urllib.request
import uuid
import zlib
from datetime import date, timedelta

AQUI = os.path.dirname(os.path.abspath(__file__))


# --------------------------------------------------------------------------- configuração

def _carregar_ambiente_local():
    """Preenche LP_* ausentes a partir de .ambiente.env (nunca sobrescreve o que já está no ambiente)."""
    arq = os.path.join(AQUI, ".ambiente.env")
    if not os.path.isfile(arq):
        return
    with open(arq, encoding="utf-8") as f:
        for linha in f:
            linha = linha.strip()
            if not linha or linha.startswith("#") or "=" not in linha:
                continue
            k, v = linha.split("=", 1)
            if k.startswith("LP_") and not os.environ.get(k):
                os.environ[k] = v


_carregar_ambiente_local()

BASE_URL = os.environ.get("LP_BASE_URL", "http://localhost:4180").rstrip("/")
API = BASE_URL + "/api/v1"
ADMIN_EMAIL = os.environ.get("LP_ADMIN_EMAIL", "").strip().lower()
ADMIN_PASSWORD = os.environ.get("LP_ADMIN_PASSWORD", "")
# Senha de TESTE dos usuários criados por cada execução: aleatória, nunca impressa.
TEST_PASSWORD = os.environ.get("LP_TEST_PASSWORD") or (secrets.token_urlsafe(12) + "aA1!")
# Sufixo único por execução (e-mails, SKUs, nomes).
SUFIXO = "%x%s" % (int(time.time()) % 0xFFFFFFF, secrets.token_hex(2))


def exigir_config():
    """Falha com exit code 2 e mensagem clara se faltar configuração obrigatória."""
    faltando = [n for n, v in (("LP_ADMIN_EMAIL", ADMIN_EMAIL), ("LP_ADMIN_PASSWORD", ADMIN_PASSWORD)) if not v]
    if faltando:
        sys.stderr.write(
            "Configuração ausente: defina %s no ambiente (e-mail e senha do proprietário criado por "
            "ambiente-pg.sh up).\n" % ", ".join(faltando))
        sys.exit(2)
    if "popularmoveis" in BASE_URL.lower() or not (
            "localhost" in BASE_URL or "127.0.0.1" in BASE_URL or "[::1]" in BASE_URL):
        sys.stderr.write("LP_BASE_URL=%s não é um endereço local. Estes roteiros criam e alteram dados: "
                         "use SOMENTE o ambiente local descartável.\n" % BASE_URL)
        sys.exit(2)


# --------------------------------------------------------------------------- HTTP

def _decodificar(txt):
    if not txt:
        return None
    try:
        return json.loads(txt)
    except ValueError:
        return txt


def _multipart(campos, arquivos):
    fronteira = "----lpval" + secrets.token_hex(12)
    partes = []
    for nome, valor in (campos or {}).items():
        partes.append(("--%s\r\nContent-Disposition: form-data; name=\"%s\"\r\n\r\n%s\r\n"
                       % (fronteira, nome, valor)).encode("utf-8"))
    for nome, (arquivo, conteudo, tipo) in (arquivos or {}).items():
        cab = ("--%s\r\nContent-Disposition: form-data; name=\"%s\"; filename=\"%s\"\r\n"
               "Content-Type: %s\r\n\r\n" % (fronteira, nome, arquivo, tipo)).encode("utf-8")
        partes.append(cab + conteudo + b"\r\n")
    partes.append(("--%s--\r\n" % fronteira).encode("utf-8"))
    return b"".join(partes), "multipart/form-data; boundary=" + fronteira


def _enviar(method, url, token, dados, content_type, headers):
    h = {}
    if content_type:
        h["Content-Type"] = content_type
    if token:
        h["Authorization"] = "Bearer " + token
    if headers:
        h.update(headers)
    req = urllib.request.Request(url, data=dados, method=method, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return r.status, r.read(), {k.lower(): v for k, v in r.headers.items()}
    except urllib.error.HTTPError as e:
        return e.code, e.read(), {k.lower(): v for k, v in e.headers.items()}


def call(method, path, token=None, body=None, headers=None, campos=None, arquivos=None):
    """Chama /api/v1<path>. JSON em `body`; multipart (campos de texto + arquivos) quando `campos`/`arquivos`.

    `arquivos` = {nome_do_campo: (nome_do_arquivo, bytes, content_type)}. Devolve (status, corpo_decodificado).
    """
    if arquivos or campos is not None:
        dados, ctype = _multipart(campos, arquivos)
    elif body is not None:
        dados, ctype = json.dumps(body).encode("utf-8"), "application/json"
    else:
        dados, ctype = None, ("application/json" if method in ("POST", "PUT") else None)
    status, corpo, _ = _enviar(method, API + path, token, dados, ctype, headers)
    return status, _decodificar(corpo.decode("utf-8", "replace"))


def bruto(method, caminho_absoluto, token=None, headers=None):
    """Requisição sem prefixo /api/v1 (ex.: /uploads/...) devolvendo (status, bytes, cabeçalhos em minúsculas)."""
    return _enviar(method, BASE_URL + caminho_absoluto, token, None, None, headers)


def chave():
    return str(uuid.uuid4())


def g(obj, *caminho, default=None):
    """Acesso seguro a dicionários/listas aninhados (não levanta exceção se a resposta for um erro)."""
    for c in caminho:
        try:
            obj = obj[c]
        except (KeyError, IndexError, TypeError):
            return default
    return obj


# --------------------------------------------------------------------------- verificações

_resultados = []


def secao(titulo):
    print("\n--- %s" % titulo)


def check(nome, cond, extra=""):
    ok = bool(cond)
    _resultados.append((nome, ok))
    print(("OK    " if ok else "FALHA ") + nome + ("" if ok else ("   -> " + str(extra)[:600])))
    return ok


def info(msg):
    print("      " + msg)


def resumo():
    falhas = [n for n, ok in _resultados if not ok]
    total = len(_resultados)
    print("\n%d/%d verificações OK" % (total - len(falhas), total))
    for n in falhas:
        print("  FALHOU: " + n)
    return 1 if falhas else 0


def concluir():
    sys.exit(resumo())


def executar(principal, finalizar=None):
    """Roda `principal`; erro inesperado vira FALHA; `finalizar` sempre roda (restauração/limpeza)."""
    try:
        principal()
    except SystemExit:
        raise
    except Exception as e:  # noqa: BLE001 - qualquer erro do roteiro deve aparecer como falha
        traceback.print_exc()
        check("execução sem erro inesperado do roteiro", False, repr(e))
    finally:
        if finalizar:
            try:
                finalizar()
            except Exception as e:  # noqa: BLE001
                traceback.print_exc()
                check("restauração/limpeza final", False, repr(e))
    concluir()


# --------------------------------------------------------------------------- autenticação e usuários

_usuarios_criados = []
_adm = {"token": None}


def login(email, senha):
    s, b = call("POST", "/auth/login", body={"email": email.strip().lower(), "senha": senha})
    return g(b, "accessToken") if s == 200 else None


def login_admin():
    t = login(ADMIN_EMAIL, ADMIN_PASSWORD)
    _adm["token"] = t
    return t


def preparar():
    """Loga como proprietário e desativa usuários 'lp-val-' de execuções interrompidas (limite de 5 ativos)."""
    adm = login_admin()
    if not adm:
        sys.stderr.write("Login do proprietário falhou: confira LP_ADMIN_EMAIL/LP_ADMIN_PASSWORD e se o "
                         "ambiente está de pé (LP_BASE_URL=%s).\n" % BASE_URL)
        sys.exit(2)
    s, us = call("GET", "/usuarios", adm)
    n = 0
    for u in us if s == 200 and isinstance(us, list) else []:
        if (u.get("ativo") and u["email"].startswith("lp-val-") and u["email"] != ADMIN_EMAIL
                and not u["email"].startswith("lp-val-cliente-") and "ADMIN" not in (u.get("perfis") or [])):
            if call("POST", "/usuarios/%s/desativar" % u["id"], adm)[0] == 200:
                n += 1
    if n:
        info("usuários de execuções anteriores desativados: %d" % n)
    return adm


def novo_usuario(adm, papel, rotulo=None):
    """Cria um usuário ativo (GERENTE/VENDEDOR) com senha de teste e devolve {id, email, token}."""
    rotulo = rotulo or papel.lower()
    email = "lp-val-%s-%s@example.invalid" % (rotulo, SUFIXO)
    s, u = call("POST", "/usuarios", adm, {"nome": "Val %s %s" % (rotulo, SUFIXO), "email": email,
                                           "senha": TEST_PASSWORD, "perfil": papel})
    if s != 200:
        raise RuntimeError("não foi possível criar o usuário %s: %s %s" % (papel, s, u))
    _usuarios_criados.append(u["id"])
    return {"id": u["id"], "email": email, "token": login(email, TEST_PASSWORD)}


def limpar():
    """Desativa os usuários criados por esta execução (libera vagas do limite de usuários ativos)."""
    adm = login_admin()
    for uid in _usuarios_criados:
        call("POST", "/usuarios/%s/desativar" % uid, adm)
    n = len(_usuarios_criados)
    _usuarios_criados.clear()
    info("usuários de teste desativados: %d" % n)


# --------------------------------------------------------------------------- dados de teste

def png_minimo(largura=1, altura=1, rgb=(200, 80, 40)):
    """Bytes de um PNG válido (cor sólida)."""
    def bloco(tipo, dados):
        c = struct.pack(">I", len(dados)) + tipo + dados
        return c + struct.pack(">I", zlib.crc32(tipo + dados) & 0xFFFFFFFF)
    linha = b"\x00" + bytes(rgb) * largura
    return (b"\x89PNG\r\n\x1a\n" + bloco(b"IHDR", struct.pack(">IIBBBBB", largura, altura, 8, 2, 0, 0, 0))
            + bloco(b"IDAT", zlib.compress(linha * altura)) + bloco(b"IEND", b""))


def cpf_aleatorio():
    """CPF válido (apenas dígitos) e único por chamada."""
    while True:
        d = [random.randint(0, 9) for _ in range(9)]
        if len(set(d)) == 1:
            continue
        for tam in (9, 10):
            soma = sum(d[i] * (tam + 1 - i) for i in range(tam))
            r = (soma * 10) % 11
            d.append(0 if r == 10 else r)
        return "".join(map(str, d))


def cpf_formatado(c):
    return "%s.%s.%s-%s" % (c[:3], c[3:6], c[6:9], c[9:])


def telefone_aleatorio():
    d = "119" + "".join(str(random.randint(0, 9)) for _ in range(8))
    return "(%s) %s-%s" % (d[:2], d[2:7], d[7:])


def cliente_body(nome=None, **extra):
    corpo = {"nome": nome or "Cliente Val %s" % SUFIXO, "cpf": cpf_formatado(cpf_aleatorio()),
             "telefone": telefone_aleatorio(),
             "enderecos": [{"apelido": "Casa", "logradouro": "Rua de Teste", "numero": "10", "bairro": "Centro",
                            "cidade": "São Paulo", "uf": "SP", "cep": "01000-000", "principal": True}]}
    corpo.update(extra)
    return corpo


def criar_cliente(tok, **extra):
    s, c = call("POST", "/clientes", tok, cliente_body(**extra))
    if s != 200:
        raise RuntimeError("não foi possível criar o cliente: %s %s" % (s, c))
    return c


_cont = {"n": 0, "categoria": None}


def categoria(adm):
    """Categoria de teste (uma por execução)."""
    if _cont["categoria"] is None:
        s, c = call("POST", "/categorias", adm, {"nome": "Cat Val %s" % SUFIXO, "descricao": "Categoria de teste",
                                                 "material": "MDF"})
        if s not in (200, 201):
            raise RuntimeError("não foi possível criar a categoria: %s %s" % (s, c))
        _cont["categoria"] = c["id"]
    return _cont["categoria"]


def criar_produto(adm, nome, preco, estoque=0, variacoes=None, modalidade="PRONTA_ENTREGA", prazo=None):
    """Cria um produto de teste com SKU único por execução. `variacoes` = lista de dicts
    {cor, tamanho, adicionalPreco, estoque}; devolve a resposta da API (com ids das variações)."""
    _cont["n"] += 1
    sku = "VAL-%s-%d" % (SUFIXO, _cont["n"])
    vs = []
    for i, v in enumerate(variacoes or []):
        vs.append({"cor": v.get("cor"), "tamanho": v.get("tamanho"), "sku": "%s-V%d" % (sku, i + 1),
                   "adicionalPreco": v.get("adicionalPreco", 0), "estoque": v.get("estoque")})
    total = sum(v["estoque"] or 0 for v in vs) if vs else estoque
    corpo = {"nome": "%s %s" % (nome, SUFIXO), "descricao": "Produto de teste da validação", "preco": preco,
             "estoque": total, "sku": sku, "categoriaId": categoria(adm), "modalidade": modalidade,
             "prazoEncomendaDias": prazo, "variacoes": vs}
    s, p = call("POST", "/produtos", adm, corpo)
    if s != 200:
        raise RuntimeError("não foi possível criar o produto %s: %s %s" % (nome, s, p))
    return p


def item(produto, quantidade=1, variacao=None, modalidade="PRONTA_ENTREGA"):
    """Item de venda; `variacao` é o dict da variação (ou None)."""
    return {"produtoId": produto["id"], "variacaoId": variacao["id"] if variacao else None,
            "quantidade": quantidade, "modalidade": modalidade}


def venda_body(cliente, itens, forma="PIX", parcelas=1, tipo="RETIRADA", **extra):
    corpo = {"clienteId": cliente["id"], "canal": "LOJA", "tipoEntrega": tipo, "formaPagamento": forma,
             "parcelas": parcelas, "itens": itens}
    if tipo == "ENTREGA":
        corpo["enderecoId"] = g(cliente, "enderecos", 0, "id")
    corpo.update(extra)
    return corpo


def registrar_venda(tok, cliente, itens, **kw):
    return call("POST", "/vendas", tok, venda_body(cliente, itens, **kw))


def confirmar(tok, venda_id, k=None):
    return call("POST", "/vendas/%s/confirmar" % venda_id, tok, headers={"Idempotency-Key": k or chave()})


def saldo(tok, produto, variacao=None):
    """Saldo de estoque {fisico, reservado, disponivel} de um produto/variação (ou None)."""
    s, lista = call("GET", "/estoque?produtoId=%s" % produto["id"], tok)
    for x in lista if s == 200 and isinstance(lista, list) else []:
        if (variacao is None and x.get("variacaoId") is None) or (variacao and x.get("variacaoId") == variacao["id"]):
            return x
    return None


def amanha(dias=1):
    return (date.today() + timedelta(days=dias)).isoformat()


# --------------------------------------------------------------------------- configuração comercial

# Condições de pagamento de TESTE (ajuste percentual sobre o preço base).
CONDICOES_TESTE = {("PIX", 1): "-5.00", ("CARTAO", 3): "10.00"}
LIMITE_TESTE = 10   # % de desconto do vendedor (valor de TESTE)
ARRED_TESTE = "HALF_UP"


def config_salvar(adm):
    """Fotografia da configuração atual (para restaurar ao final)."""
    s, c = call("GET", "/config/comercial", adm)
    if s != 200:
        raise RuntimeError("não foi possível ler a configuração comercial: %s %s" % (s, c))
    return c


def config_aplicar(adm, limite, arred, perfis, exige):
    """PUT da configuração comercial. Os quatro valores são explícitos (None = pendente)."""
    s, c = call("PUT", "/config/comercial", adm, {"limiteDescontoPercentual": limite, "arredondamento": arred,
                                                  "perfisCancelamento": perfis, "exigePagamentoExpedir": exige})
    if s != 200:
        raise RuntimeError("não foi possível gravar a configuração comercial: %s %s" % (s, c))
    return c


def condicoes_teste(adm, snap):
    """Deixa ativas SOMENTE as condições de teste (cria as que faltam; desativa as demais).
    Devolve a lista de ids criados por esta execução (para desativar ao final)."""
    existentes = {(c["forma"], c["parcelas"]): c for c in snap["condicoes"]}
    criadas = []
    for (forma, parc), ajuste in CONDICOES_TESTE.items():
        c = existentes.get((forma, parc))
        if c:
            call("PUT", "/config/comercial/condicoes/%s" % c["id"], adm, {"ajustePercentual": ajuste, "ativa": True})
        else:
            s, novo = call("POST", "/config/comercial/condicoes", adm,
                           {"forma": forma, "parcelas": parc, "ajustePercentual": ajuste, "ativa": True})
            if s == 200:
                criadas.append(novo["id"])
    for (forma, parc), c in existentes.items():
        if (forma, parc) not in CONDICOES_TESTE:
            call("PUT", "/config/comercial/condicoes/%s" % c["id"], adm,
                 {"ajustePercentual": c["ajustePercentual"], "ativa": False})
    return criadas


def condicoes_ativar(adm, ativa):
    """Ativa/desativa TODAS as condições existentes (ids lidos na hora)."""
    s, cfg = call("GET", "/config/comercial", adm)
    for c in g(cfg, "condicoes", default=[]):
        call("PUT", "/config/comercial/condicoes/%s" % c["id"], adm,
             {"ajustePercentual": c["ajustePercentual"], "ativa": ativa and (c["forma"], c["parcelas"]) in CONDICOES_TESTE})


def config_restaurar(adm, snap, criadas):
    """Volta ao estado original: configuração, condições existentes e desativa as criadas pelo roteiro."""
    config_aplicar(adm, snap["limiteDescontoPercentual"], snap["arredondamento"],
                   snap["perfisCancelamento"] or [], snap["exigePagamentoExpedir"])
    for c in snap["condicoes"]:
        call("PUT", "/config/comercial/condicoes/%s" % c["id"], adm,
             {"ajustePercentual": c["ajustePercentual"], "ativa": c["ativa"]})
    for cid in criadas:
        call("PUT", "/config/comercial/condicoes/%s" % cid, adm, {"ajustePercentual": "0.00", "ativa": False})
    info("configuração comercial restaurada ao estado original")


def rotulo_teste():
    print("\n[VALORES DE TESTE] limite de desconto=%s%%, arredondamento=%s, condições=%s "
          "(não são decisões de negócio: D03/D04 seguem pendentes para a loja)."
          % (LIMITE_TESTE, ARRED_TESTE, ", ".join("%s %dx %s%%" % (f, p, a) for (f, p), a in CONDICOES_TESTE.items())))
