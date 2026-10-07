#!/usr/bin/env bash
# Ensaio da migração V3 (gestão de vendas) sobre uma CÓPIA do banco de produção.
#
# NÃO se conecta à produção: recebe um dump já feito e o restaura num PostgreSQL 15 descartável
# (mesma versão do container de produção: postgres:15), aplica as migrations pendentes com o
# Flyway e confere o resultado (contagens, mapa de conversão, valores históricos preservados).
#
# Como gerar o dump na VPS (somente leitura; ver DEPLOY.md):
#   docker exec lojas-postgres-prod pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc > lojas.dump
#
# Uso:  scripts/ensaio-migracao-v3.sh lojas.dump [porta-local=55434] [--manter]
#
# Requisitos: Docker. O banco de ensaio é removido ao final, exceto com --manter.
set -euo pipefail

DUMP="${1:?informe o arquivo de dump (pg_dump -Fc)}"
PORTA="${2:-55434}"
MANTER="${3:-}"
NOME="lp-ensaio-v3"
DB="lojas_ensaio"
SENHA="ensaio"
RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
MIGRACOES="$RAIZ/src/main/resources/db/migration"

[ -f "$DUMP" ] || { echo "Dump não encontrado: $DUMP"; exit 2; }

cleanup() { [ "$MANTER" = "--manter" ] || docker rm -f "$NOME" >/dev/null 2>&1 || true; }
trap cleanup EXIT

psql_() { docker exec -i "$NOME" psql -U postgres -d "$DB" -v ON_ERROR_STOP=1 -tA "$@"; }

echo "==> Subindo postgres:15 descartável na porta $PORTA"
docker rm -f "$NOME" >/dev/null 2>&1 || true
docker run -d --name "$NOME" -e POSTGRES_PASSWORD="$SENHA" -p "$PORTA:5432" postgres:15 >/dev/null
for _ in $(seq 1 40); do docker exec "$NOME" pg_isready -U postgres >/dev/null 2>&1 && break; sleep 2; done
sleep 3
docker exec "$NOME" psql -U postgres -c "create database $DB" >/dev/null

echo "==> Restaurando o dump"
docker exec -i "$NOME" pg_restore -U postgres -d "$DB" --no-owner --no-privileges --exit-on-error < "$DUMP"
echo "    versão do servidor: $(psql_ -c 'show server_version')"
echo "    migrations já aplicadas: $(psql_ -c "select coalesce(string_agg(version, ','), '(sem histórico)') from flyway_schema_history" 2>/dev/null || echo '(sem histórico)')"

echo "==> Fotografia ANTES da V3"
ANTES_PEDIDOS=$(psql_ -c "select count(*) from pedidos")
ANTES_ITENS=$(psql_ -c "select count(*) from itens_pedido")
ANTES_TOTAL=$(psql_ -c "select coalesce(sum(total),0) from pedidos")
ANTES_FRETE=$(psql_ -c "select coalesce(sum(frete),0) from pedidos")
ANTES_PAGO=$(psql_ -c "select count(*) from pedidos where status='PAGO'")
ANTES_ENTREGUE=$(psql_ -c "select count(*) from pedidos where status='ENTREGUE'")
ANTES_CANCELADO=$(psql_ -c "select count(*) from pedidos where status='CANCELADO'")
ANTES_CRIADO=$(psql_ -c "select count(*) from pedidos where status='CRIADO'")
ANTES_USUARIOS=$(psql_ -c "select count(*) from users")
ANTES_PRODUTOS=$(psql_ -c "select count(*) from produtos")
echo "    pedidos=$ANTES_PEDIDOS itens=$ANTES_ITENS total=$ANTES_TOTAL frete=$ANTES_FRETE usuarios=$ANTES_USUARIOS produtos=$ANTES_PRODUTOS"

echo "==> Aplicando migrations com o Flyway (mesma configuração do app: baselineOnMigrate)"
docker run --rm --add-host=host.docker.internal:host-gateway -v "$MIGRACOES:/flyway/sql:ro" flyway/flyway:11 \
  -url="jdbc:postgresql://host.docker.internal:$PORTA/$DB" -user=postgres -password="$SENHA" \
  -baselineOnMigrate=true -baselineVersion=1 -schemas=public migrate

echo "==> Conferência DEPOIS da V3"
FALHAS=0
confere() { # descrição, esperado, obtido
  if [ "$2" = "$3" ]; then echo "    OK    $1 ($3)"; else echo "    FALHA $1: esperado $2, obtido $3"; FALHAS=$((FALHAS+1)); fi
}
confere "pedidos preservados"          "$ANTES_PEDIDOS"  "$(psql_ -c 'select count(*) from pedidos')"
confere "itens preservados"            "$ANTES_ITENS"    "$(psql_ -c 'select count(*) from itens_pedido')"
confere "soma dos totais inalterada"   "$ANTES_TOTAL"    "$(psql_ -c 'select coalesce(sum(total),0) from pedidos')"
confere "soma dos fretes inalterada (frete antigo não é recalculado)" "$ANTES_FRETE" "$(psql_ -c 'select coalesce(sum(frete),0) from pedidos')"
confere "usuários preservados"         "$ANTES_USUARIOS" "$(psql_ -c 'select count(*) from users')"
confere "produtos preservados"         "$ANTES_PRODUTOS" "$(psql_ -c 'select count(*) from produtos')"
confere "PAGO -> pagamento quitado"    "$ANTES_PAGO"     "$(psql_ -c "select count(*) from pedidos where status='PAGO' and status_pagamento='PAGO'")"
confere "ENTREGUE -> entrega concluída" "$ANTES_ENTREGUE" "$(psql_ -c "select count(*) from pedidos where status='ENTREGUE' and status_entrega='ENTREGUE'")"
confere "ENTREGUE não presume quitação" "0"             "$(psql_ -c "select count(*) from pedidos where status='ENTREGUE' and status_pagamento='PAGO'")"
confere "CANCELADO -> cancelada"       "$ANTES_CANCELADO" "$(psql_ -c "select count(*) from pedidos where status='CANCELADO' and status_comercial='CANCELADA'")"
confere "CRIADO -> revisão manual"     "$ANTES_CRIADO"   "$(psql_ -c "select count(*) from pedidos where status='CRIADO' and revisao_legado")"
confere "nenhum cliente/vendedor atribuído a pedido antigo" "0" "$(psql_ -c 'select count(*) from pedidos where cliente_id is not null or vendedor_id is not null')"
confere "nenhuma reserva gerada para pedido antigo" "0" "$(psql_ -c 'select count(*) from reservas_estoque')"
confere "todo pedido antigo ficou LEGADO ou CANCELADA" "0" "$(psql_ -c "select count(*) from pedidos where status_comercial not in ('LEGADO','CANCELADA')")"
confere "produtos antigos em PRONTA_ENTREGA" "$ANTES_PRODUTOS" "$(psql_ -c "select count(*) from produtos where modalidade='PRONTA_ENTREGA'")"
confere "histórico do Flyway sem falhas" "0" "$(psql_ -c "select count(*) from flyway_schema_history where success = false")"

echo "==> Pontos de atenção (não são falhas)"
SEM_SALDO=$(psql_ -c "select count(*) from produto_variacoes v where v.estoque is null")
echo "    variações sem saldo (não poderão ser vendidas até informar o estoque): $SEM_SALDO"
psql_ -c "select '      ' || p.id || ' | ' || p.nome || ' | ' || coalesce(v.cor,'-') || '/' || coalesce(v.tamanho,'-') from produto_variacoes v join produtos p on p.id = v.produto_id where v.estoque is null order by p.id limit 20" || true
echo "    usuários por perfil:"; psql_ -c "select '      ' || role || ': ' || count(*) from user_roles group by role order by role"
echo "    (o perfil GERENTE ainda não existe: crie em Usuários após a migração)"

if [ "$FALHAS" -eq 0 ]; then echo "==> ENSAIO OK: V3 aplicada sem perda de dados."; else echo "==> ENSAIO COM $FALHAS FALHA(S)."; exit 1; fi
