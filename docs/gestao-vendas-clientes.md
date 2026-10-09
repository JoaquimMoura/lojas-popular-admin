# Cliente x venda: vínculo e histórico de compras

## O que já existia e foi reaproveitado
- Cadastro de clientes (`clientes`, endereços, CPF/telefone normalizados, busca por nome/CPF/telefone, alerta de duplicidade por nome/telefone e bloqueio de CPF repetido, inativação).
- `Pedido.cliente` (comprador), separado de `Pedido.usuario` (quem registrou) e `Pedido.vendedor` (responsável). A venda exige cliente ativo (servidor).
- Nova venda: busca de cliente, cadastro sem sair do fluxo (o cliente fica selecionado) e revisão com o cliente.
- Critério de "venda válida" dos relatórios gerenciais.

## O que foi acrescentado (V8; V1 a V7 intactas)
| Item | Como |
|---|---|
| Dados do cliente congelados na venda | `pedidos.cliente_nome_hist/cpf_hist/telefone_hist`, gravados ao registrar. Alterar o cadastro **não** muda a venda. A V8 copiou o que o vínculo já existente mostrava; vendas sem cliente continuam sem cliente. |
| Histórico de compras | `GET /clientes/{id}/compras` (período, situação, produto, paginação, da mais recente para a mais antiga) e `GET /clientes/{id}/resumo`. Tela: Clientes › Histórico de compras. |
| Critério único | `CriteriosVenda.valida`: venda confirmada e não cancelada (e legada paga/entregue), o mesmo dos relatórios. Teste confere que o total do cliente bate com o relatório. Cancelada aparece no histórico, mas fora dos indicadores. |
| Valor comprado ≠ dinheiro recebido | O resumo traz só valores de venda e o valor **dos itens devolvidos fisicamente** (separado). Sem custo, margem, recebimento nem restituição. |
| Exclusão | `DELETE /clientes/{id}` só do proprietário e só se não houver vendas; com vendas, inativar (histórico preservado). |
| Venda antiga sem cliente | Mostra "Cliente não identificado". O proprietário vincula depois (`POST /vendas/{id}/vincular-cliente`), com justificativa, responsável e data na tabela `pedido_cliente_eventos` e na auditoria. Não muda valores, estoque, pagamentos, comissão nem fechamento. |
| Troca de cliente em venda confirmada | `POST /vendas/{id}/trocar-cliente`: exige perfis definidos pelo proprietário (**D13**) e justificativa; auditado. **Enquanto D13 estiver pendente, está bloqueada, até para o proprietário.** |

## Permissões e escopo
- Gerente e proprietário veem todas as compras do cliente; o **vendedor só as suas**, também nos resumos e indicadores (a API aplica).
- **D13** (decisão do proprietário, pendente por padrão): (a) o vendedor pode ver compras do cliente feitas por outros vendedores? (b) quem pode trocar o cliente de venda confirmada? Configuração comercial › Clientes. Liberar (a) não libera o detalhe da venda alheia nem dados financeiros.
- O detalhe completo de cada pedido continua com as permissões anteriores (vendedor só nas suas vendas; financeiro conforme a D12).

## Limitações
- O endereço de entrega fica na venda como foi registrado: trocar o cliente não altera o endereço.
- O detalhe da venda não lista ainda o histórico de vínculos/trocas (fica na auditoria e em `pedido_cliente_eventos`).
- Busca por semelhança de nome não vincula nada automaticamente: só alerta.

## Validação
`ClienteHistoricoTest` (8 testes: vínculo e cópia dos dados, duas compras, cancelada fora dos indicadores, totais iguais ao relatório, escopo do vendedor por API direta, exclusão bloqueada, duplicidade, vínculo posterior, troca bloqueada pela D13) e `scripts/validacao/api_clientes.py` (21 verificações no PostgreSQL 15).
