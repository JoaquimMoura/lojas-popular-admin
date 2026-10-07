#!/usr/bin/env bash
# Executa a SUITE AUTOMATIZADA (JUnit) contra um PostgreSQL 15 descartavel, com o esquema criado so pelas migracoes
# (V1 + Flyway) e o Hibernate em ddl-auto=validate. Registra o resultado separado dos roteiros de API/navegador.
# NUNCA aponte para producao: cria e remove o container "lp-suite-pg" (porta LP_SUITE_PG_PORT, padrao 55441).
# Uso: LP_JAVA_HOME=/c/Program\ Files/Java/jdk-21 scripts/validacao/suite-pg.sh
set -euo pipefail
AQUI="$(cd "$(dirname "$0")" && pwd)"; RAIZ="$(cd "$AQUI/../.." && pwd)"
MIG="$RAIZ/src/main/resources/db/migration"; NOME="lp-suite-pg"; PORTA="${LP_SUITE_PG_PORT:-55441}"
DB=lojas_suite; USR=lpsuite; SENHA="$(od -An -tx1 -N12 /dev/urandom | tr -d ' \n')"
nativo_win() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi; }
limpar() { docker rm -f "$NOME" >/dev/null 2>&1 || true; }
trap limpar EXIT; limpar
[ -n "${LP_JAVA_HOME:-}" ] && export JAVA_HOME="$LP_JAVA_HOME"
POSTGRES_PASSWORD="$SENHA" docker run -d --name "$NOME" -e POSTGRES_USER="$USR" -e POSTGRES_DB="$DB" -e POSTGRES_PASSWORD \
  -p "127.0.0.1:$PORTA:5432" postgres:15 >/dev/null
for _ in $(seq 1 60); do docker exec "$NOME" psql -h 127.0.0.1 -U "$USR" -d "$DB" -tAc 'select 1' >/dev/null 2>&1 && break; sleep 2; done
docker exec -i "$NOME" psql -U "$USR" -d "$DB" -v ON_ERROR_STOP=1 -q -o /dev/null < "$MIG/V1__baseline.sql"
FLYWAY_PASSWORD="$SENHA" MSYS_NO_PATHCONV=1 docker run --rm --add-host=host.docker.internal:host-gateway -e FLYWAY_PASSWORD \
  -v "$(nativo_win "$MIG"):/flyway/sql:ro" flyway/flyway:11 -url="jdbc:postgresql://host.docker.internal:$PORTA/$DB" \
  -user="$USR" -baselineOnMigrate=true -baselineVersion=1 -schemas=public migrate | grep -E "Successfully applied|now at version"
cd "$RAIZ"
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:$PORTA/$DB" SPRING_DATASOURCE_USERNAME="$USR" \
  SPRING_DATASOURCE_PASSWORD="$SENHA" SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver \
  SPRING_JPA_HIBERNATE_DDL_AUTO=validate SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT=org.hibernate.dialect.PostgreSQLDialect \
  SPRING_FLYWAY_ENABLED=false
./mvnw -o test "$@"
