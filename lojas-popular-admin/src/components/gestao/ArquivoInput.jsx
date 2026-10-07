import { LIMITE_ARQUIVO_MB, arquivoGrande } from "../../utils/format";

/** Seleção de foto/PDF (limite de 5 MB). O pai deve bloquear o envio se arquivoGrande(arquivo). */
export default function ArquivoInput({ label, arquivo, onChange, obrigatorio = false }) {
  return (
    <div>
      <label className="form-label">
        {label}
        {obrigatorio && <span className="text-danger"> *</span>}
      </label>
      <input
        type="file"
        className="form-control"
        accept="image/*,application/pdf"
        onChange={(e) => onChange(e.target.files?.[0] ?? null)}
      />
      <div className={`form-text ${arquivoGrande(arquivo) ? "text-danger" : ""}`}>
        {arquivoGrande(arquivo)
          ? `O arquivo excede o limite de ${LIMITE_ARQUIVO_MB} MB.`
          : `Foto ou PDF de até ${LIMITE_ARQUIVO_MB} MB.`}
      </div>
    </div>
  );
}
