// src/pages/Fanpage.jsx
import { useEffect, useState } from "react";
import { storeConfigApi } from "../services/storeConfigApi";
import { absUrl } from "../utils/url";

const WHATS = "11986789299"; // (11) 98678-9299
const WA_LINK = `https://wa.me/55${WHATS}?text=${encodeURIComponent("Olá! Vim pela fanpage e quero saber mais sobre os produtos.")}`;

export default function Fanpage() {
  const [cfg, setCfg] = useState({
    nomeLoja: "Popular Móveis",
    endereco: "Avenida Presidente Médici, 417",
    whatsapp: "(11) 98678-9299",
    bannerUrl: null,
  });

  useEffect(() => {
    (async () => {
      try {
        const data = await storeConfigApi.get(); // precisa estar liberado no backend (GET /config/loja)
        setCfg((c) => ({
          ...c,
          nomeLoja: data?.nomeLoja ?? c.nomeLoja,
          endereco: data?.endereco ?? c.endereco,
          whatsapp: data?.whatsapp ?? c.whatsapp,
          bannerUrl: data?.bannerUrl ?? c.bannerUrl,
        }));
      } catch {
        // fallback silencioso
      }
    })();
  }, []);

  return (
    <div className="container">
      <div className="p-4 rounded-3 border bg-light d-flex flex-column flex-md-row align-items-center gap-3">
        <div className="flex-fill">
          <h2 className="mb-1">{cfg.nomeLoja}</h2>
          <p className="text-muted mb-3">{cfg.endereco}</p>
          <a href={WA_LINK} target="_blank" rel="noreferrer"
             className="btn btn-success btn-lg">
            Falar no WhatsApp
          </a>
        </div>

        <div className="flex-fill text-center">
          {cfg.bannerUrl ? (
            <img src={absUrl(cfg.bannerUrl)} alt="Banner" className="img-fluid rounded"/>
          ) : (
            <div className="text-muted">Sem banner configurado</div>
          )}
        </div>
      </div>

      {/* Aqui você pode inserir uma vitrine simples de produtos públicos, se quiser */}
    </div>
  );
}
