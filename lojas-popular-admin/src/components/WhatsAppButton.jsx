const PHONE = "5511986789299"; // +55 11 98678-9299
const UTM = "utm_source=site&utm_medium=btn_whatsapp&utm_campaign=contato";

export default function WhatsAppButton({ text = "Fale com a Popular Móveis" }) {
  const msg = encodeURIComponent("Olá! Vim pelo site e gostaria de falar sobre um produto.");
  const href = `https://wa.me/${PHONE}?text=${msg}&${UTM}`;

  return (
    <a
      href={href}
      target="_blank"
      rel="noreferrer"
      className="btn btn-success"
      style={{
        position: "fixed",
        right: 16,
        bottom: 16,
        zIndex: 9999,
        borderRadius: 999,
        padding: "12px 16px",
        boxShadow: "0 6px 16px rgba(0,0,0,0.2)"
      }}
      aria-label="WhatsApp"
      title="Atendimento no WhatsApp"
    >
      💬 {text}
    </a>
  );
}
