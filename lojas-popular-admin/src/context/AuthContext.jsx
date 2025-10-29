// src/context/AuthContext.jsx
import { createContext, useContext, useEffect, useMemo, useState } from "react";
import { api } from "../services/api";

const AuthCtx = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const u = localStorage.getItem("lp_user");
    return u ? JSON.parse(u) : null;
  });
  const isAuthenticated = !!user;

  async function login(email, senha) {
    const { data } = await api.post("/auth/login", { email, senha });
    localStorage.setItem("lp_token", data.accessToken);
    localStorage.setItem("lp_refresh", data.refreshToken);
    // mock do usuário (ajuste se o backend devolver dados)
    const u = { email, roles: ["ADMIN"] };
    setUser(u);
    localStorage.setItem("lp_user", JSON.stringify(u));
  }

  function logout() {
    localStorage.removeItem("lp_token");
    localStorage.removeItem("lp_refresh");
    localStorage.removeItem("lp_user");
    setUser(null);
  }

  // opcional: refresh token/validação inicial
  useEffect(() => {
    // noop por enquanto
  }, []);

  const value = useMemo(() => ({ user, isAuthenticated, login, logout }), [user, isAuthenticated]);

  return <AuthCtx.Provider value={value}>{children}</AuthCtx.Provider>;
}

export function useAuth() {
  return useContext(AuthCtx);
}
