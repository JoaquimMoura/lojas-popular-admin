import { useEffect, useState } from "react";
import { Link, NavLink, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { categoriesApi } from "../services/categoriesApi";
import { storeConfigApi } from "../services/storeConfigApi";
import WhatsAppButton from "./WhatsAppButton";
import "../styles/navbar.css";

function resolveMenu(user) {
  const roles = user?.roles ?? [];
  if (roles.includes("ADMIN")) {
    return {
      label: "Admin",
      links: [
        { to: "/admin", text: "Dashboard", end: true },
        { to: "/admin/produtos", text: "Produtos" },
        { to: "/admin/categorias", text: "Categorias" },
        { to: "/admin/fanpage", text: "Fanpage" },
        { to: "/admin/config", text: "Configuracao da Loja" },
      ],
    };
  }
  if (roles.includes("VENDEDOR")) {
    return {
      label: "Vendedor",
      links: [
        { to: "/vendedor", text: "Painel", end: true },
        { to: "/vendedor/produtos", text: "Produtos" },
        { to: "/vendedor/categorias", text: "Categorias" },
      ],
    };
  }
  return null;
}

export default function NavBar() {
  const { isAuthenticated, user, logout } = useAuth();
  const navigate = useNavigate();
  const menu = resolveMenu(user);
  const [categories, setCategories] = useState([]);
  const [storeWhatsapp, setStoreWhatsapp] = useState(null);
  const [busca, setBusca] = useState("");

  useEffect(() => {
    let active = true;
    categoriesApi
      .list()
      .then((data) => {
        if (active) setCategories(Array.isArray(data) ? data : []);
      })
      .catch(() => {
        if (active) setCategories([]);
      });
    storeConfigApi
      .get()
      .then((data) => {
        if (active) setStoreWhatsapp(data?.whatsapp ?? null);
      })
      .catch(() => {
        if (active) setStoreWhatsapp(null);
      });
    return () => {
      active = false;
    };
  }, []);

  function exit() {
    logout();
    navigate("/", { replace: true });
  }

  function handleSearch(e) {
    e.preventDefault();
    const termo = busca.trim();
    navigate(termo ? `/loja?busca=${encodeURIComponent(termo)}` : "/loja");
  }

  return (
    <>
      <nav className="site-navbar navbar navbar-expand-lg bg-body-tertiary border-bottom">
        <div className="container">
          <Link className="navbar-brand fw-bold" to="/">
            Popular Moveis
          </Link>

          <button
            className="navbar-toggler"
            type="button"
            data-bs-toggle="collapse"
            data-bs-target="#mainNav"
            aria-controls="mainNav"
            aria-expanded="false"
            aria-label="Toggle navigation"
          >
            <span className="navbar-toggler-icon" />
          </button>

          <div id="mainNav" className="collapse navbar-collapse">
            <ul className="navbar-nav me-auto mb-2 mb-lg-0">
              <li className="nav-item">
                <NavLink className="nav-link" to="/loja">
                  Loja
                </NavLink>
              </li>
              <li className="nav-item">
                <NavLink className="nav-link" to="/carrinho">
                  Carrinho
                </NavLink>
              </li>
            </ul>

            <form className="navbar-search mb-2 mb-lg-0" onSubmit={handleSearch} role="search">
              <label htmlFor="navbar-busca" className="visually-hidden">
                Buscar produto
              </label>
              <input
                id="navbar-busca"
                type="search"
                placeholder="Buscar produto..."
                value={busca}
                onChange={(e) => setBusca(e.target.value)}
              />
              <button type="submit">Buscar</button>
            </form>

            <WhatsAppButton
              phone={storeWhatsapp}
              label="WhatsApp"
              text="Ola! Vim pelo site e gostaria de mais informacoes."
              className="btn btn-success navbar-whatsapp ms-lg-3"
            />

            <ul className="navbar-nav ms-auto">
              {!isAuthenticated && (
                <li className="nav-item">
                  <NavLink className="navbar-admin-link" to="/login">
                    Área Administrativa
                  </NavLink>
                </li>
              )}

              {isAuthenticated && menu && (
                <>
                  <li className="nav-item dropdown">
                    <button
                      className="nav-link dropdown-toggle btn btn-link"
                      type="button"
                      data-bs-toggle="dropdown"
                    >
                      {menu.label}
                    </button>
                    <ul className="dropdown-menu dropdown-menu-end">
                      {menu.links.map((link) => (
                        <li key={link.to}>
                          <NavLink className="dropdown-item" to={link.to} end={link.end}>
                            {link.text}
                          </NavLink>
                        </li>
                      ))}
                    </ul>
                  </li>
                  <li className="nav-item ms-2">
                    <button className="btn btn-outline-danger btn-sm" onClick={exit}>
                      Sair
                    </button>
                  </li>
                </>
              )}
            </ul>
          </div>
        </div>
      </nav>

      {categories.length > 0 && (
        <div className="bg-white border-bottom mb-3">
          <div className="container py-2">
            <div className="category-pills nav nav-pills flex-wrap gap-2">
              {categories.map((categoria) => (
                <NavLink
                  key={categoria.id}
                  to={`/categoria/${categoria.id}`}
                  className={({ isActive }) =>
                    `nav-link px-3 py-1 ${isActive ? "active fw-semibold" : "text-secondary"}`
                  }
                >
                  {categoria.nome}
                </NavLink>
              ))}
            </div>
          </div>
        </div>
      )}
    </>
  );
}
