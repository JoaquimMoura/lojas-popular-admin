# Catálogo dinâmico: categorias, materiais e características

Evolução do cadastro para que a loja crie materiais, características e opções **sem alterar código** (migração V10; V1 a V9 intactas). Nenhuma regra depende do nome de uma categoria: "Colchões" e "Guarda-roupas" são só exemplos.

## Conceitos (três coisas diferentes)
| Conceito | O que é | Onde fica |
|---|---|---|
| **Categoria** | Organiza os produtos (Colchões, Sofás, Guarda-roupas…) | `categorias` (nome e descrição) |
| **Material** | Composição (espuma, látex, MDF, madeira, tecido…). **Um cadastro compartilhado**: "Madeira" serve a várias categorias. Uma categoria permite vários materiais; um produto pode ter mais de um, escolhidos por quem cadastra (nunca presumidos) | `materiais`, `categoria_materiais`, `produto_materiais` |
| **Característica** | Descreve o produto (densidade, tipo de mola, acabamento, medidas…), configurada **por categoria** | `caracteristicas`, `caracteristica_opcoes`, `produto_caracteristica_valores` |

**Características não são variações.** Cor, tamanho ou qualquer opção que mude **preço, SKU ou estoque** continua nas **variações** do produto. Característica só descreve: não gera combinações nem saldo. As dimensões do produto (largura, altura, profundidade, peso) continuam nos campos existentes.

## Como usar
1. **Materiais**: no formulário (categoria ou produto), digite no campo de materiais; se não existir, **"+ Cadastrar material"** abre um modal, salva e já seleciona, sem perder o que foi preenchido. Duplicidade por maiúscula, espaço ou acento é recusada. Material em uso é **inativado** (os vínculos ficam; só não pode mais ser escolhido).
2. **Categoria**: nome e descrição; **materiais disponíveis** (seleção múltipla; nada vem marcado); **características** (opcional): "+ Adicionar característica" com nome, tipo (texto, número com unidade como cm/kg, escolher uma opção, escolher várias), obrigatório ou opcional, mostrar na vitrine e ordem; para os tipos de escolha, "+ Adicionar opção". Dá para salvar uma categoria simples e configurar depois.
3. **Produto**: ao escolher a categoria, os materiais permitidos e as características aparecem sozinhos, cada um no tipo certo; só os **obrigatórios** são exigidos. Os valores marcados "mostrar na vitrine" aparecem na página do produto.
4. **Trocar a categoria de um produto**: o sistema mostra, antes de salvar, os materiais e valores que **deixam de se aplicar** e só descarta com a sua confirmação.
5. **Remover algo já usado** (opção, característica) **só desativa**: o produto mantém o valor histórico e a opção deixa de ser oferecida.
6. **Campo obrigatório novo** não invalida produtos antigos: eles ficam marcados **"Complementar cadastro"** (lista por categoria) e continuam vendáveis.

## Regras validadas no servidor (também por API direta)
Tipo do valor (texto, número ≥ 0, opção), uma opção só em "escolher uma", opção pertencente à característica certa, característica pertencente à categoria do produto, material disponível na categoria, opção/material/característica inativos não aceitam **novas** seleções, obrigatórios ausentes, tipo de característica **em uso** não muda, nome de categoria e de característica únicos (sem acento/maiúscula).

## Permissões (proposta, sem ampliar acesso)
| Ação | Quem |
|---|---|
| Cadastrar/alterar **materiais** e configurar **materiais e características da categoria** | Proprietário e gerente |
| Criar/editar categoria **simples** (nome e descrição), preencher **materiais e características do produto**, escolher materiais existentes | Como antes: proprietário, gerente e vendedor |
| Cadastrar material **durante o preenchimento do produto** | Só quem pode (proprietário e gerente) |
O vendedor continua com o acesso ao catálogo que já tinha; a **configuração nova** ficou restrita a gerente/proprietário. Se a loja quiser que o vendedor também cadastre materiais, é uma decisão a registrar.

## Migração e compatibilidade (V10)
- Os 6 materiais antigos (MDF, MDP, Madeira, Ferro, Vidro, Plástico) entram no cadastro **sem duplicar**; cada categoria que tinha um material recebe **o vínculo categoria × material correspondente**. A coluna antiga `categorias.material` é mantida (agora opcional) e não é mais usada.
- **Nenhum material é atribuído a produto** (não há como saber): fica como **pendência de complementação** da loja. Nenhum preço, estoque, venda, financeiro ou comprovante é alterado; vendas e comprovantes guardam sua própria cópia do produto.
- **Ambiguidade registrada**: antes, o mesmo nome podia existir com materiais diferentes (ex.: "Guarda-roupa" em MDF e em MDP, duas categorias). Elas continuam como estão, cada uma com seu material; unificá-las (mover os produtos) é decisão manual da loja. Novas categorias não podem repetir nome.
- Ensaio na cópia do banco real continua obrigatório (`scripts/ensaio-migracao-v3.sh` confere a V10: 6 materiais, vínculos 1:1 com o material antigo, nenhum produto com material, produtos inalterados).

## Limitações
- Opções e características são por categoria (não há biblioteca global de características).
- Mover várias categorias/produtos em lote não existe; unificar categorias antigas repetidas é manual.
- Filtros da vitrine por característica não fazem parte desta etapa.

## Validação
`CatalogoDinamicoTest` (8 testes: duplicidade e busca sem acento, categoria simples sem material padrão, campos dinâmicos validados por tipo e por característica, materiais da categoria, inativação preservando vínculos, troca de categoria com confirmação, campo obrigatório novo identificando pendentes, permissões) e `scripts/validacao/api_catalogo.py`.
