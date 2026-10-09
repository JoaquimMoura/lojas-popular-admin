import { useEffect, useId, useMemo, useRef, useState } from "react";
import { useDebounce } from "./gestao/useDebounce";
import { casaBusca } from "../utils/busca";
import "./gestao/gestao.css";

/**
 * Campo de busca com sugestões (combobox). A página filtra a própria lista pelo `value`
 * (atualizado a cada tecla); as sugestões vêm de `itens` (filtro local) ou de
 * `fetchSugestoes(texto)` (assíncrono, com debounce de 250 ms).
 *
 * Teclado: setas navegam, Enter escolhe, Esc fecha (ou limpa o campo).
 */
export default function FiltroAutocomplete({
  value,
  onChange,
  itens,
  filtrarLocal = true,
  getTextoBusca,
  fetchSugestoes,
  getRotulo = (i) => String(i),
  getDetalhe,
  getValorBusca,
  getChave = (i, idx) => i?.id ?? idx,
  itemClass = "",
  onSelecionar,
  placeholder = "Buscar",
  ajuda,
  ariaLabel,
  maxSugestoes = 8,
  inline = false,
  autoFocus = false,
  className = "",
}) {
  const uid = useId();
  const listaId = `${uid}-lista`;
  const raiz = useRef(null);
  const [aberto, setAberto] = useState(false);
  const [ativo, setAtivo] = useState(-1);
  const [remotas, setRemotas] = useState([]);
  const [buscando, setBuscando] = useState(false);
  const texto = value ?? "";
  const textoDeb = useDebounce(texto.trim(), 250);

  useEffect(() => {
    if (!fetchSugestoes) return undefined;
    if (textoDeb.length < 2) {
      setRemotas([]);
      return undefined;
    }
    let vivo = true;
    setBuscando(true);
    Promise.resolve(fetchSugestoes(textoDeb))
      .then((l) => vivo && setRemotas(Array.isArray(l) ? l : []))
      .catch(() => vivo && setRemotas([]))
      .finally(() => vivo && setBuscando(false));
    return () => {
      vivo = false;
    };
  }, [textoDeb, fetchSugestoes]);

  const locais = useMemo(() => {
    if (fetchSugestoes || !itens || !textoDeb) return [];
    if (!filtrarLocal) return itens;
    return itens.filter((i) =>
      casaBusca(textoDeb, getTextoBusca ? getTextoBusca(i) : [getRotulo(i), getDetalhe?.(i)]),
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [itens, textoDeb, filtrarLocal, fetchSugestoes]);

  const sugestoes = (fetchSugestoes ? remotas : locais).slice(0, maxSugestoes);
  const mostrar = aberto && texto.trim().length >= (fetchSugestoes ? 2 : 1) && textoDeb === texto.trim();

  useEffect(() => setAtivo(-1), [textoDeb]);

  useEffect(() => {
    function fora(e) {
      if (raiz.current && !raiz.current.contains(e.target)) setAberto(false);
    }
    document.addEventListener("mousedown", fora);
    return () => document.removeEventListener("mousedown", fora);
  }, []);

  function escolher(item) {
    setAberto(false);
    setAtivo(-1);
    onChange((getValorBusca ?? getRotulo)(item));
    onSelecionar?.(item);
  }

  function aoTeclar(e) {
    if (e.key === "ArrowDown" || e.key === "ArrowUp") {
      if (sugestoes.length === 0) return;
      e.preventDefault();
      setAberto(true);
      const passo = e.key === "ArrowDown" ? 1 : -1;
      setAtivo((a) => (a < 0 ? (passo > 0 ? 0 : sugestoes.length - 1) : (a + passo + sugestoes.length) % sugestoes.length));
    } else if (e.key === "Enter") {
      if (mostrar && ativo >= 0 && sugestoes[ativo]) {
        e.preventDefault();
        escolher(sugestoes[ativo]);
      } else {
        setAberto(false);
      }
    } else if (e.key === "Escape") {
      if (mostrar && sugestoes.length > 0) {
        e.preventDefault();
        setAberto(false);
      } else if (texto) {
        e.preventDefault();
        onChange("");
      }
    }
  }

  const idOpcao = (i) => `${uid}-op-${i}`;
  const semResultado = !!fetchSugestoes && !buscando && sugestoes.length === 0;
  const listaVisivel = mostrar && (sugestoes.length > 0 || buscando || semResultado);

  return (
    <div className={`filtro-ac ${inline ? "filtro-ac-inline" : ""} ${className}`} ref={raiz}>
      <div className="filtro-ac-campo">
        <input
          type="text"
          className="form-control"
          role="combobox"
          aria-expanded={listaVisivel}
          aria-controls={listaId}
          aria-autocomplete="list"
          aria-activedescendant={listaVisivel && ativo >= 0 ? idOpcao(ativo) : undefined}
          aria-label={ariaLabel ?? placeholder}
          autoComplete="off"
          autoFocus={autoFocus}
          placeholder={placeholder}
          value={texto}
          onChange={(e) => {
            onChange(e.target.value);
            setAberto(true);
          }}
          onFocus={() => setAberto(true)}
          onKeyDown={aoTeclar}
        />
        {texto && (
          <button
            type="button"
            className="filtro-ac-limpar"
            aria-label="Limpar busca"
            onClick={() => {
              onChange("");
              setAberto(false);
            }}
          >
            &times;
          </button>
        )}
      </div>
      {ajuda && <div className="form-text">{ajuda}</div>}
      <ul id={listaId} role="listbox" className="filtro-ac-lista" hidden={!listaVisivel}>
        {sugestoes.map((item, i) => (
          <li
            key={getChave(item, i)}
            id={idOpcao(i)}
            role="option"
            aria-selected={i === ativo}
            className={`filtro-ac-item ${itemClass} ${i === ativo ? "ativo" : ""}`}
            onMouseDown={(e) => e.preventDefault()}
            onClick={() => escolher(item)}
            onMouseEnter={() => setAtivo(i)}
          >
            <span className="filtro-ac-rotulo">{getRotulo(item)}</span>
            {getDetalhe && getDetalhe(item) ? <span className="filtro-ac-detalhe">{getDetalhe(item)}</span> : null}
          </li>
        ))}
        {sugestoes.length === 0 && buscando && <li className="filtro-ac-vazio">Buscando...</li>}
        {semResultado && <li className="filtro-ac-vazio">Nenhum resultado para «{texto.trim()}»</li>}
      </ul>
    </div>
  );
}

/** Estado vazio padrão de uma lista filtrada. */
export function SemResultados({ busca, vazio = "Nenhum registro." }) {
  const t = (busca ?? "").trim();
  return <div className="text-center text-muted py-5">{t ? `Nenhum resultado para «${t}»` : vazio}</div>;
}
