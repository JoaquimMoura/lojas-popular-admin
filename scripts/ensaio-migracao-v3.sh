#!/usr/bin/env bash
# Ensaio das migrações da gestão de vendas (V3 e V4, ou as que estiverem pendentes) sobre uma CÓPIA do
# banco de produção.
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
# senha descartável (o container só existe durante o ensaio e não publica a porta além do localhost)
SENHA="$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 20)"
RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
MIGRACOES="$RAIZ/src/main/resources/db/migration"

[ -f "$DUMP" ] || { echo "Dump não encontrado: $DUMP"; exit 2; }

cleanup() { [ "$MANTER" = "--manter" ] || docker rm -f "$NOME" >/dev/null 2>&1 || true; }
trap cleanup EXIT

psql_() { docker exec -i "$NOME" psql -U postgres -d "$DB" -v ON_ERROR_STOP=1 -tA "$@"; }

echo "==> Subindo postgres:15 descartável na porta $PORTA"
docker rm -f "$NOME" >/dev/null 2>&1 || true
docker run -d --name "$NOME" -e POSTGRES_PASSWORD="$SENHA" -p "127.0.0.1:$PORTA:5432" postgres:15 >/dev/null
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
ANTES_ESTOQUE_VAR=$(psql_ -c "select coalesce(sum(estoque),0) from produto_variacoes")
ANTES_ESTOQUE_PROD=$(psql_ -c "select coalesce(sum(estoque),0) from produtos")
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

# --- Etapa 2 (V4): só confere o que existir depois da migração; em bancos que parem na V3 é ignorado
TEM_V4=$(psql_ -c "select count(*) from information_schema.tables where table_schema='public' and table_name='movimentacoes_estoque'")
if [ "$TEM_V4" = "1" ]; then
  echo "    -- V4 (atendimento e pós-venda)"
  for t in movimentacoes_estoque encomendas entregas entrega_eventos montagens ocorrencias_pos_venda ocorrencia_evidencias; do
    confere "tabela $t criada e vazia (nada é gerado para pedidos antigos)" "0" "$(psql_ -c "select count(*) from $t")"
  done
  confere "nenhuma saída/baixa atribuída a pedido antigo" "0" "$(psql_ -c 'select count(*) from pedidos where saida_realizada_em is not null or chave_saida is not null')"
  confere "D05 (pagamento para expedir) nasce pendente (nulo)" "0" "$(psql_ -c 'select count(*) from configuracao_comercial where exige_pagamento_expedir is not null')"
  confere "estoque físico das variações inalterado pela V4" "$ANTES_ESTOQUE_VAR" "$(psql_ -c 'select coalesce(sum(estoque),0) from produto_variacoes')"
  confere "estoque físico dos produtos inalterado pela V4" "$ANTES_ESTOQUE_PROD" "$(psql_ -c 'select coalesce(sum(estoque),0) from produtos')"
fi

# --- Etapa 3 (V5): financeiro. Nada é gerado para vendas antigas; decisões financeiras nascem pendentes (nulas)
TEM_V5=$(psql_ -c "select count(*) from information_schema.tables where table_schema='public' and table_name='lancamentos_financeiros'")
if [ "$TEM_V5" = "1" ]; then
  echo "    -- V5 (recebimentos, caixa, contas, cartão, comissões, metas e fechamento)"
  for t in encomenda_recebimentos taxas_cartao sessoes_caixa recebimentos recebiveis_cartao contas_financeiras conta_eventos restituicoes lancamentos_financeiros comissoes metas_vendedor fechamentos_mensais; do
    confere "tabela $t criada e vazia (nenhum recebimento/comissão é inventado para vendas antigas)" "0" "$(psql_ -c "select count(*) from $t")"
  done
  confere "decisões financeiras D01/D02/D06/D07/D09/D10 nascem pendentes (nulas)" "0" "$(psql_ -c 'select count(*) from configuracao_comercial where comissao_percentual is not null or comissao_aquisicao is not null or competencia_receita is not null or perfis_reabertura is not null or perfis_restituicao is not null or permite_restituicao is not null or permite_cobranca_diferenca is not null or meta_desconta_devolucoes is not null or fechamento_exige_sem_pendencias is not null')"
  confere "nenhum pedido antigo recebe status PARCIAL pela V5" "0" "$(psql_ -c "select count(*) from pedidos where status_pagamento = 'PARCIAL'")"
  confere "estoque físico das variações inalterado pela V5" "$ANTES_ESTOQUE_VAR" "$(psql_ -c 'select coalesce(sum(estoque),0) from produto_variacoes')"
  confere "PARCIAL aceito em pedidos.status_pagamento" "1" "$(psql_ -c "select count(*) from pg_constraint where conrelid = 'pedidos'::regclass and contype = 'c' and pg_get_constraintdef(oid) like '%PARCIAL%'")"
  confere "PARCIALMENTE_RECEBIDA aceito em encomendas.status" "1" "$(psql_ -c "select count(*) from pg_constraint where conrelid = 'encomendas'::regclass and contype = 'c' and pg_get_constraintdef(oid) like '%PARCIALMENTE_RECEBIDA%'")"
fi

# --- Etapa 3, complemento (V6): custos. Nenhum custo e preenchido (nem zero) para vendas antigas
TEM_V6=$(psql_ -c "select count(*) from information_schema.tables where table_schema='public' and table_name='custos_produto'")
if [ "$TEM_V6" = "1" ]; then
  echo "    -- V6 (custos)"
  confere "custos_produto criada e vazia" "0" "$(psql_ -c 'select count(*) from custos_produto')"
  confere "nenhum item antigo ganhou origem de custo" "0" "$(psql_ -c 'select count(*) from itens_pedido where custo_origem is not null')"
  confere "custo_unitario dos itens antigos permanece como estava (nenhum zero inventado)" "0" "$(psql_ -c "select count(*) from itens_pedido i join pedidos p on p.id = i.pedido_id where i.custo_unitario = 0 and p.status_comercial = 'LEGADO'")"
fi

# --- Catalogo dinamico (V10): materiais antigos copiados; vinculos categoria x material preservados; nenhum produto recebe material
TEM_V10=$(psql_ -c "select count(*) from information_schema.tables where table_schema='public' and table_name='materiais'")
if [ "$TEM_V10" = "1" ]; then
  echo "    -- V10 (materiais e características)"
  confere "os 6 materiais antigos entraram no cadastro, sem duplicar" "6" "$(psql_ -c "select count(*) from materiais where nome_normalizado in ('mdf','mdp','madeira','ferro','vidro','plastico')")"
  confere "cada categoria com material antigo recebeu exatamente 1 vínculo categoria x material" "$(psql_ -c "select count(*) from categorias where material is not null")" "$(psql_ -c 'select count(*) from categoria_materiais')"
  confere "nenhum material foi atribuído a produto por suposição" "0" "$(psql_ -c 'select count(*) from produto_materiais')"
  confere "nenhuma característica nem valor foi criado" "0" "$(psql_ -c 'select (select count(*) from caracteristicas) + (select count(*) from produto_caracteristica_valores)')"
  confere "produtos e estoque inalterados pela V10" "$ANTES_PRODUTOS" "$(psql_ -c 'select count(*) from produtos')"
fi

echo "==> Pontos de atenção (não são falhas)"
SEM_SALDO=$(psql_ -c "select count(*) from produto_variacoes v where v.estoque is null")
echo "    variações sem saldo (não poderão ser vendidas até informar o estoque): $SEM_SALDO"
psql_ -c "select '      ' || p.id || ' | ' || p.nome || ' | ' || coalesce(v.cor,'-') || '/' || coalesce(v.tamanho,'-') from produto_variacoes v join produtos p on p.id = v.produto_id where v.estoque is null order by p.id limit 20" || true
echo "    usuários por perfil:"; psql_ -c "select '      ' || role || ': ' || count(*) from user_roles group by role order by role"
echo "    (o perfil GERENTE ainda não existe: crie em Usuários após a migração)"

if [ "$FALHAS" -eq 0 ]; then echo "==> ENSAIO OK: migrações aplicadas sem perda de dados."; else echo "==> ENSAIO COM $FALHAS FALHA(S)."; exit 1; fi
