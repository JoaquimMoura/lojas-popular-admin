# Gestão de vendas — revisão final

Documentos relacionados: [questionário para a loja](gestao-vendas-questionario-loja.md) · [homologação e implantação](gestao-vendas-homologacao.md) · [Etapa 1](gestao-vendas-etapa1.md) · [Etapa 2](gestao-vendas-etapa2.md) · [Etapa 3](gestao-vendas-etapa3.md).

## 1. Como o trabalho chegou à `master`

- A `feature/gestao-vendas` (Etapas 1 a 3) foi levada à `master` por **fast-forward** (`git merge --ff-only`), sem commit de merge: `48a67d3` → `20037a2`, e esse estado foi enviado ao `origin/master` a pedido do responsável. A branch `feature/gestao-vendas` continua em `20037a2`.
- Depois disso há **commits locais, ainda sem push** na `master`: custos e relatórios (backend e telas), a revisão final e, nesta rodada, a D12 e as correções abaixo.
- O trabalho anterior foi preservado: `git diff` dos commits locais contra `origin/master` só toca financeiro, custos, relatórios, o ponto de congelamento de custo na confirmação (`VendaService`), configuração comercial, a trava de exclusão de produto com custo, testes, roteiros e docs. Nada fora desse escopo.
- Fora do histórico: `.env.prod.example` (apagado) e `bb.example` (novo) continuam só no diretório de trabalho, por instrução do responsável.
- A **V6 ainda não foi aplicada em nenhum banco real** (só em bancos descartáveis), por isso a D12 entrou na própria V6 em vez de uma V7. Depois do primeiro deploy, qualquer mudança de esquema deve virar V7.

## 2. Achados da revisão

| # | Onde | Impacto | Correção |
|---|---|---|---|
| 1 | `CartaoService.salvarTaxa` | O gerente podia cadastrar taxas de cartão, contra a regra "só o proprietário" (taxa errada distorce todo o líquido e a previsão). | Somente ADMIN. |
| 2 | Todos os serviços financeiros (`exigirGestor`) | Gerente tinha **todas** as operações financeiras de forma implícita. | `PermissaoFinanceiraService` + D12: sem decisão o gerente não opera; operações separadas (ver seção 3). |
| 3 | `RecebimentoService.estornar` × `ComissaoService` | Com D02 = quitação, estornar o pagamento de uma venda cuja comissão já estava em pagamento/paga deixava comissão paga sem quitação. | Estorno bloqueado com instrução (estornar/cancelar antes o pagamento da comissão). |
| 4 | `ProdutoService.excluir` e remoção de variação | Produto/variação com custo cadastrado violava a FK (`custos_produto`) e viraria erro 500. | Recusa com mensagem clara (`CustoProdutoRepository.existsBy…`). |
| 5 | `RecebimentoService.painel` | O detalhe da venda mostrava o livro de lançamentos a quem não pode consultar o financeiro. | Lançamentos só para quem tem CONSULTAR. |
| 6 | `EncomendaService` (Javadoc) | Comentário dizia que o recebimento precisa cobrir a quantidade vendida. | Corrigido (recebimento parcial do fornecedor aceito). |
| 7 | `FechamentoService.resultado` | Taxas de cartão entram pelo mês do **pagamento**, a receita pelo mês da confirmação/entrega: pode haver defasagem. | **Limitação** (o resultado só é "definitivo" sem faltantes, mas essa defasagem não é um faltante). Tratar na D06. |
| 8 | `RelatorioService.vendas` | Vendas legadas (checkout online antigo) não entram: o relatório conta vendas confirmadas da gestão. | **Limitação** documentada. |
| 9 | Ocorrência de pós-venda | A ocorrência mostra as restituições da venda mesmo a gerente sem CONSULTAR. | **Limitação**: dado operacional do atendimento; não alterado. |
| 10 | `MetaService.definir` | Meta é gestão comercial, não está na D12 (gerente e proprietário definem). | Mantido; registrado. |

Verificado sem defeito: parcelas (a última absorve o arredondamento), estornos idempotentes e únicos, reversão de comissão por cancelamento e restituição, despesas sem dupla contagem de comissão (despesas = contas manuais; comissão à parte), congelamento de custo (imutável, nulo nunca vira zero), bloqueio de período fechado, V6 aditiva.

## 3. Permissões financeiras (D12)

**Enquanto não houver decisão do proprietário, o gerente não opera nenhuma operação financeira.** O proprietário sempre opera. Cada operação é decidida separadamente em Configuração comercial › Permissões financeiras (3 opções: pendente, somente o proprietário, proprietário e gerente).

| Operação | Cobre | Padrão do gerente |
|---|---|---|
| Consultar | caixa, contas, cartão, comissões, metas, prévia do fechamento, relatórios, custos, lançamentos do pedido | **Negado** |
| Receber | registrar recebimento, abrir/suprimento/fechar caixa, liquidar cartão, baixar conta a receber | **Negado** |
| Pagar | criar/alterar/cancelar conta, baixar conta a pagar, retirada de caixa | **Negado** |
| Estornar | estornar recebimento, liquidação de cartão e baixa de conta | **Negado** |
| Restituir | solicitar/autorizar/efetivar/cancelar restituição e cobrar diferença (além dos perfis da D07) | **Negado** |
| Sempre só o proprietário | aprovar fechamento, pagar comissões e gerar previsões, cadastrar custos e taxas de cartão, alterar decisões | — |

O servidor confere em cada serviço e nas consultas HTTP (`GET /financeiro/**`); a tela esconde o que o usuário não pode. A D12 aparece como pendência até as cinco operações serem decididas.

## 4. Casos de uso: implementado × validado × habilitado

- **Implementado**: existe no código.
- **Validado**: coberto por teste automatizado (T), roteiro de API (A) ou de navegador (N).
- **Habilitado para operação**: pode ser usado de fato na loja **depois** de decisões, configuração e dados iniciais. **Decisão pendente não equivale a fluxo concluído.**

| UC | Implementado | Validado | Habilitado para operar quando… |
|---|---|---|---|
| 01 Acesso | Sim | T A N | Sempre (limite de 5 usuários ativos). |
| 02 Cliente | Sim | A N | Sempre. |
| 03 Catálogo | Sim | N | Sempre; custos em Financeiro › Custos. |
| 04 Preços | Sim | T A | **D03 e D04** definidas e condições de pagamento cadastradas. |
| 05 Registrar venda | Sim | T N | Depois do UC-04. |
| 06 Aprovar desconto | Sim | T N | **D03** definida. |
| 07 Confirmar | Sim | T | Depois do UC-05 e **contagem inicial do estoque**. |
| 08 Encomenda | Sim | T A N | Sempre; prazo padrão (D08) só afeta o texto. |
| 09 Saída | Sim | T A N | **D05** definida e estoque contado. |
| 10 Inventário | Sim | T A | Sempre (é a contagem inicial). |
| 11 Acompanhar pedido | Sim | N | Sempre. |
| 12 Entrega | Sim | A N | Depois da saída (UC-09). Sem notificação ao cliente. |
| 13 Montagem | Sim | A N | Depois da entrega. |
| 14 Cancelar venda | Sim | T | **D07** (perfis de cancelamento). Com recebimento: estornar/restituir antes. |
| 15 Receber | Sim (parcial*) | T A N | **D12** (se for o gerente), caixa aberto para dinheiro, **D11** para cartão. *Sem anexo de comprovante. |
| 16 Cartão | Sim | T A | **D11** (taxas e prazos da operadora). |
| 17 Caixa | Sim | T A N | **D12** (se for o gerente). |
| 18 Contas | Sim | T A | **D12** (se for o gerente). |
| 19 Metas | Sim | T A | Metas definidas; atingimento definitivo só com **D09**. |
| 20 Comissão | Sim | T A | **D01** e **D02**; pagamento só pelo proprietário. |
| 21 Pós-venda | Sim (parcial*) | T A N | Restituição/diferença: **D09 + D07 + D12**. *Sem crédito de troca e sem reposição automática. |
| 22 Fechar mês | Sim (parcial*) | T A N | **D10 e D07**; resultado definitivo só com **D06**, D01/D02 e custos. *Defasagem das taxas (achado 7). |
| 23 Relatórios | Sim | T A N | **D12** consultar (gerente); margem só com custos cadastrados. |

Em resumo: o que **pode operar hoje, só com o proprietário e sem decisões**: usuários, clientes, catálogo, encomendas, inventário e acompanhamento. Vender, receber, pagar comissão, restituir e fechar o mês **dependem das decisões do questionário**.

## 5. Limitações explícitas

- **Comprovantes**: o recebimento guarda referência/observação em texto; **não há upload de comprovante** de pagamento (só de entrega e montagem).
- **Trocas**: sem crédito/haver de troca e sem reposição física automática. A conclusão é manual, com rastreio (a diferença vira cobrança ou restituição; a reposição é uma nova venda citando a ocorrência, e a ocorrência registra a solução).
- **Resultado**: o lucro nunca é apresentado como definitivo enquanto houver faltantes (D06, D01/D02, itens sem custo, comissões só previstas); margem só em grupos com todos os itens com custo e sempre como "margem bruta dos itens". Taxas de cartão entram pelo mês do pagamento (achado 7).
- **Mercado Pago**: o checkout online legado segue o `PaymentService` endurecido (valor divergente recusado, evento repetido sem efeito, rejeição não cancela). Está coberto por teste de integração, **sem validação ponta a ponta com o gateway**. Vendas da gestão recebem pelo Financeiro.
- Sem validação em dispositivo físico e outros navegadores; sem notificações; sem conciliação bancária; relatórios sem gráficos/PDF.

## 6. Cartão e parcelamento

"Cartão pelo valor total" **permite parcelamento na operadora**: a venda define as parcelas (ex.: 3x) e o recebimento é um só, pelo total, gerando um recebível por parcela. Não existe dividir a venda em dois cartões, pagar parte no cartão ou misturar formas.

## 7. Custos

Registro imutável por produto/variação; custo vigente congelado no item na **confirmação**; sem custo o item fica **nulo** (nunca zero); itens vendidos sem custo podem receber o custo **uma vez**, pelo proprietário, com motivo e auditoria. O catálogo também impede excluir produto/variação que tenha custo.

## 8. Validação

Ver a tabela no fim de [gestao-vendas-homologacao.md](gestao-vendas-homologacao.md) (resultados por tipo de teste, atualizados a cada rodada).
