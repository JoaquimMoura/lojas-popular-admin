import { useEffect, useState } from "react";
import { Link, NavLink, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { categoriesApi } from "../services/categoriesApi";

function resolveMenu(user) {
  const roles = user?.roles ?? [];
  if (roles.includes("ADMIN")) {
    return {
      label: "Admin",
      links: [
        { to: "/admin", text: "Dashboard" },
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
        { to: "/vendedor", text: "Painel" },
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
    return () => {
      active = false;
    };
  }, []);

  function exit() {
    logout();
    navigate("/", { replace: true });
  }

  return (
    <>
      <nav className="navbar navbar-expand-lg bg-body-tertiary border-bottom">
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
             {/*  <li className="nav-item">
                <NavLink className="nav-link" to="/">
                  Fanpage
                </NavLink>
              </li>*/}
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

            <ul className="navbar-nav ms-auto">
              {!isAuthenticated && (
                <li className="nav-item">
                  <NavLink className="btn btn-outline-primary btn-sm" to="/login">
                    Area Administrativa
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
                          <NavLink className="dropdown-item" to={link.to}>
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
            <div className="nav nav-pills flex-wrap gap-2">
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
