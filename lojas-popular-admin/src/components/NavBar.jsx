import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { useCart } from "../context/CartContext";

export default function NavBar() {
  const { user, logout } = useAuth();
  const { cartItems } = useCart();
  const navigate = useNavigate();

  return (
    <nav
      className="navbar navbar-expand-lg navbar-dark"
      style={{ backgroundColor: "#B71C1C" }}
    >
      <div className="container">
        <Link className="navbar-brand fw-bold text-warning" to="/">
          Popular Moveis
        </Link>

        <div className="collapse navbar-collapse">
          <ul className="navbar-nav me-auto mb-2 mb-lg-0">
            <li className="nav-item">
              <Link className="nav-link" to="/loja">
                Loja
              </Link>
            </li>
            <li className="nav-item">
              <Link className="nav-link" to="/carrinho">
                Carrinho ({cartItems.length})
              </Link>
            </li>
          </ul>

          <div className="d-flex align-items-center gap-2">
            {!user ? (
              <button
                className="btn btn-warning btn-sm"
                onClick={() => navigate("/login")}
              >
                Area Administrativa
              </button>
            ) : (
              <button
                className="btn btn-outline-light btn-sm"
                onClick={logout}
              >
                Sair
              </button>
            )}
          </div>
        </div>
      </div>
    </nav>
  );
}
