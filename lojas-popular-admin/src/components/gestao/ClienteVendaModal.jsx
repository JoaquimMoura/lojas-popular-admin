import { useEffect, useState } from "react";
import { clientesApi } from "../../services/clientesApi";
import { vendasApi } from "../../services/vendasApi";
import FormModal from "./FormModal";

/**
 * Vincula (venda sem cliente) ou troca (venda confirmada) o cliente de uma venda.
 * modo: "vincular" | "trocar". `onFeito(venda)` recebe o detalhe atualizado.
 */
export default function ClienteVendaModal({ venda, modo, onClose, onFeito }) {
  const trocar = modo === "trocar";
  const [busca, setBusca] = useState("");
  const [resultados, setResultados] = useState(null);
  const [buscando, setBuscando] = useState(false);
  const [escolhido, setEscolhido] = useState(null);
  const [justificativa, setJustificativa] = useState("");

  useEffect(() => {
    const t = busca.trim();
    if (t.length < 2) {
      setResultados(null);
      return undefined;
    }
    let ativo = true;
    setBuscando(true);
    const h = setTimeout(() => {
      clientesApi
        .buscar(t)
        .then((l) => ativo && setResultados(Array.isArray(l) ? l.filter((c) => c.ativo !== false) : []))
        .catch(() => ativo && setResultados([]))
        .finally(() => ativo && setBuscando(false));
    }, 300);
    return () => {
      ativo = false;
      clearTimeout(h);
    };
  }, [busca]);

  const mesmo = trocar && escolhido && escolhido.id === venda.cliente?.id;

  return (
    <FormModal
      titulo={trocar ? `Trocar o cliente do pedido #${venda.id}` : `Vincular o pedido #${venda.id} a um cliente`}
      submitLabel={trocar ? "Trocar cliente" : "Vincular cliente"}
      size="md"
      submitDisabled={!escolhido || mesmo || !justificativa.trim()}
      onClose={onClose}
      onSubmit={async () => {
        const body = { clienteId: escolhido.id, justificativa: justificativa.trim() };
        const v = trocar ? await vendasApi.trocarCliente(venda.id, body) : await vendasApi.vincularCliente(venda.id, body);
        onFeito(v);
      }}
    >
      <div className="alert alert-warning py-2 small">
        {trocar
          ? "A troca fica registrada com a justificativa. "
          : "O vínculo fica registrado com a justificativa. "}
        <strong>Não altera valores, estoque, pagamentos, comissão nem fechamento.</strong>
        {trocar && venda.cliente && <> Cliente atual: <strong>{venda.cliente.nome}</strong>.</>}
      </div>

      {escolhido ? (
        <div className="border rounded p-2 mb-3 d-flex justify-content-between align-items-start gap-2">
          <div>
            <strong>{escolhido.nome}</strong>
            <div className="small text-muted">
              {escolhido.telefone || "Sem telefone"}
              {escolhido.cpf ? ` · CPF ${escolhido.cpf}` : ""}
            </div>
          </div>
          <button type="button" className="btn btn-sm btn-outline-secondary" onClick={() => setEscolhido(null)}>
            Trocar seleção
          </button>
        </div>
      ) : (
        <div className="mb-3">
          <label className="form-label">Cliente (nome, CPF ou telefone)</label>
          <input type="search" className="form-control" value={busca} autoFocus
            onChange={(e) => setBusca(e.target.value)} placeholder="Digite ao menos 2 caracteres" />
          {buscando && <div className="small text-muted mt-2">Buscando...</div>}
          {resultados && (
            <div className="list-group mt-2">
              {resultados.length === 0 && !buscando && (
                <div className="list-group-item text-muted">Nenhum cliente encontrado.</div>
              )}
              {resultados.map((c) => (
                <button key={c.id} type="button" className="list-group-item list-group-item-action"
                  onClick={() => setEscolhido(c)}>
                  <strong>{c.nome}</strong>
                  <div className="small text-muted">{c.telefone || "Sem telefone"}{c.cpf ? ` · CPF ${c.cpf}` : ""}</div>
                </button>
              ))}
            </div>
          )}
        </div>
      )}
      {mesmo && <div className="text-danger small mb-2">Este já é o cliente da venda.</div>}

      <label className="form-label">
        {trocar ? "Justificativa da troca" : "Justificativa (como o cliente foi conferido)"}
      </label>
      <textarea className="form-control" rows={3} maxLength={300} value={justificativa} required
        onChange={(e) => setJustificativa(e.target.value)} />
      <div className="form-text">Obrigatória, até 300 caracteres.</div>
    </FormModal>
  );
}
