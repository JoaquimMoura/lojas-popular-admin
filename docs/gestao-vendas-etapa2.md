# Gestão de vendas — Etapa 2 (atendimento e pós-venda)

Continuação de [gestao-vendas-etapa1.md](gestao-vendas-etapa1.md), na mesma branch (`feature/gestao-vendas`).
A Etapa 1 está concluída; o **ensaio da V3 em cópia do banco de produção segue pendente** (ver o documento da Etapa 1) e agora vale também para a **V4**.

## O que foi entregue

| Área | Entrega |
|---|---|
| Encomendas (UC-08) | Acompanhamento por item (aguardando pedido → pedido realizado → recebida), com fornecedor, referência e previsão; recebimento físico gera **entrada de estoque vinculada à encomenda** e, em seguida, a **reserva do item**. Nada é enviado ao fornecedor. |
| Movimentações e inventário (UC-10) | Trilha imutável de movimentações (entrada de encomenda, saída de venda, ajuste de inventário, entrada de devolução) com saldo anterior/posterior, usuário e motivo; contagem de inventário com motivo obrigatório, que respeita o que já está reservado. |
| Saída com baixa (UC-09) | A saída converte as reservas em baixa física, **uma única vez**, tudo ou nada, sem saldo negativo e sem entrega parcial. |
| Entrega e retirada (UC-12) | Agenda (data, período, equipe), saída, tentativa frustrada, reagendamento e conclusão com comprovação (nome de quem recebeu e/ou foto/PDF). |
| Montagem (UC-13) | Inclusa e sem cobrança: agenda, responsável, conclusão com evidência ou marcação "não necessária" com motivo. |
| Pós-venda (UC-21) | Assistência, troca e devolução, com evidências, avaliação física da devolução e cálculo **informativo** da diferença de troca. |
| Agenda | Visão de entregas/retiradas e montagens por período. |
| Acompanhamento | A tela do pedido (a mesma da Etapa 1) ganhou entrega, montagem, encomendas, ocorrências e movimentações. |

## Regras fixas (não configuráveis)

- **Frete gratuito**: não existe valor de frete na entrega; a resposta traz `frete: "Gratuito"`.
- **Montagem inclusa**: não existe preço de montagem; a resposta traz "Inclusa, sem cobrança adicional". Nenhum total do pedido muda por entrega ou montagem.
- **Sem entrega parcial**: há **uma entrega por pedido**, cobrindo todos os itens. A saída exige que **todos** os itens tenham reserva ativa integral; se um item é encomenda ainda não recebida, a saída é recusada e **nada é baixado**. > **Revisado na Etapa 3**: a proibição vale para a **entrega ao cliente**. O **recebimento parcial do fornecedor é aceito** (entrada de estoque e reserva progressivas); a saída continua só quando todos os itens estão reservados por completo. Ver [gestao-vendas-etapa3.md](gestao-vendas-etapa3.md).
- **Baixa física na saída** (e não na confirmação): a confirmação continua só reservando.

## Estados

```
Entrega (pedido.statusEntrega)
  NAO_AGENDADA --agendar--> AGENDADA --saída--> SAIU --concluir (comprovação)--> ENTREGUE
                              ^   |                |
                              |   |                +--tentativa frustrada--> TENTATIVA_FRUSTRADA
                              |   +--reagendar (motivo)--> AGENDADA              |
                              +----------------- reagendar (motivo) -------------+
  (a baixa de estoque acontece na 1ª saída; saídas seguintes ao reagendar NÃO baixam de novo)
  Cancelar a venda: permitido até a 1ª saída; depois exige devolução (pós-venda).

Montagem:  NAO_AGENDADA --agendar--> AGENDADA --concluir (após ENTREGUE, com evidência)--> CONCLUIDA
           NAO_AGENDADA|AGENDADA --não necessária (motivo)--> NAO_NECESSARIA

Encomenda: AGUARDANDO_PEDIDO --(fornecedor/referência/previsão)--> PEDIDO_REALIZADO --receber--> RECEBIDA
           qualquer estado não recebido --cancelamento da venda--> CANCELADA

Ocorrência: ASSISTENCIA: ABERTA -> RESOLVIDA            TROCA/DEVOLUCAO: ABERTA -> DEVOLUCAO_RECEBIDA -> RESOLVIDA
            ABERTA -> CANCELADA (somente antes do recebimento da devolução)
```

## Saldo, reservas e movimentações

- Disponível = físico − reservas **ativas**. Reserva `ATIVA` → `CONSUMIDA` na saída; `LIBERADA` no cancelamento.
- Cada alteração do físico gera uma `MovimentacaoEstoque` (`ENTRADA_ENCOMENDA`, `SAIDA_VENDA`, `AJUSTE_INVENTARIO`, `ENTRADA_DEVOLUCAO`). Um índice único impede duas baixas do mesmo item.
- **Regra de agregação** (fecha a pendência da Etapa 1): produto com variações → vale o saldo da variação e `produtos.estoque` passa a ser **mantido pelo sistema como a soma das variações** quando todas têm saldo; produto sem variações → vale o do produto.
- Variação com saldo desconhecido (nulo) **não é movimentada** nem recebe entrada: o sistema não presume zero. O primeiro saldo é definido por **contagem de inventário** (a 1ª contagem de um saldo nulo grava uma movimentação de quantidade 0).
- O inventário não aceita contagem abaixo do reservado e é idempotente por `Idempotency-Key`.
- Concorrência: pedido e unidades de estoque são travados (lock pessimista, ordem determinística) e as alterações pendentes são gravadas antes da releitura sob lock. Idempotência por chave em saída, recebimento de encomenda, recebimento de devolução e inventário.

## Decisões pendentes (explícitas) e regras provisórias

| ID | Situação nesta etapa |
|---|---|
| **D05** — pagamento exigido para sair | Agora é uma **configuração** (`exigePagamentoExpedir`: nulo = pendente). Enquanto nulo, **a saída fica bloqueada** (422 `CONFIGURACAO_PENDENTE`, e `acoes.bloqueios.saida` na tela). `true` exige pagamento quitado; `false` não exige. A confirmação da venda segue sem exigir pagamento. Nada foi presumido a partir de "uma forma por venda". |
| **D08** — prazo de encomenda e atributos de variação | Prazo continua nulo; a tela mostra "A definir (D08)". Variações seguem cor/tamanho. |
| **D09** — troca, devolução e restituição | O sistema registra a ocorrência, a avaliação física, a entrada de estoque e **calcula a diferença da troca de forma informativa**. **Nenhum valor é cobrado, restituído ou creditado**; toda ocorrência de troca/devolução traz `bloqueios.solucaoFinanceira`. A reposição física do item trocado também não é automática (depende da mesma política). |
| D01, D02, D06, D10 | Fora do escopo desta etapa (comissão, custos, fechamento). |

**Regras mínimas adotadas e que podem ser alteradas** (não afetam dinheiro; foram necessárias para o fluxo funcionar):
1. *Comprovação da entrega*: exige **nome de quem recebeu e/ou arquivo** (foto/PDF até 5 MB). A montagem exige **arquivo e/ou descrição** do serviço.
2. *Recebimento de encomenda* (**revisado na Etapa 3**): a versão original exigia o lote completo, o que confundia recebimento do fornecedor com entrega parcial ao cliente. Agora o fornecedor pode entregar em partes: cada recebimento gera entrada de estoque e reserva até a quantidade vendida; a saída ao cliente segue indivisível.
3. *Devolução*: só volta ao saldo disponível se a condição física avaliada for **APTA_REVENDA**; "NAO_APTA" exige descrição e fica registrada sem entrada de estoque.
4. *Quem opera*: saída, entrega, montagem, encomendas, inventário e pós-venda são do **gerente e do proprietário**; o vendedor consulta as próprias vendas, a agenda e os comprovantes das próprias vendas.
5. *Pós-venda* só depois da entrega concluída; a quantidade devolvida/trocada não pode exceder o vendido.
6. *Equipe de entrega e responsável pela montagem* são texto livre (não há cadastro de equipes).

## Arquivos privados (comprovantes e evidências)

- Gravados em `<upload-dir>/privado/...` (o mesmo volume de uploads, sem mudança de deploy), com nome aleatório, extensão e **assinatura do conteúdo** validadas (JPG, PNG, WEBP, PDF; 5 MB).
- `/uploads/privado/**` é **negado** na configuração de segurança; o acesso é por `GET /api/v1/arquivos?caminho=...`, autenticado, sem cache. Vendedor só lê comprovantes de entrega/montagem das **próprias** vendas.
- `spring.servlet.multipart.max-file-size` passou de 1 MB (padrão) para 6 MB. Isso também vale para os uploads de imagens de produto já existentes.

## API (resumo)

| Método e caminho | Perfil | Observação |
|---|---|---|
| `POST /vendas/{id}/entrega/agendar` · `/entrega/reagendar` | gerente/proprietário | reagendar exige motivo |
| `POST /vendas/{id}/saida` | gerente/proprietário | cabeçalho `Idempotency-Key` obrigatório; bloqueada por D05 |
| `POST /vendas/{id}/entrega/tentativa-frustrada` | gerente/proprietário | motivo |
| `POST /vendas/{id}/entrega/concluir` | gerente/proprietário | multipart: `recebedor`, `observacao`, `arquivo` |
| `POST /vendas/{id}/montagem/agendar` · `/concluir` · `/nao-necessaria` | gerente/proprietário | concluir é multipart |
| `GET /encomendas` · `PUT /encomendas/{id}` · `POST /encomendas/{id}/receber` | gerente/proprietário | receber: `Idempotency-Key` |
| `POST /estoque/ajustes` · `GET /estoque/movimentacoes` | ajuste: gerente/proprietário; leitura: operação | ajuste: `Idempotency-Key` |
| `POST /vendas/{id}/ocorrencias` · `GET/POST /ocorrencias/...` | gerente/proprietário | evidências multipart; receber-devolução com `Idempotency-Key` |
| `GET /agenda?de&ate` | operação (leitura) | período máx. 92 dias |
| `GET /arquivos?caminho=` | operação | escopo por venda para o vendedor |

## Migração V4 e compatibilidade

`V4__gestao_vendas_etapa2.sql` é **aditiva** (V1–V3 intactas): novas tabelas `encomendas`, `entregas`, `entrega_eventos`, `montagens`, `ocorrencias_pos_venda`, `ocorrencia_evidencias`, `movimentacoes_estoque`; colunas `pedidos.saida_realizada_em`/`chave_saida` e `configuracao_comercial.exige_pagamento_expedir` (nulas); constraints de `status_entrega` e de `auditoria_evento.tipo` ampliadas (conjunto superior ao anterior).

- Vendas confirmadas na Etapa 1 continuam válidas: encomendas confirmadas antes da V4 ganham acompanhamento na primeira consulta de `GET /encomendas`.
- **Rollback de código** para a Etapa 1 continua compatível com o schema V4 (colunas novas nulas/com padrão; tabelas novas ignoradas). **Não reverter a migração.** Se houver vendas com saída já registrada, o código anterior não conhece a baixa: não fazer rollback de código depois de registrar saídas em produção sem conferir o estoque.
- **Atenção ao estoque real**: a baixa passa a existir. Antes de usar a saída em produção, faça a contagem de inventário (Estoque) para variações com saldo nulo e confira o saldo físico; a saída recusa saldo insuficiente.
- O ensaio sobre cópia do banco de produção (`scripts/ensaio-migracao-v3.sh`, que aplica **todas** as migrations pendentes, inclusive a V4) segue **PENDENTE**.

## Validação executada (07/10/2026)

Ambiente: PostgreSQL **15.19** com V1 aplicada e V2–V4 pelo Flyway, backend em modo de produção (`ddl-auto: validate`), frontend em **build de produção** atrás de um proxy que imita o Traefik (`/api` e `/uploads` → backend) e Chromium (Playwright) em **1366×900** e **390×844**. Os roteiros agora fazem parte do repositório (`scripts/validacao/`, ver README): configuração por variáveis `LP_*`, nenhuma credencial gravada, senhas de teste geradas a cada execução, configuração comercial restaurada e usuários desativados ao final.

| Verificação | Resultado |
|---|---|
| `mvnw test` (57 testes) | OK no H2 **e** no PostgreSQL 15 com schema do Flyway |
| `scripts/validacao/api_etapa1.py` | 118/118 OK (ambiente criado do zero) |
| `scripts/validacao/api_etapa2.py` | 152/152 OK (ambiente criado do zero) |
| `scripts/validacao/ui_fluxo.py` (navegador, computador e celular, vitrine) | 67/67 OK (ambiente criado do zero) |
| `scripts/ensaio-migracao-v3.sh` (V3 + V4 sobre dump sintético de 120 pedidos, PG 15.19) | OK: contagens, somas de total/frete, mapa de conversão, estoque físico e ausência de dados gerados em pedidos antigos |
| `npm run build` | OK |
| `npm run lint` | 6 erros, todos anteriores à etapa (os mesmos listados no documento da Etapa 1) |

Cobertura dos testes automatizados da Etapa 2 (`AtendimentoServiceTest`, `SegurancaApiTest`): saída bloqueada por D05 pendente; D05 verdadeiro exige pagamento; **baixa única** (repetição com a mesma chave e saídas simultâneas em duas threads); sem entrega parcial (item de encomenda sem reserva bloqueia e nada é baixado); saldo físico insuficiente não grava nada; tentativa frustrada, reagendamento sem nova baixa e cancelamento bloqueado após a saída; comprovação e evidência obrigatórias; montagem só após a entrega; encomenda (previsão, recebimento total, reserva posterior, idempotência, cancelamento da venda); saldo nulo orienta a contagem; inventário (motivo, idempotência, respeito às reservas, 1ª contagem); devolução apta/não apta, cota de quantidade, troca com diferença informativa e bloqueio D09, assistência com evidência; arquivos privados (tipo, assinatura, tamanho, path traversal, `/uploads/privado` negado, escopo do vendedor).

No navegador (computador, 1366 px): D05 pendente bloqueia o botão com a explicação; o proprietário define a regra pela tela; **clique duplo em Registrar saída baixa uma vez**; tentativa frustrada → reagendar → nova saída sem nova baixa; conclusão exige comprovação e o comprovante abre pelo endpoint autenticado (HTTP 200); montagem com evidência; devolução apta volta ao estoque e exibe o D09; encomenda com recebimento parcial **aceito (Etapa 3)** e **saída bloqueada até a chegada completa** (depois sai de uma vez); inventário com motivo e histórico; vendedor sem ações de atendimento e sem acesso a Encomendas. No celular (390 px): sem rolagem horizontal em detalhe do pedido, encomendas, pós-venda, estoque, agenda e lista de pedidos.

### Problemas encontrados e correção

| # | Problema | Origem | Correção |
|---|---|---|---|
| 1 | Reservar o item logo após a entrada de estoque lia o saldo antigo: o `refresh` sob lock descartava a alteração ainda não gravada | **Introduzido durante a Etapa 2** (pego pelos testes) | `flush` antes de reler sob lock; coberto por teste de recebimento de encomenda |
| 2 | Qualquer usuário da operação podia ler **comprovantes de vendas alheias** pelo caminho do arquivo | **Introduzido durante a Etapa 2** (pego em revisão) | Endpoint exige gerente/proprietário, ou venda própria no caso de comprovantes de entrega/montagem; evidências de pós-venda só do gestor; teste HTTP |
| 3 | Login com senha errada respondia **500** (`IllegalArgumentException` sem tratamento) | **Anterior** (fluxo de autenticação existente), achado pelo roteiro de API | `CredenciaisInvalidasException` → **401** com mensagem; teste |
| 4 | `GET /api/v1/auditoria` respondia **500** (entidade com proxy lazy serializada fora da sessão) | **Anterior**, achado pelo roteiro de API | Resposta em DTO montado dentro da transação; teste |
| 5 | `iniciar-app.sh` no Git Bash gravava `C:/Program Files/Git/api/v1` no frontend (conversão de caminho do MSYS), e a interface não chamava a API | Ferramental dos roteiros | `MSYS_NO_PATHCONV` + verificação que aborta se o valor errado aparecer no build |
| 6 | Fixar o container descartável do ensaio com senha constante | Ferramental | Senha gerada em tempo de execução e porta só em `127.0.0.1` |

Nenhuma **regressão** de funcionalidade existente foi encontrada (a vitrine pública, o carrinho e o encaminhamento ao WhatsApp foram reverificados no navegador; o carrinho continua sem gravar venda).

Observação fora do escopo: o navegador registra bloqueios de imagens externas (`ERR_BLOCKED_BY_ORB`) na página inicial — fotos de um serviço externo, anteriores a esta etapa.

## Pendências da Etapa 2

- **Ensaio das migrações V3 e V4 em cópia do banco de produção — PENDENTE** (sem acesso/dump; não houve conexão à produção). O roteiro `scripts/ensaio-migracao-v3.sh` aplica todas as migrations pendentes e confere o resultado; o procedimento está em `docs/gestao-vendas-etapa1.md`. Só depois disso fazer deploy.
- **Contagem física antes de usar a saída em produção**: variações com saldo nulo não são movimentadas; defina-as por inventário (Estoque). A baixa passa a existir de verdade.
- **Decisões abertas**: D05 (a saída continua bloqueada até alguém definir), D08 (prazo de encomenda), D09 (restituição/diferença de troca), além de D01/D02/D06/D10 (Etapa 3). As regras mínimas adotadas (acima) devem ser confirmadas pela loja.
- **Troca**: o sistema registra a ocorrência, recebe a devolução e calcula a diferença, mas **não gera automaticamente a saída do item de reposição nem lança valores** (depende de D09). A reposição física precisa de nova venda/saída manual por enquanto.
- ~~Recebimento de encomenda só aceita o lote completo~~: **resolvido na Etapa 3** (recebimento parcial do fornecedor aceito; entrega parcial ao cliente continua proibida).
- **Pagamento quitado** só existe pelo fluxo de pagamento existente (Mercado Pago) e pela Etapa 3 (registros manuais): o caminho "D05 = exigir pagamento com venda paga" foi coberto por teste de unidade, não por roteiro de ponta a ponta.
- Notificações (cliente/vendedor) sobre agendamento, saída e conclusão não foram criadas.
- Teste em **dispositivo físico** e em outros navegadores não foi feito (celular emulado no Chromium).
- Sem cadastro de equipes de entrega/montagem (texto livre) e sem agenda por capacidade/conflito de horário.
- A listagem legada `GET /pedidos/gerenciar` segue com o problema de lazy loading anterior à etapa (substituída pela tela Pedidos).
