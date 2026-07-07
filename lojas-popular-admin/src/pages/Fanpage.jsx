// src/pages/Fanpage.jsx
import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";

import { storeApi } from "../services/storeApi";
import { categoriesApi } from "../services/categoriesApi";
import { storeConfigApi } from "../services/storeConfigApi";
import { fanpageApi } from "../services/fanpageApi";
import { absUrl } from "../utils/url";

import ProductCard from "../components/ProductCard";
import ProductCardSkeleton from "../components/ProductCardSkeleton";
import WhatsAppButton from "../components/WhatsAppButton";

import "../styles/Fanpage.css";

// ============================================================
// Fallback completo — tudo configurável via fanpageApi
// ============================================================
const FALLBACK_CONFIG = {
  announcements: [
    "🚚 Entrega em até 48h na Grande São Paulo",
    "💳 Parcelamento em até 12x sem juros",
    "🔧 Montagem inclusa em todos os combos",
  ],
  heroTitle: "Popular Moveis — moveis planejados com preco popular",
  heroSubtitle: "Sua casa renovada sem complicacoes",
  heroDescription:
    "Kits completos de sala, cozinha, quarto e escritorio com condicoes especiais. Entregamos e montamos em tempo recorde para voce usar no mesmo dia.",
  heroPrimaryLabel: "Quero ser atendido agora",
  heroPrimaryMessage: "Ola! Vi as ofertas na fanpage e quero montar meu ambiente.",
  heroSecondaryLabel: "Ver toda a colecao",
  heroSecondaryUrl: "/loja",
  benefits: [
    { icon: "🔧", title: "Montagem rapida", description: "Equipe propria e agenda flexivel." },
    { icon: "🚚", title: "Entrega expressa", description: "Despacho em ate 48h na capital." },
    { icon: "💳", title: "Pagamento facilitado", description: "Parcelamento em ate 12x sem juros." },
  ],
  collections: [
    {
      name: "Salas Planejadas",
      description: "Paineis, racks e sofas que deixam o ambiente completo.",
      imageUrl:
        "https://images.unsplash.com/photo-1484100356142-db6ab6244067?auto=format&fit=crop&w=1200&q=80",
    },
    {
      name: "Cozinhas Compactas",
      description: "Kits completos com armarios, balcoes e torres.",
      imageUrl:
        "https://images.unsplash.com/photo-1588853431081-02531f0526eb?auto=format&fit=crop&w=1200&q=80",
    },
    {
      name: "Quartos Aconchegantes",
      description: "Guarda-roupas, camas box e cabeceras combinando.",
      imageUrl:
        "https://images.unsplash.com/photo-1505692794403-55b39b05e08c?auto=format&fit=crop&w=1200&q=80",
    },
  ],
  offersTitle: "Ofertas imperdiveis da semana",
  offersDescription:
    "Conjuntos selecionados para renovar sua casa com descontos exclusivos da fanpage.",
  combosTitle: "Combos planejados",
  combosDescription:
    "Kits completos com armarios, mesas, cadeiras e acessorios que cabem no seu espaco e no seu bolso.",
  ctaTitle: "Atendimento personalizado",
  ctaDescription:
    "Conte para a nossa equipe como e o seu comodo e receba um projeto com os moveis perfeitos para o seu espaco.",
  ctaHighlights: [
    "Plantao de segunda a sabado das 8h as 20h;",
    "Envio de catalogo atualizado em PDF e video tour dos produtos;",
    "Simulacao de pagamento em tempo real com as melhores condicoes.",
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
};

const FALLBACK_HERO_IMAGE =
  "https://images.unsplash.com/photo-1616594039964-1959be9883a7?auto=format&fit=crop&w=1400&q=80";

// ============================================================
// Sub-componentes internos
// ============================================================

function StarRating({ nota = 5 }) {
  return (
    <span className="fanpage-stars" aria-label={`${nota} de 5 estrelas`}>
      {Array.from({ length: 5 }).map((_, i) => (
        <span key={i} className={i < nota ? "star filled" : "star"}>★</span>
      ))}
    </span>
  );
}

function AnnouncementBar({ items }) {
  const [current, setCurrent] = useState(0);

  useEffect(() => {
    if (!items?.length) return;
    const id = setInterval(
      () => setCurrent((prev) => (prev + 1) % items.length),
      3500
    );
    return () => clearInterval(id);
  }, [items]);

  if (!items?.length) return null;

  return (
    <div className="announcement-bar">
      <span className="announcement-text">{items[current]}</span>
    </div>
  );
}

function TrustBadges() {
  const badges = [
    { icon: "💳", label: "Pix" },
    { icon: "🏦", label: "Boleto" },
    { icon: "💰", label: "Parcelado" },
    { icon: "🛡️", label: "Garantia 12m" },
    { icon: "🚚", label: "Entrega SP" },
    { icon: "🔧", label: "Montagem" },
  ];

  return (
    <div className="trust-badges">
      {badges.map((b) => (
        <div className="trust-badge" key={b.label}>
          <span className="trust-badge-icon">{b.icon}</span>
          <span className="trust-badge-label">{b.label}</span>
        </div>
      ))}
    </div>
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
          setFanpageConfig({ ...FALLBACK_CONFIG, ...config.value });
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
  const heroBanner = useMemo(() => {
    if (fanpageConfig.heroBannerUrl) return absUrl(fanpageConfig.heroBannerUrl);
    if (storeInfo?.bannerUrl) return absUrl(storeInfo.bannerUrl);
    return FALLBACK_HERO_IMAGE;
  }, [fanpageConfig.heroBannerUrl, storeInfo]);

  const lojaNome = storeInfo?.nomeLoja ?? "Popular Moveis";
  const lojaEndereco = storeInfo?.endereco ?? "Avenida Presidente Medici, 417";
  const lojaWhatsapp = storeInfo?.whatsapp ?? null;

  const announcements =
    fanpageConfig.announcements?.length
      ? fanpageConfig.announcements
      : FALLBACK_CONFIG.announcements;

  const benefits = fanpageConfig.benefits?.length
    ? fanpageConfig.benefits
    : FALLBACK_CONFIG.benefits;

  const colecoes = useMemo(() => {
    if (fanpageConfig.collections?.length) {
      return fanpageConfig.collections.map((col) => ({
        nome: col.name ?? col.nome,
        descricao: col.description ?? col.descricao,
        imagemUrl: col.imageUrl
          ? absUrl(col.imageUrl)
          : FALLBACK_CONFIG.collections[0].imageUrl,
        link: col.link ?? (col.categoryId ? `/categoria/${col.categoryId}` : "/loja"),
      }));
    }
    if (categorias?.length) {
      return categorias.slice(0, 6).map((cat, i) => ({
        nome: cat.nome,
        descricao: cat.descricao ?? "Linha completa pronta para entregar.",
        imagemUrl: cat.imagemUrl
          ? absUrl(cat.imagemUrl)
          : FALLBACK_CONFIG.collections[i % FALLBACK_CONFIG.collections.length].imageUrl,
        link: `/categoria/${cat.id}`,
      }));
    }
    return FALLBACK_CONFIG.collections.map((col) => ({
      nome: col.name,
      descricao: col.description,
      imagemUrl: col.imageUrl,
      link: "/loja",
    }));
  }, [fanpageConfig.collections, categorias]);

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

  return (
    <div className="fanpage">
      {/* ── Barra de anúncio ── */}
      <AnnouncementBar items={announcements} />

      {/* ── Hero ── */}
      <section className="hero-section container">
        <div className="hero-content rounded-4 shadow-sm">
          <div className="hero-text">
            <img
              src="/uploads/logo-lojas-popular.svg"
              alt="Lojas Popular - um novo jeito de comprar"
              className="hero-logo mb-3"
            />
            <p className="hero-subtitle">{fanpageConfig.heroSubtitle}</p>
            <h1 className="hero-title">{fanpageConfig.heroTitle}</h1>
            <p className="hero-description">{fanpageConfig.heroDescription}</p>

            <div className="hero-cta">
              <WhatsAppButton
                phone={lojaWhatsapp}
                text={fanpageConfig.heroPrimaryMessage}
                label={fanpageConfig.heroPrimaryLabel}
              />
              {heroSecondaryUrl?.startsWith("http") ? (
                <a
                  className="btn btn-outline-dark btn-lg px-4"
                  href={heroSecondaryUrl}
                  target="_blank"
                  rel="noreferrer"
                >
                  {fanpageConfig.heroSecondaryLabel}
                </a>
              ) : (
                <Link to={heroSecondaryUrl} className="btn btn-outline-dark btn-lg px-4">
                  {fanpageConfig.heroSecondaryLabel}
                </Link>
              )}
            </div>

            <ul className="hero-benefits list-unstyled mt-4">
              {benefits.map((benefit) => (
                <li key={benefit.title}>
                  {benefit.icon && (
                    <span className="benefit-icon">{benefit.icon}</span>
                  )}
                  <span className="benefit-body">
                    <strong>{benefit.title}</strong>
                    <span>{benefit.description}</span>
                  </span>
                </li>
              ))}
            </ul>
          </div>

          <div
            className="hero-banner rounded-4"
            style={{ backgroundImage: `url(${heroBanner})` }}
            aria-label="Ambiente planejado Popular Moveis"
          >
            <div className="hero-info-card shadow">
              <p className="mb-1 fw-semibold">{lojaNome}</p>
              <span className="d-block text-muted small">{lojaEndereco}</span>
              {lojaWhatsapp && (
                <span className="d-block text-muted small">{lojaWhatsapp}</span>
              )}
            </div>
          </div>
        </div>
      </section>

      {/* ── Coleções ── */}
      <section className="container my-5">
        <h2 className="section-title">Colecoes para cada ambiente</h2>
        <p className="section-subtitle">
          Ambientes completos inspirados nas principais tendencias de decoracao, prontos para caber
          no seu orcamento.
        </p>

        <div className="row g-3 g-lg-4">
          {colecoes.map((colecao) => (
            <div className="col-12 col-md-6 col-lg-4" key={colecao.nome}>
              <article
                className="collection-card rounded-4"
                style={{ backgroundImage: `url(${colecao.imagemUrl})` }}
              >
                <div className="collection-overlay rounded-4">
                  <h3>{colecao.nome}</h3>
                  <p>{colecao.descricao}</p>
                  {colecao.link?.startsWith("http") ? (
                    <a
                      href={colecao.link}
                      className="btn btn-light btn-sm"
                      target="_blank"
                      rel="noreferrer"
                    >
                      Ver mais opcoes
                    </a>
                  ) : (
                    <Link to={colecao.link} className="btn btn-light btn-sm">
                      Ver mais opcoes
                    </Link>
                  )}
                </div>
              </article>
            </div>
          ))}
        </div>
      </section>

      {/* ── Vitrine de ofertas ── */}
      <section className="container py-5 bg-light rounded-4 shadow-sm">
        <div className="d-flex flex-column flex-md-row align-items-md-center justify-content-between mb-4 gap-3">
          <div>
            <h2 className="section-title mb-2">{fanpageConfig.offersTitle}</h2>
            <p className="section-subtitle mb-0">{fanpageConfig.offersDescription}</p>
          </div>
          <Link to="/loja" className="btn btn-danger px-4">
            Ver todas as ofertas
          </Link>
        </div>

        <div className="row g-4">
          {loading
            ? Array.from({ length: 4 }).map((_, i) => (
                <div className="col-6 col-lg-3" key={`sk-${i}`}>
                  <ProductCardSkeleton />
                </div>
              ))
            : vitrinePrincipal.length === 0
            ? (
                <div className="col-12 text-center text-muted py-5">
                  Os produtos aparecerao aqui assim que forem publicados.
                </div>
              )
            : vitrinePrincipal.map((produto, i) => (
                <div className="col-6 col-lg-3" key={`vitrine-${produto.id}`}>
                  <ProductCard
                    produto={produto}
                    whatsapp={lojaWhatsapp}
                    badge={i < 4 ? "Oferta" : undefined}
                  />
                </div>
              ))}
        </div>
      </section>

      {/* ── Categorias com produtos ── */}
      {categoriasComProdutos.length > 0 && (
        <section className="container my-5">
          <h2 className="section-title">Ambientes completos</h2>
          <p className="section-subtitle">
            Combine moveis da mesma linha para montar um ambiente harmonioso e funcional.
          </p>

          <div className="row gy-5">
            {categoriasComProdutos.map((categoria) => (
              <div className="col-12" key={categoria.id}>
                <header className="d-flex justify-content-between align-items-center mb-3">
                  <div>
                    <h3 className="category-title mb-1">{categoria.nome}</h3>
                    {categoria.descricao && (
                      <span className="text-muted small">{categoria.descricao}</span>
                    )}
                  </div>
                  <Link to={`/categoria/${categoria.id}`} className="btn btn-link">
                    Ver tudo
                  </Link>
                </header>

                <div className="row g-4">
                  {categoria.produtos.map((produto) => (
                    <div
                      className="col-6 col-lg-3"
                      key={`cat-${categoria.id}-prod-${produto.id}`}
                    >
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
        <section className="container my-5">
          <div className="combo-banner rounded-4 shadow-sm">
            <div>
              <h2 className="section-title text-white">{fanpageConfig.combosTitle}</h2>
              <p className="section-subtitle text-white-50">{fanpageConfig.combosDescription}</p>
              <WhatsAppButton
                phone={lojaWhatsapp}
                label="Quero montar meu combo"
                text="Ola! Quero montar um combo completo com a Popular Moveis."
                className="btn btn-success btn-lg px-4"
              />
            </div>
          </div>

          <div className="row g-4 mt-3">
            {combos.map((produto) => (
              <div className="col-6 col-lg-3" key={`combo-${produto.id}`}>
                <ProductCard produto={produto} whatsapp={lojaWhatsapp} />
              </div>
            ))}
          </div>
        </section>
      )}

      {/* ── Depoimentos ── */}
      <section className="container my-5">
        <h2 className="section-title text-center">O que nossos clientes dizem</h2>
        <p className="section-subtitle text-center mx-auto mb-4">
          Mais de 1.000 familias ja transformaram seus lares com a Popular Moveis.
        </p>

        <div className="row g-4">
          {testimonials.map((t, i) => (
            <div className="col-12 col-md-4" key={`t-${i}`}>
              <div className="testimonial-card rounded-4 shadow-sm p-4 h-100">
                <StarRating nota={t.nota ?? 5} />
                <p className="testimonial-text mt-3 mb-3">"{t.texto}"</p>
                <div className="testimonial-footer">
                  <span className="testimonial-name fw-bold">{t.nome}</span>
                  {t.produto && (
                    <span className="testimonial-product text-muted small d-block">
                      Comprou: {t.produto}
                    </span>
                  )}
                  {t.cidade && (
                    <span className="testimonial-city text-muted small">{t.cidade}</span>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* ── CTA final ── */}
      <section className="container mb-5" style={{ marginTop: "50px" }}>
        <div className="cta-panel rounded-4 border shadow-sm p-4 p-md-5">
          <div>
            <h2 className="section-title mb-2">{fanpageConfig.ctaTitle}</h2>
            <p className="section-subtitle mb-0">{fanpageConfig.ctaDescription}</p>
            <ul className="cta-highlights mt-4">
              {ctaHighlights.map((text, index) => (
                <li key={`highlight-${index}`}>{text}</li>
              ))}
            </ul>
          </div>

          <div className="d-flex flex-column gap-3">
            <div className="d-flex flex-column flex-md-row align-items-stretch gap-3">
              <WhatsAppButton
                phone={lojaWhatsapp}
                label="Iniciar atendimento"
                text="Ola! Quero falar com a Popular Moveis para montar meu ambiente."
                className="btn btn-success btn-lg flex-fill"
              />
              <Link to="/loja" className="btn btn-outline-secondary btn-lg flex-fill">
                Ver catalogo completo
              </Link>
            </div>

            <TrustBadges />
          </div>
        </div>
      </section>
    </div>
  );
}
