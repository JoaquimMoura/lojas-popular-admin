/** Modal genérico (controlado por quem renderiza). Use para formulários maiores. */
export default function ModalShell({ titulo, onClose, children, size = "lg" }) {
  return (
    <>
      <div className="modal d-block" tabIndex={-1} role="dialog" aria-modal="true">
        <div className={`modal-dialog modal-${size} modal-dialog-scrollable`}>
          <div className="modal-content">
            <div className="modal-header">
              <h5 className="modal-title">{titulo}</h5>
              <button type="button" className="btn-close" aria-label="Fechar" onClick={onClose} />
            </div>
            <div className="modal-body">{children}</div>
          </div>
        </div>
      </div>
      <div className="modal-backdrop show" />
    </>
  );
}
