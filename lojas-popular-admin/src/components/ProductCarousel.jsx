// src/components/ProductCarousel.jsx
import { useEffect, useRef } from "react";
import ProductCard from "./ProductCard";
import "../styles/ProductCarousel.css";

export default function ProductCarousel({ produtos = [], whatsapp, intervalMs = 3500 }) {
  const trackRef = useRef(null);
  const indexRef = useRef(0);

  useEffect(() => {
    indexRef.current = 0;
    if (!produtos.length) return;

    const id = setInterval(() => {
      const track = trackRef.current;
      if (!track) return;

      const nextIndex = (indexRef.current + 1) % produtos.length;
      const wrapped = nextIndex === 0;
      indexRef.current = nextIndex;

      const target = track.children[nextIndex];
      if (target) {
        track.scrollTo({ left: target.offsetLeft, behavior: wrapped ? "auto" : "smooth" });
      }
    }, intervalMs);

    return () => clearInterval(id);
  }, [produtos, intervalMs]);

  if (!produtos.length) return null;

  return (
    <div className="product-carousel-track" ref={trackRef}>
      {produtos.map((produto) => (
        <div className="product-carousel-item" key={produto.id}>
          <ProductCard produto={produto} whatsapp={whatsapp} />
        </div>
      ))}
    </div>
  );
}
