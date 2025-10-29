// src/pages/LoginPage.jsx
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState("admin@loja.com");
  const [senha, setSenha] = useState("admin123");
  const [loading, setLoading] = useState(false);
  const [erro, setErro] = useState("");

  async function handleSubmit(e) {
    e.preventDefault();
    setErro("");
    setLoading(true);
    try {
      await login(email, senha);
      navigate("/admin", { replace: true });
    } catch (err) {
      console.error("Erro no login:", err);
      setErro("Usuário ou senha inválidos");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="container" style={{ maxWidth: 420, marginTop: 48 }}>
      <h3 className="mb-4 text-center">Acesso Administrativo</h3>

      {erro && <div className="alert alert-danger">{erro}</div>}

      <form onSubmit={handleSubmit}>
        <div className="mb-3">
          <label className="form-label">E-mail</label>
          <input type="email" className="form-control"
                 value={email} onChange={(e) => setEmail(e.target.value)} required/>
        </div>

        <div className="mb-3">
          <label className="form-label">Senha</label>
          <input type="password" className="form-control"
                 value={senha} onChange={(e) => setSenha(e.target.value)} required/>
        </div>

        <button type="submit" className="btn btn-primary w-100" disabled={loading}>
          {loading ? "Entrando..." : "Entrar"}
        </button>
      </form>
    </div>
  );
}
