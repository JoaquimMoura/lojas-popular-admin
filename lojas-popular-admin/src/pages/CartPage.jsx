import { useCart } from "../context/CartContext";
import WhatsAppButton from "../components/WhatsAppButton";
import { ToastContainer, toast } from "react-toastify";
import "react-toastify/dist/ReactToastify.css";

export default function CartPage() {
  const { cartItems, total, removeFromCart, clearCart } = useCart();

  if (!cartItems || cartItems.length === 0) {
    return (
      <div className="container text-center py-5">
        <h4>Seu carrinho esta vazio</h4>
        <p>Adicione produtos e finalize o pedido pelo WhatsApp.</p>
      </div>
    );
  }

  const message = `Ola! Gostaria de comprar os seguintes produtos:\n\n${cartItems
    .map((item) => `${item.nome} - R$ ${item.preco.toFixed(2)}`)
    .join("\n")}\n\nTotal: R$ ${total.toFixed(2)}`;

  const handleClearCart = () => {
    clearCart();
    toast.info("Carrinho esvaziado com sucesso!", {
      position: "top-right",
      autoClose: 2000,
      theme: "dark",
      style: { backgroundColor: "#B71C1C", color: "#fff" },
    });
  };

  return (
    <div className="container py-4">
      <h3 className="mb-3 text-danger fw-bold">Meu Carrinho</h3>

      <ul className="list-group mb-4">
        {cartItems.map((item) => (
          <li
            key={item.id}
            className="list-group-item d-flex justify-content-between align-items-center"
          >
            <div className="d-flex align-items-center gap-3">
              {item.imagemUrl && (
                <img
                  src={item.imagemUrl}
                  alt={item.nome}
                  style={{
                    width: 60,
                    height: 60,
                    objectFit: "cover",
                    borderRadius: 6,
                  }}
                  onError={(event) => {
                    event.target.src = "/assets/no-image.png";
                  }}
                />
              )}
              <span>{item.nome}</span>
            </div>
            <div>
              <span className="me-3 fw-semibold text-danger">
                R$ {item.preco.toFixed(2)}
              </span>
              <button
                className="btn btn-sm btn-outline-danger"
                onClick={() => removeFromCart(item.id)}
              >
                Remover
              </button>
            </div>
          </li>
        ))}
      </ul>

      <div className="d-flex justify-content-between align-items-center mb-3">
        <h5 className="mb-0 text-dark">
          Total: <strong>R$ {total.toFixed(2)}</strong>
        </h5>

        <div className="d-flex gap-2">
          <button
            className="btn btn-outline-secondary"
            onClick={handleClearCart}
          >
            Esvaziar Carrinho
          </button>
          <WhatsAppButton
            text={message}
            label="Finalizar pelo WhatsApp"
          />
        </div>
      </div>

      <ToastContainer />
    </div>
  );
}
