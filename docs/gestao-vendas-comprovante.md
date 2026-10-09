# Comprovante de compra (pedido de venda)

Documento para entregar ao cliente, com a identificação **"Pedido de venda — não é documento fiscal"** (não é NF-e nem DANFE). Rota: `Pedidos › detalhe › Imprimir pedido` (`/gestao/pedidos/{id}/imprimir`), com layout A4 em preto e branco; imprime ou salva em PDF pelo navegador. Não há emissão fiscal nem serviço externo.

## De onde vêm os dados (uma só fonte)
O backend (`GET /api/v1/vendas/{id}/comprovante`, `ComprovanteService`) **reproduz o detalhe oficial do pedido** (`VendaService.obter`): itens, preços, subtotal, desconto, frete, total, endereço da entrega, agendamentos e situação. Não recalcula nada; os totais impressos são os do pedido.

| Bloco | Conteúdo | Observações de fidelidade |
|---|---|---|
| Loja | Logotipo, nome, endereço e telefone cadastrados na loja | **CNPJ não é impresso: a loja não tem esse campo cadastrado** (nada é inventado). |
| Pedido | Número, data da venda (confirmação), vendedor responsável | Pedido antigo sem dado: "Não informado". |
| Cliente | Nome e telefone **como estavam na venda** (V8); endereço **desta entrega** (cópia gravada na venda) e, quando diferente, o **endereço cadastral** atual do cliente | Alterar o cadastro ou o catálogo depois não muda a compra. |
| Produtos | Código/SKU, produto e variação (texto histórico), quantidade, preço unitário, total do item, pronta entrega ou encomenda (com previsão de chegada ou "a combinar") | O desconto existe no pedido (não por item): aparece nos totais. O ajuste da forma de pagamento já está nos preços e é explicado numa nota. |
| Totais | Subtotal, desconto, frete (Gratuito) e total | — |
| Pagamento | Forma e parcelas, **situação** (Pendente, Parcial, Pago) | **Valor pago, saldo e valor de cada parcela só para quem pode consultar o financeiro (D12)**; as parcelas saem exatamente como registradas. Sem taxas da operadora, custos, margens ou comissões. Não usa "quitado" nem "recibo". Pedido antigo sem controle de pagamento: "Não informado". |
| Entrega e montagem | Modalidade, agendamento (data e período) separado da previsão de chegada de encomenda, montagem inclusa e sua situação | Sem agendamento: "Não agendada — a combinar". |
| Observações | **Somente** o campo "Observações para o cliente" (V9), editável no detalhe do pedido | Observações internas, equipe e notas de entrega **não saem**. |
| Rodapé | Data e hora da emissão | Não há garantia, condição comercial nem declaração de recebimento. |

## Quando e para quem
- Só para vendas **confirmadas** (e canceladas que foram confirmadas, com a faixa **"PEDIDO CANCELADO"**); rascunho e "aguardando aprovação" são recusados (400). Pedidos antigos saem com "Não informado" nos dados que faltam.
- Mesmo escopo do pedido: o vendedor só emite das suas vendas (404 nas alheias); sem login, 401. Não existe URL pública nem link por número.
- Cada emissão grava **uma linha de auditoria** (pedido, usuário e data). Imprimir ou reimprimir **não** altera estoque, pagamento, comissão nem situação do pedido (testado).

## Layout
Blocos Loja, Cliente, Produtos, Pagamento, Entrega e Montagem; linha de produto não é partida entre páginas, o cabeçalho da tabela se repete, textos e endereços longos quebram sem cortar; pedidos curtos cabem em uma página (os seis cenários validados saíram em 1 página).

## Validação
`ComprovanteTest` (8 testes: totais oficiais, rascunho recusado, histórico preservado, entrega agendada x não agendada e observação só ao cliente, encomenda, pagamento pendente/pago/cartão em 3 parcelas e D12, cancelado e escopo por API direta, reimpressão sem efeito, pedido antigo incompleto) e `scripts/validacao/ui_comprovante.py` (27 verificações no navegador: A4/PDF, celular e conteúdo).

## Limitações
- Sem CNPJ (campo inexistente na configuração da loja).
- O vendedor, que não consulta o financeiro, imprime a situação do pagamento, mas não o valor pago nem o saldo.
- Textos legais (garantia, trocas, política) não existem: dependem de aprovação da loja.
