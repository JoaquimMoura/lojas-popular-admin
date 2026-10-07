import { useState } from "react";
import FormModal from "../FormModal";

/**
 * Modal com um texto (motivo/justificativa) obrigatório e confirmação explícita.
 * `onSubmit(texto)` é assíncrono; se falhar, o modal fica aberto mostrando a mensagem do servidor.
 * `children` recebe a explicação do que a ação faz (antes do campo).
 */
export default function MotivoModal({
  titulo,
  rotulo = "Motivo",
  submitLabel = "Confirmar",
  variant = "danger",
  max = 300,
  onClose,
  onSubmit,
  children,
}) {
  const [texto, setTexto] = useState("");
  return (
    <FormModal titulo={titulo} submitLabel={submitLabel} variant={variant} size="md"
      submitDisabled={texto.trim() === ""} onClose={onClose} onSubmit={() => onSubmit(texto.trim())}>
      {children}
      <label className="form-label">{rotulo} <span className="text-danger">*</span></label>
      <textarea className="form-control" rows={3} maxLength={max} value={texto}
        onChange={(e) => setTexto(e.target.value)} autoFocus />
    </FormModal>
  );
}
