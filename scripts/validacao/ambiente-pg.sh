#!/usr/bin/env bash
# Banco PostgreSQL DESCARTÁVEL para a validação local da gestão de vendas.
#
# NUNCA aponte para produção: este script cria (e remove) o container "lp-val-pg" com postgres:15,
# aplica V1 (psql) e V2..V6 (Flyway), cria o PRIMEIRO usuário proprietário e um catálogo de exemplo.
#
# Uso:
#   export LP_ADMIN_EMAIL='proprietario@example.invalid'   # obrigatórias no "up"
#   export LP_ADMIN_PASSWORD='uma-senha-de-teste'
#   scripts/validacao/ambiente-pg.sh up
#   scripts/validacao/ambiente-pg.sh down [--limpar]
#
# Variáveis: LP_PG_PORT (55440), LP_UPLOAD_DIR (validacao-out/uploads).
# Segredos gerados (senha do banco, JWT, usuário CLIENTE de teste) ficam em scripts/validacao/.ambiente.env,
# que NÃO é versionado. Nenhuma senha é escrita em log.
set -euo pipefail

AQUI="$(cd "$(dirname "$0")" && pwd)"
RAIZ="$(cd "$AQUI/../.." && pwd)"
ENV_FILE="$AQUI/.ambiente.env"
OUT="$RAIZ/validacao-out"
MIGRACOES="$RAIZ/src/main/resources/db/migration"
NOME="lp-val-pg"
DB="lojas_val"
USUARIO="lpval"
PORTA="${LP_PG_PORT:-55440}"

# Caminho no formato do sistema (Windows: C:/...; Linux: inalterado), para Java/Node/Docker.
nativo() { if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else printf '%s' "$1"; fi; }
nativo_win() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi; }
aleatorio() { od -An -tx1 -N"$1" /dev/urandom | tr -d ' \n'; }
absoluto() { case "$1" in /*|[A-Za-z]:*) printf '%s' "$1" ;; *) printf '%s/%s' "$RAIZ" "$1" ;; esac; }

UPLOAD_DIR="$(absoluto "${LP_UPLOAD_DIR:-validacao-out/uploads}")"

uso() { echo "Uso: $0 up | down [--limpar]"; exit 2; }
[ $# -ge 1 ] || uso

comando_up() {
    if [ -z "${LP_ADMIN_EMAIL:-}" ] || [ -z "${LP_ADMIN_PASSWORD:-}" ]; then
    echo "Defina LP_ADMIN_EMAIL e LP_ADMIN_PASSWORD no ambiente (primeiro proprietário; use e-mail e senha de teste)."; exit 2
  fi
  [ "${#LP_ADMIN_PASSWORD}" -ge 8 ] || { echo "LP_ADMIN_PASSWORD precisa ter ao menos 8 caracteres."; exit 2; }
  command -v docker >/dev/null || { echo "Docker não encontrado."; exit 2; }
  [ -d "$MIGRACOES" ] || { echo "Migrações não encontradas em $MIGRACOES"; exit 2; }

  if docker ps -a --format '{{.Names}}' | grep -qx "$NOME"; then
    echo "O container $NOME já existe. Rode '$0 down' antes de subir um ambiente novo."; exit 2
  fi

  mkdir -p "$OUT"
  local pg_senha jwt cliente_senha cliente_email
  pg_senha="$(aleatorio 18)"
  jwt="$(aleatorio 32)"
  cliente_senha="$(aleatorio 12)Aa1!"
  cliente_email="lp-val-cliente-$(aleatorio 4)@example.invalid"

  ( umask 077
    {
      echo "# Gerado por ambiente-pg.sh up. NÃO versionar. Contém segredos de um ambiente descartável."
      echo "LP_PG_PORT=$PORTA"
      echo "LP_PG_DB=$DB"
      echo "LP_PG_USER=$USUARIO"
      echo "LP_PG_PASSWORD=$pg_senha"
      echo "LP_JWT_SECRET=$jwt"
      echo "LP_UPLOAD_DIR=$(nativo "$UPLOAD_DIR")"
      echo "LP_CLIENTE_EMAIL=$cliente_email"
      echo "LP_CLIENTE_PASSWORD=$cliente_senha"
    } > "$ENV_FILE" )

  echo "==> Subindo postgres:15 descartável ($NOME) na porta $PORTA"
  # -e NOME (sem valor) repassa a variável do ambiente: a senha não aparece na linha de comando.
  POSTGRES_PASSWORD="$pg_senha" docker run -d --name "$NOME" -e POSTGRES_USER="$USUARIO" -e POSTGRES_DB="$DB" \
    -e POSTGRES_PASSWORD -p "127.0.0.1:$PORTA:5432" postgres:15 >/dev/null
  # O servidor de inicialização só escuta no socket; o definitivo escuta em TCP. Espera o definitivo.
  local pronto=0
  for _ in $(seq 1 60); do
    if docker exec "$NOME" psql -h 127.0.0.1 -U "$USUARIO" -d "$DB" -tAc 'select 1' >/dev/null 2>&1; then pronto=1; break; fi
    sleep 2
  done
  [ "$pronto" = 1 ] || { echo "O PostgreSQL não ficou pronto a tempo."; exit 1; }

  export LP_CLIENTE_EMAIL="$cliente_email" LP_CLIENTE_PASSWORD="$cliente_senha"
  psql_() {
    docker exec -i -e LP_ADMIN_EMAIL -e LP_ADMIN_PASSWORD -e LP_CLIENTE_EMAIL -e LP_CLIENTE_PASSWORD \
      "$NOME" psql -U "$USUARIO" -d "$DB" -v ON_ERROR_STOP=1 -q "$@"
  }

  echo "==> Aplicando V1 (baseline) com psql"
  psql_ -o /dev/null < "$MIGRACOES/V1__baseline.sql"

  echo "==> Aplicando V2..V6 com o Flyway (baselineOnMigrate, baselineVersion=1)"
  # MSYS_NO_PATHCONV evita que o Git Bash reescreva os caminhos; a senha vai por variável de ambiente.
  FLYWAY_PASSWORD="$pg_senha" MSYS_NO_PATHCONV=1 docker run --rm --add-host=host.docker.internal:host-gateway \
    -e FLYWAY_PASSWORD -v "$(nativo_win "$MIGRACOES"):/flyway/sql:ro" flyway/flyway:11 \
    -url="jdbc:postgresql://host.docker.internal:$PORTA/$DB" -user="$USUARIO" \
    -baselineOnMigrate=true -baselineVersion=1 -schemas=public migrate
  local aplicadas
  aplicadas="$(docker exec "$NOME" psql -U "$USUARIO" -d "$DB" -tA -c "select string_agg(version, ',' order by installed_rank) from flyway_schema_history where success")"
  echo "    versões aplicadas no histórico: $aplicadas"
  case "$aplicadas" in *6) ;; *) echo "Migrações incompletas (esperado até a V6)."; exit 1 ;; esac

  echo "==> Criando o proprietário, um usuário CLIENTE de teste e o catálogo de exemplo"
  # E-mails e senhas entram no SQL por variável do psql lida do ambiente do container (não aparecem em 'ps').
  psql_ -o /dev/null <<'SQL'
\set email `printenv LP_ADMIN_EMAIL`
\set senha `printenv LP_ADMIN_PASSWORD`
\set cli_email `printenv LP_CLIENTE_EMAIL`
\set cli_senha `printenv LP_CLIENTE_PASSWORD`
begin;
create extension if not exists pgcrypto;

insert into users (enabled, created_at, email, password_hash, nome)
values (true, now(), lower(trim(:'email')), crypt(:'senha', gen_salt('bf', 10)), 'Proprietário (validação)')
returning id as admin_id \gset
insert into user_roles (user_id, role) values (:admin_id, 'ADMIN');

insert into users (enabled, created_at, email, password_hash, nome)
values (true, now(), lower(trim(:'cli_email')), crypt(:'cli_senha', gen_salt('bf', 10)), 'Cliente de teste')
returning id as cli_id \gset
insert into user_roles (user_id, role) values (:cli_id, 'CLIENTE');

insert into categorias (ativa, material, nome, descricao, data_criacao, data_atualizacao)
values (true, 'MDF', 'Exemplo (validação)', 'Categoria de exemplo da validação local', now(), now())
returning id as cat_id \gset

insert into produtos (ativa, estoque, preco, nome, descricao, sku, categoria_id, imagem_url, data_criacao, data_atualizacao)
values (true, 10, 899.90, 'Guarda-roupa Exemplo', 'Produto de exemplo sem variações', 'EXEMPLO-GR', :cat_id,
        '/uploads/produtos/exemplo/exemplo.png', now(), now());

insert into produtos (ativa, estoque, preco, nome, descricao, sku, categoria_id, imagem_url, data_criacao, data_atualizacao)
values (true, 10, 1500.00, 'Sofá Exemplo', 'Produto de exemplo com variações', 'EXEMPLO-SF', :cat_id,
        '/uploads/produtos/exemplo/exemplo.png', now(), now())
returning id as sofa_id \gset
insert into produto_variacoes (produto_id, cor, tamanho, sku, adicional_preco, estoque, imagem_url) values
  (:sofa_id, 'Cinza', '2 lugares', 'EXEMPLO-SF-CZ2', 0, 5, '/uploads/produtos/exemplo/exemplo.png'),
  (:sofa_id, 'Azul', '3 lugares', 'EXEMPLO-SF-AZ3', 200, 5, '/uploads/produtos/exemplo/exemplo.png');

drop extension pgcrypto;
commit;
SQL

  echo "==> Gravando a imagem de exemplo em $UPLOAD_DIR/produtos/exemplo/exemplo.png"
  mkdir -p "$UPLOAD_DIR/produtos/exemplo"
  : > "$UPLOAD_DIR/.lp-val-marca"
  # PNG 1x1 válido
  printf '%s' 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==' \
    | base64 -d > "$UPLOAD_DIR/produtos/exemplo/exemplo.png"

  echo "==> Ambiente pronto: PostgreSQL em localhost:$PORTA (banco $DB, usuário $USUARIO)."
  echo "    Segredos locais em $ENV_FILE (não versionado)."
  echo "    Próximo passo: scripts/validacao/iniciar-app.sh"
}

comando_down() {
  local limpar=0
  [ "${1:-}" = "--limpar" ] && limpar=1
  if [ -f "$OUT/pids" ]; then
    echo "AVISO: há serviços registrados em validacao-out/pids. Rode parar-app.sh antes de remover o banco."
  fi
  if docker rm -f "$NOME" >/dev/null 2>&1; then echo "==> Container $NOME removido."; else echo "==> Container $NOME não existia."; fi
  if [ "$limpar" = 1 ]; then
    rm -f "$ENV_FILE"
    # O diretório de uploads só é apagado se está dentro de validacao-out ou se foi criado por este script (marca).
    case "$UPLOAD_DIR" in
      "$OUT"/*) rm -rf "$UPLOAD_DIR" ;;
      *) if [ -f "$UPLOAD_DIR/.lp-val-marca" ]; then rm -rf "$UPLOAD_DIR"; fi ;;
    esac
    rm -rf "$OUT"
    echo "==> Pastas e arquivos gerados removidos (validacao-out/, .ambiente.env)."
  fi
}

case "$1" in
  up) comando_up ;;
  down) shift; comando_down "$@" ;;
  *) uso ;;
esac
