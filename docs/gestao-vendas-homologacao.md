# Homologação e implantação

Nada aqui foi executado em produção. **Sem push e sem deploy até o ensaio das migrações V3 a V6 na cópia do banco real passar** (passo 2). Referências: `DEPLOY.md`, `scripts/ensaio-migracao-v3.sh`, `scripts/validacao/`.

## 1. Pré-requisitos

- Respostas do [questionário](gestao-vendas-questionario-loja.md), no mínimo as perguntas 1 a 5 (vender), 7 (se for vender no cartão), 8 (se o gerente operar o financeiro) e 20 (estoque).
- Janela de implantação sem vendas em andamento; responsável pelo dono do banco e do servidor.
- Estado atual do banco de produção: versão do Flyway esperada = 2 (baseline em 1). A V3, V4, V5 e V6 são novas.

## 2. Backup e ensaio das V3 a V6 na cópia do banco real

1. **Backup (VPS, somente leitura):**
   `docker exec lojas-postgres-prod pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc > lojas-AAAAMMDD.dump`
   Guardar **fora** do servidor; copiar também a pasta de uploads. **Testar a restauração** num PostgreSQL descartável (o próprio ensaio faz isso).
2. **Ensaio** (máquina de apoio, com Docker): `scripts/ensaio-migracao-v3.sh lojas-AAAAMMDD.dump`
   Critérios para seguir: `ENSAIO OK` sem nenhuma falha; contagens de pedidos/itens/totais/frete/usuários/produtos/estoque iguais antes e depois; pedidos antigos marcados como legado; tabelas novas vazias (V4, V5, V6); decisões financeiras nulas; nenhum item antigo com custo zero inventado; histórico do Flyway sem falhas até a V6.
3. Se o ensaio falhar: **não implantar**; corrigir a causa, gerar novo dump e repetir.
4. Subir o backend novo **contra a cópia ensaiada** (`ddl-auto=validate`, igual à produção) e abrir o painel: o Hibernate valida o esquema contra o código.

## 3. Homologação (ambiente de teste com a cópia do banco)

Executar com usuários de teste, nunca com dados reais de clientes em canais externos:

1. Roteiros automáticos (ambiente descartável): `suite-pg.sh`, `api_etapa1/2/3.py`, `ui_etapa3.py`, `ui_fluxo.py` — todos verdes.
2. Passo a passo manual de ponta a ponta, por perfil:
   - Proprietário: configurar regras (passo 5), cadastrar custo, abrir caixa.
   - Vendedor: vender (Pix e cartão), pedir desconto acima do limite.
   - Gerente: aprovar desconto, receber, agendar e registrar saída, concluir entrega com comprovante, abrir devolução.
   - Proprietário: estornar um recebimento, restituir (se D09 permitir), pagar comissão, ver a prévia do fechamento e **aprovar/reabrir um mês de teste**.
3. Conferir: gerente **sem** permissão D12 é barrado; relatórios batem com o que foi lançado; nada é calculado onde a decisão está pendente.
4. Registrar quem testou, data e resultado.

## 4. Contagem inicial do estoque

1. Variações sem saldo aparecem como "sem saldo" e **não podem ser vendidas**.
2. Contar fisicamente (ou usar o relatório de estoque para a lista) e lançar cada saldo em **Estoque › ajustar contagem**, com motivo ("contagem inicial").
3. Conferir o relatório de estoque (físico = contado; reservado = 0 antes das vendas).
4. Só liberar a venda depois da contagem.

## 5. Configuração das regras (ordem)

1. Usuários e perfis (limite de 5 ativos).
2. Vendas: limite de desconto (D03), arredondamento (D04), condições de pagamento, regra de saída (D05), perfis de cancelamento (D07).
3. Cartão: taxas e prazos por operadora (D11).
4. **Permissões financeiras do gerente (D12)** — sem isto o gerente não opera.
5. Comissão (D01, D02), competência (D06), restituição e diferença (D09), perfis de restituição/reabertura (D07), fechamento (D10).
6. Custos dos produtos (se informados) e metas do mês.
7. Abrir o caixa do dia. A tela de configuração lista as pendências restantes: o que sobrar fica bloqueado, não presumido.

## 6. Implantação

1. Backup final imediatamente antes (passo 2.1) e conferência de espaço em disco.
2. Procedimento do `DEPLOY.md` (`docker compose -f docker-compose.prod.yml up -d --build`). O Flyway aplica V3 a V6 na subida; acompanhar `docker compose … logs backend -f` até "Started".
3. Verificação pós-implantação: login do proprietário; configuração mostrando as pendências esperadas; vitrine pública (home, produto) normal; uma venda de teste cancelada (sem recebimento); relatório de estoque; auditoria registrando a alteração.
4. Só então liberar para a equipe.

## 7. Recuperação em caso de falha

| Situação | Ação |
|---|---|
| Migração falha e o backend não sobe | A V3 a V6 rodam em transação por migração; conferir `flyway_schema_history`. Voltar a imagem anterior **e restaurar o backup** do passo 6.1 (as migrações não têm "down"). |
| Sobe, mas há erro grave **antes de qualquer venda nova** | Voltar a imagem anterior e restaurar o backup. Nada se perde (nenhum dado novo existe). |
| Erro **depois** de haver vendas/recebimentos novos | **Não restaurar** (perde o que foi lançado). Corrigir adiante; se preciso, desabilitar a operação afetada deixando a decisão pendente (ex.: D05 nula bloqueia a saída). Estornos e cancelamentos são rastreáveis. |
| Esquema divergente (`Schema-validation`) | Parar, não editar o banco à mão; comparar com a cópia ensaiada e a V correspondente. |
| Regra configurada errada | Corrigir na configuração (auditada); vendas já gravadas mantêm a regra histórica (comissão, preço, custo, plano de cartão). |

Contatos, janela e responsável pela decisão de rollback devem ser preenchidos antes do dia.

## 7.1 Instalação com uma V6 diferente da deste repositório

A **V6 está congelada**. Se algum banco tiver uma V6 aplicada diferente do arquivo atual (por exemplo, de uma versão anterior do código):

1. **Não rodar** `flyway repair`, não editar `flyway_schema_history` e não mover colunas à mão ou por script automático.
2. Coletar: `select installed_rank, version, description, checksum, installed_on, success from flyway_schema_history where version = '6';` e o resultado de `flyway info`/`validate` (mensagem de divergência de checksum).
3. Comparar o **arquivo que foi aplicado** (recuperar do commit/artefato que subiu aquela versão) com `src/main/resources/db/migration/V6__gestao_vendas_custos.sql`. Versões conhecidas: `c73a71c` (sem as colunas `perm_fin_*` da D12) e a atual (com elas).
4. Comparar o esquema real: `\d configuracao_comercial` (colunas `perm_fin_consultar|receber|pagar|estornar|restituir`), `\d custos_produto`, `\d itens_pedido` (`custo_origem`) e o CHECK de `auditoria_evento`.
5. Só então propor a correção, **por escrito e revisada**: normalmente uma **V7** que cria o que falta (ex.: adicionar as colunas `perm_fin_*` se a V6 aplicada era a de `c73a71c`) ou, se houver coluna a mais/diferente, uma V7 explícita. A divergência de checksum se resolve com decisão humana documentada, nunca automaticamente.

## 8. Limitações que continuam valendo na operação

- **Comprovantes** de pagamento: só referência em texto (sem anexo).
- **Trocas**: sem crédito de troca nem reposição automática (processo manual rastreável descrito na revisão final).
- **Resultado**: nunca definitivo com faltantes; margem só com custo completo; taxas de cartão pelo mês do pagamento.
- **Mercado Pago**: sem validação ponta a ponta com o gateway.
- Sem teste em dispositivo físico; sem notificações; sem conciliação bancária.

## 9. Resultados da validação automática (rodada atual)

| Tipo | Ambiente | Resultado |
|---|---|---|
| Suíte JUnit completa | H2 e PostgreSQL 15 (V1→V6, `validate`) | 107 testes passaram na rodada anterior; hoje há **111** (4 novos: pós-venda x D12 por API direta, vendas legadas, taxas previstas x liquidadas, critérios do resultado) |
| Testes afetados nesta rodada | H2 | `PermissaoFinanceiraTest`, `CustoRelatorioTest`, `FinanceiroServiceTest` (53) e `VendaServiceTest`, `PagamentoPropriedadeTest` (24): todos passam |
| Testes afetados nesta rodada | PostgreSQL 15 | `PermissaoFinanceiraTest`, `CustoRelatorioTest`, `FinanceiroServiceTest`, `AtendimentoServiceTest`, `SegurancaApiTest` (86): todos passam |
| Roteiros de API | PostgreSQL 15 | `api_etapa3` 159/159 (etapas 1 e 2 não foram tocadas: 118/118 e 154/154 da rodada anterior) |
| Roteiros de navegador | PostgreSQL 15 | `ui_etapa3` 50/50 · `ui_fluxo` 68/68 (uma execução em banco novo falhou uma vez em "devolução exibe o bloqueio D09" por tempo de renderização; não se repetiu em duas execuções seguintes. O roteiro agora espera 500 ms e imprime a tela em caso de falha) |
| Frontend | build e lint | build OK; lint com os mesmos 6 erros antigos |

A suíte completa **não** foi repetida nesta rodada: as mudanças ficaram em pós-venda, relatórios, fechamento e no painel de pagamento, cobertos pelas classes acima. Os roteiros `ui_fluxo` e `ui_etapa3` concedem a D12 ao gerente como valor de teste e restauram o original.
