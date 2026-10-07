-- Etapa 3 — Financeiro e gestão: recebimentos, caixa, contas, cartão, restituições, comissões, metas e
-- fechamento mensal. Migração ADITIVA (não altera V1–V4 além de ampliar CHECKs).
--
-- Decisões ainda pendentes viram colunas NULAS de configuração (nulo = pendente; nenhum valor é presumido):
--   D01 percentual da comissão, D02 momento de aquisição da comissão, D06 critério de competência da receita,
--   D07 perfis que reabrem período e que autorizam restituição, D09 política de restituição/cobrança de
--   diferença/metas, D10 exigência de ausência de pendências no fechamento e taxas/prazos da operadora (D11).

-- ---------------------------------------------------------------------------
-- Ampliações de CHECK
-- ---------------------------------------------------------------------------
ALTER TABLE public.pedidos DROP CONSTRAINT IF EXISTS pedidos_status_pagamento_check;
ALTER TABLE public.pedidos ADD CONSTRAINT pedidos_status_pagamento_check CHECK (status_pagamento IN
    ('NAO_INFORMADO', 'PENDENTE', 'PARCIAL', 'PAGO'));

ALTER TABLE public.encomendas DROP CONSTRAINT IF EXISTS encomendas_status_check;
ALTER TABLE public.encomendas ADD CONSTRAINT encomendas_status_check CHECK (status IN
    ('AGUARDANDO_PEDIDO', 'PEDIDO_REALIZADO', 'PARCIALMENTE_RECEBIDA', 'RECEBIDA', 'CANCELADA'));

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
    'META_DEFINIDA', 'FECHAMENTO_APROVADO', 'FECHAMENTO_REABERTO'));

-- ---------------------------------------------------------------------------
-- Configuração comercial/financeira: decisões pendentes (nulo = pendente)
-- ---------------------------------------------------------------------------
ALTER TABLE public.configuracao_comercial ADD COLUMN comissao_percentual numeric(5,2);              -- D01
ALTER TABLE public.configuracao_comercial ADD COLUMN comissao_aquisicao character varying(20);      -- D02
ALTER TABLE public.configuracao_comercial ADD COLUMN competencia_receita character varying(20);     -- D06
ALTER TABLE public.configuracao_comercial ADD COLUMN perfis_reabertura character varying(100);      -- D07
ALTER TABLE public.configuracao_comercial ADD COLUMN perfis_restituicao character varying(100);     -- D07
ALTER TABLE public.configuracao_comercial ADD COLUMN permite_restituicao boolean;                   -- D09
ALTER TABLE public.configuracao_comercial ADD COLUMN permite_cobranca_diferenca boolean;            -- D09
ALTER TABLE public.configuracao_comercial ADD COLUMN meta_desconta_devolucoes boolean;              -- D09
ALTER TABLE public.configuracao_comercial ADD COLUMN fechamento_exige_sem_pendencias boolean;       -- D10
ALTER TABLE public.configuracao_comercial ADD CONSTRAINT configuracao_comissao_pct_check
    CHECK (comissao_percentual IS NULL OR (comissao_percentual >= 0 AND comissao_percentual <= 100));
ALTER TABLE public.configuracao_comercial ADD CONSTRAINT configuracao_comissao_aq_check
    CHECK (comissao_aquisicao IS NULL OR comissao_aquisicao IN ('CONFIRMACAO', 'QUITACAO', 'ENTREGA'));
ALTER TABLE public.configuracao_comercial ADD CONSTRAINT configuracao_competencia_check
    CHECK (competencia_receita IS NULL OR competencia_receita IN ('CONFIRMACAO', 'ENTREGA'));

-- ---------------------------------------------------------------------------
-- Encomenda: recebimentos parciais do fornecedor (distintos de entrega parcial ao cliente)
-- ---------------------------------------------------------------------------
CREATE TABLE public.encomenda_recebimentos (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    encomenda_id bigint NOT NULL REFERENCES public.encomendas (id),
    quantidade integer NOT NULL,
    observacao character varying(300),
    movimentacao_id bigint REFERENCES public.movimentacoes_estoque (id),
    usuario_id bigint REFERENCES public.users (id),
    chave character varying(80) NOT NULL,
    recebido_em timestamp(6) with time zone NOT NULL,
    CONSTRAINT encomenda_recebimentos_qtd_check CHECK (quantidade > 0),
    CONSTRAINT uk_encomenda_recebimento_chave UNIQUE (chave)
);
CREATE INDEX idx_encomenda_recebimentos ON public.encomenda_recebimentos (encomenda_id);

-- ---------------------------------------------------------------------------
-- Taxas e prazos da operadora de cartão (D11: sem valores presumidos)
-- ---------------------------------------------------------------------------
CREATE TABLE public.taxas_cartao (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    operadora character varying(60) NOT NULL,
    parcelas integer NOT NULL,
    taxa_percentual numeric(7,4) NOT NULL,
    prazo_primeira_parcela_dias integer NOT NULL,
    intervalo_dias integer NOT NULL,
    ativa boolean NOT NULL DEFAULT true,
    CONSTRAINT taxas_cartao_parcelas_check CHECK (parcelas >= 1),
    CONSTRAINT taxas_cartao_taxa_check CHECK (taxa_percentual >= 0 AND taxa_percentual < 100),
    CONSTRAINT taxas_cartao_prazos_check CHECK (prazo_primeira_parcela_dias >= 0 AND intervalo_dias >= 0),
    CONSTRAINT uk_taxa_cartao UNIQUE (operadora, parcelas)
);

-- ---------------------------------------------------------------------------
-- Caixa físico: uma sessão aberta por vez
-- ---------------------------------------------------------------------------
CREATE TABLE public.sessoes_caixa (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    status character varying(10) NOT NULL,
    data_referencia date NOT NULL,
    saldo_inicial numeric(38,2) NOT NULL,
    aberta_em timestamp(6) with time zone NOT NULL,
    aberta_por bigint NOT NULL REFERENCES public.users (id),
    saldo_esperado numeric(38,2),
    saldo_contado numeric(38,2),
    diferenca numeric(38,2),
    motivo_diferenca character varying(300),
    fechada_em timestamp(6) with time zone,
    fechada_por bigint REFERENCES public.users (id),
    CONSTRAINT sessoes_caixa_status_check CHECK (status IN ('ABERTA', 'FECHADA')),
    CONSTRAINT sessoes_caixa_saldo_check CHECK (saldo_inicial >= 0)
);
CREATE UNIQUE INDEX uk_sessao_caixa_aberta ON public.sessoes_caixa (status) WHERE status = 'ABERTA';

-- ---------------------------------------------------------------------------
-- Recebimentos (pagamento do cliente) e recebíveis de cartão (valor a receber da operadora)
-- ---------------------------------------------------------------------------
CREATE TABLE public.recebimentos (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    pedido_id bigint NOT NULL REFERENCES public.pedidos (id),
    forma character varying(20) NOT NULL,
    valor numeric(38,2) NOT NULL,
    parcelas integer,
    data_pagamento date NOT NULL,
    referencia character varying(100),
    operadora character varying(60),
    observacao character varying(300),
    status character varying(15) NOT NULL,
    registrado_por bigint NOT NULL REFERENCES public.users (id),
    criado_em timestamp(6) with time zone NOT NULL,
    chave character varying(80) NOT NULL,
    estornado_em timestamp(6) with time zone,
    estornado_por bigint REFERENCES public.users (id),
    motivo_estorno character varying(300),
    chave_estorno character varying(80),
    CONSTRAINT recebimentos_forma_check CHECK (forma IN ('DINHEIRO', 'PIX', 'CARTAO')),
    CONSTRAINT recebimentos_status_check CHECK (status IN ('REGISTRADO', 'ESTORNADO')),
    CONSTRAINT recebimentos_valor_check CHECK (valor > 0),
    CONSTRAINT uk_recebimento_chave UNIQUE (chave)
);
CREATE INDEX idx_recebimentos_pedido ON public.recebimentos (pedido_id);
CREATE INDEX idx_recebimentos_data ON public.recebimentos (data_pagamento);

CREATE TABLE public.recebiveis_cartao (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    recebimento_id bigint NOT NULL REFERENCES public.recebimentos (id),
    pedido_id bigint NOT NULL REFERENCES public.pedidos (id),
    operadora character varying(60) NOT NULL,
    parcela integer NOT NULL,
    total_parcelas integer NOT NULL,
    valor_bruto numeric(38,2) NOT NULL,
    taxa_percentual numeric(7,4) NOT NULL,
    valor_taxa numeric(38,2) NOT NULL,
    valor_liquido numeric(38,2) NOT NULL,
    data_prevista date NOT NULL,
    status character varying(12) NOT NULL,
    data_liquidacao date,
    valor_liquidado numeric(38,2),
    liquidado_em timestamp(6) with time zone,
    liquidado_por bigint REFERENCES public.users (id),
    chave_liquidacao character varying(80),
    CONSTRAINT recebiveis_status_check CHECK (status IN ('PREVISTO', 'LIQUIDADO', 'CANCELADO')),
    CONSTRAINT uk_recebivel_parcela UNIQUE (recebimento_id, parcela)
);
CREATE INDEX idx_recebiveis_status_data ON public.recebiveis_cartao (status, data_prevista);
CREATE INDEX idx_recebiveis_pedido ON public.recebiveis_cartao (pedido_id);

-- ---------------------------------------------------------------------------
-- Contas a pagar e a receber (com histórico)
-- ---------------------------------------------------------------------------
CREATE TABLE public.contas_financeiras (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    tipo character varying(10) NOT NULL,
    descricao character varying(200) NOT NULL,
    categoria character varying(60) NOT NULL,
    competencia date NOT NULL,
    valor numeric(38,2) NOT NULL,
    vencimento date NOT NULL,
    situacao character varying(12) NOT NULL,
    origem character varying(20) NOT NULL,
    pedido_id bigint REFERENCES public.pedidos (id),
    ocorrencia_id bigint REFERENCES public.ocorrencias_pos_venda (id),
    vendedor_id bigint REFERENCES public.users (id),
    observacao character varying(300),
    pago_em date,
    meio character varying(10),
    criado_por bigint NOT NULL REFERENCES public.users (id),
    criado_em timestamp(6) with time zone NOT NULL,
    CONSTRAINT contas_tipo_check CHECK (tipo IN ('PAGAR', 'RECEBER')),
    CONSTRAINT contas_situacao_check CHECK (situacao IN ('ABERTA', 'PAGA', 'CANCELADA')),
    CONSTRAINT contas_origem_check CHECK (origem IN ('MANUAL', 'COMISSAO', 'DIFERENCA_TROCA')),
    CONSTRAINT contas_meio_check CHECK (meio IS NULL OR meio IN ('CAIXA', 'BANCO')),
    CONSTRAINT contas_valor_check CHECK (valor > 0)
);
CREATE INDEX idx_contas_vencimento ON public.contas_financeiras (situacao, vencimento);
CREATE UNIQUE INDEX uk_conta_diferenca_ocorrencia ON public.contas_financeiras (ocorrencia_id)
    WHERE origem = 'DIFERENCA_TROCA' AND situacao <> 'CANCELADA';

CREATE TABLE public.conta_eventos (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    conta_id bigint NOT NULL REFERENCES public.contas_financeiras (id),
    tipo character varying(12) NOT NULL,
    motivo character varying(300),
    usuario_id bigint REFERENCES public.users (id),
    criado_em timestamp(6) with time zone NOT NULL,
    CONSTRAINT conta_eventos_tipo_check CHECK (tipo IN ('CRIADA', 'ALTERADA', 'PAGA', 'ESTORNADA', 'CANCELADA'))
);
CREATE INDEX idx_conta_eventos ON public.conta_eventos (conta_id);

-- ---------------------------------------------------------------------------
-- Restituições (devolução FINANCEIRA, com controles próprios da devolução física)
-- ---------------------------------------------------------------------------
CREATE TABLE public.restituicoes (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    pedido_id bigint NOT NULL REFERENCES public.pedidos (id),
    ocorrencia_id bigint NOT NULL REFERENCES public.ocorrencias_pos_venda (id),
    valor numeric(38,2) NOT NULL,
    forma character varying(20) NOT NULL,
    status character varying(12) NOT NULL,
    motivo character varying(300) NOT NULL,
    solicitada_por bigint NOT NULL REFERENCES public.users (id),
    solicitada_em timestamp(6) with time zone NOT NULL,
    autorizada_por bigint REFERENCES public.users (id),
    autorizada_em timestamp(6) with time zone,
    efetivada_por bigint REFERENCES public.users (id),
    efetivada_em timestamp(6) with time zone,
    data_efetiva date,
    chave_efetivacao character varying(80),
    motivo_cancelamento character varying(300),
    CONSTRAINT restituicoes_status_check CHECK (status IN ('SOLICITADA', 'AUTORIZADA', 'EFETIVADA', 'CANCELADA')),
    CONSTRAINT restituicoes_valor_check CHECK (valor > 0)
);
CREATE INDEX idx_restituicoes_pedido ON public.restituicoes (pedido_id);
CREATE INDEX idx_restituicoes_ocorrencia ON public.restituicoes (ocorrencia_id);

-- ---------------------------------------------------------------------------
-- Livro de lançamentos financeiros: imutável; estorno = lançamento oposto vinculado
-- ---------------------------------------------------------------------------
CREATE TABLE public.lancamentos_financeiros (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    conta character varying(10) NOT NULL,
    tipo character varying(10) NOT NULL,
    valor numeric(38,2) NOT NULL,
    data_efetiva date NOT NULL,
    origem character varying(30) NOT NULL,
    descricao character varying(300),
    sessao_caixa_id bigint REFERENCES public.sessoes_caixa (id),
    pedido_id bigint REFERENCES public.pedidos (id),
    recebimento_id bigint REFERENCES public.recebimentos (id),
    recebivel_id bigint REFERENCES public.recebiveis_cartao (id),
    conta_financeira_id bigint REFERENCES public.contas_financeiras (id),
    restituicao_id bigint REFERENCES public.restituicoes (id),
    estorna_id bigint REFERENCES public.lancamentos_financeiros (id),
    criado_por bigint REFERENCES public.users (id),
    criado_em timestamp(6) with time zone NOT NULL,
    chave character varying(120),
    CONSTRAINT lancamentos_conta_check CHECK (conta IN ('CAIXA', 'BANCO')),
    CONSTRAINT lancamentos_tipo_check CHECK (tipo IN ('ENTRADA', 'SAIDA')),
    CONSTRAINT lancamentos_valor_check CHECK (valor > 0),
    CONSTRAINT lancamentos_origem_check CHECK (origem IN ('RECEBIMENTO', 'LIQUIDACAO_CARTAO', 'RESTITUICAO',
        'PAGAMENTO_CONTA', 'RECEBIMENTO_CONTA', 'SUPRIMENTO_CAIXA', 'RETIRADA_CAIXA')),
    -- dinheiro físico sempre pertence a uma sessão de caixa; banco nunca
    CONSTRAINT lancamentos_sessao_check CHECK ((conta = 'CAIXA') = (sessao_caixa_id IS NOT NULL))
);
CREATE INDEX idx_lancamentos_data ON public.lancamentos_financeiros (data_efetiva, conta);
CREATE INDEX idx_lancamentos_sessao ON public.lancamentos_financeiros (sessao_caixa_id);
CREATE INDEX idx_lancamentos_pedido ON public.lancamentos_financeiros (pedido_id);
CREATE UNIQUE INDEX uk_lancamento_estorno ON public.lancamentos_financeiros (estorna_id) WHERE estorna_id IS NOT NULL;
CREATE UNIQUE INDEX uk_lancamento_chave ON public.lancamentos_financeiros (chave) WHERE chave IS NOT NULL;

-- ---------------------------------------------------------------------------
-- Comissões (previsão x devida x paga) e reversões vinculadas
-- ---------------------------------------------------------------------------
CREATE TABLE public.comissoes (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    pedido_id bigint NOT NULL REFERENCES public.pedidos (id),
    vendedor_id bigint NOT NULL REFERENCES public.users (id),
    tipo character varying(10) NOT NULL,
    status character varying(12) NOT NULL,
    base numeric(38,2) NOT NULL,
    percentual numeric(5,2) NOT NULL,           -- regra histórica: percentual vigente quando o lançamento foi criado
    valor numeric(38,2) NOT NULL,               -- positivo na previsão; NEGATIVO na reversão
    competencia date,                           -- mês da aquisição (apenas quando devida)
    adquirida_em date,
    reverte_id bigint REFERENCES public.comissoes (id),
    restituicao_id bigint REFERENCES public.restituicoes (id),
    conta_id bigint REFERENCES public.contas_financeiras (id),
    motivo character varying(300),
    criada_em timestamp(6) with time zone NOT NULL,
    CONSTRAINT comissoes_tipo_check CHECK (tipo IN ('PREVISAO', 'REVERSAO')),
    CONSTRAINT comissoes_status_check CHECK (status IN ('PREVISTA', 'DEVIDA', 'EM_CONTA', 'PAGA', 'REVERTIDA',
        'LANCADA', 'COMPENSADA'))
);
CREATE UNIQUE INDEX uk_comissao_previsao_pedido ON public.comissoes (pedido_id) WHERE tipo = 'PREVISAO';
CREATE INDEX idx_comissoes_vendedor ON public.comissoes (vendedor_id, status);

-- ---------------------------------------------------------------------------
-- Metas mensais individuais
-- ---------------------------------------------------------------------------
CREATE TABLE public.metas_vendedor (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    vendedor_id bigint NOT NULL REFERENCES public.users (id),
    mes date NOT NULL,                          -- primeiro dia do mês
    valor numeric(38,2) NOT NULL,
    definida_por bigint REFERENCES public.users (id),
    definida_em timestamp(6) with time zone NOT NULL,
    CONSTRAINT metas_valor_check CHECK (valor > 0),
    CONSTRAINT uk_meta_vendedor_mes UNIQUE (vendedor_id, mes)
);

-- ---------------------------------------------------------------------------
-- Fechamento mensal versionado (aprovação do proprietário bloqueia o período)
-- ---------------------------------------------------------------------------
CREATE TABLE public.fechamentos_mensais (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    mes date NOT NULL,
    versao integer NOT NULL,
    status character varying(12) NOT NULL,
    snapshot text NOT NULL,
    aprovado_por bigint NOT NULL REFERENCES public.users (id),
    aprovado_em timestamp(6) with time zone NOT NULL,
    reaberto_por bigint REFERENCES public.users (id),
    reaberto_em timestamp(6) with time zone,
    justificativa_reabertura character varying(300),
    CONSTRAINT fechamentos_status_check CHECK (status IN ('APROVADO', 'REABERTO')),
    CONSTRAINT uk_fechamento_mes_versao UNIQUE (mes, versao)
);
-- no máximo um fechamento APROVADO (período bloqueado) por mês
CREATE UNIQUE INDEX uk_fechamento_aprovado_mes ON public.fechamentos_mensais (mes) WHERE status = 'APROVADO';
