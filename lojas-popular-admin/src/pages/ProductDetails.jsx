import { useEffect, useState, useCallback, useRef } from "react";
import { useParams } from "react-router-dom";
import { storeApi } from "../services/storeApi";
import { resolveImageUrl } from "../utils/url";
import WhatsAppButton from "../components/WhatsAppButton";
import "../styles/Lightbox.css";
import { useCart } from "../context/CartContext";
import { ToastContainer, toast } from "react-toastify";
import "react-toastify/dist/ReactToastify.css";

export default function ProductDetails() {
  const { id } = useParams();
  const [produto, setProduto] = useState(null);
  const [imagemSelecionada, setImagemSelecionada] = useState(null);
  const [lightboxIndex, setLightboxIndex] = useState(null);
  const [zoom, setZoom] = useState(1);
  const zoomRef = useRef(null);
  const { addToCart } = useCart();

  const handleKey = useCallback(
    (event) => {
      if (event.key === "Escape") {
        closeLightbox();
      }

      if (lightboxIndex !== null) {
        if (event.key === "ArrowRight") nextImage();
        if (event.key === "ArrowLeft") prevImage();
      }
    },
    [lightboxIndex]
  );

  useEffect(() => {
    async function load() {
      try {
        const data = await storeApi.getProduct(id);

        let galeria = [];
        if (Array.isArray(data.galeria)) {
          galeria = data.galeria;
        } else if (typeof data.galeria === "string") {
          try {
            galeria = JSON.parse(data.galeria);
          } catch {
            galeria = [];
          }
        }

        const principal =
          data.imagemUrl || galeria[0] || "/assets/no-image.png";

        setProduto({ ...data, galeria });
        setImagemSelecionada(resolveImageUrl(principal));
      } catch (error) {
        console.error("Erro ao carregar produto:", error);
      }
    }

    load();
  }, [id]);

  useEffect(() => {
    window.addEventListener("keydown", handleKey);
    return () => window.removeEventListener("keydown", handleKey);
  }, [handleKey]);

  if (!produto) {
    return <p className="text-center mt-5">Carregando...</p>;
  }

  const openLightbox = (index) => {
    if (!produto?.galeria?.length) return;
    setLightboxIndex(index >= 0 ? index : 0);
    setZoom(1);
  };

  const closeLightbox = () => {
    setLightboxIndex(null);
    setZoom(1);
  };

  const nextImage = () => {
    if (!produto?.galeria?.length) return;
    setLightboxIndex((prev) =>
      prev === produto.galeria.length - 1 ? 0 : prev + 1
    );
    setZoom(1);
  };

  const prevImage = () => {
    if (!produto?.galeria?.length) return;
    setLightboxIndex((prev) =>
      prev === 0 ? produto.galeria.length - 1 : prev - 1
    );
    setZoom(1);
  };

  const handleWheelZoom = (event) => {
    event.preventDefault();
    const delta = event.deltaY < 0 ? 0.15 : -0.15;
    setZoom((value) => Math.min(Math.max(value + delta, 1), 3));
  };

  const handleDrag = (event) => {
    if (zoom <= 1) return;
    event.preventDefault();
    const img = zoomRef.current;
    const startX = event.clientX - img.offsetLeft;
    const startY = event.clientY - img.offsetTop;
    img.style.cursor = "grabbing";

    const onMouseMove = (moveEvent) => {
      img.style.left = `${moveEvent.clientX - startX}px`;
      img.style.top = `${moveEvent.clientY - startY}px`;
    };

    const onMouseUp = () => {
      img.style.cursor = "grab";
      document.removeEventListener("mousemove", onMouseMove);
      document.removeEventListener("mouseup", onMouseUp);
    };

    document.addEventListener("mousemove", onMouseMove);
    document.addEventListener("mouseup", onMouseUp);
  };

  const currentImage =
    lightboxIndex !== null && produto.galeria[lightboxIndex]
      ? resolveImageUrl(produto.galeria[lightboxIndex])
      : imagemSelecionada;

  const handleAddToCart = () => {
    addToCart({
      id: produto.id,
      nome: produto.nome,
      preco: produto.preco,
      imagemUrl: produto.imagemUrl,
    });

    toast.success(`${produto.nome} foi adicionado ao carrinho!`, {
      position: "top-right",
      autoClose: 3000,
      hideProgressBar: false,
      closeOnClick: true,
      pauseOnHover: true,
      draggable: true,
      theme: "dark",
      style: { backgroundColor: "#B71C1C", color: "#fff" },
    });
  };

  return (
    <div className="container py-4">
      <h3 className="text-danger fw-bold mb-1">{produto.nome}</h3>
      <p className="text-muted">{produto.descricao}</p>
      <div className="row mt-4">
        <div className="col-md-6 text-center">
          <img
            src={imagemSelecionada}
            alt={produto.nome}
            className="img-fluid rounded shadow-sm mb-3"
            style={{ maxHeight: 420, objectFit: "contain", cursor: "zoom-in" }}
            onClick={() =>
              openLightbox(
                produto.galeria.findIndex(
                  (item) => resolveImageUrl(item) === imagemSelecionada
                )
              )
            }
            onError={(event) => {
              event.target.src = "/assets/no-image.png";
            }}
          />

          <div className="d-flex flex-wrap justify-content-center gap-2">
            {produto.galeria?.map((g, index) => {
              const imgUrl = resolveImageUrl(g);
              const selected = imgUrl === imagemSelecionada;
              return (
                <img
                  key={index}
                  src={imgUrl}
                  alt=""
                  className="rounded"
                  style={{
                    width: 80,
                    height: 80,
                    objectFit: "cover",
                    cursor: "pointer",
                    border: selected ? "3px solid #dc3545" : "2px solid #ccc",
                    opacity: selected ? 1 : 0.7,
                  }}
                  onClick={() => setImagemSelecionada(imgUrl)}
                  onError={(event) => {
                    event.target.src = "/assets/no-image.png";
                  }}
                />
              );
            })}
          </div>
        </div>

        <div className="col-md-6">
          <h4 className="text-danger mb-3">
            R$ {produto.preco?.toFixed(2) || "0.00"}
          </h4>
          <p>
            <strong>Categoria:</strong> {produto.categoria || "-"}
          </p>
          <p>
            <strong>Estoque:</strong> {produto.estoque || 0}
          </p>

          <WhatsAppButton
            text={`Ola! Tenho interesse no produto ${produto.nome}`}
            label="Falar com um atendente"
          />
        </div>
      </div>

      <div className="d-flex gap-3 mt-4">
        <button className="btn btn-warning px-4" onClick={handleAddToCart}>
          Adicionar ao carrinho
        </button>

        <WhatsAppButton
          text={`Ola! Tenho interesse no produto ${produto.nome}`}
          label="Comprar pelo WhatsApp"
        />
      </div>

      <ToastContainer />

      {lightboxIndex !== null && (
        <div
          className="lightbox-overlay"
          onClick={closeLightbox}
          onWheel={handleWheelZoom}
        >
          <button
            className="lightbox-prev btn btn-light btn-lg"
            onClick={(event) => {
              event.stopPropagation();
              prevImage();
            }}
          >
            &lt;
          </button>

          <div
            className="lightbox-container"
            onClick={(event) => event.stopPropagation()}
          >
            <img
              ref={zoomRef}
              src={currentImage || "/assets/no-image.png"}
              alt=""
              className="lightbox-image"
              style={{
                transform: `scale(${zoom})`,
                cursor: zoom > 1 ? "grab" : "zoom-out",
                transition: "transform 0.25s ease",
              }}
              onMouseDown={handleDrag}
              onError={(event) => {
                event.target.src = "/assets/no-image.png";
              }}
            />
          </div>

          <button
            className="lightbox-next btn btn-light btn-lg"
            onClick={(event) => {
              event.stopPropagation();
              nextImage();
            }}
          >
            &gt;
          </button>
        </div>
      )}
    </div>
  );
}
