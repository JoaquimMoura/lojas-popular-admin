// src/components/Icons.jsx
// Ícones de traço simples (decorativos, aria-hidden) usados na home pública.
const base = {
  width: 24,
  height: 24,
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.8,
  strokeLinecap: "round",
  strokeLinejoin: "round",
  "aria-hidden": "true",
  focusable: "false",
};

const PATHS = {
  truck: (
    <>
      <path d="M2 6h11v10H2z" />
      <path d="M13 9h4l3 3v4h-7" />
      <circle cx="6.5" cy="17.5" r="1.8" />
      <circle cx="16.5" cy="17.5" r="1.8" />
    </>
  ),
  wrench: (
    <path d="M14.7 6.3a4 4 0 0 0-5 5L3 18l3 3 6.7-6.7a4 4 0 0 0 5-5l-2.7 2.7-2.3-.7-.7-2.3z" />
  ),
  card: (
    <>
      <rect x="2.5" y="5" width="19" height="14" rx="2.5" />
      <path d="M2.5 10h19M6 15h4" />
    </>
  ),
  pix: (
    <>
      <path d="M12 3l4.5 4.5L12 12 7.5 7.5z" />
      <path d="M12 12l4.5 4.5L12 21l-4.5-4.5z" />
      <path d="M3 12l3-3M21 12l-3-3M3 12l3 3M21 12l-3 3" />
    </>
  ),
  sofa: (
    <>
      <path d="M5 11V8a3 3 0 0 1 3-3h8a3 3 0 0 1 3 3v3" />
      <path d="M3 13a2 2 0 0 1 4 0v2h10v-2a2 2 0 0 1 4 0v5H3z" />
      <path d="M5 18v2M19 18v2" />
    </>
  ),
  chat: (
    <>
      <path d="M4 5h16v11H9l-5 4z" />
      <path d="M8 9.5h8M8 12.5h5" />
    </>
  ),
  home: (
    <>
      <path d="M3 11l9-7 9 7" />
      <path d="M5 10v10h14V10" />
      <path d="M9.5 14.5l2 2 3.5-4" />
    </>
  ),
  cash: (
    <>
      <rect x="2.5" y="6" width="19" height="12" rx="2" />
      <circle cx="12" cy="12" r="2.6" />
    </>
  ),
  chevronLeft: <path d="M15 5l-7 7 7 7" />,
  chevronRight: <path d="M9 5l7 7-7 7" />,
  pause: (
    <>
      <path d="M8 5v14M16 5v14" />
    </>
  ),
  play: <path d="M8 5l11 7-11 7z" />,
};

export default function Icon({ name, size = 24, className }) {
  return (
    <svg {...base} width={size} height={size} className={className}>
      {PATHS[name]}
    </svg>
  );
}
