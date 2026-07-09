const BADGES = [
  { icon: "💳", label: "Pix" },
  { icon: "🏦", label: "Boleto" },
  { icon: "💰", label: "Parcelado" },
  { icon: "🛡️", label: "Garantia 12m" },
  { icon: "🚚", label: "Entrega SP" },
  { icon: "🔧", label: "Montagem" },
];

export default function TrustBadges({ className = "trust-badges" }) {
  return (
    <div className={className}>
      {BADGES.map((b) => (
        <div className="trust-badge" key={b.label}>
          <span className="trust-badge-icon" aria-hidden="true">{b.icon}</span>
          <span className="trust-badge-label">{b.label}</span>
        </div>
      ))}
    </div>
  );
}
