import { useState } from "react";
import ErroAlert from "./ErroAlert";

/**
 * Modal de formulário. `onSubmit` é assíncrono: se lançar erro, o modal permanece aberto e
 * mostra o erro do backend em alerta inline; em sucesso, quem chama fecha o modal.
 */
export default function FormModal({
  titulo,
  onClose,
  onSubmit,
  submitLabel = "Confirmar",
  variant = "primary",
  submitDisabled = false,
  size = "lg",
  children,
}) {
  const [busy, setBusy] = useState(false);
  const [erro, setErro] = useState(null);

  async function submit(e) {
    e.preventDefault();
    if (busy || submitDisabled) return;
    setBusy(true);
    setErro(null);
    try {
      await onSubmit();
    } catch (err) {
      setErro(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <div className="modal d-block" tabIndex={-1} role="dialog" aria-modal="true">
        <div className={`modal-dialog modal-${size} modal-dialog-centered modal-dialog-scrollable`}>
          <form className="modal-content" onSubmit={submit}>
            <div className="modal-header">
              <h5 className="modal-title">{titulo}</h5>
              <button type="button" className="btn-close" aria-label="Fechar" onClick={onClose} disabled={busy} />
            </div>
            <div className="modal-body">
              <ErroAlert erro={erro} onClose={() => setErro(null)} />
              {children}
            </div>
            <div className="modal-footer flex-column flex-sm-row">
              <button type="button" className="btn btn-outline-secondary w-100 w-sm-auto" onClick={onClose} disabled={busy}>
                Voltar
              </button>
              <button type="submit" className={`btn btn-${variant} w-100 w-sm-auto`} disabled={busy || submitDisabled}>
                {busy ? "Aguarde..." : submitLabel}
              </button>
            </div>
          </form>
        </div>
      </div>
      <div className="modal-backdrop show" />
    </>
  );
}
