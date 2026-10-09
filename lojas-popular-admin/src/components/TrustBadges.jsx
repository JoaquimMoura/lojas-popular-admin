import "../styles/TrustBadges.css";
import Icon from "./Icons";
import { TEXTO_PARCELAMENTO } from "../constants/loja";

const BADGES = [
  { icon: "card", label: `Cartão ${TEXTO_PARCELAMENTO}` },
  { icon: "pix", label: "Pix" },
  { icon: "cash", label: "Dinheiro" },
  { icon: "truck", label: "Frete grátis" },
  { icon: "wrench", label: "Montagem inclusa" },
];

export default function TrustBadges({ className = "trust-badges" }) {
  return (
    <ul className={className}>
      {BADGES.map((b) => (
        <li className="trust-badge" key={b.label}>
          <span className="trust-badge-icon" aria-hidden="true">
            <Icon name={b.icon} size={16} />
          </span>
          <span className="trust-badge-label">{b.label}</span>
        </li>
      ))}
    </ul>
  );
}
