// src/components/ProductGalleryModal.jsx
import { useEffect, useMemo, useRef, useState } from "react";
import { productsApi } from "../services/productsApi";
import { resolveImageUrl } from "../utils/url";

export default function ProductGalleryModal({ produto, onClose, onUpdated }) {
  const [gallery, setGallery] = useState(produto.galeria ?? []);
  const [dragIndex, setDragIndex] = useState(null);
  const [overIndex, setOverIndex] = useState(null);
  const [zoomUrl, setZoomUrl] = useState(null);
  const inputRef = useRef(null);
  const dropRef = useRef(null);

  useEffect(() => setGallery(produto.galeria ?? []), [produto]);

  // Upload (input)
  const handleSelectFiles = async (evt) => {
    const files = Array.from(evt.target.files || []);
    if (!files.length) return;
    await productsApi.uploadGallery(produto.id, files);
    await onUpdated();
  };

  // Upload (drag&drop)
  useEffect(() => {
    const el = dropRef.current;
    if (!el) return;
    const prevent = (e) => { e.preventDefault(); e.stopPropagation(); };

    const onDrop = async (e) => {
      prevent(e);
      const files = Array.from(e.dataTransfer.files || []);
      if (!files.length) return;
      await productsApi.uploadGallery(produto.id, files);
      await onUpdated();
    };

    el.addEventListener("dragenter", prevent);
    el.addEventListener("dragover", prevent);
    el.addEventListener("dragleave", prevent);
    el.addEventListener("drop", onDrop);
    return () => {
      el.removeEventListener("dragenter", prevent);
      el.removeEventListener("dragover", prevent);
      el.removeEventListener("dragleave", prevent);
      el.removeEventListener("drop", onDrop);
    };
  }, [produto.id, onUpdated]);

  // Delete
  const removeImage = async (url) => {
    if (!confirm("Remover esta imagem da galeria?")) return;
    await productsApi.deleteImage(produto.id, url);
    await onUpdated();
  };

  // Drag & Drop de ordenação
  const onDragStart = (idx) => setDragIndex(idx);
  const onDragEnter = (idx) => setOverIndex(idx);
  const onDragEnd = async () => {
    if (dragIndex === null || overIndex === null || dragIndex === overIndex) {
      setDragIndex(null); setOverIndex(null);
      return;
    }
    const novo = [...gallery];
    const [moved] = novo.splice(dragIndex, 1);
    novo.splice(overIndex, 0, moved);
    setGallery(novo);
    setDragIndex(null); setOverIndex(null);

    // persiste ordenação:
    await productsApi.reorderGallery(produto.id, novo);
    await onUpdated();
  };

  return (
    <>
      <div className="modal fade show d-block" tabIndex="-1" style={{ background: "rgba(0,0,0,0.4)" }}>
        <div className="modal-dialog modal-xl">
          <div className="modal-content">

            <div className="modal-header">
              <h5 className="modal-title">Galeria: {produto.nome}</h5>
              <button className="btn-close" onClick={onClose}></button>
            </div>

            <div className="modal-body">
              {/* Área de Upload */}
              <div className="mb-3">
                <div className="d-flex gap-2 align-items-center">
                  <button className="btn btn-primary" onClick={() => inputRef.current?.click()}>
                    Adicionar imagens
                  </button>
                  <input
                    ref={inputRef}
                    type="file"
                    accept="image/*"
                    multiple
                    className="d-none"
                    onChange={handleSelectFiles}
                  />
                  <small className="text-muted">Você também pode arrastar as imagens para a área abaixo.</small>
                </div>
              </div>

              {/* Dropzone */}
              <div
                ref={dropRef}
                className="border border-2 rounded p-3 mb-3"
                style={{ borderStyle: "dashed" }}
              >
                <div className="row g-2">
                  {gallery.map((url, idx) => (
                    <div
                      className="col-3"
                      key={idx}
                      draggable
                      onDragStart={() => onDragStart(idx)}
                      onDragEnter={() => onDragEnter(idx)}
                      onDragEnd={onDragEnd}
                      style={{
                        outline:
                          overIndex === idx
                            ? "2px solid #0d6efd"
                            : dragIndex === idx
                            ? "2px dashed #0d6efd"
                            : "none",
                        borderRadius: 6,
                        padding: 4,
                      }}
                    >
                      <div className="position-relative">
                        <img
                          src={resolveImageUrl(url)}
                          alt=""
                          className="img-thumbnail"
                          loading="lazy"
                          style={{ height: 120, objectFit: "cover", width: "100%" }}
                          onClick={() => setZoomUrl(resolveImageUrl(url))}
                          onError={(e) => { e.currentTarget.src = "data:image/gif;base64,R0lGODlhAQABAIAAAAUEBA=="; }}
                        />

                        <button
                          type="button"
                          className="btn btn-sm btn-danger position-absolute"
                          style={{ top: 6, right: 6 }}
                          onClick={() => removeImage(url)}
                          title="Remover"
                        >
                          ×
                        </button>
                      </div>
                      <div className="text-center text-muted mt-1" style={{ fontSize: 12 }}>
                        {idx + 1}
                      </div>
                    </div>
                  ))}

                  {gallery.length === 0 && (
                    <div className="col-12 text-center text-muted py-4">
                      Arraste imagens aqui ou clique em <b>Adicionar imagens</b>.
                    </div>
                  )}
                </div>
              </div>
            </div>

            <div className="modal-footer">
              <button className="btn btn-secondary" onClick={onClose}>Fechar</button>
            </div>

          </div>
        </div>
      </div>

      {/* Overlay de Zoom */}
      {zoomUrl && (
        <div
          className="position-fixed top-0 start-0 w-100 h-100 d-flex align-items-center justify-content-center"
          style={{ background: "rgba(0,0,0,0.8)", zIndex: 2000 }}
          onClick={() => setZoomUrl(null)}
        >
          <img
            src={zoomUrl}
            alt=""
            style={{ maxHeight: "90%", maxWidth: "90%", boxShadow: "0 0 20px rgba(0,0,0,.6)" }}
          />
        </div>
      )}
    </>
  );
}
