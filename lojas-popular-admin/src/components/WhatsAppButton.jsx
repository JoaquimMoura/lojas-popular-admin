// src/components/WhatsAppButton.jsx
export default function WhatsAppButton({ text, label = "Falar no WhatsApp" }) {
  const phone = "5511961118141"; // numero da loja (sem sinal de +)
  const message = text ?? "Ola! Gostaria de falar com a loja.";
  const url = `https://wa.me/${phone}?text=${encodeURIComponent(message)}`;

  return (
    <a
      href={url}
      target="_blank"
      rel="noopener noreferrer"
      className="btn btn-success btn-lg"
      style={{
        backgroundColor: "#25D366",
        border: "none",
        fontWeight: "bold",
      }}
    >
      {label}
    </a>
  );
}


