// src/components/PrivateRoute.jsx
import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

export default function PrivateRoute({ roles = ["ADMIN"], children }) {
  const { isAuthenticated, user } = useAuth();

  if (!isAuthenticated) return <Navigate to="/login" replace />;

  const hasRole = roles.length === 0 || roles.some((r) => user?.roles?.includes(r));
  if (!hasRole) return <Navigate to="/" replace />;

  // permite uso como wrapper ou com Outlet
  return children ? children : <Outlet />;
}
