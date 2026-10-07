# Gestão de vendas — Etapa 3 (financeiro)

Continuação de [gestao-vendas-etapa1.md](gestao-vendas-etapa1.md) e [gestao-vendas-etapa2.md](gestao-vendas-etapa2.md), na mesma branch (`feature/gestao-vendas`).
As Etapas 1 e 2 foram preservadas. O **ensaio das migrações V3, V4 e V5 em cópia do banco de produção segue pendente** (ver "Pendências").

## O que foi entregue

| Área | Entrega |
|---|---|
| Recebimentos (UC-14) | Dinheiro, Pix e cartão, **uma forma por venda** (a da própria venda; o formulário não permite trocar). Valor até o saldo, parcial permitido (`statusPagamento = PARCIAL`), `Idempotency-Key` obrigatória, estorno com motivo e rastro. |
| Três conceitos separados | **Pagamento do cliente** (`recebimentos`), **recebível da operadora** (`recebiveis_cartao`: bruto, taxa, líquido, previsão) e **entrada efetiva** (`lancamentos_financeiros`: dinheiro no CAIXA, Pix e liquidações no BANCO). O cartão não gera entrada nenhuma até a liquidação, então a receita não é contada duas vezes. |
| Caixa (UC-15) | Abertura com saldo inicial (uma sessão aberta por vez), suprimento/retirada com motivo, fechamento diário com valor contado e **motivo obrigatório quando há diferença**. Só dinheiro e movimentos físicos entram no caixa; Pix, liquidações e baixas "pelo banco" nunca. |
| Contas (UC-16) | Contas a pagar e a receber com competência e vencimento, baixa por CAIXA ou BANCO (idempotente), estorno da baixa, cancelamento e histórico de eventos. |
| Cartão (UC-17) | Taxas por operadora/parcelas (D11), parcelas com arredondamento definido (a última absorve a diferença), liquidação (inclusive divergente, registrando a diferença sem inventar valor) e estorno da liquidação. |
| Comissões (UC-18) | Percentual **comum** sobre o **total cobrado**, previsão na confirmação, aquisição conforme D02, regra **histórica por venda** (mudar o percentual não altera vendas antigas), reversão por cancelamento ou restituição, compensação em pagamentos futuros e pagamento por conta a pagar. |
| Metas (UC-19) | Meta mensal individual, vendido no mês (cancelamentos não contam), atingimento com política de devoluções conforme D09. |
| Fechamento mensal (UC-20) | Prévia, pendências, caixa e banco separados, resultado **parcial/provisório**, aprovação do proprietário, versões, bloqueio de lançamentos no período e reabertura com justificativa. |
| Pós-venda (UC-21/22) | Restituição (solicitar → autorizar → efetivar) e cobrança da diferença de troca, só conforme D09/D07; **devolução física e devolução financeira são controles separados**. |
| Encomendas (revisão) | **Recebimento parcial do fornecedor passa a ser aceito**; a entrega parcial ao cliente continua proibida. |

## Regras fixas

- **Livro imutável**: todo movimento é um lançamento; corrigir é lançar o **oposto** vinculado ao original (`estorna_id`, no máximo um estorno por lançamento, na data atual). Nada é editado ou apagado. O `LivroService` é o único ponto de escrita.
- **Caixa × banco**: dinheiro exige caixa aberto do dia; Pix, liquidação de cartão e baixas pelo banco vão para o BANCO e nunca alteram o saldo físico do caixa.
- **Uma forma por venda**: forma e parcelas vêm da venda; cartão exige o **valor total** em um único recebimento.
- **Idempotência**: recebimento, estorno, movimento de caixa, liquidação, estorno de liquidação, baixa/estorno de conta e efetivação de restituição usam `Idempotency-Key`. A mesma chave devolve o mesmo resultado; a mesma chave em outra venda/operação é recusada; outra chave sobre algo já feito é recusada (nunca duplica).
- **Concorrência**: bloqueio pessimista do pedido e da linha afetada; índices únicos parciais no banco (uma sessão de caixa aberta, um estorno por lançamento, uma chave de lançamento, uma previsão de comissão por pedido, um fechamento aprovado por mês).
- **Período fechado**: com fechamento APROVADO, nenhum serviço aceita lançamento, conta ou recebimento **com data naquele mês**; fatos novos entram com a data atual. Reabrir exige perfil D07 e justificativa e preserva a versão anterior.
- **Saída condicionada à quitação**: com D05 = verdadeiro, só sai com `statusPagamento = PAGO` (parcial não basta); a Etapa 2 já bloqueava a saída enquanto D05 estivesse pendente.
- **Cancelamento**: venda com recebimento ativo não é cancelada por este caminho (primeiro estorna/restitui).
- **Restituição**: exige devolução física **recebida e avaliada**; valor até o do item devolvido (menos o já restituído); quem solicita não autoriza a própria; efetivar movimenta caixa/banco/recebíveis uma única vez; não é possível estornar um recebimento se o restante ficaria abaixo do já restituído.
- **Encomenda**: cada recebimento do fornecedor gera entrada de estoque e reserva progressiva até a quantidade vendida; o excedente fica como estoque livre; a saída ao cliente continua exigindo todos os itens reservados por completo ("não há entrega parcial").

## Decisões pendentes (explícitas) e valores de teste

Todas nascem **nulas** (V5) e aparecem como pendência da área `FINANCEIRO` na configuração comercial (somente o proprietário as altera: `PUT /api/v1/config/comercial/financeiro`). Nenhum valor é presumido.

| ID | O que decide | O que bloqueia enquanto pendente |
|---|---|---|
| **D01** | Percentual de comissão | Nenhuma comissão é calculada (nem zero, nem estimativa); gerar previsões é recusado (422). |
| **D02** | Momento de aquisição (`CONFIRMACAO`, `QUITACAO` ou `ENTREGA`) | A comissão permanece **prevista**; o pagamento de comissões fica bloqueado. A data de pagamento nunca é presumida: o proprietário informa o vencimento. |
| **D06** | Competência da receita (`CONFIRMACAO` ou `ENTREGA`) | O resultado do fechamento é apenas provisório (critério listado como faltante). |
| **D07** | Perfis que reabrem fechamento e que autorizam restituição | Reabrir e autorizar/efetivar restituição ficam bloqueados (422). |
| **D09** | Se há restituição/cobrança de diferença e se a meta desconta devoluções | Restituição e cobrança bloqueadas; meta com atingimento **provisório** (`PENDENTE_D09`). |
| **D10** | Se o fechamento exige ausência de pendências | A aprovação do fechamento fica bloqueada. |
| **D11** | Taxas do cartão por operadora/parcelas | Recebimento no cartão é recusado para operadora sem taxa cadastrada. |
| D03, D04, D05, D08 | Já tratadas nas Etapas 1 e 2 | Sem mudança. |

**Valores de TESTE** usados nos testes automatizados e nos roteiros (nunca recomendações de negócio): comissão 5% (e 8% para provar a regra histórica), aquisição por quitação, competência na confirmação, taxa de cartão 4% em 3x com 30/30 dias, perfis de reabertura = proprietário, perfis de restituição = proprietário e gerente, D09/D10 definidos só durante o teste. Os roteiros **restauram** a configuração original ao final.

**Resultado e lucro**: a prévia traz receita, restituições, taxas de cartão, despesas, comissões (e previstas) e custos conhecidos, mas **`lucroApurado` é nulo e `definitivo` é falso** enquanto houver itens sem custo ou critério de competência pendente; a resposta lista os `faltantes`. O cliente (tela) não calcula lucro.

## Estados

- **Recebimento**: `REGISTRADO` → `ESTORNADO`. **Pagamento da venda**: `PENDENTE`/`NAO_INFORMADO` → `PARCIAL` → `PAGO` (e de volta por estorno).
- **Recebível**: `PREVISTO` → `LIQUIDADO` (→ `PREVISTO` pelo estorno da liquidação) · `CANCELADO` (estorno do recebimento ou restituição).
- **Caixa**: `ABERTA` → `FECHADA`.
- **Conta**: `ABERTA` → `PAGA` (→ `ABERTA` por estorno) · `CANCELADA`. Eventos: criada, atualizada, paga, estornada, cancelada.
- **Restituição**: `SOLICITADA` → `AUTORIZADA` → `EFETIVADA` · `CANCELADA`.
- **Comissão**: `PREVISTA` → `DEVIDA` → `EM_CONTA` → `PAGA` (`EM_CONTA` volta se o pagamento é estornado) · `REVERTIDA` (previsão cancelada); lançamentos de reversão: `LANCADA` → `COMPENSADA`.
- **Fechamento**: `APROVADO` ⇄ `REABERTO`, com versões numeradas e snapshot da apuração.
- **Encomenda**: `AGUARDANDO_PEDIDO` → `PEDIDO_REALIZADO` → `PARCIALMENTE_RECEBIDA` → `RECEBIDA` (ou `CANCELADA`).

## API (resumo)

Todas as rotas financeiras são de **ADMIN e GERENTE**, exceto: `GET /financeiro/comissoes/minhas` e `GET /financeiro/metas/minha` (o vendedor lê só o que é dele); gerar previsões/pagamento de comissão, aprovar/reabrir fechamento e editar decisões financeiras são **só do proprietário**; metas são definidas por gerente ou proprietário.

| Rota | Função |
|---|---|
| `POST /vendas/{id}/recebimentos` · `POST /recebimentos/{id}/estornar` | Registrar e estornar pagamento do cliente (devolvem o detalhe da venda, que traz `pagamento` e `acoes.podeRegistrarRecebimento/podeEstornarRecebimento`). |
| `GET /financeiro/caixa/atual` · `POST …/abrir` · `…/movimentos` · `…/fechar` · `GET …/sessoes` | Caixa físico. |
| `GET /financeiro/lancamentos` | Livro de lançamentos (período, conta). |
| `GET/POST/PUT /financeiro/taxas-cartao` | Taxas por operadora (D11). |
| `GET /financeiro/recebiveis` · `POST …/{id}/liquidar` · `…/estornar-liquidacao` | Recebíveis de cartão. |
| `GET/POST/PUT /financeiro/contas` · `POST …/{id}/baixar` · `…/estornar` · `…/cancelar` | Contas a pagar e a receber. |
| `GET /financeiro/restituicoes` · `POST /ocorrencias/{id}/restituicoes` · `…/cobrar-diferenca` · `POST /restituicoes/{id}/autorizar` · `…/efetivar` · `…/cancelar` | Restituições e diferença de troca. |
| `GET /financeiro/comissoes` · `…/minhas` · `POST …/gerar-previsoes` · `POST …/pagamento` | Comissões. |
| `GET /financeiro/metas?mes=` · `…/minha` · `PUT /financeiro/metas` | Metas. |
| `GET /financeiro/fechamento/previa?mes=` · `POST …/aprovar` · `POST …/reabrir` | Fechamento mensal. |
| `PUT /config/comercial/financeiro` | Decisões D01, D02, D06, D07, D09, D10 (proprietário). |
| `POST /encomendas/{id}/receber` | Agora aceita quantidade parcial (não excede o saldo a receber). |

## Migração V5 e compatibilidade

- `V5__gestao_vendas_etapa3.sql` é **aditiva**: amplia CHECKs (`pedidos.status_pagamento` + `PARCIAL`, `encomendas.status` + `PARCIALMENTE_RECEBIDA`, tipos de auditoria), acrescenta colunas **nulas** em `configuracao_comercial` e cria as tabelas `encomenda_recebimentos`, `taxas_cartao`, `sessoes_caixa`, `recebimentos`, `recebiveis_cartao`, `contas_financeiras`, `conta_eventos`, `restituicoes`, `lancamentos_financeiros`, `comissoes`, `metas_vendedor` e `fechamentos_mensais`.
- **Nada é gerado para vendas antigas**: nenhum recebimento, lançamento ou comissão é inventado; o `statusPagamento` dos pedidos existentes não muda.
- **Rollback**: como a V5 só adiciona, o código da Etapa 2 continua funcionando sobre o esquema novo (as colunas novas são nulas e as tabelas ficam sem uso). Para voltar ao esquema anterior, restaurar o backup tirado antes da migração (não há *down migration*). Antes de aplicar em produção: backup + `scripts/ensaio-migracao-v3.sh` em cópia do banco real.
- O ensaio (`scripts/ensaio-migracao-v3.sh`) agora também confere a V5: tabelas novas vazias, decisões financeiras nulas, nenhum pedido antigo `PARCIAL`, estoque inalterado e os novos valores dos CHECKs.

## Validação executada (07/10/2026)

**Testes automatizados (H2): 92 passam** — `FinanceiroServiceTest` (34 novos), `AtendimentoServiceTest` (24, com a regra de encomendas revisada), `VendaServiceTest`, `SegurancaApiTest`, `PagamentoPropriedadeTest` e o contexto da aplicação. Cobrem, entre outros: dinheiro exige caixa; Pix não toca o caixa; cartão sem entrada até a liquidação; liquidação e estornos idempotentes; **recebimentos, estornos e baixas repetidos e simultâneos (duas threads)**; saída condicionada à quitação; cancelamento bloqueado com recebimento; comissão (D01/D02 pendentes, regra histórica, pagamento, reversão por cancelamento e por restituição, compensação); restituição (D09/D07, devolução física obrigatória, limites, caixa, cartão, cobrança de diferença); metas provisórias; prévia sem lucro definitivo; **bloqueio de lançamentos em período fechado**, D10 e reabertura (D07 + justificativa).

**PostgreSQL 15 descartável** (Flyway V1→V5 e Hibernate `ddl-auto: validate`; sem tocar produção):

- `api_etapa1.py` 118/118 · `api_etapa2.py` 154/154 · **`api_etapa3.py` 123/123** (recebimentos, caixa, cartão, concorrência, contas, comissões, restituição, metas e fechamento).

**Navegador (Chromium; computador 1366 px e celular 390 px)** — `ui_etapa3.py` 44/44 e `ui_fluxo.py` 68/68 (Etapas 1 e 2 sem regressão): decisões financeiras aparecem como pendentes (D01, D02, D09, D10); registrar recebimento na tela do pedido com **clique duplo gera um único recebimento**; estorno exige motivo; as sete abas do Financeiro abrem sem erro; a prévia do fechamento mostra resultado parcial/provisório, "Não apurado", o que falta, e **caixa físico e banco separados**; abrir e fechar o caixa (diferença exige motivo); comissão da venda aparece após definir a regra; o vendedor só vê "Minhas comissões" e "Minha meta" e não acessa o Financeiro; recebimento parcial de encomenda aceito pela tela, com a saída bloqueada até completar; sem rolagem horizontal nas telas financeiras, na configuração e no detalhe do pedido no celular. Teste em dispositivo físico não foi feito.
### Problemas encontrados e correções

| Problema | Onde apareceu | Correção |
|---|---|---|
| `snapshot` do fechamento mapeado como `varchar(255)` (a V5 usa `text`) | Teste de fechamento | `columnDefinition = "text"`. |
| Colunas `*_por_id` nas entidades × `*_por` na migração | **Somente no PostgreSQL** (`validate`); o H2 recriava o esquema e escondia o erro | Entidades alinhadas à convenção do projeto (`criada_por`…). |
| Estorno/efetivação simultâneos podiam falhar com `ObjectOptimisticLockingFailure` (a leitura prévia deixava a entidade em cache antes do bloqueio) | Teste de concorrência | A leitura prévia passou a buscar só o id do pedido; o bloqueio pessimista lê a linha atual. |
| Com encomenda parcialmente recebida, o detalhe da venda ainda indicava `podeRegistrarSaida = true` (o servidor recusava só ao clicar) | Navegador (`ui_fluxo`) | `bloqueioDaSaida` agora exige reserva ativa que **cubra a quantidade** do item; teste e roteiro cobrem o bloqueio já no detalhe. |
| Teste da Etapa 2 assumia "recebimento parcial recusado" | Suíte | Regra revisada (recebimento do fornecedor × entrega ao cliente); teste e roteiro reescritos. |
| Roteiro da Etapa 1 comparava as pendências do gerente com as do vendedor | Roteiro | Compara só as áreas de vendas/atendimento (as financeiras vêm à parte). |

## Limitações conhecidas

- **Cartão**: exige pagamento pelo valor total em um único recebimento. Restituição de cartão **reduz recebíveis previstos de trás para frente**; se o recebível já foi liquidado, a restituição sai pelo banco.
- **Quem é "usuário financeiro"** não foi definido: adotados ADMIN e GERENTE (o vendedor só vê a própria comissão e meta). Confirmar com a loja.
- **Crédito/haver** de diferença de troca e **reposição física automática** da troca não existem (a diferença a cobrar vira conta a receber; a reposição é nova venda/saída manual).
- **Relatórios gerenciais** (UC-23) e conciliação bancária não fazem parte desta etapa.
- Pagamentos pelo **Mercado Pago** (checkout legado) seguem o `PaymentService` com regras mais estritas (valor divergente é recusado, evento repetido não duplica, rejeição não cancela o pedido, pendente nunca rebaixa pago) — cobertas por teste de integração, sem simulação ponta a ponta. Vendas da gestão recebem pelo Financeiro.
- Nenhum custo de produto é cadastrado; por isso o lucro nunca é definitivo nesta etapa.

## Pendências da Etapa 3

- **Ensaio das migrações V3, V4 e V5 em cópia do banco de produção — PENDENTE** (sem dump/acesso; não houve conexão à produção). Rodar `scripts/ensaio-migracao-v3.sh` com o dump real e só depois fazer deploy.
- **Decisões abertas**: D01, D02, D06, D07, D09, D10, D11 (além das anteriores). Enquanto nulas, o sistema bloqueia apenas o que depende de cada uma.
- Confirmar com a loja: perfis financeiros (ADMIN/GERENTE), prazo/forma de pagamento das comissões, política de metas e devoluções, taxas reais das operadoras e critério de competência.
- Teste em dispositivo físico e outros navegadores não foi feito.
