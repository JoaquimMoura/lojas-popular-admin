import { Navigate, NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../../../context/AuthContext";
import { useFinanceiroPermissoes } from "../../../components/gestao/financeiro/useFinanceiroPermissoes";

const ABAS = [
  { to: "caixa", text: "Caixa" },
  { to: "contas", text: "Contas" },
  { to: "cartao", text: "Cartão" },
  { to: "comissoes", text: "Comissões" },
  { to: "metas", text: "Metas" },
  { to: "fechamento", text: "Fechamento" },
  { to: "restituicoes", text: "Restituições" },
  { to: "relatorios", text: "Relatórios" },
  { to: "custos", text: "Custos" },
];

export function FinanceiroIndex() {
  return <Navigate to="/gestao/financeiro/caixa" replace />;
}

export default function FinanceiroLayout() {
  const { user } = useAuth();
  const ehAdmin = user?.roles?.includes("ADMIN");
  const { perm, loading } = useFinanceiroPermissoes();
  const semAcesso = !ehAdmin && !loading && !perm.CONSULTAR;
  return (
    <div>
      <h3 className="mb-2">Financeiro</h3>
      {!ehAdmin && loading ? (
        <div className="text-muted py-3">Carregando permissões...</div>
      ) : semAcesso ? (
        <div className="alert alert-warning" role="alert">
          Seu perfil ainda não foi autorizado a consultar o financeiro (decisão D12 pendente com o proprietário).
        </div>
      ) : (
        <>
          <nav className="gestao-nav nav nav-pills flex-nowrap overflow-auto pb-2 mb-3" aria-label="Financeiro">
            {ABAS.map((a) => (
              <NavLink key={a.to} to={a.to}
                className={({ isActive }) => `nav-link text-nowrap ${isActive ? "active" : ""}`}>
                {a.text}
              </NavLink>
            ))}
          </nav>
          <Outlet />
        </>
      )}
    </div>
  );
}
