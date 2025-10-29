import { createContext, useContext, useEffect, useMemo, useState } from "react";

const CartContext = createContext(null);

export function CartProvider({ children }) {
  const [items, setItems] = useState(() => {
    try {
      const raw = localStorage.getItem("cart_items");
      return raw ? JSON.parse(raw) : [];
    } catch {
      return [];
    }
  });

  useEffect(() => {
    localStorage.setItem("cart_items", JSON.stringify(items));
  }, [items]);

  function add(produto, qty = 1) {
    setItems((prev) => {
      const idx = prev.findIndex((p) => p.id === produto.id);
      if (idx >= 0) {
        const copy = [...prev];
        copy[idx] = { ...copy[idx], qty: copy[idx].qty + qty };
        return copy;
      }
      return [...prev, { ...produto, qty }];
    });
  }

  function remove(id) {
    setItems((prev) => prev.filter((p) => p.id !== id));
  }

  function updateQty(id, qty) {
    setItems((prev) =>
      prev.map((p) => (p.id === id ? { ...p, qty: Math.max(1, qty) } : p))
    );
  }

  function clear() {
    setItems([]);
  }

  const total = useMemo(
    () => items.reduce((sum, it) => sum + (Number(it.preco) || 0) * it.qty, 0),
    [items]
  );

  return (
    <CartContext.Provider value={{ items, add, remove, updateQty, clear, total }}>
      {children}
    </CartContext.Provider>
  );
}

export function useCart() {
  const ctx = useContext(CartContext);
  if (!ctx) throw new Error("useCart deve ser usado dentro de <CartProvider />");
  return ctx;
}
