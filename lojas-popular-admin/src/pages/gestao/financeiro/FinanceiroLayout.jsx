import { Navigate, NavLink, Outlet } from "react-router-dom";

const ABAS = [
  { to: "caixa", text: "Caixa" },
  { to: "contas", text: "Contas" },
  { to: "cartao", text: "Cartão" },
  { to: "comissoes", text: "Comissões" },
  { to: "metas", text: "Metas" },
  { to: "fechamento", text: "Fechamento" },
  { to: "restituicoes", text: "Restituições" },
];

export function FinanceiroIndex() {
  return <Navigate to="/gestao/financeiro/caixa" replace />;
}

export default function FinanceiroLayout() {
  return (
    <div>
      <h3 className="mb-2">Financeiro</h3>
      <nav className="gestao-nav nav nav-pills flex-nowrap overflow-auto pb-2 mb-3" aria-label="Financeiro">
        {ABAS.map((a) => (
          <NavLink key={a.to} to={a.to}
            className={({ isActive }) => `nav-link text-nowrap ${isActive ? "active" : ""}`}>
            {a.text}
          </NavLink>
        ))}
      </nav>
      <Outlet />
    </div>
  );
}
