import { useCallback, useEffect, useRef, useState } from "react";

/**
 * Carrega dados de uma função assíncrona. `recarregar()` refaz a chamada sem apagar o conteúdo atual.
 * Respostas fora de ordem (filtros trocados rapidamente) são descartadas.
 */
export function useCarga(fn, deps) {
  const [dados, setDados] = useState(null);
  const [loading, setLoading] = useState(true);
  const [erro, setErro] = useState(null);
  const seq = useRef(0);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const executar = useCallback(fn, deps);

  const recarregar = useCallback(async () => {
    const n = ++seq.current;
    setLoading(true);
    setErro(null);
    try {
      const d = await executar();
      if (n === seq.current) setDados(d);
    } catch (err) {
      if (n === seq.current) setErro(err);
    } finally {
      if (n === seq.current) setLoading(false);
    }
  }, [executar]);

  useEffect(() => {
    recarregar();
  }, [recarregar]);

  return { dados, setDados, loading, erro, setErro, recarregar };
}
