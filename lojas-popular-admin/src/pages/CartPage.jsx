import { useCart } from "../context/CartContext";
import { resolveImageUrl } from "../utils/url";

export default function CartPage() {
  const { items, updateQty, remove, total, clear } = useCart();

  return (
    <div className="py-3">
      <h4 className="mb-3">Carrinho</h4>
      {items.length === 0 ? (
        <div className="text-muted">Seu carrinho está vazio.</div>
      ) : (
        <>
          <div className="table-responsive">
            <table className="table align-middle">
              <thead>
                <tr>
                  <th style={{ width: 80 }}>Imagem</th>
                  <th>Produto</th>
                  <th style={{ width: 120 }}>Preço</th>
                  <th style={{ width: 120 }}>Qtd</th>
                  <th style={{ width: 120 }}>Subtotal</th>
                  <th style={{ width: 100 }}></th>
                </tr>
              </thead>
              <tbody>
                {items.map((it) => (
                  <tr key={it.id}>
                    <td>
                      <img
                        src={resolveImageUrl(it.imagemUrl) ?? "https://via.placeholder.com/80x80?text=IMG"}
                        alt=""
                        width={64}
                        height={64}
                        style={{ objectFit: "cover" }}
                        className="rounded"
                      />
                    </td>
                    <td>{it.nome}</td>
                    <td>R$ {Number(it.preco).toFixed(2)}</td>
                    <td>
                      <input
                        type="number"
                        min={1}
                        className="form-control form-control-sm"
                        value={it.qty}
                        onChange={(e) => updateQty(it.id, Number(e.target.value || 1))}
                      />
                    </td>
                    <td>R$ {(Number(it.preco) * it.qty).toFixed(2)}</td>
                    <td>
                      <button className="btn btn-outline-danger btn-sm" onClick={() => remove(it.id)}>
                        Remover
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <td colSpan={4} className="text-end fw-bold">Total</td>
                  <td className="fw-bold">R$ {total.toFixed(2)}</td>
                  <td></td>
                </tr>
              </tfoot>
            </table>
          </div>

          <div className="d-flex gap-2">
            <button className="btn btn-outline-secondary" onClick={clear}>Limpar</button>
            <a
              className="btn btn-success"
              href={`https://wa.me/5511986789299?text=${encodeURIComponent(
                `Olá! Quero finalizar a compra:\n` +
                items.map(i => `• ${i.nome} x${i.qty} — R$ ${(i.preco * i.qty).toFixed(2)}`).join("\n") +
                `\nTotal: R$ ${total.toFixed(2)}`
              )}&utm_source=site&utm_medium=carrinho&utm_campaign=whatsapp`}
              target="_blank"
              rel="noreferrer"
            >
              Finalizar pelo WhatsApp
            </a>
          </div>
        </>
      )}
    </div>
  );
}
