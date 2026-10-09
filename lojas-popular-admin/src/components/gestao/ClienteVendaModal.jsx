import { useState } from "react";
import { clientesApi } from "../../services/clientesApi";
import { vendasApi } from "../../services/vendasApi";
import FormModal from "./FormModal";
import FiltroAutocomplete from "../FiltroAutocomplete";
import { mascararTelefone } from "../../utils/mascaras";

const buscarAtivos = (t) =>
  clientesApi.buscar(t).then((l) => (Array.isArray(l) ? l.filter((c) => c.ativo !== false) : []));

/**
 * Vincula (venda sem cliente) ou troca (venda confirmada) o cliente de uma venda.
 * modo: "vincular" | "trocar". `onFeito(venda)` recebe o detalhe atualizado.
 */
export default function ClienteVendaModal({ venda, modo, onClose, onFeito }) {
  const trocar = modo === "trocar";
  const [busca, setBusca] = useState("");
  const [escolhido, setEscolhido] = useState(null);
  const [justificativa, setJustificativa] = useState("");

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
              {escolhido.telefone ? mascararTelefone(escolhido.telefone) : "Sem telefone"}
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
          <FiltroAutocomplete
            inline
            autoFocus
            value={busca}
            onChange={setBusca}
            fetchSugestoes={buscarAtivos}
            getRotulo={(c) => c.nome}
            getDetalhe={(c) => `${c.telefone ? mascararTelefone(c.telefone) : "Sem telefone"}${c.cpf ? ` · CPF ${c.cpf}` : ""}`}
            itemClass="list-group-item-action"
            onSelecionar={setEscolhido}
            placeholder="Digite ao menos 2 caracteres"
            ariaLabel="Buscar cliente por nome, CPF ou telefone"
          />
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
