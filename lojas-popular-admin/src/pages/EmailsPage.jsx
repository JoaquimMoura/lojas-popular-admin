// src/pages/EmailsPage.jsx
import { useEffect, useState } from "react";
import { api } from "../services/api";

export default function EmailsPage() {
  const [items, setItems] = useState([]);
  const [erro, setErro] = useState(null);

  useEffect(() => {
    api
      .get("/admin/notificacoes/email")
      .then((res) => setItems(res.data))
      .catch((err) => setErro(err.message));
  }, []);

  return (
    <div>
      <h2>Emails Enviados</h2>
      {erro && <div style={{ color: "crimson" }}>{erro}</div>}
      <ul>
        {items.map((e) => (
          <li key={e.id}>
            <strong>{e.assunto}</strong> → {e.destinatario} [{e.status}]
          </li>
        ))}
      </ul>
    </div>
  );
}
