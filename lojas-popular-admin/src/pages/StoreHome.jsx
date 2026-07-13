// src/pages/StoreHome.jsx
import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { storeApi } from "../services/storeApi";
import { categoriesApi } from "../services/categoriesApi";
import ProductCard from "../components/ProductCard";
import ProductCarousel from "../components/ProductCarousel";
import WhatsAppButton from "../components/WhatsAppButton";

export default function StoreHome() {
  const [searchParams] = useSearchParams();
  const busca = searchParams.get("busca") ?? "";

  const [produtosBusca, setProdutosBusca] = useState([]);
  const [secoes, setSecoes] = useState([]);
  const [loading, setLoading] = useState(true);

  // Busca ativa: mantem a listagem simples em grade.
  useEffect(() => {
    if (!busca) return;

    let ativo = true;
    setLoading(true);
    storeApi
      .listProducts({ page: 0, size: 24, nome: busca })
      .then((data) => {
        if (ativo) setProdutosBusca(data ?? []);
      })
      .finally(() => {
        if (ativo) setLoading(false);
      });

    return () => {
      ativo = false;
    };
  }, [busca]);

  // Sem busca: agrupa os produtos por categoria, cada uma com seu carrossel.
  useEffect(() => {
    if (busca) return;

    let ativo = true;
    setLoading(true);

    async function load() {
      try {
        const categorias = await categoriesApi.list();
        const lista = Array.isArray(categorias) ? categorias : [];

        const resultados = await Promise.all(
          lista.map((categoria) =>
            storeApi
              .listProducts({ page: 0, size: 16, categoriaId: categoria.id })
              .then((produtos) => ({ categoria, produtos: produtos ?? [] }))
          )
        );

        if (ativo) {
          setSecoes(resultados.filter((secao) => secao.produtos.length > 0));
        }
      } catch (error) {
        console.error("Erro ao carregar vitrine por categoria:", error);
        if (ativo) setSecoes([]);
      } finally {
        if (ativo) setLoading(false);
      }
    }

    load();

    return () => {
      ativo = false;
    };
  }, [busca]);

  return (
    <div className="container py-4">
      <h2 className="text-center mb-4 text-danger fw-bold">Popular Móveis</h2>
      <p className="text-center text-muted mb-5">
        {busca ? `Resultados para "${busca}"` : "Móveis com qualidade e preco popular!"}
      </p>

      {loading && <div className="text-center text-muted py-5">Carregando produtos...</div>}

      {!loading && busca && (
        <div className="row g-4">
          {produtosBusca.map((produto) => (
            <div className="col-6 col-md-4 col-lg-3" key={produto.id}>
              <ProductCard produto={produto} />
            </div>
          ))}
          {produtosBusca.length === 0 && (
            <div className="text-center text-muted py-5">
              Nenhum produto encontrado para "{busca}".
            </div>
          )}
        </div>
      )}

      {!loading && !busca && (
        <>
          {secoes.map(({ categoria, produtos }) => (
            <section key={categoria.id} className="mb-5">
              <h3 className="fw-bold mb-3">{categoria.nome}</h3>
              <ProductCarousel produtos={produtos} />
            </section>
          ))}

          {secoes.length === 0 && (
            <div className="text-center text-muted py-5">
              Nenhum produto cadastrado por enquanto.
            </div>
          )}
        </>
      )}

      <div className="text-center mt-5">
        <WhatsAppButton
          text="Olá! Gostaria de falar com um atendente sobre os produtos."
          label="Falar com um atendente"
        />
      </div>
    </div>
  );
}
