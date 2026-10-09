// src/pages/Fanpage.jsx
import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";

import { storeApi } from "../services/storeApi";
import { categoriesApi } from "../services/categoriesApi";
import { storeConfigApi } from "../services/storeConfigApi";
import { fanpageApi } from "../services/fanpageApi";
import { absUrl } from "../utils/url";
import { corrigirConfig, corrigirTexto, corrigirEndereco, semBoleto, fotoDaLoja } from "../utils/textoLoja";
import {
  PARCELAS_SEM_JUROS,
  PROMESSA_PRINCIPAL,
  TEXTO_ENTREGA,
  TEXTO_PARCELAMENTO,
} from "../constants/loja";

import ProductCard from "../components/ProductCard";
import ProductCardSkeleton from "../components/ProductCardSkeleton";
import WhatsAppButton from "../components/WhatsAppButton";
import FloatingWhatsApp from "../components/FloatingWhatsApp";
import HeroCarousel from "../components/HeroCarousel";
import Icon from "../components/Icons";
import StarRating from "../components/StarRating";
import TrustBadges from "../components/TrustBadges";
import SectionTitle from "../components/SectionTitle";

import "../styles/pages/Fanpage.css";

// ============================================================
// Fallback completo — tudo configurável via fanpageApi
// ============================================================
const FALLBACK_CONFIG = {
  announcements: [
    "🚚 Frete grátis para todos os móveis",
    "🔧 Montagem inclusa, sem custo",
    `💳 Cartão em 1x até ${PARCELAS_SEM_JUROS}x sem juros`,
    "💸 Pix e dinheiro sem acréscimo",
    "📦 Sob encomenda: prazo de 5 dias",
  ],
  heroTitle: PROMESSA_PRINCIPAL,
  heroSubtitle: "Lá Casa Popular Móveis · móveis modulados com preço popular",
  heroDescription:
    "Kits completos de sala, cozinha, quarto e escritório com condições especiais. Você escolhe, fala com a gente no WhatsApp e nós entregamos e montamos.",
  heroPrimaryLabel: "Quero ser atendido agora",
  heroPrimaryMessage: "Olá! Vi as ofertas no site e quero montar meu ambiente.",
  heroSecondaryLabel: "Ver toda a coleção",
  heroSecondaryUrl: "/loja",
  benefits: [
    {
      title: "Montagem inclusa",
      description: "Equipe própria monta tudo, sem custo.",
    },
    {
      title: "Entrega agendada",
      description: TEXTO_ENTREGA + ".",
    },
    {
      title: "Pagamento facilitado",
      description: `Cartão de 1x a ${PARCELAS_SEM_JUROS}x sem juros, Pix ou dinheiro.`,
    },
  ],
  // Sem fotos próprias das coleções: cada cartão mostra o bloco de marca até existir imagem.
  collections: [
    { name: "Salas", description: "Painéis, racks e sofás que deixam o ambiente completo." },
    { name: "Cozinhas", description: "Armários, balcões e torres para sua cozinha." },
    { name: "Quartos", description: "Guarda-roupas, camas box e cabeceiras combinando." },
  ],
  offersTitle: "Ofertas da semana",
  offersDescription: `Móveis selecionados com frete grátis, montagem inclusa e cartão em até ${PARCELAS_SEM_JUROS}x sem juros.`,
  combosTitle: "Combos planejados",
  combosDescription:
    "Kits completos com armários, mesas, cadeiras e acessórios que cabem no seu espaço e no seu bolso.",
  ctaTitle: "Atendimento personalizado",
  ctaDescription:
    "Conte para a nossa equipe como é o seu cômodo e receba um projeto com os móveis perfeitos para o seu espaço.",
  ctaHighlights: [
    "Plantão de segunda a sexta das 8h às 19h",
    "Sábado das 8h às 18h",
    "Simulação de pagamento em tempo real com as melhores condições.",
  ],
  testimonials: [
    {
      nome: "Maria Souza",
      nota: 5,
      texto:
        "Ficou lindo! A equipe montou tudo em um dia e o ambiente ficou exatamente como eu queria. Super recomendo!",
      produto: "Kit Sala Completo",
      cidade: "São Paulo, SP",
    },
    {
      nome: "João Alves",
      nota: 5,
      texto:
        "Ótimo atendimento pelo WhatsApp, me enviaram fotos e medidas de tudo antes de comprar. Chegou no prazo!",
      produto: "Guarda-Roupa 6 Portas",
      cidade: "Guarulhos, SP",
    },
    {
      nome: "Ana Lima",
      nota: 5,
      texto:
        "Comprei a cozinha compacta e me apaixonei. Prazo cumprido, qualidade excelente e a montagem foi impecável.",
      produto: "Cozinha Compacta",
      cidade: "Santo André, SP",
    },
  ],
  brands: ["Pix", "Visa", "Mastercard", "Elo", "Dinheiro"],
};

const SELOS_HERO = [
  { icon: "card", label: TEXTO_PARCELAMENTO },
  { icon: "truck", label: "Frete grátis" },
  { icon: "wrench", label: "Montagem inclusa" },
  { icon: "pix", label: "Pix" },
];

const ICONES_BENEFICIOS = ["wrench", "truck", "card"];

const PASSOS = [
  {
    icon: "sofa",
    titulo: "Escolha",
    texto: "Veja as coleções e ofertas e separe os móveis que combinam com a sua casa.",
  },
  {
    icon: "chat",
    titulo: "Fale com a gente no WhatsApp",
    texto: `Tire dúvidas, confirme medidas e combine o pagamento: Pix, dinheiro ou cartão em até ${PARCELAS_SEM_JUROS}x sem juros.`,
  },
  {
    icon: "home",
    titulo: "Entregamos e montamos, sem custo",
    texto: "Frete grátis e montagem inclusa, com data combinada com você.",
  },
];

// Títulos antigos salvos no banco que não trazem a promessa da loja.
const TITULO_LEGADO = /^popular m[óo]veis\s*[—–-]/i;

// ============================================================
// Sub-componentes internos (específicos da home)
// ============================================================

function AnnouncementBar({ items }) {
  const [current, setCurrent] = useState(0);

  useEffect(() => {
    if (!items?.length) return undefined;
    const id = setInterval(() => setCurrent((prev) => (prev + 1) % items.length), 3500);
    return () => clearInterval(id);
  }, [items]);

  if (!items?.length) return null;

  return (
    <div className="announcement-bar" role="status" aria-live="off">
      <span className="announcement-text" key={current}>
        {items[current % items.length]}
      </span>
    </div>
  );
}

function LinkOuAncora({ to, className, ariaLabel, children }) {
  if (to?.startsWith("http")) {
    return (
      <a className={className} href={to} target="_blank" rel="noreferrer" aria-label={ariaLabel}>
        {children}
      </a>
    );
  }
  return (
    <Link to={to || "/loja"} className={className} aria-label={ariaLabel}>
      {children}
    </Link>
  );
}

function CartaoColecao({ colecao }) {
  const [falhou, setFalhou] = useState(false);
  const temImagem = colecao.imagemUrl && !falhou;

  return (
    <article className={`collection-card${temImagem ? "" : " collection-card--brand"}`}>
      {temImagem && (
        <img
          className="collection-card-img"
          src={colecao.imagemUrl}
          alt=""
          loading="lazy"
          onError={() => setFalhou(true)}
          onLoad={(e) => {
            if (e.currentTarget.naturalWidth < 40) setFalhou(true);
          }}
        />
      )}
      {!temImagem && (
        <span className="collection-card-mark" aria-hidden="true">
          <Icon name="sofa" size={72} />
        </span>
      )}
      <div className="collection-overlay">
        <h3>{colecao.nome}</h3>
        <p>{colecao.descricao}</p>
        <LinkOuAncora
          to={colecao.link}
          className="btn collection-btn"
          ariaLabel={`Ver opções de ${colecao.nome}`}
        >
          Ver opções
        </LinkOuAncora>
      </div>
    </article>
  );
}

// ============================================================
// Página principal
// ============================================================

export default function Fanpage() {
  const [storeInfo, setStoreInfo] = useState(null);
  const [fanpageConfig, setFanpageConfig] = useState(FALLBACK_CONFIG);
  const [produtos, setProdutos] = useState([]);
  const [categorias, setCategorias] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function bootstrap() {
      setLoading(true);
      try {
        const [info, config, prods, cats] = await Promise.allSettled([
          storeConfigApi.get(),
          fanpageApi.get(),
          storeApi.listProducts({ page: 0, size: 60 }),
          categoriesApi.list(),
        ]);

        if (info.status === "fulfilled") setStoreInfo(info.value);
        if (config.status === "fulfilled" && config.value) {
          const limpa = Object.fromEntries(
            Object.entries(corrigirConfig(config.value)).filter(
              ([, v]) => v !== null && v !== undefined && v !== ""
            )
          );
          setFanpageConfig({ ...FALLBACK_CONFIG, ...limpa });
        }
        if (prods.status === "fulfilled") setProdutos(prods.value ?? []);
        if (cats.status === "fulfilled") setCategorias(cats.value ?? []);
      } finally {
        setLoading(false);
      }
    }
    bootstrap();
  }, []);

  // --- valores derivados ---
  const lojaNome = corrigirTexto(storeInfo?.nomeLoja) ?? "Lá Casa Popular Móveis";
  const lojaEndereco = corrigirEndereco(storeInfo?.endereco) ?? "Avenida Presidente Médici, 417";
  const lojaWhatsapp = storeInfo?.whatsapp ?? null;

  const heroTitle = TITULO_LEGADO.test(fanpageConfig.heroTitle ?? "")
    ? FALLBACK_CONFIG.heroTitle
    : fanpageConfig.heroTitle || FALLBACK_CONFIG.heroTitle;

  const announcements = semBoleto(
    fanpageConfig.announcements?.length ? fanpageConfig.announcements : FALLBACK_CONFIG.announcements
  );

  const benefits = fanpageConfig.benefits?.length
    ? fanpageConfig.benefits
    : FALLBACK_CONFIG.benefits;

  const colecoes = useMemo(() => {
    if (fanpageConfig.collections?.length) {
      return fanpageConfig.collections.map((col) => {
        const img = col.imageUrl ?? col.imagemUrl;
        return {
          nome: col.name ?? col.nome,
          descricao: col.description ?? col.descricao,
          imagemUrl: fotoDaLoja(img) ? absUrl(img) : "",
          link: col.link ?? (col.categoryId ? `/categoria/${col.categoryId}` : "/loja"),
        };
      });
    }
    return (categorias ?? []).slice(0, 6).map((cat) => ({
      nome: cat.nome,
      descricao: cat.descricao ?? "Linha completa pronta para entregar.",
      imagemUrl: cat.imagemUrl ? absUrl(cat.imagemUrl) : "",
      link: `/categoria/${cat.id}`,
    }));
  }, [fanpageConfig.collections, categorias]);

  // imagens do carrossel do hero: banner configurado, coleções, categorias e produtos
  const slidesHero = useMemo(() => {
    const lista = [];
    const vistos = new Set();
    const add = (src, alt, legenda) => {
      if (!src || vistos.has(src)) return;
      vistos.add(src);
      lista.push({ src, alt, legenda });
    };
    const banner = fanpageConfig.heroBannerUrl || storeInfo?.bannerUrl;
    if (banner) add(absUrl(banner), "Ambiente planejado Lá Casa Popular Móveis");
    colecoes.forEach((c) => add(c.imagemUrl, `${c.nome} — Lá Casa Popular Móveis`, c.nome));
    (categorias ?? []).forEach((c) =>
      add(c.imagemUrl ? absUrl(c.imagemUrl) : "", `${c.nome} — Lá Casa Popular Móveis`, c.nome)
    );
    produtos.forEach((p) =>
      add(p.imagemUrl ? absUrl(p.imagemUrl) : "", `${p.nome} — Lá Casa Popular Móveis`, p.nome)
    );
    return lista.slice(0, 5);
  }, [fanpageConfig.heroBannerUrl, storeInfo, colecoes, categorias, produtos]);

  const vitrinePrincipal = useMemo(() => produtos.slice(0, 8), [produtos]);
  const combos = useMemo(() => produtos.slice(8, 16), [produtos]);

  const categoriasComProdutos = useMemo(() => {
    if (!categorias.length) return [];
    return categorias
      .map((cat) => ({
        ...cat,
        produtos: produtos.filter((p) => p.categoria === cat.nome).slice(0, 4),
      }))
      .filter((cat) => cat.produtos.length > 0)
      .slice(0, 3);
  }, [categorias, produtos]);

  const heroSecondaryUrl = fanpageConfig.heroSecondaryUrl ?? "/loja";
  const ctaHighlights =
    fanpageConfig.ctaHighlights?.length > 0
      ? fanpageConfig.ctaHighlights
      : FALLBACK_CONFIG.ctaHighlights;

  const testimonials =
    fanpageConfig.testimonials?.length > 0
      ? fanpageConfig.testimonials
      : FALLBACK_CONFIG.testimonials;

  const brandsLista = semBoleto(
    fanpageConfig.brands?.length > 0 ? fanpageConfig.brands : FALLBACK_CONFIG.brands
  ).filter((b) => b !== "Brasil Card");

  return (
    <div className="fanpage">
      {/* ── Barra de anúncio ── */}
      <AnnouncementBar items={announcements} />

      {/* ── Hero ── */}
      <section className="hero-section" aria-labelledby="hero-titulo">
        <div className="container hero-grid">
          <div className="hero-text">
            {fanpageConfig.heroSubtitle && (
              <p className="hero-subtitle">{fanpageConfig.heroSubtitle}</p>
            )}
            <h1 className="hero-title" id="hero-titulo">
              {heroTitle}
            </h1>
            <p className="hero-description">{fanpageConfig.heroDescription}</p>

            <ul className="hero-seals" aria-label="Condições da loja">
              {SELOS_HERO.map((s) => (
                <li key={s.label} className="hero-seal">
                  <Icon name={s.icon} size={18} />
                  <span>{s.label}</span>
                </li>
              ))}
            </ul>

            <div className="hero-cta">
              <WhatsAppButton
                phone={lojaWhatsapp}
                text={fanpageConfig.heroPrimaryMessage}
                label={fanpageConfig.heroPrimaryLabel}
                className="btn btn-lg hero-wa-btn"
              />
              {fanpageConfig.heroSecondaryLabel && (
                <LinkOuAncora to={heroSecondaryUrl} className="btn btn-lg hero-secondary-btn">
                  {fanpageConfig.heroSecondaryLabel}
                </LinkOuAncora>
              )}
            </div>
          </div>

          <div className="hero-media">
            <HeroCarousel slides={slidesHero} />
          </div>
        </div>
      </section>

      {/* ── Faixa de benefícios ── */}
      <section className="benefits-strip" aria-label="Benefícios">
        <div className="container">
          <ul className="benefits-list">
            {benefits.map((benefit, i) => (
              <li key={benefit.title} className="benefit-item">
                <span className="benefit-icon" aria-hidden="true">
                  <Icon name={ICONES_BENEFICIOS[i % ICONES_BENEFICIOS.length]} size={26} />
                </span>
                <span className="benefit-body">
                  <strong>{benefit.title}</strong>
                  <span>{benefit.description}</span>
                </span>
              </li>
            ))}
          </ul>
        </div>
      </section>

      {/* ── Coleções ── */}
      {colecoes.length > 0 && (
        <section className="container section-block">
          <SectionTitle
            title="Coleções para cada ambiente"
            subtitle="Ambientes completos, prontos para caber no seu orçamento."
          />
          <div className="snap-row snap-row--3">
            {colecoes.map((colecao) => (
              <div className="snap-item" key={colecao.nome}>
                <CartaoColecao colecao={colecao} />
              </div>
            ))}
          </div>
        </section>
      )}

      {/* ── Vitrine de ofertas ── */}
      <section className="offers-band" aria-labelledby="ofertas-titulo">
        <div className="container">
          <div className="d-flex flex-column flex-md-row align-items-md-end justify-content-between mb-4 gap-3">
            <div id="ofertas-titulo">
              <SectionTitle
                light
                className="mb-2"
                subtitleClassName="mb-0"
                title={fanpageConfig.offersTitle}
                subtitle={fanpageConfig.offersDescription}
              />
            </div>
            <Link to="/loja" className="btn btn-accent btn-lg px-4 fw-bold">
              Ver todas as ofertas
            </Link>
          </div>

          <div className="snap-row snap-row--4">
            {loading ? (
              Array.from({ length: 4 }).map((_, i) => (
                <div className="snap-item" key={`sk-${i}`}>
                  <ProductCardSkeleton />
                </div>
              ))
            ) : vitrinePrincipal.length === 0 ? (
              <p className="offers-empty">
                Os produtos aparecerão aqui assim que forem publicados.
              </p>
            ) : (
              vitrinePrincipal.map((produto, i) => (
                <div className="snap-item" key={`vitrine-${produto.id}`}>
                  <ProductCard
                    produto={produto}
                    whatsapp={lojaWhatsapp}
                    badge={i < 4 ? "Oferta" : undefined}
                  />
                </div>
              ))
            )}
          </div>
        </div>
      </section>

      {/* ── Como funciona ── */}
      <section className="container section-block" aria-labelledby="como-funciona">
        <div id="como-funciona">
          <SectionTitle
            center
            subtitleClassName="mx-auto"
            title="Como funciona"
            subtitle="Comprar com a Lá Casa Popular Móveis é simples, do primeiro contato à montagem."
          />
        </div>
        <ol className="steps">
          {PASSOS.map((passo, i) => (
            <li className="step-card" key={passo.titulo}>
              <span className="step-number" aria-hidden="true">
                {i + 1}
              </span>
              <span className="step-icon" aria-hidden="true">
                <Icon name={passo.icon} size={34} />
              </span>
              <h3 className="step-title">{passo.titulo}</h3>
              <p className="step-text">{passo.texto}</p>
            </li>
          ))}
        </ol>
        <div className="text-center mt-4">
          <WhatsAppButton
            phone={lojaWhatsapp}
            label="Começar pelo WhatsApp"
            text={fanpageConfig.heroPrimaryMessage}
            className="btn btn-lg"
          />
        </div>
      </section>

      {/* ── Categorias com produtos ── */}
      {categoriasComProdutos.length > 0 && (
        <section className="container section-block">
          <SectionTitle
            title="Ambientes completos"
            subtitle="Combine móveis da mesma linha para montar um ambiente harmonioso e funcional."
          />

          <div className="d-flex flex-column gap-5">
            {categoriasComProdutos.map((categoria) => (
              <div key={categoria.id}>
                <header className="d-flex justify-content-between align-items-center mb-3 gap-2">
                  <div>
                    <h3 className="category-title mb-1">{categoria.nome}</h3>
                    {categoria.descricao && (
                      <span className="category-desc small">{categoria.descricao}</span>
                    )}
                  </div>
                  <Link to={`/categoria/${categoria.id}`} className="btn btn-brand-outline btn-sm">
                    Ver tudo
                  </Link>
                </header>

                <div className="snap-row snap-row--4">
                  {categoria.produtos.map((produto) => (
                    <div className="snap-item" key={`cat-${categoria.id}-prod-${produto.id}`}>
                      <ProductCard produto={produto} whatsapp={lojaWhatsapp} />
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </section>
      )}

      {/* ── Combos ── */}
      {combos.length > 0 && (
        <section className="container section-block">
          <div className="combo-banner rounded-4 shadow-sm">
            <div>
              <SectionTitle
                light
                title={fanpageConfig.combosTitle}
                subtitle={fanpageConfig.combosDescription}
              />
              <WhatsAppButton
                phone={lojaWhatsapp}
                label="Quero montar meu combo"
                text="Olá! Quero montar um combo completo com a Lá Casa Popular Móveis."
                className="btn btn-lg px-4"
              />
            </div>
          </div>

          <div className="snap-row snap-row--4 mt-3">
            {combos.map((produto) => (
              <div className="snap-item" key={`combo-${produto.id}`}>
                <ProductCard produto={produto} whatsapp={lojaWhatsapp} />
              </div>
            ))}
          </div>
        </section>
      )}

      {/* ── Depoimentos ── */}
      <section className="testimonials-band">
        <div className="container">
          <SectionTitle
            center
            subtitleClassName="mx-auto mb-4"
            title="O que nossos clientes dizem"
            subtitle="Mais de 1.000 famílias já transformaram seus lares com a Lá Casa Popular Móveis."
          />

          <div className="snap-row snap-row--3">
            {testimonials.map((t, i) => (
              <div className="snap-item" key={`t-${i}`}>
                <div className="testimonial-card rounded-4 shadow-sm p-4 h-100">
                  <StarRating nota={t.nota ?? 5} />
                  <p className="testimonial-text mt-3 mb-3">&ldquo;{t.texto}&rdquo;</p>
                  <div className="testimonial-footer">
                    <span className="testimonial-name fw-bold">{t.nome}</span>
                    {t.produto && (
                      <span className="testimonial-product d-block">Comprou: {t.produto}</span>
                    )}
                    {t.cidade && <span className="testimonial-city">{t.cidade}</span>}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── Formas de pagamento ── */}
      <section className="brands-strip" aria-label="Formas de pagamento">
        <div className="container">
          <p className="brands-strip-label">
            Formas de pagamento: cartão de 1x a {PARCELAS_SEM_JUROS}x sem juros
          </p>
          <ul className="brands-list">
            {brandsLista.map((brand) => (
              <li key={brand} className="brands-item">
                {brand}
              </li>
            ))}
          </ul>
        </div>
      </section>

      {/* ── CTA final ── */}
      <section className="container section-block mb-5">
        <div className="cta-panel rounded-4 shadow-sm p-4 p-md-5">
          <div>
            <SectionTitle
              light
              className="mb-2"
              subtitleClassName="mb-0"
              title={fanpageConfig.ctaTitle}
              subtitle={fanpageConfig.ctaDescription}
            />
            <ul className="cta-highlights mt-4">
              {ctaHighlights.map((text, index) => (
                <li key={`highlight-${index}`}>{text}</li>
              ))}
            </ul>
            <p className="cta-address mt-3 mb-0">
              <strong>{lojaNome}</strong> · {lojaEndereco}
            </p>
          </div>

          <div className="d-flex flex-column gap-3">
            <div className="d-flex flex-column flex-md-row align-items-stretch gap-3">
              <WhatsAppButton
                phone={lojaWhatsapp}
                label="Iniciar atendimento"
                text="Olá! Quero falar com a Lá Casa Popular Móveis para montar meu ambiente."
                className="btn btn-lg flex-fill"
              />
              <Link to="/loja" className="btn btn-accent btn-lg flex-fill fw-bold">
                Ver catálogo completo
              </Link>
            </div>

            <TrustBadges className="trust-badges trust-badges--cta" />
          </div>
        </div>
      </section>

      <FloatingWhatsApp phone={lojaWhatsapp} />
    </div>
  );
}
