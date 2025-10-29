// src/components/NavBar.jsx
import { Link, NavLink, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

export default function NavBar() {
  const { isAuthenticated, user, logout } = useAuth();
  const navigate = useNavigate();

  function exit() {
    logout();
    navigate("/", { replace: true });
  }

  return (
    <nav className="navbar navbar-expand-lg bg-body-tertiary border-bottom mb-3">
      <div className="container">
        <Link className="navbar-brand fw-bold" to="/">Popular Móveis</Link>

        <button className="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#mainNav">
          <span className="navbar-toggler-icon"></span>
        </button>

        <div id="mainNav" className="collapse navbar-collapse">
          <ul className="navbar-nav me-auto mb-2 mb-lg-0">
            <li className="nav-item">
              <NavLink className="nav-link" to="/">Fanpage</NavLink>
            </li>
            <li className="nav-item">
              <NavLink className="nav-link" to="/loja">Loja</NavLink>
            </li>
            <li className="nav-item">
              <NavLink className="nav-link" to="/carrinho">Carrinho</NavLink>
            </li>
          </ul>

          <ul className="navbar-nav ms-auto">
            {!isAuthenticated && (
              <li className="nav-item">
                <NavLink className="btn btn-outline-primary btn-sm" to="/login">
                  Área Administrativa
                </NavLink>
              </li>
            )}

            {isAuthenticated && (
              <>
                <li className="nav-item dropdown">
                  <a className="nav-link dropdown-toggle" role="button" data-bs-toggle="dropdown">
                    Admin
                  </a>
                  <ul className="dropdown-menu dropdown-menu-end">
                    <li><NavLink className="dropdown-item" to="/admin">Dashboard</NavLink></li>
                    <li><NavLink className="dropdown-item" to="/admin/produtos">Produtos</NavLink></li>
                    <li><NavLink className="dropdown-item" to="/admin/categorias">Categorias</NavLink></li>
                    <li><NavLink className="dropdown-item" to="/admin/config">Configuração da Loja</NavLink></li>
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
  );
}
