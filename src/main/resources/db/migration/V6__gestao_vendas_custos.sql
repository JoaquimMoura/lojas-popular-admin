-- Etapa 3 (complemento): cadastro de custos e custo historico dos itens vendidos.
-- Aditiva: nada e preenchido para vendas antigas (custo ausente continua nulo, nunca zero).

CREATE TABLE public.custos_produto (
    id bigserial PRIMARY KEY,
    produto_id bigint NOT NULL REFERENCES public.produtos (id),
    variacao_id bigint REFERENCES public.produto_variacoes (id),
    custo numeric(12,2) NOT NULL CHECK (custo >= 0),
    vigente_desde date NOT NULL,
    motivo character varying(300),
    criado_por bigint NOT NULL REFERENCES public.users (id),
    criado_em timestamp with time zone NOT NULL DEFAULT now()
);
CREATE INDEX idx_custos_produto_busca ON public.custos_produto (produto_id, variacao_id, vigente_desde DESC, id DESC);

-- CATALOGO = congelado do cadastro de custos na confirmacao; MANUAL = informado depois, com motivo e auditoria.
-- Custos que ja existissem antes ficam sem origem, sem alterar o valor.
ALTER TABLE public.itens_pedido ADD COLUMN custo_origem character varying(20);
ALTER TABLE public.itens_pedido ADD CONSTRAINT itens_pedido_custo_origem_check
    CHECK (custo_origem IS NULL OR custo_origem IN ('CATALOGO', 'MANUAL'));

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
    'CUSTO_REGISTRADO', 'CUSTO_ITEM_INFORMADO'));
