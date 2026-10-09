-- Catalogo dinamico: materiais cadastraveis (compartilhados), categoria com varios materiais, produto com varios materiais
-- e caracteristicas configuraveis por categoria. Aditiva: nenhum produto, venda ou saldo e alterado.
-- O material unico que cada categoria tinha (coluna categorias.material) e mantido como esta (nulo permitido) e
-- COPIADO como vinculo categoria x material; nenhum material e atribuido a produto (nao ha como saber).

CREATE TABLE public.materiais (
    id bigserial PRIMARY KEY,
    nome character varying(100) NOT NULL,
    nome_normalizado character varying(100) NOT NULL UNIQUE,
    ativo boolean NOT NULL DEFAULT true,
    criado_em timestamp with time zone NOT NULL DEFAULT now()
);

-- os seis materiais que existiam como lista fixa (normalizado: minusculo, sem acento)
INSERT INTO public.materiais (nome, nome_normalizado) VALUES
    ('MDF', 'mdf'), ('MDP', 'mdp'), ('Madeira', 'madeira'), ('Ferro', 'ferro'), ('Vidro', 'vidro'), ('Plástico', 'plastico');

CREATE TABLE public.categoria_materiais (
    categoria_id bigint NOT NULL REFERENCES public.categorias (id) ON DELETE CASCADE,
    material_id bigint NOT NULL REFERENCES public.materiais (id),
    PRIMARY KEY (categoria_id, material_id)
);
INSERT INTO public.categoria_materiais (categoria_id, material_id)
SELECT c.id, m.id FROM public.categorias c JOIN public.materiais m ON m.nome_normalizado = lower(c.material);

-- a categoria passa a poder existir sem material unico; o mesmo nome com materiais diferentes ja nao e a regra
ALTER TABLE public.categorias DROP CONSTRAINT IF EXISTS categorias_material_check;
ALTER TABLE public.categorias DROP CONSTRAINT IF EXISTS uk_categoria_nome_material;
ALTER TABLE public.categorias ALTER COLUMN material DROP NOT NULL;

CREATE TABLE public.produto_materiais (
    produto_id bigint NOT NULL REFERENCES public.produtos (id) ON DELETE CASCADE,
    material_id bigint NOT NULL REFERENCES public.materiais (id),
    PRIMARY KEY (produto_id, material_id)
);

CREATE TABLE public.caracteristicas (
    id bigserial PRIMARY KEY,
    categoria_id bigint NOT NULL REFERENCES public.categorias (id) ON DELETE CASCADE,
    nome character varying(100) NOT NULL,
    nome_normalizado character varying(100) NOT NULL,
    tipo character varying(20) NOT NULL CHECK (tipo IN ('TEXTO', 'NUMERO', 'SELECAO_UNICA', 'SELECAO_MULTIPLA')),
    unidade character varying(20),
    obrigatoria boolean NOT NULL DEFAULT false,
    ordem integer NOT NULL DEFAULT 0,
    exibir_na_vitrine boolean NOT NULL DEFAULT false,
    ativa boolean NOT NULL DEFAULT true,
    criado_em timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uk_caracteristica_categoria_nome UNIQUE (categoria_id, nome_normalizado)
);

CREATE TABLE public.caracteristica_opcoes (
    id bigserial PRIMARY KEY,
    caracteristica_id bigint NOT NULL REFERENCES public.caracteristicas (id) ON DELETE CASCADE,
    valor character varying(100) NOT NULL,
    valor_normalizado character varying(100) NOT NULL,
    ordem integer NOT NULL DEFAULT 0,
    ativa boolean NOT NULL DEFAULT true,
    CONSTRAINT uk_opcao_caracteristica_valor UNIQUE (caracteristica_id, valor_normalizado)
);

CREATE TABLE public.produto_caracteristica_valores (
    id bigserial PRIMARY KEY,
    produto_id bigint NOT NULL REFERENCES public.produtos (id) ON DELETE CASCADE,
    caracteristica_id bigint NOT NULL REFERENCES public.caracteristicas (id),
    opcao_id bigint REFERENCES public.caracteristica_opcoes (id),
    valor_texto character varying(300),
    valor_numero numeric(14,3)
);
CREATE UNIQUE INDEX uk_pcv_simples ON public.produto_caracteristica_valores (produto_id, caracteristica_id) WHERE opcao_id IS NULL;
CREATE UNIQUE INDEX uk_pcv_opcao ON public.produto_caracteristica_valores (produto_id, caracteristica_id, opcao_id) WHERE opcao_id IS NOT NULL;
CREATE INDEX idx_pcv_caracteristica ON public.produto_caracteristica_valores (caracteristica_id);

ALTER TABLE public.auditoria_evento DROP CONSTRAINT IF EXISTS auditoria_evento_tipo_check;
ALTER TABLE public.auditoria_evento ADD CONSTRAINT auditoria_evento_tipo_check CHECK (tipo IN (
    'LOGIN_SUCESSO', 'LOGIN_FALHA', 'PEDIDO_CRIADO', 'PAGAMENTO_APROVADO', 'PAGAMENTO_REJEITADO',
    'PRODUTO_CRIADO', 'PRODUTO_ATUALIZADO', 'PRODUTO_REMOVIDO', 'NOTIFICACAO_FALHA', 'NOTIFICACAO_ENVIADA',
    'USUARIO_CRIADO', 'USUARIO_ALTERADO', 'USUARIO_DESATIVADO', 'USUARIO_ATIVADO',
    'CLIENTE_CRIADO', 'CLIENTE_ALTERADO',
    'CONFIG_COMERCIAL_ALTERADA',
    'VENDA_REGISTRADA', 'VENDA_ALTERADA', 'VENDA_CONFIRMADA', 'VENDA_CANCELADA',
    'DESCONTO_SOLICITADO', 'DESCONTO_APROVADO', 'DESCONTO_REJEITADO', 'DESCONTO_INVALIDADO',
    'RESERVA_CRIADA', 'RESERVA_LIBERADA',
    'ENCOMENDA_ATUALIZADA', 'ENCOMENDA_RECEBIDA', 'ESTOQUE_AJUSTADO', 'SAIDA_REGISTRADA',
    'ENTREGA_AGENDADA', 'ENTREGA_REAGENDADA', 'ENTREGA_FRUSTRADA', 'ENTREGA_CONCLUIDA',
    'MONTAGEM_AGENDADA', 'MONTAGEM_CONCLUIDA', 'MONTAGEM_DISPENSADA',
    'OCORRENCIA_ABERTA', 'OCORRENCIA_ATUALIZADA', 'DEVOLUCAO_RECEBIDA', 'OCORRENCIA_RESOLVIDA',
    'OCORRENCIA_CANCELADA',
    'RECEBIMENTO_REGISTRADO', 'RECEBIMENTO_ESTORNADO', 'CAIXA_ABERTO', 'CAIXA_MOVIMENTO', 'CAIXA_FECHADO',
    'CARTAO_LIQUIDADO', 'CARTAO_LIQUIDACAO_ESTORNADA', 'TAXA_CARTAO_ALTERADA',
    'CONTA_CRIADA', 'CONTA_ALTERADA', 'CONTA_PAGA', 'CONTA_ESTORNADA', 'CONTA_CANCELADA',
    'RESTITUICAO_SOLICITADA', 'RESTITUICAO_AUTORIZADA', 'RESTITUICAO_EFETIVADA', 'RESTITUICAO_CANCELADA',
    'COBRANCA_DIFERENCA_GERADA',
    'COMISSAO_PREVISTA', 'COMISSAO_DEVIDA', 'COMISSAO_REVERTIDA', 'COMISSAO_PAGAMENTO_GERADO',
    'META_DEFINIDA', 'FECHAMENTO_APROVADO', 'FECHAMENTO_REABERTO',
    'CUSTO_REGISTRADO', 'CUSTO_ITEM_INFORMADO',
    'VENDA_CLIENTE_VINCULADO', 'VENDA_CLIENTE_TROCADO', 'CLIENTE_EXCLUIDO',
    'COMPROVANTE_EMITIDO',
    'MATERIAL_CRIADO', 'MATERIAL_ALTERADO', 'CATEGORIA_CONFIGURADA'));
