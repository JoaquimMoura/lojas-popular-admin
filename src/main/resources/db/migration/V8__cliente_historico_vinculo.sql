-- Cliente x venda: dados do cliente preservados na venda, vinculo posterior/troca de cliente com trilha e permissoes.
-- Aditiva. Nenhuma venda antiga e atribuida a cliente por suposicao: vendas sem cliente continuam sem cliente.

ALTER TABLE public.pedidos ADD COLUMN cliente_nome_hist character varying(150);
ALTER TABLE public.pedidos ADD COLUMN cliente_cpf_hist character varying(11);
ALTER TABLE public.pedidos ADD COLUMN cliente_telefone_hist character varying(20);

-- Copia o que ja era conhecido pelo vinculo existente (a venda passa a guardar o cadastro como estava).
UPDATE public.pedidos p
   SET cliente_nome_hist = c.nome, cliente_cpf_hist = c.cpf, cliente_telefone_hist = c.telefone
  FROM public.clientes c
 WHERE p.cliente_id = c.id AND p.cliente_nome_hist IS NULL;

CREATE INDEX IF NOT EXISTS idx_pedidos_cliente ON public.pedidos (cliente_id);

CREATE TABLE public.pedido_cliente_eventos (
    id bigserial PRIMARY KEY,
    pedido_id bigint NOT NULL REFERENCES public.pedidos (id),
    tipo character varying(20) NOT NULL CHECK (tipo IN ('VINCULO_POSTERIOR', 'TROCA')),
    cliente_anterior_id bigint REFERENCES public.clientes (id),
    cliente_novo_id bigint NOT NULL REFERENCES public.clientes (id),
    justificativa character varying(300) NOT NULL,
    usuario_id bigint NOT NULL REFERENCES public.users (id),
    criado_em timestamp with time zone NOT NULL DEFAULT now()
);
CREATE INDEX idx_pedido_cliente_eventos_pedido ON public.pedido_cliente_eventos (pedido_id);

-- D13: decisoes do proprietario (nulo = pendente: escopo atual / troca de cliente bloqueada)
ALTER TABLE public.configuracao_comercial ADD COLUMN vendedor_ve_historico_cliente boolean;
ALTER TABLE public.configuracao_comercial ADD COLUMN perfis_troca_cliente character varying(100);

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
    'VENDA_CLIENTE_VINCULADO', 'VENDA_CLIENTE_TROCADO', 'CLIENTE_EXCLUIDO'));
