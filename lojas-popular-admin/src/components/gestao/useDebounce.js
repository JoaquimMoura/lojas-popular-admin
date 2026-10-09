import { useEffect, useState } from "react";

/** Devolve `valor` atrasado em `ms` (para buscas enquanto digita). */
export function useDebounce(valor, ms = 250) {
  const [v, setV] = useState(valor);
  useEffect(() => {
    const t = setTimeout(() => setV(valor), ms);
    return () => clearTimeout(t);
  }, [valor, ms]);
  return v;
}
