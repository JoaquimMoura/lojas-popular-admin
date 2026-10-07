# Gestão de vendas — revisão final do escopo

Compara os casos de uso do plano (UC-01 a UC-23) com o que está no código. Evidência = teste automatizado (T), roteiro de API (A), roteiro de navegador (N). Números e datas em "Validação".

## 1. Casos de uso

| UC | Situação | Evidência | Limitações |
|---|---|---|---|
| 01 Acesso | Completo | `SegurancaApiTest` (T), `api_etapa1` (A), `ui_fluxo` (N) | Limite de 5 usuários ativos (regra existente). |
| 02 Cliente | Completo | `api_etapa1`, `ui_fluxo` | — |
| 03 Catálogo | Completo | `ui_fluxo` (variações preservadas, vitrine) | Custo é cadastrado na aba Financeiro › Custos, não no formulário do produto. |
| 04 Preços | Completo | `VendaServiceTest`, `api_etapa1` | D03/D04 pendentes bloqueiam a venda até a loja definir. |
| 05 Registrar venda | Completo | `VendaServiceTest`, `ui_fluxo` | — |
| 06 Aprovar desconto | Completo | `VendaServiceTest`, `ui_fluxo` | Depende de D03. |
| 07 Confirmar | Completo | `VendaServiceTest` (clique repetido, concorrência) | — |
| 08 Encomenda | Completo | `AtendimentoServiceTest`, `api_etapa2`, `ui_fluxo` | Prazo padrão (D08) pendente. Recebimento parcial do fornecedor aceito; sem envio ao fornecedor. |
| 09 Saída | Completo | `AtendimentoServiceTest` (baixa única, concorrência) | D05 pendente bloqueia a saída; sem entrega parcial. |
| 10 Inventário | Completo | `AtendimentoServiceTest`, `api_etapa2` | Variação sem saldo exige a primeira contagem. |
| 11 Acompanhar pedido | Completo | `ui_fluxo` | — |
| 12 Entrega | Completo | `api_etapa2`, `ui_fluxo` | Sem notificação ao cliente. |
| 13 Montagem | Completo | `api_etapa2`, `ui_fluxo` | Equipe/responsável em texto livre. |
| 14 Cancelar venda | Completo, com dependências | `VendaServiceTest`, `FinanceiroServiceTest` | Perfis autorizados dependem de D07. Com recebimento ativo, primeiro estornar/restituir. Reversão de comissão automática. |
| 15 Receber | Parcial | `FinanceiroServiceTest`, `api_etapa3`, `ui_etapa3` | Evidência é referência/observação em texto: **não há anexo de comprovante**. Cartão: um recebimento pelo total. |
| 16 Cartão | Completo | `FinanceiroServiceTest`, `api_etapa3` | Sem taxa cadastrada (D11) o cartão é recusado. Sem conciliação bancária. |
| 17 Caixa | Completo | `FinanceiroServiceTest`, `api_etapa3`, `ui_etapa3` | Um caixa físico por vez. |
| 18 Contas | Completo | `FinanceiroServiceTest`, `api_etapa3` | Sem recorrência nem anexo. |
| 19 Metas | Completo, atingimento provisório sem D09 | `FinanceiroServiceTest`, `api_etapa3` | Política de devoluções (D09) pendente. |
| 20 Comissão | Completo, bloqueado por D01/D02 | `FinanceiroServiceTest`, `api_etapa3` | Sem D01 nada é calculado; sem D02 fica só prevista. |
| 21 Pós-venda | Parcial | `AtendimentoServiceTest`, `FinanceiroServiceTest` | Restituição e diferença só com D09/D07. **Sem crédito de troca e sem reposição automática** (ver seção 5). |
| 22 Fechar mês | Parcial | `FinanceiroServiceTest`, `api_etapa3` | Prévia, pendências, versões, bloqueio e reabertura prontos. **Resultado definitivo bloqueado** por D06, D01/D02 e itens sem custo. Despesas = contas pagas lançadas. |
| 23 Relatórios | Completo no escopo pedido | `CustoRelatorioTest`, `api_etapa3`, `ui_etapa3` | Vendas por vendedor/canal/dia/mês, recebimentos, contas pendentes, estoque, entregas, comissões e metas. Sem gráficos/PDF; CSV só em Vendas e Recebimentos. Vendas não filtram por produto/cliente. |

## 2. Custos e resultado (V6)

- `custos_produto` guarda cada custo como registro **imutável** (produto ou variação, vigência, motivo, usuário). A variação herda o custo do produto se não tiver o seu.
- Na **confirmação** o custo vigente é congelado no item (`custoUnitario`, origem `CATALOGO`). Mudar o custo depois **não altera** vendas antigas.
- Sem custo cadastrado o item fica **nulo** — nunca zero. Vendas antigas e itens sem custo aparecem em "Itens vendidos sem custo"; o proprietário pode informar o custo **uma vez**, com motivo, auditoria e período aberto (origem `MANUAL`).
- Fechamento e relatórios só mostram margem quando **todos** os itens do grupo têm custo, e a chamam de "margem bruta dos itens" (antes de despesas, taxas e comissões), nunca de lucro. O lucro apurado continua nulo enquanto houver faltantes (D06, D01/D02, custos).
- A **regra de apuração** (competência por confirmação ou entrega) segue na D06; os relatórios de vendas usam a confirmação e dizem isso no aviso.
- Cadastrar/informar custo: só o proprietário. Consultar: gerente e proprietário.

## 3. Decisões abertas (para o responsável da loja responder)

D01–D10 vêm do plano original. **D11 e D12 foram criadas na implementação**, porque o plano não previa taxas de operadora nem a matriz de permissões financeiras.

| ID | Pergunta | Exemplo de resposta | O que bloqueia enquanto aberta |
|---|---|---|---|
| D01 | Qual o percentual de comissão sobre o total cobrado? | "5% para todos os vendedores" | Nenhuma comissão é calculada. |
| D02 | Quando a comissão vira devida? | "Quando a venda estiver 100% paga" (quitação) / na confirmação / na entrega | Comissão fica só prevista; pagamento de comissão bloqueado. |
| D03 | Limite de desconto do vendedor? | "Até 10%" | Desconto em vendas. |
| D04 | Arredondamento dos preços e condições de pagamento (Pix, cartão 3x etc.)? | "Arredondar para centavos; Pix -5%, cartão 3x +10%" | Registro de vendas. |
| D05 | A saída exige pagamento quitado? | "Sim, só sai pago" / "Não" | Saída de mercadoria. |
| D06 | A receita pertence ao mês da confirmação ou da entrega? | "Mês da entrega" | Resultado definitivo (só provisório). |
| D07 | Quem cancela venda, autoriza restituição e reabre mês fechado? | "Cancelar: gerente e proprietário; reabrir: só o proprietário" | Cancelamento, restituição e reabertura. |
| D08 | Prazo padrão de encomenda e atributos de variação? | "Encomenda: 30 dias; variação: cor e tamanho" | Apenas exibição do prazo ("A definir"). |
| D09 | Há restituição ao cliente e cobrança de diferença de troca? A meta desconta devoluções? | "Restitui em até 7 dias; cobra a diferença; meta desconta devoluções" | Restituições e cobrança de diferença; meta fica provisória. |
| D10 | O fechamento mensal exige zero pendências? | "Sim: caixa fechado e sem conta vencida" | Aprovação do fechamento (a prévia segue). |
| **D11** | Quais são a **taxa e os prazos de cada operadora de cartão** por número de parcelas? | "Operadora X, 3x: 4% de taxa, 1ª parcela em 30 dias, demais a cada 30" | Só **recebimento em cartão** (e a agenda de recebíveis). Dinheiro e Pix não dependem. |
| **D12** | A matriz de permissões financeiras (seção 4) está aprovada? | "Aprovada" ou "gerente não estorna" | Nada: a proposta (gerente e proprietário) está em vigor. |

D11 tem origem no UC-16 (cartão com valores brutos, taxas e líquidos): sem a taxa e o prazo informados pela loja o sistema não inventa líquido nem previsão. O cadastro fica em Financeiro › Cartão › Taxas; vendas já registradas mantêm o plano gravado.

## 4. Permissões financeiras (proposta D12)

**ADMIN e GERENTE são uma proposta pendente de aprovação.** Estado atual no código e a separação que a loja pode pedir:

| Operação | Hoje | Observação |
|---|---|---|
| Consultar (caixa, contas, cartão, relatórios, custos) | ADMIN, GERENTE | Vendedor só vê a própria comissão e meta. |
| Receber (recebimento, caixa, liquidar cartão) | ADMIN, GERENTE | Uma única verificação central (`VendaAcesso.exigirGestor`), fácil de separar. |
| Pagar (baixar conta) | ADMIN, GERENTE | — |
| Estornar (recebimento, liquidação, baixa) | ADMIN, GERENTE | Sempre com motivo e rastro. |
| Restituir | Perfis de D07 (padrão de teste: ADMIN, GERENTE); quem solicita não autoriza | Configurável pelo proprietário. |
| Fechar período (aprovar) | **Só ADMIN** | Fixo, conforme o plano. |
| Reabrir período | Perfis de D07, com justificativa | Configurável. |
| Pagar comissões, gerar previsões, decisões financeiras, cadastrar custos, taxas de cartão | **Só ADMIN** | — |

Para separar "receber" de "pagar/estornar" basta decidir D12; o ponto de controle é único.

## 5. Cartão, parcelamento e trocas

- **"Cartão pelo valor total" permite parcelamento na operadora.** A venda define as parcelas (ex.: 3x); o recebimento é **um só, pelo total**, e gera N recebíveis (bruto, taxa, líquido, data prevista). O que não existe é dividir a venda em dois cartões, pagar parte no cartão ou misturar formas (uma forma por venda).
- **Crédito de troca e reposição automática estão pendentes** (dependem de D09). Para concluir uma troca **manualmente, com rastreio**:
  1. Abrir a ocorrência de troca no pedido original e **receber a devolução física** (condição APTA/NÃO APTA).
  2. Diferença a favor da loja: **Cobrar diferença** (conta a receber ligada à ocorrência; exige D09) e baixar. Diferença a favor do cliente: **Restituição** (solicitar → autorizar por outra pessoa → efetivar), ou nenhuma movimentação se a loja preferir compensar na nova venda.
  3. Reposição: registrar uma **nova venda** do produto novo, escrevendo na observação "Troca da ocorrência #N / pedido #M".
  4. Resolver a ocorrência informando a solução ("Reposição na venda #X; diferença na conta #Y"). Tudo fica na auditoria e no histórico da ocorrência.

## 6. Validação final (07/10/2026)

Resultados **separados** por tipo:

| Tipo | Ambiente | Resultado |
|---|---|---|
| Suíte JUnit | H2 (perfil `test`) | **100 testes passam** |
| Suíte JUnit | **PostgreSQL 15**, esquema só das migrações V1→V6, Hibernate `validate` (`scripts/validacao/suite-pg.sh`) | **100 testes passam** |
| Roteiros de API | PostgreSQL 15 | `api_etapa1` 118/118 · `api_etapa2` 154/154 · `api_etapa3` 142/142 |
| Roteiros de navegador | PostgreSQL 15, Chromium 1366 px e 390 px | `ui_etapa3` 50/50 · `ui_fluxo` 68/68 |
| Frontend | — | `npm run build` OK; `npm run lint` com os **mesmos 6 erros anteriores** (ImageCropModal, ProductGalleryModal, AuthContext, CartContext, ProductDetails, routes.jsx:43), nenhum novo |

Reconciliação da contagem de testes: 57 antes da Etapa 3 (23 + 9 + 22 + 2 + 1 de contexto) + 1 teste a mais em `AtendimentoServiceTest` (recebimento além do vendido) + 34 de `FinanceiroServiceTest` = **92**; + 8 de `CustoRelatorioTest` = **100**.

Não coberto: dispositivo físico e outros navegadores; Mercado Pago ponta a ponta (regras cobertas por teste de integração).

## 7. Condições para produção

- **Ensaio das migrações V3, V4, V5 e V6 na cópia do banco real: PENDENTE e obrigatório** (`scripts/ensaio-migracao-v3.sh`, que já confere as quatro). Sem push/deploy até lá.
- Respostas da loja às decisões abertas (seção 3), no mínimo D05, D03/D04 e D11 para começar a vender e receber.
- Contagem física do estoque e cadastro de custos antes de usar relatórios de margem.
