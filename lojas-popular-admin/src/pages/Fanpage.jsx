// src/pages/Fanpage.jsx
import { useEffect, useState } from "react";
import { storeConfigApi } from "../services/storeConfigApi";
import { absUrl } from "../utils/url";

const WHATS = "11961118141";
const WA_LINK = `https://wa.me/55${WHATS}?text=${encodeURIComponent(
  "Ola! Vim pela fanpage e quero saber mais sobre os produtos."
)}`;

export default function Fanpage() {
  const [cfg, setCfg] = useState({
    nomeLoja: "Popular Moveis",
    endereco: "Avenida Presidente Medici, 417",
    whatsapp: "(11) 96111-8141",
    bannerUrl: null,
  });

  useEffect(() => {
    (async () => {
      try {
        const data = await storeConfigApi.get();
        setCfg((current) => ({
          ...current,
          nomeLoja: data?.nomeLoja ?? current.nomeLoja,
          endereco: data?.endereco ?? current.endereco,
          whatsapp: data?.whatsapp ?? current.whatsapp,
          bannerUrl: data?.bannerUrl ?? current.bannerUrl,
        }));
      } catch {
        // fallback silencioso se a API nao estiver disponivel
      }
    })();
  }, []);

  return (
    <div className="container">
      <div className="p-4 rounded-3 border bg-light d-flex flex-column flex-md-row align-items-center gap-3">
        <div className="flex-fill">
          <h2 className="mb-1">{cfg.nomeLoja}</h2>
          <p className="text-muted mb-3">{cfg.endereco}</p>
          <a
            href={WA_LINK}
            target="_blank"
            rel="noreferrer"
            className="btn btn-success btn-lg"
          >
            Falar no WhatsApp
          </a>
        </div>

        <div className="flex-fill text-center">
          {cfg.bannerUrl ? (
            <img
              src={absUrl(cfg.bannerUrl)}
              alt="Banner"
              className="img-fluid rounded"
            />
          ) : (
            <div className="text-muted">Sem banner configurado</div>
          )}
        </div>
      </div>

      {/* Aqui voce pode inserir uma vitrine simples de produtos publicos, se quiser */}
    </div>
  );
}
