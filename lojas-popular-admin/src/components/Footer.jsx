import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { storeConfigApi } from "../services/storeConfigApi";
import { categoriesApi } from "../services/categoriesApi";
import WhatsAppButton from "./WhatsAppButton";
import TrustBadges from "./TrustBadges";
import "../styles/footer.css";

export default function Footer() {
  const [storeInfo, setStoreInfo] = useState(null);
  const [categories, setCategories] = useState([]);

  useEffect(() => {
    let active = true;
    storeConfigApi
      .get()
      .then((data) => {
        if (active) setStoreInfo(data);
      })
      .catch(() => {
        if (active) setStoreInfo(null);
      });
    categoriesApi
      .list()
      .then((data) => {
        if (active) setCategories(Array.isArray(data) ? data : []);
      })
      .catch(() => {
        if (active) setCategories([]);
      });
    return () => {
      active = false;
    };
  }, []);

  const lojaNome = storeInfo?.nomeLoja ?? "Popular Moveis";
  const lojaEndereco = storeInfo?.endereco ?? null;
  const lojaWhatsapp = storeInfo?.whatsapp ?? null;
  const ano = new Date().getFullYear();

  return (
    <footer className="site-footer" role="contentinfo">
      <div className="footer-top">
        <div className="container footer-grid">
          <div className="footer-col footer-brand">
            <p className="footer-brand-name">{lojaNome}</p>
            <p className="footer-tagline">
              Móveis planejados com preço popular, entrega e montagem inclusas.
            </p>
            <WhatsAppButton
              phone={lojaWhatsapp}
              label="Falar no WhatsApp"
              text="Ola! Vim pelo site e gostaria de mais informacoes."
              className="btn btn-brand btn-sm"
            />
          </div>

          <nav className="footer-col" aria-label="Institucional">
            <h2 className="footer-col-title">Institucional</h2>
            <ul className="footer-links">
              <li>
                <Link to="/loja">Ver catálogo completo</Link>
              </li>
              <li>
                <Link to="/carrinho">Meu carrinho</Link>
              </li>
              <li>
                <Link to="/login">Área administrativa</Link>
              </li>
            </ul>
          </nav>

          {categories.length > 0 && (
            <nav className="footer-col" aria-label="Categorias">
              <h2 className="footer-col-title">Categorias</h2>
              <ul className="footer-links">
                {categories.slice(0, 6).map((categoria) => (
                  <li key={categoria.id}>
                    <Link to={`/categoria/${categoria.id}`}>{categoria.nome}</Link>
                  </li>
                ))}
              </ul>
            </nav>
          )}

          <div className="footer-col">
            <h2 className="footer-col-title">Pagamento e garantia</h2>
            <TrustBadges className="trust-badges trust-badges--footer" />
          </div>
        </div>
      </div>

      <div className="footer-bottom">
        <div className="container footer-bottom-inner">
          <span>© {ano} {lojaNome}. Todos os direitos reservados.</span>
          {lojaEndereco && <span>{lojaEndereco}</span>}
        </div>
      </div>
    </footer>
  );
}
