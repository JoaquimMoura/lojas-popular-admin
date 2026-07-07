// src/components/ProductCardSkeleton.jsx
export default function ProductCardSkeleton() {
  return (
    <div className="card product-card border-0 shadow-sm" aria-hidden="true">
      <div className="skeleton-box skeleton-img-box" />
      <div className="card-body text-center">
        <div className="skeleton-box skeleton-title-box mx-auto mb-2" />
        <div className="skeleton-box skeleton-text-box mx-auto mb-1" />
        <div className="skeleton-box skeleton-text-box short mx-auto mb-3" />
        <div className="skeleton-box skeleton-price-box mx-auto mb-2" />
        <div className="skeleton-box skeleton-btn-box mx-auto" />
      </div>
    </div>
  );
}
