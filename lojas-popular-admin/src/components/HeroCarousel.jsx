// src/components/HeroCarousel.jsx
import { useCallback, useEffect, useRef, useState } from "react";
import Icon from "./Icons";
import { PROMESSA_PRINCIPAL } from "../constants/loja";
import "../styles/HeroCarousel.css";

const INTERVALO_MS = 6500;
const LARGURA_MINIMA = 120; // descarta placeholders minúsculos (ex.: imagem de 1px)

function useReducedMotion() {
  const [reduzido, setReduzido] = useState(() =>
    typeof window !== "undefined" && window.matchMedia
      ? window.matchMedia("(prefers-reduced-motion: reduce)").matches
      : false
  );
  useEffect(() => {
    if (!window.matchMedia) return undefined;
    const mq = window.matchMedia("(prefers-reduced-motion: reduce)");
    const onChange = () => setReduzido(mq.matches);
    mq.addEventListener("change", onChange);
    return () => mq.removeEventListener("change", onChange);
  }, []);
  return reduzido;
}

// Pré-carrega as candidatas e mantém só as que realmente carregam com tamanho útil.
function useImagensValidas(candidatas) {
  const chave = candidatas.map((c) => c.src).join("|");
  const [validas, setValidas] = useState([]);

  useEffect(() => {
    let ativo = true;
    if (!candidatas.length) {
      setValidas([]);
      return undefined;
    }
    Promise.all(
      candidatas.map(
        (c) =>
          new Promise((resolve) => {
            const img = new Image();
            img.onload = () => resolve(img.naturalWidth >= LARGURA_MINIMA ? c : null);
            img.onerror = () => resolve(null);
            img.src = c.src;
          })
      )
    ).then((lista) => {
      if (ativo) setValidas(lista.filter(Boolean));
    });
    return () => {
      ativo = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [chave]);

  return validas;
}

function PainelMarca() {
  return (
    <div className="hero-brand-panel">
      <span className="hero-brand-panel-icon" aria-hidden="true">
        <Icon name="sofa" size={64} />
      </span>
      <p className="hero-brand-panel-title">{PROMESSA_PRINCIPAL}</p>
      <p className="hero-brand-panel-sub">Lá Casa Popular Móveis: móveis modulados com preço popular</p>
    </div>
  );
}

/**
 * Carrossel acessível do hero.
 * slides: [{ src, alt, legenda? }]. Sem imagens válidas, mostra o painel de marca.
 */
export default function HeroCarousel({ slides = [], ariaLabel = "Ambientes da Lá Casa Popular Móveis" }) {
  const validos = useImagensValidas(slides);
  const reduzido = useReducedMotion();
  const [atual, setAtual] = useState(0);
  const [pausadoPorUsuario, setPausadoPorUsuario] = useState(false);
  const [interagindo, setInteragindo] = useState(false);
  const raiz = useRef(null);

  const total = validos.length;
  const indice = total ? atual % total : 0;
  const autoplay = total > 1 && !reduzido && !pausadoPorUsuario && !interagindo;

  const ir = useCallback((i) => setAtual(total ? (i + total) % total : 0), [total]);

  useEffect(() => {
    if (!autoplay) return undefined;
    const id = setInterval(() => setAtual((a) => a + 1), INTERVALO_MS);
    return () => clearInterval(id);
  }, [autoplay]);

  const onKeyDown = (e) => {
    if (e.key === "ArrowLeft") ir(indice - 1);
    if (e.key === "ArrowRight") ir(indice + 1);
  };

  if (!total) {
    return (
      <div className="hero-carousel hero-carousel--brand">
        <PainelMarca />
      </div>
    );
  }

  return (
    <section
      ref={raiz}
      className="hero-carousel"
      aria-roledescription="carrossel"
      aria-label={ariaLabel}
      onMouseEnter={() => setInteragindo(true)}
      onMouseLeave={() => setInteragindo(false)}
      onFocus={() => setInteragindo(true)}
      onBlur={(e) => {
        if (!raiz.current?.contains(e.relatedTarget)) setInteragindo(false);
      }}
      onKeyDown={onKeyDown}
    >
      <div className="hero-carousel-viewport" aria-live={autoplay ? "off" : "polite"}>
        {validos.map((s, i) => (
          <figure
            key={s.src}
            className={`hero-slide${i === indice ? " is-active" : ""}`}
            role="group"
            aria-roledescription="slide"
            aria-label={`${i + 1} de ${total}`}
            aria-hidden={i === indice ? undefined : "true"}
          >
            <img
              src={s.src}
              alt={s.alt}
              loading={i === 0 ? "eager" : "lazy"}
              fetchPriority={i === 0 ? "high" : undefined}
              decoding="async"
            />
            {s.legenda && <figcaption className="hero-slide-caption">{s.legenda}</figcaption>}
          </figure>
        ))}
      </div>

      {total > 1 && (
        <>
          <button
            type="button"
            className="hero-nav hero-nav--prev"
            onClick={() => ir(indice - 1)}
            aria-label="Imagem anterior"
          >
            <Icon name="chevronLeft" size={22} />
          </button>
          <button
            type="button"
            className="hero-nav hero-nav--next"
            onClick={() => ir(indice + 1)}
            aria-label="Próxima imagem"
          >
            <Icon name="chevronRight" size={22} />
          </button>

          <div className="hero-controls">
            <div className="hero-dots" role="group" aria-label="Escolher imagem">
              {validos.map((s, i) => (
                <button
                  key={s.src}
                  type="button"
                  className={`hero-dot${i === indice ? " is-active" : ""}`}
                  onClick={() => ir(i)}
                  aria-label={`Ir para a imagem ${i + 1} de ${total}`}
                  aria-current={i === indice ? "true" : undefined}
                />
              ))}
            </div>
            {!reduzido && (
              <button
                type="button"
                className="hero-pause"
                onClick={() => setPausadoPorUsuario((p) => !p)}
                aria-label={
                  pausadoPorUsuario ? "Retomar rotação automática" : "Pausar rotação automática"
                }
              >
                <Icon name={pausadoPorUsuario ? "play" : "pause"} size={16} />
              </button>
            )}
          </div>
        </>
      )}
    </section>
  );
}
