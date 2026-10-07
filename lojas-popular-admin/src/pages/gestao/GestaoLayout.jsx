import { NavLink, Outlet } from "react-router-dom";
import { ToastContainer } from "react-toastify";
import "react-toastify/dist/ReactToastify.css";
import { useAuth } from "../../context/AuthContext";
import { gestaoLinks } from "../../utils/gestaoMenu";
import "../../components/gestao/gestao.css";

export default function GestaoLayout() {
  const { user } = useAuth();
  const links = gestaoLinks(user);
  return (
    <div className="gestao-page">
      <nav className="gestao-nav nav nav-pills flex-nowrap overflow-auto pb-2 mb-3" aria-label="Gestão">
        {links.map((l) => (
          <NavLink
            key={l.to}
            to={l.to}
            className={({ isActive }) => `nav-link text-nowrap ${isActive ? "active" : ""}`}
          >
            {l.text}
          </NavLink>
        ))}
      </nav>
      <Outlet />
      <ToastContainer position="top-center" autoClose={4000} />
    </div>
  );
}
