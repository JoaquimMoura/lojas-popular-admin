import { useRef } from "react";
import { novaChave } from "../../utils/format";

/**
 * Chave de idempotência por tentativa. `obter(escopo)` devolve a MESMA chave enquanto o escopo
 * (ex.: id + quantidade) não mudar; `limpar()` descarta a chave após o sucesso.
 */
export function useChave() {
  const ref = useRef({ escopo: null, chave: null });
  return {
    obter(escopo = "") {
      if (!ref.current.chave || ref.current.escopo !== escopo) {
        ref.current = { escopo, chave: novaChave() };
      }
      return ref.current.chave;
    },
    limpar() {
      ref.current = { escopo: null, chave: null };
    },
  };
}
