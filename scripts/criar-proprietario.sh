#!/usr/bin/env bash
# Cria o PRIMEIRO usuário proprietário (ADMIN) num banco recém-instalado (sem usuários), via docker exec no container do Postgres.
# A senha vem de variável de ambiente (não aparece em linha de comando nem em log) e é gravada com bcrypt.
#
# Uso (na VPS, depois do primeiro "up" que aplicou as migrações):
#   export LP_OWNER_EMAIL='proprietario@seudominio.com.br'
#   export LP_OWNER_PASSWORD='uma-senha-forte-com-8+-caracteres'
#   scripts/criar-proprietario.sh [container=lojas-postgres-prod] [usuario=lojas_popular] [banco=lojas_popular]
# Recusa-se a rodar se já existir qualquer usuário (nunca sobrescreve nem redefine senha).
set -euo pipefail
CONT="${1:-lojas-postgres-prod}"; USR="${2:-lojas_popular}"; DB="${3:-lojas_popular}"
[ -n "${LP_OWNER_EMAIL:-}" ] && [ -n "${LP_OWNER_PASSWORD:-}" ] || { echo "Defina LP_OWNER_EMAIL e LP_OWNER_PASSWORD."; exit 2; }
[ "${#LP_OWNER_PASSWORD}" -ge 8 ] || { echo "A senha precisa ter ao menos 8 caracteres."; exit 2; }
psql_() { docker exec -i -e LP_OWNER_EMAIL -e LP_OWNER_PASSWORD "$CONT" psql -U "$USR" -d "$DB" -v ON_ERROR_STOP=1 -q "$@"; }
N="$(docker exec "$CONT" psql -U "$USR" -d "$DB" -tAc 'select count(*) from users')"
[ "$N" = "0" ] || { echo "O banco já tem $N usuário(s): nada foi feito."; exit 1; }
psql_ <<'SQL'
\set email `printenv LP_OWNER_EMAIL`
\set senha `printenv LP_OWNER_PASSWORD`
begin;
create extension if not exists pgcrypto;
insert into users (enabled, created_at, email, password_hash, nome)
values (true, now(), lower(trim(:'email')), crypt(:'senha', gen_salt('bf', 10)), 'Proprietário')
returning id as admin_id \gset
insert into user_roles (user_id, role) values (:admin_id, 'ADMIN');
commit;
SQL
echo "Proprietário criado: $(printf '%s' "$LP_OWNER_EMAIL" | tr 'A-Z' 'a-z'). Entre em /login e troque a senha quando houver a tela."
