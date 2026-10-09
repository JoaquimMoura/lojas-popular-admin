-- Recebimento manual: tipo do cartao (credito/debito) e marca de "plano manual" (cartao sem taxa cadastrada).
-- Aditiva; recebimentos ja gravados ficam com tipo nulo e plano_manual = false.
ALTER TABLE public.recebimentos ADD COLUMN tipo_cartao character varying(10);
ALTER TABLE public.recebimentos ADD CONSTRAINT recebimentos_tipo_cartao_check
    CHECK (tipo_cartao IS NULL OR tipo_cartao IN ('CREDITO', 'DEBITO'));
ALTER TABLE public.recebimentos ADD COLUMN plano_manual boolean NOT NULL DEFAULT false;
