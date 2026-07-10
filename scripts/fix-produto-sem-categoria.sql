-- Corrige produtos salvos sem categoria (categoria_id NULL) antes do campo
-- "Categoria" se tornar obrigatorio no formulario do painel admin.
-- Seguro rodar mais de uma vez: so afeta linhas com categoria_id ainda NULL.

UPDATE public.produtos
SET categoria_id = (SELECT id FROM public.categorias WHERE nome = 'Dormitórios')
WHERE nome = 'Guarda Roupa de Canto SONHO MEU'
  AND categoria_id IS NULL;
