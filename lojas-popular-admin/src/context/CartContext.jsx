import { createContext, useContext, useState, useEffect } from "react";

const CartContext = createContext();

export function CartProvider({ children }) {
  const [cartItems, setCartItems] = useState([]);
  const [total, setTotal] = useState(0);

  useEffect(() => {
    const saved = localStorage.getItem("cart");
    if (saved) {
      const parsed = JSON.parse(saved);
      setCartItems(parsed);
      setTotal(parsed.reduce((sum, i) => sum + i.preco, 0));
    }
  }, []);

  useEffect(() => {
    localStorage.setItem("cart", JSON.stringify(cartItems));
    setTotal(cartItems.reduce((sum, i) => sum + i.preco, 0));
  }, [cartItems]);

  const addToCart = (produto) => {
    if (!produto?.id) return;
    setCartItems((prev) => {
      const exists = prev.find((p) => p.id === produto.id);
      if (exists) return prev;
      return [...prev, produto];
    });
  };

  const removeFromCart = (id) => {
    setCartItems((prev) => prev.filter((p) => p.id !== id));
  };

  const clearCart = () => {
    setCartItems([]);
    localStorage.removeItem("cart");
  };

  return (
    <CartContext.Provider
      value={{ cartItems, total, addToCart, removeFromCart, clearCart }}
    >
      {children}
    </CartContext.Provider>
  );
}

export const useCart = () => useContext(CartContext);
