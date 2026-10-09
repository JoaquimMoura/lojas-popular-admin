# Validação local da gestão de vendas (Etapas 1 e 2)

Roteiros reproduzíveis para validar, num ambiente **local e descartável**, o backend e o frontend da gestão de
vendas: ambiente (PostgreSQL + aplicação + proxy) e roteiros de API.

> **Atenção: uso exclusivo em ambiente local descartável. Nunca aponte estes scripts para produção.**
> Os roteiros CRIAM e ALTERAM dados (usuários, clientes, produtos, vendas, configuração comercial, estoque).
> `lib.py` recusa `LP_BASE_URL` que não seja `localhost`/`127.0.0.1`, e nenhum script se conecta a banco ou a
> serviço de produção: o banco é o container `lp-val-pg`, criado e removido por `ambiente-pg.sh`.

Nenhum segredo é versionado. Toda configuração vem de variáveis de ambiente `LP_*`; a senha do banco, o segredo
do JWT e um usuário de perfil CLIENTE de teste são **gerados na hora** e gravados em
`scripts/validacao/.ambiente.env`; as senhas dos usuários que cada roteiro cria são aleatórias a cada execução
e nunca são impressas. `validacao-out/` e `scripts/validacao/.ambiente.env` estão no `.gitignore`.

## Conteúdo

| Arquivo | Para quê |
|---|---|
| `ambiente-pg.sh` | `up`/`down`: PostgreSQL 15 descartável, migrações V1..V6, proprietário, catálogo de exemplo |
| `iniciar-app.sh` / `parar-app.sh` | backend (JAR), build do frontend e proxy que emula o Traefik; PIDs em `validacao-out/pids` |
| `servidor_proxy.js` | proxy em Node puro: `/api` e `/uploads` -> backend; o resto -> build do frontend (fallback SPA) |
| `lib.py` | módulo comum (HTTP/multipart, verificações, usuários de teste, config comercial, dados de teste) |
| `api_etapa1.py` | roteiro de API da Etapa 1 (venda com reserva) |
| `api_etapa2.py` | roteiro de API da Etapa 2 (saída, entrega, montagem, encomendas, inventário, pós-venda, agenda) |
| `suite-pg.sh` | roda a suíte JUnit inteira contra um PostgreSQL 15 descartável (V1→V6, `ddl-auto=validate`), separado dos roteiros |
| `ui_etapa3.py` | navegador (computador e celular) do financeiro: pagamento no pedido, caixa, abas, fechamento, vendedor |
| `ui_comprovante.py` | navegador (A4, PDF, celular) do comprovante de compra: seis cenários, sem observação interna, sem "quitado"/"recibo" |
| `api_clientes.py` | roteiro de API do vínculo cliente x venda e histórico de compras (escopo, exclusão, vínculo/troca) |
| `api_etapa3.py` | roteiro de API da Etapa 3 (recebimentos, caixa, contas, cartão, comissões, metas, restituição, fechamento) |
| `ui_fluxo.py` | roteiro de navegador (Playwright): fluxo completo da Etapa 1 e 2 em computador e celular, mais a vitrine pública |

## Requisitos

- Docker (imagens `postgres:15` e `flyway/flyway:11`; a primeira execução baixa as imagens).
- JDK 21 (o `mvnw` baixa o Maven na primeira vez; offline, só funciona com o cache do `~/.m2` já populado).
- Node.js (o frontend usa Vite) e Python 3 (somente biblioteca padrão).
- `curl` e Git Bash (Windows) ou um shell POSIX (Linux/macOS).
- Só para o roteiro de navegador: `pip install playwright && playwright install chromium`.

Portas usadas (todas configuráveis): PostgreSQL `55440`, backend `18090`, proxy `4180`. O container se chama
`lp-val-pg`. Não conflitam com o ambiente de desenvolvimento usual (`8080`, `5432`).

## Variáveis

| Variável | Obrigatória | Padrão | Uso |
|---|---|---|---|
| `LP_ADMIN_EMAIL` | sim | - | e-mail do primeiro proprietário (use um endereço de teste, ex. `dono@example.invalid`) |
| `LP_ADMIN_PASSWORD` | sim | - | senha dele (mínimo 8 caracteres) |
| `LP_PG_PORT` | não | `55440` | porta do PostgreSQL local |
| `LP_BACKEND_PORT` | não | `18090` | porta do backend |
| `LP_PROXY_PORT` | não | `4180` | porta do proxy (é por onde se acessa a aplicação) |
| `LP_UPLOAD_DIR` | não | `validacao-out/uploads` | pasta de uploads do backend |
| `LP_JAVA_HOME` | não | - | JDK 21 quando o `java` do PATH não for 21+ (Git Bash: `C:\Program Files\Java\jdk-21` ou `/c/Program Files/Java/jdk-21`) |
| `LP_PULAR_BUILD=1` | não | - | `iniciar-app.sh` reaproveita `validacao-out/app.jar` |
| `LP_SEM_FRONT=1` | não | - | `iniciar-app.sh` não compila o frontend (só a API) |
| `LP_BASE_URL` | não | `http://localhost:4180` | endereço do proxy usado pelos roteiros Python (somente local) |
| `LP_TEST_PASSWORD` | não | aleatória por execução | senha dos usuários de teste criados pelos roteiros |

`LP_CLIENTE_EMAIL`/`LP_CLIENTE_PASSWORD` (usuário de perfil CLIENTE para testar 403 x 401) e as demais
`LP_*` geradas ficam em `.ambiente.env`; `lib.py` as lê de lá quando não estão no ambiente.

## Passo a passo

A partir da raiz do repositório (Git Bash no Windows):

```bash
export LP_ADMIN_EMAIL='dono@example.invalid'
export LP_ADMIN_PASSWORD='uma-senha-de-teste-aqui'      # não grave em arquivos versionados
export LP_JAVA_HOME='C:\Program Files\Java\jdk-21'      # se o java do PATH não for 21+

scripts/validacao/ambiente-pg.sh up         # banco descartável, V1..V6, proprietário e catálogo de exemplo
scripts/validacao/iniciar-app.sh            # compila e sobe backend + frontend + proxy (http://localhost:4180)

python3 scripts/validacao/api_etapa1.py     # Etapa 1
python3 scripts/validacao/api_etapa2.py     # Etapa 2
python3 scripts/validacao/api_etapa3.py     # Etapa 3 (financeiro)
python3 scripts/validacao/ui_etapa3.py      # navegador: financeiro (Etapa 3)
python3 scripts/validacao/ui_fluxo.py       # navegador (computador e celular) + vitrine pública

scripts/validacao/parar-app.sh              # encerra backend e proxy
scripts/validacao/ambiente-pg.sh down --limpar   # remove o container e as pastas/arquivos gerados
```

Tempo aproximado: `up` ~15 s; `iniciar-app.sh` ~40 s (build do backend e do frontend); `api_etapa1.py` ~15 s;
`api_etapa2.py` ~20 s; `ui_fluxo.py` ~2 a 4 min.

Com o ambiente de pé é possível abrir o frontend em `http://localhost:4180/gestao` e entrar com o e-mail e a
senha do proprietário, para inspeção manual.

Os roteiros podem ser repetidos no mesmo ambiente: cada execução cria seus próprios usuários, clientes,
produtos (SKU único) e vendas, e desativa os usuários que criou ao final (a loja limita a 5 usuários
operacionais ativos, e o proprietário conta). Usuários `lp-val-*` de execuções interrompidas são desativados
no início da próxima.

## O que cada roteiro cobre

### `ui_fluxo.py` (navegador)

Variáveis opcionais: `LP_VIEWPORTS` (padrão `1366x900,390x844`: o primeiro recebe o fluxo completo, os demais só
conferem o layout e a ausência de rolagem horizontal), `LP_SHOTS_DIR` (capturas de tela; padrão
`validacao-out/shots`) e `LP_HEADFUL=1` (navegador visível).

1. **Configuração comercial pela interface**: pendências D03/D04/D05/D07 visíveis; o proprietário define as
   regras e as pendências somem (a D05 segue pendente de propósito); o vendedor vê o alerta em Nova venda.
2. **Etapa 1**: venda com desconto acima do limite fica *Aguardando aprovação*; o vendedor não aprova; o gerente
   aprova; **clique duplo em Confirmar gera uma única reserva**; a reserva aparece no pedido e em Estoque; o
   vendedor não cancela; o gerente cancela com motivo e o estoque é liberado.
3. **D03, D04 e D07 isolados na interface**: cada um bloqueia só o que depende dele (desconto, pagamento, cancelamento).
4. **Etapa 2**: D05 pendente bloqueia a saída com explicação; com D05 definido (valor de TESTE) a saída baixa o
   estoque **uma vez** (clique duplo), tentativa frustrada, reagendamento sem nova baixa, conclusão exige
   comprovação (com upload) e o comprovante abre pelo endpoint autenticado; montagem inclusa com evidência;
   devolução apta volta ao estoque e exibe o bloqueio D09; encomenda com recebimento parcial recusado e **saída
   bloqueada enquanto houver item sem reserva (sem entrega parcial)**; inventário com motivo e histórico.
5. **Perfis**: o vendedor lê a agenda, não vê ações de saída/entrega/montagem/pós-venda, não acessa Encomendas e
   não enxerga vendas de outro vendedor.
6. **Layout no celular (390 px)**: sem rolagem horizontal em detalhe do pedido, encomendas, pós-venda, estoque,
   agenda e lista de pedidos.
7. **Vitrine pública** (anônimo): produtos, detalhe, carrinho, link do WhatsApp com a mensagem do pedido, **o
   carrinho não grava venda**, `/gestao` leva ao login e nenhuma resposta 4xx/5xx de API.

Observação: respostas bloqueadas de imagens externas (`ERR_BLOCKED_BY_ORB`, fotos de um serviço externo na
página inicial) podem aparecer no navegador e não são do sistema.

### `api_etapa1.py`

1. **Autenticação**: login, senha errada, 401 sem token/token inválido; **403 (não 401)** para usuário autenticado
   sem perfil operacional (CLIENTE).
2. **Usuários**: criação de gerente e vendedores, senha curta, e-mail duplicado, perfil inválido, não
   desativar o próprio usuário, **limite de 5 usuários ativos**, desativado não faz login nem usa token antigo.
3. **Matriz de permissões**: o que vendedor, gerente e proprietário podem ler/alterar (usuários, configuração
   comercial, encomendas, ocorrências, inventário, auditoria); só o proprietário define quem cancela.
4. **Clientes**: CPF válido/inválido/duplicado (bloqueia), nome ou telefone repetido (alerta confirmável),
   busca, atualização, desativação e escopo.
5. **Configuração pendente**: o roteiro salva a configuração, zera tudo (D03/D04/D05/D07 pendentes) e verifica
   que cadastrar cliente, consultar vendas/estoque e a vitrine **não** são bloqueados, que registrar venda é
   (422 `CONFIGURACAO_PENDENTE`); depois testa cada decisão **isolada**: D07 (só cancelar bloqueia), D03 (só
   desconto bloqueia), D04 (arredondamento ou condições ausentes bloqueiam o registro; forma não cadastrada).
6. **Venda completa**: preço calculado no servidor (condição PIX), idempotência do registro, validações,
   desconto no limite e acima, aprovação/rejeição, auto-aprovação bloqueada (gerente) e permitida ao proprietário,
   confirmação idempotente com `Idempotency-Key` (mesma chave = 1 reserva; outra chave recusada), reserva
   (físico inalterado, disponível -1), confirmação atômica sem estoque, cancelamento e permissões por D07.
7. **Escopo entre vendedores**: um vendedor não vê, confirma ou cancela venda de outro (404); gerente e
   proprietário veem todas.
8. **Catálogo**: editar produto com e sem `id` preserva os ids das variações, remover variação já vendida e
   excluir produto com vendas são recusados, adicionar variação mantém as antigas.

### `api_etapa2.py`

1. **D05 pendente** bloqueia a saída (422 `CONFIGURACAO_PENDENTE` citando D05) e aparece em
   `acoes.bloqueios.saida`; nada é baixado.
2. **Pronta entrega (D05 = falso)**: agendar (data futura; passado e duplicidade recusados; vendedor 403),
   saída com `Idempotency-Key` (mesma chave não baixa de novo; outra chave recusada), baixa conferida em
   `GET /estoque` e `GET /estoque/movimentacoes` (`SAIDA_VENDA`), cancelamento bloqueado depois da saída,
   tentativa frustrada, reagendamento (exige motivo), nova saída sem nova baixa, conclusão multipart (recebedor +
   PNG) com validação de tipo/assinatura do arquivo; comprovante em `GET /api/v1/arquivos?caminho=` (200 com
   token, 401 sem token, 404 para outro vendedor e para path traversal) e `/uploads/<privado>` negado.
3. **Montagem**: concluir exige entrega concluída e evidência; "não necessária" exige motivo e bloqueia novo
   agendamento; cancelar antes da saída encerra entrega e montagem.
4. **D05 = verdadeiro**: venda não paga tem a saída recusada.
5. **Encomendas**: `GET /encomendas`, atualizar previsão (atrasada), recebimento **parcial** do fornecedor aceito
   (PARCIALMENTE_RECEBIDA, reserva progressiva), saída recusada até o recebimento completo, idempotência do recebimento,
   saída só com **todos** os itens reservados (nada baixado antes do recebimento), cancelamento.
6. **Inventário**: `POST /estoque/ajustes` (motivo e `Idempotency-Key` obrigatórios, não abaixo do reservado,
   mesma chave idempotente, primeira contagem de saldo nulo), histórico em `/estoque/movimentacoes`.
7. **Pós-venda**: abrir só após a entrega; devolução apta (volta ao estoque) e não apta (não volta; exige
   avaliação); cota de quantidade; troca com diferença calculada e bloqueio D09 no texto; assistência com
   evidência (upload) e resolução; cancelamento de ocorrência; filtros.
8. **Agenda**: `GET /agenda?de&ate` lista entrega e montagem; limites de período; vendedor lê (200) mas não opera (403).

Ao final, cada roteiro **restaura a configuração comercial original** (bloco `finally`) e desativa os usuários
que criou. Condições de pagamento criadas pelo roteiro não podem ser removidas pela API: ficam **inativas**.

### `api_etapa3.py`

Os valores financeiros são de **TESTE** (comissão 5%, aquisição por quitação, competência na confirmação, taxa de
cartão 4% em 3x com 30/30 dias, perfis e políticas D07/D09/D10 definidos só durante o roteiro) e as decisões
financeiras originais são **restauradas** ao final.

1. **Decisões financeiras pendentes** (D01, D02, D06, D07, D09, D10 listadas como pendências da área FINANCEIRO),
   só o proprietário as altera, o vendedor não acessa o financeiro nem vê as pendências financeiras na venda,
   e com D01 pendente nenhuma comissão é calculada.
2. **Pix**: `Idempotency-Key` obrigatória, valor acima do saldo recusado, recebimento parcial (`PARCIAL`),
   repetição com a mesma chave sem duplicar, Pix no BANCO (nunca no caixa), venda quitada não recebe mais,
   cancelamento bloqueado com recebimento ativo, estorno (motivo obrigatório; repetido é idempotente; outro
   estorno recusado) e novo recebimento depois do estorno.
3. **D05 = verdadeiro**: a saída só é aceita com o pagamento **quitado** (parcial não basta).
4. **Dinheiro e caixa**: sem caixa aberto é recusado; não abre dois caixas; entrada no CAIXA; suprimento
   idempotente; retirada acima do saldo recusada; fechamento com diferença exige motivo e fica registrado.
5. **Cartão**: operadora sem taxa (D11) bloqueia; exige o valor total; parcelas com bruto/taxa/líquido e previsão
   30/60/90; **nenhuma entrada de dinheiro** antes da liquidação; liquidação única (idempotente), divergência
   registrada sem inventar valor; recebimento com parcela liquidada não é estornado; estorno da liquidação.
6. **Concorrência** (threads): recebimentos com a mesma chave, recebimentos com chaves diferentes (saldo),
   estornos e baixas de conta simultâneos.
7. **Contas**: criação, baixa idempotente por BANCO/CAIXA, estorno com histórico, cancelamento e conta a receber.
8. **Comissões**: previsão, aquisição por quitação, regra **histórica** (mudar o percentual não altera vendas
   antigas), pagamento por conta (só o proprietário), reversão por cancelamento, D02 pendente mantém PREVISTA,
   vendedor lê só a própria.
9. **Restituição (D09/D07)**: D09 pendente e "não permite" bloqueiam; exige devolução física recebida; limite do
   item; solicitar → autorizar (não o próprio solicitante) → efetivar uma vez; recebimento restituído não é
   estornado; reversão proporcional da comissão.
10. **Metas**: definição pelo gerente, acompanhamento, atingimento **provisório** com D09 pendente, vendedor lê
    só a própria.
11. **Fechamento mensal**: prévia sem lucro definitivo e com `faltantes`; caixa e banco separados; mês corrente
    não aprova; D10 pendente bloqueia; só o proprietário aprova; **lançamentos em período fechado são
    bloqueados** (recebimento e conta); reabertura com justificativa e D07; nova versão preserva a anterior.

## Como interpretar a saída

```
OK    nome da verificação
FALHA nome da verificação   -> detalhes (status HTTP e corpo da resposta)
...
118/118 verificações OK
```

- Código de saída `0`: todas as verificações passaram. `1`: alguma FALHOU (a lista aparece no fim). `2`: configuração
  ou ambiente ausente (variáveis `LP_*`, ambiente fora do ar, `LP_BASE_URL` não local).
- Linhas `OBSERVAÇÃO` e `info` não são falhas: apontam comportamento digno de nota (por exemplo, respostas que
  não seguem o código HTTP esperado, mas não afetam a regra de negócio verificada).
- A linha `[VALORES DE TESTE]` lembra que limite de desconto, arredondamento, condições de pagamento e D05 usados
  são **valores de teste**, não decisões de negócio; a configuração original é restaurada ao final.
- Uma `FALHA` com `-> (status, corpo)` mostra a requisição problemática: confira também
  `validacao-out/backend.log`.
- Falha de **roteiro** (suposição errada do teste) e falha de **backend** (regra violada) têm a mesma aparência:
  investigue pelo corpo da resposta e pelo log antes de decidir de que lado está o defeito.

## Limpeza

```bash
scripts/validacao/parar-app.sh
scripts/validacao/ambiente-pg.sh down --limpar
```

`down` sem `--limpar` remove só o container (mantém `validacao-out/` e `.ambiente.env`). `--limpar` apaga
`validacao-out/` (JAR, logs, build do frontend, uploads), o `.ambiente.env` e, se `LP_UPLOAD_DIR` apontar
para fora, a pasta de uploads **somente** se ela foi criada por este script (marca `.lp-val-marca`).

## O que NÃO está coberto

- **Dispositivos reais**: o celular é emulado (tamanho de tela e toque) no Chromium; não há Firefox/Safari.
- Pagamento pelo **Mercado Pago** (webhook/PaymentService) não é simulado em ponto a ponto: as regras do
  `PaymentService` (valor divergente, evento repetido, rejeição não cancela) são cobertas por teste de integração.
- Crédito/haver de diferença de troca e reposição física automática da troca (não implementados).
- Concorrência real (requisições simultâneas), carga e desempenho.
- Migração sobre dados reais de produção: ver `scripts/ensaio-migracao-v3.sh` e `docs/gestao-vendas-etapa1.md`.
- Pedidos legados (`LEGADO`), pagamentos via Mercado Pago/WhatsApp (as chaves usadas são fictícias; o RabbitMQ
  aponta para uma porta sem serviço) e, na vitrine pública, avaliação em dispositivos reais ou em outros navegadores.
- Envio de e-mail/WhatsApp, `refresh` de token e expiração de sessão.
