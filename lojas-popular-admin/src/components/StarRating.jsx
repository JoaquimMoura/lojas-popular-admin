export default function StarRating({ nota = 5 }) {
  return (
    <span className="fanpage-stars" aria-label={`${nota} de 5 estrelas`}>
      {Array.from({ length: 5 }).map((_, i) => (
        <span key={i} className={i < nota ? "star filled" : "star"}>★</span>
      ))}
    </span>
  );
}
