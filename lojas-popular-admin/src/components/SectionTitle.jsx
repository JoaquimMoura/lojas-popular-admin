export default function SectionTitle({
  title,
  subtitle,
  center = false,
  light = false,
  className = "",
  subtitleClassName = "",
}) {
  const alignment = center ? "text-center" : "";
  const titleClass = ["section-title", alignment, light ? "text-white" : "", className]
    .filter(Boolean)
    .join(" ");
  const subtitleClass = [
    "section-subtitle",
    alignment,
    light ? "text-white-50" : "",
    subtitleClassName,
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <>
      <h2 className={titleClass}>{title}</h2>
      {subtitle && <p className={subtitleClass}>{subtitle}</p>}
    </>
  );
}
