# Gestão de vendas — Etapa 1 (venda com reserva)

Base: `master` em 48a67d3, branch `feature/gestao-vendas`. Backend Java 21 / Spring Boot 3.5.6, frontend React 19 / Vite.
Este documento descreve o que foi implementado na Etapa 1, como usar, o que ficou bloqueado por decisão pendente e como validar.

## Comparação com a base

| Requisito | Base (48a67d3) | Etapa 1 |
|---|---|---|
| Cliente x vendedor | `Pedido.usuario` único | `Pedido.cliente` e `Pedido.vendedor`; `usuario` = quem registrou |
| Perfis | ADMIN, CLIENTE, VENDEDOR | + GERENTE. ADMIN = Proprietário |
| Itens | só `Produto` | + variação, SKU/descrição históricos, preço base, modalidade, custo (nulo) |
| Frete | R$ 50 abaixo de R$ 1.000 | sempre R$ 0 em vendas novas; vendas antigas mantêm o gravado |
| Reserva de estoque | inexistente | criada atomicamente na confirmação (pronta entrega) |
| Status | `CRIADO/PAGO/CANCELADO/ENTREGUE` | status comercial, pagamento, entrega e montagem separados; `status` legado mantido |
| Troca de status | qualquer valor por qualquer vendedor/admin | vendas novas só por confirmar/cancelar; legado continua como era |
| Variações no cadastro | recriadas a cada edição do produto | atualizadas no lugar (por id/SKU) — necessário para não quebrar vínculos com vendas |

## O que foi entregue

- **UC-01** usuários (`/gestao/usuarios`, só proprietário): criar, alterar perfil, ativar/desativar, redefinir senha; limite de 5 usuários operacionais ativos (`app.max-usuarios-ativos`); a loja não fica sem proprietário; desativado não faz login nem usa token já emitido; vendas preservadas.
- **UC-02** clientes (`/gestao/clientes`): busca antes de cadastrar, endereços, CPF validado e único, alerta de telefone/nome repetido (confirmável).
- **UC-03** catálogo: modalidade (pronta entrega/encomenda/ambas) e prazo de encomenda; GERENTE passa a gerir produtos/categorias.
- **UC-04** configuração comercial (`/gestao/config-comercial`): condições por forma/parcelas, limite de desconto, arredondamento, perfis que cancelam.
- **UC-05/06/07** Nova venda, desconto com aprovação e confirmação com reserva, com idempotência.
- **UC-11** detalhe do pedido numa tela (itens, pagamento, reserva, entrega, montagem, descontos, histórico).
- **UC-14** cancelamento com motivo, libera a reserva.
- Estoque (`/gestao/estoque`): físico, reservado e disponível por unidade, com alertas de cadastro inconsistente.

## Regras implementadas

- **Preço**: calculado só no servidor, `BigDecimal`, `preço base × (100 + ajuste%) / 100`, escala 2, arredondamento configurado. Preço base = produto + adicional da variação. Preço, ajuste e condição ficam gravados no pedido; mudar a tabela não altera vendas existentes. Se o catálogo ou a condição mudarem entre registrar e confirmar, a confirmação é recusada até "Recalcular preços".
- **Desconto**: em R$ sobre o subtotal. Até o limite configurado segue; acima gera solicitação e a venda fica *Aguardando aprovação*. Qualquer alteração de valores invalida aprovação anterior. Aprova/rejeita: gerente ou proprietário; gerente não aprova o próprio desconto (proprietário pode).
- **Estoque (saldo autoritativo)**: produto com variações → vale o saldo da variação (`Produto.estoque` é só informativo); produto sem variações → vale o do produto. Variação sem saldo informado não pode ser vendida. Disponível = físico − reservas ativas. Reserva não baixa o físico (a baixa é da Etapa 2).
- **Concorrência**: confirmação trava o pedido e as unidades de estoque (lock pessimista, ordem determinística) numa única transação; se qualquer item faltar, nada é gravado. Índice único parcial impede duas reservas ativas para o mesmo item.
- **Idempotência**: registro (`chaveIdempotencia` no corpo, única) e confirmação (cabeçalho `Idempotency-Key`; mesma chave devolve a mesma venda, outra chave em venda já confirmada é recusada).
- **Escopo**: vendedor só enxerga/opera vendas em que é o responsável; gerente e proprietário veem todas. Vendedor só registra em seu nome.
- **Auditoria**: eventos de usuário, cliente, configuração, venda, desconto e reserva, com `entidade/entidade_id` para a linha do tempo.
- **Pagamento rejeitado** em venda do novo fluxo não cancela a venda (cancelar é decisão comercial com liberação de reserva); o fluxo de pagamento completo é da Etapa 3.

## Decisões pendentes e comportamento até a definição

Nada abaixo recebe valor padrão; os campos nascem vazios e a tela mostra a pendência.

| ID | Decisão | Onde se configura | Enquanto pendente |
|---|---|---|---|
| D03 | Limite de desconto | Configuração comercial | Nenhum desconto pode ser concedido |
| D04 | Arredondamento; condições/parcelas/ajustes | Configuração comercial | Não é possível registrar vendas; só condições cadastradas são oferecidas |
| D07 | Perfis que cancelam | Configuração comercial (só proprietário altera) | Cancelamento bloqueado para todos |
| D05 | Pagamento exigido p/ confirmar e p/ expedir | — | Confirmar não exige pagamento (fica *Pendente*); a expedição (Etapa 2) deve ficar bloqueada até a regra |
| D08 | Prazo de encomenda e atributos de variação | Cadastro do produto | Prazo vazio; variação usa cor/tamanho |
| D01, D02, D06, D09, D10 | Comissão, custos, política de troca/devolução, fechamento | — | Fora do escopo da Etapa 1 (nada apurado) |

Também bloqueado por decisão: cancelar venda **já paga** (restituição depende de D07/D09) e venda **já expedida** (devolução é da Etapa 2).

## Migração e compatibilidade

`V3__gestao_vendas_etapa1.sql`, aditiva; V1 e V2 não foram alteradas.

- Pedidos antigos: `PAGO` → pagamento `PAGO`; `ENTREGUE` → entrega `ENTREGUE` (sem presumir quitação); `CANCELADO` → `CANCELADA`; `CRIADO` → `revisao_legado = true`. Ficam como `LEGADO`, sem cliente, vendedor, reserva ou comissão. Frete e preços históricos não são recalculados.
- Colunas novas têm `DEFAULT`/são nulas, então o código anterior (48a67d3) continua funcionando sobre o schema V3: **rollback de código é compatível, a migração não deve ser revertida**.
- Constraints de `user_roles` e `auditoria_evento.tipo` foram ampliadas (conjunto superior ao anterior).
- Perfil `GERENTE` não existe nos usuários atuais: crie por **Usuários**.
- Antes de produção: testar em cópia do banco real, preservar banco e `uploads/`. Variações antigas com `estoque` nulo em produtos com variações aparecem como alerta em **Estoque** e não podem ser vendidas até informar o saldo.

## Validação executada (07/10/2026)

Ambiente do ensaio: PostgreSQL **15.19** (mesma série do container de produção, `postgres:15`) com V1 aplicada, pedidos legados em todos os status e migrations V2/V3 aplicadas pelo próprio app (Flyway, `baseline-on-migrate`, `ddl-auto: validate`); backend Spring em modo de produção; frontend em **build de produção** (`VITE_API_BASE_URL=/api/v1`) atrás de um proxy local que imita o Traefik (`/api` e `/uploads` → backend, resto → SPA); Chromium headless via Playwright em **1366×900 (computador)** e **390×844 (celular)**.

| Verificação | Resultado |
|---|---|
| Testes automatizados (`mvnw test`), 30 testes | OK no H2 **e** no PostgreSQL 15 com schema do Flyway |
| Fluxo no navegador, computador e celular (config → cliente → venda → desconto → aprovação → confirmação com clique duplo → reserva → cancelamento com liberação) | 29/29 OK (+ 5 da configuração inicial) |
| Contratos e permissões por API (proprietário, gerente, vendedor, cliente, anônimo) | 32/32 OK |
| Escopo entre vendedores, idempotência, bloqueios D03/D04/D07 isolados, regressão do catálogo | 27/27 OK |
| Interface com D03/D04/D07 pendentes, desativação de usuário, limite de 5, vitrine pública | 22/22 OK |
| Ensaio da V3 (`scripts/ensaio-migracao-v3.sh`, PostgreSQL 15.19, 200 pedidos sintéticos) | OK: contagens, somas de total/frete e mapa de conversão preservados |
| `npm run build` | OK |
| `npm run lint` | 6 erros, todos anteriores à etapa (ver abaixo) |

Os roteiros de navegador e de API (Playwright e chamadas HTTP) foram incorporados ao repositório na Etapa 2, em `scripts/validacao/` (ver o README: ambiente PostgreSQL 15 descartável, configuração por variáveis `LP_*`, sem credenciais). Além deles, a regressão automatizada inclui os testes Java e o script de ensaio da migração.

O que foi exercitado:

- **Fluxo no navegador**: pendências visíveis e removidas após configurar; venda com desconto de 15% (limite 10%) fica *Aguardando aprovação* e a confirmação aparece bloqueada com explicação; vendedor não vê aprovar; gerente aprova com motivo; **clique duplo em Confirmar gera uma única reserva**; a reserva aparece no pedido e em Estoque (reservado/disponível); vendedor não cancela (perfil não autorizado, com texto); gerente cancela com motivo obrigatório; a reserva fica *Liberada* e o disponível volta ao valor inicial. No celular: sem rolagem horizontal em detalhe, estoque e lista de pedidos (cards).
- **D03/D04/D07 bloqueiam só o que dependem**, na API (422 `CONFIGURACAO_PENDENTE`) e na interface:
  - D03 pendente: desconto bloqueado (campo desabilitado com a explicação); vender sem desconto, confirmar e cancelar seguem liberados.
  - D04 pendente (arredondamento **ou** nenhuma condição ativa): registrar venda bloqueado; consultas, estoque, clientes e cancelamento de vendas já existentes seguem liberados. Forma/parcela não cadastrada não é oferecida.
  - D07 pendente: só o cancelamento bloqueia (botão desabilitado com o motivo); vender e confirmar seguem liberados.
  - Os caminhos liberados foram exercitados com configuração de teste explícita (limite 10%, arredondamento "meio para cima", Pix 1x 0%, Cartão 3x +10%, cancelam Proprietário e Gerente). **Esses valores são só de teste, não recomendação**; nenhum valor foi preenchido em banco real.
- **Permissões**: proprietário (usuários, configuração, tudo); gerente (configura preços e limite, aprova desconto, cancela se autorizado; não altera quem cancela nem gere usuários); vendedor (só as próprias vendas, não aprova desconto, não vê configuração nem usuários, não vende em nome de outro); perfil CLIENTE sem acesso à área de gestão; anônimo é levado ao login. Usuário desativado perde o login e o token já emitido; o 6º usuário ativo é recusado com mensagem.
- **Vitrine pública**: produtos e categorias, imagens de `/uploads` (carregam), detalhe do produto, carrinho e link *Finalizar pelo WhatsApp* com a mensagem do pedido; **o carrinho não grava venda**; nenhuma resposta 4xx/5xx da API nas páginas públicas. Login funcionou para todos os perfis.

### Problemas encontrados e correção

| # | Problema | Origem | Correção |
|---|---|---|---|
| 1 | Usuário autenticado **sem o perfil** recebia **401** (e não 403) nas rotas protegidas por URL, porque o 403 vira um redirecionamento interno a `/error`, que exigia login. O `api.js` do frontend trata 401 como sessão expirada e **deslogava** o usuário. | **Anterior à etapa** (configuração de segurança existente); ficou evidente ao testar permissões | `dispatcherTypeMatchers(ERROR).permitAll()` em `SecurityConfig`; teste `SegurancaApiTest` |
| 2 | CORS permitia só `Authorization, Content-Type, X-Requested-With, Accept, Origin`: o cabeçalho `Idempotency-Key` da confirmação seria bloqueado em acesso entre origens (ambiente de desenvolvimento 5173→8080). Em produção a origem é a mesma (Traefik), então não afetaria. | **Introduzido pela etapa** (novo cabeçalho) | `Idempotency-Key` adicionado em `CorsConfig`; teste de preflight |
| 3 | A edição do produto recriava as variações, perdendo os vínculos com vendas e reservas | Anterior (comportamento existente), agravado pela etapa | Atualização no lugar por id/SKU; coberto também por API: com id, sem id, remoção de variação vendida, exclusão de produto vendido |
| 4 | Edições feitas durante a implementação trocaram CRLF por LF em 16 arquivos (diff inflado) | Ferramental desta sessão | Quebras de linha originais restauradas antes do commit |

Nenhuma **regressão** de funcionalidade existente foi encontrada: vitrine, imagens, login, carrinho e WhatsApp se comportaram como antes.

**Erros de lint anteriores à etapa (não corrigidos):** `ImageCropModal.jsx:41` (`dpr` não usado), `ProductGalleryModal.jsx:2` (`useMemo` não usado), `AuthContext.jsx:59`, `CartContext.jsx:50` e `routes.jsx:31` (`react-refresh/only-export-components`; o de `routes.jsx` já existia, pois o arquivo exporta `router` junto de `RootLayout`) e `ProductDetails.jsx:6` (`buildWhatsAppUrl` não usado). Nenhum arquivo novo tem erro de lint.

## Ensaio da V3 em cópia da produção — PENDENTE

**Não foi possível** ensaiar sobre uma cópia do banco de produção: não há acesso à VPS (`srv1817871.hstgr.cloud`) nem dump disponível nesta sessão, e conectar à produção não foi autorizado. O ensaio acima usou dados **sintéticos** em PostgreSQL 15.19 e **não substitui** a verificação real: dados de produção podem ter combinações não previstas (por exemplo, nulos em colunas de totais, usuários com perfis inesperados, variações sem saldo).

Para fechar a pendência (somente leitura na produção):

1. Na VPS: `docker exec lojas-postgres-prod pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc > lojas.dump`. O `DEPLOY.md` registra que **não há backup automático**: guarde esse dump também como backup antes do deploy.
2. Numa máquina com Docker, rode `scripts/ensaio-migracao-v3.sh lojas.dump`. O script sobe um `postgres:15` descartável, restaura, aplica a V3 com o Flyway, confere contagens, somas de total/frete, mapa de conversão e ausência de cliente/vendedor/reserva em pedidos antigos, e lista variações sem saldo.
3. Registre aqui o resultado. Só então faça o deploy.

## Observações de interface (não bloqueiam)

- O telefone do cliente aparece só com dígitos (ex.: `11988887777`) no detalhe do pedido.
- No celular, a barra de abas de `/gestao` rola na horizontal e esconde as últimas abas (Produtos, Categorias…); é intencional, mas vale conferir com os usuários.

## Como usar (resumo)

1. Proprietário entra, abre **Configuração comercial** e define arredondamento, condições de pagamento, limite de desconto e quem cancela (a tela lista as pendências).
2. Proprietário cria o gerente e os vendedores em **Usuários** (até 5 ativos).
3. Em **Produtos**, confira variações e o estoque **por variação**; veja **Estoque** para alertas.
4. Vendedor: **Clientes** (buscar/cadastrar) → **Nova venda** → salvar → no detalhe, **Confirmar**. Desconto acima do limite espera aprovação do gerente.
5. A reserva aparece no detalhe do pedido e em **Estoque**; **Cancelar** libera.

## Pendências desta etapa

- **Ensaio da V3 em cópia do banco de produção**: pendente (seção própria acima), por falta de acesso e de dump.
- Notificações do novo fluxo (cliente/vendedor) não foram criadas; quando forem, devem ser disparadas após o commit.
- `PedidoController` legado (`GET /pedidos/gerenciar`) mapeia itens fora de transação e pode falhar por lazy loading; a tela Pedidos o substitui. Não foi alterado além de permissões.
- Desconto por item e custo histórico (o campo existe, sem origem de dados) ficam para etapas seguintes.
- `PaymentService`: só foi acrescentada a verificação de propriedade do pedido e a recusa de pedido cancelado/pago; varredura de todos os pedidos e validação de valor/vínculo/repetição de eventos seguem para a Etapa 3.
- Cenários da Etapa 3 **não testados** por ainda não existirem: pagamento repetido, reversão de comissão, bloqueio/reabertura mensal.
- Não houve teste em dispositivo físico nem em navegadores além do Chromium; o celular foi emulado por tamanho de tela e toque.
