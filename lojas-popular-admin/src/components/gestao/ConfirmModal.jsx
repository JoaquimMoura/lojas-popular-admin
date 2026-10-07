import { useState } from "react";

/**
 * Modal controlado por estado. Quando `motivo` é informado, exibe campo de texto.
 * motivo: { label, obrigatorio, max }
 */
export default function ConfirmModal({
  open,
  titulo,
  children,
  confirmLabel = "Confirmar",
  variant = "primary",
  motivo,
  busy = false,
  onConfirm,
  onCancel,
}) {
  if (!open) return null;
  return (
    <ConfirmModalInner
      titulo={titulo}
      confirmLabel={confirmLabel}
      variant={variant}
      motivo={motivo}
      busy={busy}
      onConfirm={onConfirm}
      onCancel={onCancel}
    >
      {children}
    </ConfirmModalInner>
  );
}

function ConfirmModalInner({ titulo, children, confirmLabel, variant, motivo, busy, onConfirm, onCancel }) {
  const [texto, setTexto] = useState("");
  const invalido = !!motivo?.obrigatorio && texto.trim() === "";

  function submit(e) {
    e.preventDefault();
    if (busy || invalido) return;
    onConfirm(texto.trim());
  }

  return (
    <>
      <div className="modal d-block" tabIndex={-1} role="dialog" aria-modal="true">
        <div className="modal-dialog modal-dialog-centered modal-dialog-scrollable">
          <form className="modal-content" onSubmit={submit}>
            <div className="modal-header">
              <h5 className="modal-title">{titulo}</h5>
              <button type="button" className="btn-close" aria-label="Fechar" onClick={onCancel} disabled={busy} />
            </div>
            <div className="modal-body">
              {children}
              {motivo && (
                <div className="mt-2">
                  <label className="form-label">
                    {motivo.label ?? "Motivo"}
                    {motivo.obrigatorio && <span className="text-danger"> *</span>}
                  </label>
                  <textarea
                    className="form-control"
                    rows={3}
                    maxLength={motivo.max ?? 300}
                    value={texto}
                    onChange={(e) => setTexto(e.target.value)}
                    autoFocus
                  />
                </div>
              )}
            </div>
            <div className="modal-footer flex-column flex-sm-row">
              <button type="button" className="btn btn-outline-secondary w-100 w-sm-auto" onClick={onCancel} disabled={busy}>
                Voltar
              </button>
              <button type="submit" className={`btn btn-${variant} w-100 w-sm-auto`} disabled={busy || invalido}>
                {busy ? "Aguarde..." : confirmLabel}
              </button>
            </div>
          </form>
        </div>
      </div>
      <div className="modal-backdrop show" />
    </>
  );
}
