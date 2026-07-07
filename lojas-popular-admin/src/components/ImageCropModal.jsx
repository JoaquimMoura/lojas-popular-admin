// src/components/ImageCropModal.jsx
import { useRef, useState } from "react";
import ReactCrop, { centerCrop, makeAspectCrop } from "react-image-crop";
import "react-image-crop/dist/ReactCrop.css";

function initCrop(w, h) {
  // Default: free-form crop, 85% centered
  return centerCrop(
    makeAspectCrop({ unit: "%", width: 85 }, w / h, w, h),
    w,
    h
  );
}

export default function ImageCropModal({ src, label, onCrop, onCancel, remaining = 0 }) {
  const imgRef = useRef(null);
  const [crop, setCrop] = useState();
  const [completed, setCompleted] = useState(null);

  function onImageLoad(e) {
    const { naturalWidth: w, naturalHeight: h } = e.currentTarget;
    setCrop(initCrop(w, h));
  }

  function applyFull() {
    // Use image as-is (no crop needed)
    fetch(src)
      .then(r => r.blob())
      .then(blob => {
        const file = new File([blob], "original.jpg", { type: blob.type || "image/jpeg" });
        onCrop(file);
      });
  }

  function applyCrop() {
    if (!completed || !imgRef.current) return;
    const image = imgRef.current;
    const canvas = document.createElement("canvas");
    const scaleX = image.naturalWidth / image.width;
    const scaleY = image.naturalHeight / image.height;
    const dpr = window.devicePixelRatio || 1;

    canvas.width  = Math.round(completed.width  * scaleX);
    canvas.height = Math.round(completed.height * scaleY);

    const ctx = canvas.getContext("2d");
    ctx.imageSmoothingQuality = "high";
    ctx.drawImage(
      image,
      completed.x * scaleX,
      completed.y * scaleY,
      completed.width  * scaleX,
      completed.height * scaleY,
      0, 0,
      canvas.width,
      canvas.height
    );

    canvas.toBlob(blob => {
      if (!blob) return;
      const file = new File([blob], "cropped.jpg", { type: "image/jpeg" });
      onCrop(file);
    }, "image/jpeg", 0.92);
  }

  const canApply = completed && completed.width > 0 && completed.height > 0;

  return (
    <div
      className="modal show d-block"
      style={{ background: "rgba(0,0,0,0.72)", zIndex: 1060 }}
      tabIndex="-1"
      onClick={e => e.target === e.currentTarget && onCancel()}
    >
      <div className="modal-dialog modal-xl modal-dialog-centered modal-dialog-scrollable">
        <div className="modal-content">
          <div className="modal-header py-2">
            <div>
              <h6 className="modal-title mb-0 text-danger fw-bold">Recortar imagem</h6>
              {label && <small className="text-muted">{label}</small>}
            </div>
            {remaining > 0 && (
              <span className="badge bg-warning text-dark ms-2">+{remaining} restante{remaining > 1 ? "s" : ""}</span>
            )}
            <button type="button" className="btn-close ms-auto" onClick={onCancel} />
          </div>

          <div className="modal-body p-2 d-flex justify-content-center align-items-start"
            style={{ background: "#1a1a1a", minHeight: 260, overflowY: "auto" }}>
            <ReactCrop
              crop={crop}
              onChange={c => setCrop(c)}
              onComplete={c => setCompleted(c)}
              keepSelection
              style={{ maxWidth: "100%" }}
            >
              <img
                ref={imgRef}
                src={src}
                alt="Recortar"
                onLoad={onImageLoad}
                style={{ maxWidth: "100%", maxHeight: "65vh", display: "block" }}
                crossOrigin="anonymous"
              />
            </ReactCrop>
          </div>

          <div className="modal-footer py-2 gap-2 flex-wrap">
            <small className="text-muted me-auto">
              Selecione a área a manter. Logotipos de fornecedores ficam fora da seleção.
            </small>
            <button type="button" className="btn btn-outline-secondary btn-sm" onClick={onCancel}>
              Cancelar
            </button>
            <button type="button" className="btn btn-outline-dark btn-sm" onClick={applyFull}>
              Usar sem recorte
            </button>
            <button
              type="button"
              className="btn btn-danger btn-sm"
              onClick={applyCrop}
              disabled={!canApply}
            >
              Aplicar recorte {remaining > 0 ? `(${remaining} restante${remaining > 1 ? "s" : ""})` : ""}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
