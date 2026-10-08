# Decisões da loja (respostas do questionário)

Registro das respostas dadas pelo responsável em 08/10/2026. **Nada disto foi aplicado em banco real**: são os valores a configurar na implantação (tela *Configuração comercial*, ou API como proprietário). Itens "pendente" continuam bloqueando só o que dependem deles.

## Respondido

| Pergunta | Resposta | Onde configurar |
|---|---|---|
| 1 Desconto sem aprovação (D03) | **Sem desconto** (limite 0%): todo desconto exige aprovação | Configuração comercial › limite de desconto = 0 |
| 2 Arredondamento (D04) | **Meio para cima** | Configuração comercial › arredondamento |
| 3a Pix | **0%** (preço de tabela) | Condições de pagamento › Pix 1x = 0 |
| 3b Cartão | **1x a 6x, sem juros ao cliente (ajuste 0% em todas)** | Condições de pagamento › Cartão 1x, 2x, 3x, 4x, 5x e 6x = 0 |
| 3c Dinheiro | **0%** | Condições › Dinheiro 1x = 0 |
| 4 Saída exige pagamento (D05) | **Não** (pode sair a receber) | Configuração comercial › exige pagamento para a saída = não |
| 5 Quem cancela venda (D07) | **Gerente e proprietário** | Configuração comercial › perfis de cancelamento |
| 6 Prazo de encomenda (D08) | **5 dias** | **Por produto** (campo "prazo de encomenda" no cadastro de cada produto sob encomenda). Não existe prazo global: o sistema só mostra "A definir" onde o produto não tem prazo. |
| 8 Permissões do gerente (D12) | **Consultar, receber, pagar, estornar e restituir: proprietário e gerente** | Configuração comercial › Permissões financeiras (as cinco = proprietário e gerente) |
| 9 Percentual de comissão (D01) | **3%** sobre o total cobrado, igual para todos | Regras financeiras › percentual de comissão = 3 |
| 10 Comissão devida quando (D02) | **Ao confirmar a venda** | Regras financeiras › aquisição da comissão = confirmação |
| 11 Pagamento das comissões | **Dia 5 de cada mês** | Não é configuração: o sistema pede o vencimento a cada pagamento; usar sempre dia 5 |
| 12 Meta desconta devoluções (D09) | **Sim** | Regras financeiras › meta desconta devoluções = sim |
| 13 Devolve dinheiro (D09) | **Sim** | Regras financeiras › permite restituição = sim |
| 14 Cobra diferença de troca (D09) | **Sim** | Regras financeiras › permite cobrança de diferença = sim |
| 15 Quem autoriza restituição (D07) | **Gerente e proprietário** (quem pede nunca autoriza o próprio pedido) | Regras financeiras › perfis de restituição |
| 16 Competência da receita (D06) | **Mês da confirmação** (vale também para as taxas de cartão) | Regras financeiras › competência da receita = confirmação |
| 17 Fechamento exige zero pendências (D10) | **Sim** | Regras financeiras › fechamento exige ausência de pendências = sim |
| 18 Quem reabre mês fechado (D07) | **Gerente e proprietário**, com justificativa | Regras financeiras › perfis de reabertura |
| 19 Custos | **Sim, vamos informar, antes da primeira venda** | Financeiro › Custos (proprietário) |
| 20 Estoque inicial | **O proprietário conta antes de liberar a venda** (data a definir) | Estoque › ajustar contagem, com motivo "contagem inicial" |
| 21 Usuários | **Proprietário e um gerente** | Usuários |

## Ainda pendente

| Pergunta | O que bloqueia |
|---|---|
| 7 Taxas e prazos de cada operadora (D11): nome, taxa % por parcelas (1x a 6x), dias até a 1ª parcela e dias entre parcelas | Recebimento no cartão (a venda pode ser registrada, mas não recebida). |
| 20 e 19 Datas | Previstas para a **próxima semana (12 a 16/10/2026)**: contagem do estoque (libera a venda) e cadastro dos custos (margem e resultado definitivo; D01 também precisa estar definido). |

## Pontos que merecem atenção

- **Sem desconto + Pix 0% + dinheiro 0%**: a loja vende pelo preço de tabela; o preço só muda no cartão, quando as condições forem cadastradas.
- **Venda a receber (D05 = não)**: a mercadoria pode sair sem pagamento; o acompanhamento do que falta receber é pela situação de pagamento do pedido e pelos relatórios. O sistema não impede a saída.
- **Gerente com todas as operações financeiras**: só o proprietário aprova o fechamento do mês, paga comissões, cadastra custos e taxas de cartão e altera estas regras.
- **Fechamento exige zero pendências**: caixa aberto, conta ou recebível vencido, restituição em aberto etc. impedem aprovar o mês; o proprietário precisa resolver antes.
- **Comissão ao confirmar**: se a venda for cancelada depois, a comissão é revertida; se já foi paga, a reversão é compensada no pagamento seguinte.
- **Prazo de encomenda**: informar os 5 dias em cada produto sob encomenda.

## Para aplicar (na implantação, nunca em produção sem o ensaio das migrações)

1. Entrar como proprietário; Configuração comercial: itens 1, 2, 4, 5 e as condições Pix e dinheiro.
2. Permissões financeiras (D12) e regras financeiras (D02, D06, D07, D09, D10).
3. Usuários (gerente).
4. Cadastrar produtos com prazo de encomenda e custos; contar o estoque; só então liberar a venda.
5. Conferir a lista de pendências restantes na própria tela (D01, D11 e a condição de cartão).
