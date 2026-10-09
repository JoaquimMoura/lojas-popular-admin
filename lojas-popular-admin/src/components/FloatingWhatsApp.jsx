// src/components/FloatingWhatsApp.jsx
import { useEffect, useState } from "react";
import { buildWhatsAppUrl } from "../utils/whatsapp";
import { MENSAGEM_WHATSAPP_GERAL } from "../constants/loja";
import { WaIcon } from "./WhatsAppButton";
import "../styles/FloatingWhatsApp.css";

// Botão fixo de WhatsApp. Some quando o rodapé entra na tela para não cobri-lo.
export default function FloatingWhatsApp({ phone, text = MENSAGEM_WHATSAPP_GERAL }) {
  const [sobreRodape, setSobreRodape] = useState(false);

  useEffect(() => {
    const rodape = document.querySelector(".site-footer");
    if (!rodape || typeof IntersectionObserver === "undefined") return undefined;
    const obs = new IntersectionObserver(
      ([entry]) => setSobreRodape(entry.isIntersecting),
      { threshold: 0 }
    );
    obs.observe(rodape);
    return () => obs.disconnect();
  }, []);

  return (
    <a
      href={buildWhatsAppUrl(phone, text)}
      target="_blank"
      rel="noopener noreferrer"
      className={`floating-wa${sobreRodape ? " floating-wa--hidden" : ""}`}
      aria-label="Falar com a Lá Casa Popular Móveis no WhatsApp"
      tabIndex={sobreRodape ? -1 : 0}
    >
      <WaIcon size={26} className="floating-wa-icon" />
      <span className="floating-wa-label">Fale no WhatsApp</span>
    </a>
  );
}
