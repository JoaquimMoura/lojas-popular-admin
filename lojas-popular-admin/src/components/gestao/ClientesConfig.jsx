import { useEffect, useState } from "react";
import { toast } from "react-toastify";
import { comercialApi } from "../../services/comercialApi";
import ErroAlert from "./ErroAlert";
import { PERFIS } from "../../utils/format";

const PERFIS_TROCA = ["GERENTE", "ADMIN"];

const OPCOES_HISTORICO = [
  {
    valor: "null",
    rotulo: "Pendente (vendedor vê só as próprias vendas / troca de cliente bloqueada)",
    ajuda: "Enquanto não for decidido, o vendedor vê no histórico do cliente apenas as compras que ele mesmo vendeu.",
  },
  {
    valor: "false",
    rotulo: "Vendedor vê só as compras que ele mesmo fez",
    ajuda: "O vendedor não enxerga compras de outros vendedores, nem nos indicadores do cliente.",
  },
  {
    valor: "true",
    rotulo: "Vendedor vê o histórico completo do cliente",
    ajuda: "O vendedor vê todas as compras do cliente, inclusive as feitas por colegas.",
  },
];

/** D13: histórico de compras para vendedores e troca do cliente de uma venda confirmada. */
export default function ClientesConfig({ ehAdmin }) {
  const [carregado, setCarregado] = useState(false);
  const [historico, setHistorico] = useState("null");
  const [perfis, setPerfis] = useState([]);
  const [busy, setBusy] = useState(false);
  const [erro, setErro] = useState(null);

  function aplicar(d) {
    setHistorico(d?.vendedorVeHistoricoCompleto === true ? "true" : d?.vendedorVeHistoricoCompleto === false ? "false" : "null");
    setPerfis(d?.perfisTrocaCliente ?? []);
  }

  useEffect(() => {
    comercialApi
      .obterClientes()
      .then(aplicar)
      .catch(setErro)
      .finally(() => setCarregado(true));
  }, []);

  async function salvar(e) {
    e.preventDefault();
    if (busy) return;
    setBusy(true);
    setErro(null);
    try {
      aplicar(
        await comercialApi.atualizarClientes({
          vendedorVeHistoricoCompleto: historico === "true" ? true : historico === "false" ? false : null,
          perfisTrocaCliente: perfis,
        }),
      );
      toast.success("Regras de clientes salvas.");
    } catch (err) {
      setErro(err);
    } finally {
      setBusy(false);
    }
  }

  const alternar = (p) => setPerfis((l) => (l.includes(p) ? l.filter((x) => x !== p) : [...l, p]));

  return (
    <form className="card mb-4" onSubmit={salvar}>
      <div className="card-header">Clientes e histórico de compras (D13)</div>
      <div className="card-body">
        {!carregado ? (
          <div className="text-muted">Carregando...</div>
        ) : (
          <>
            <ErroAlert erro={erro} onClose={() => setErro(null)} />
            <fieldset className="mb-3" disabled={!ehAdmin || busy}>
              <legend className="fs-6 fw-semibold">O que o vendedor vê no histórico de compras do cliente</legend>
              {OPCOES_HISTORICO.map((o) => (
                <div className="form-check mb-2" key={o.valor}>
                  <input className="form-check-input" type="radio" name="d13-historico" id={`d13-${o.valor}`}
                    checked={historico === o.valor} onChange={() => setHistorico(o.valor)} />
                  <label className="form-check-label" htmlFor={`d13-${o.valor}`}>
                    {o.rotulo}
                    <span className="d-block small text-muted">{o.ajuda}</span>
                  </label>
                </div>
              ))}
              <div className="form-text">Gerente e proprietário sempre veem o histórico completo.</div>
            </fieldset>

            <fieldset className="mb-3" disabled={!ehAdmin || busy}>
              <legend className="fs-6 fw-semibold">Perfis autorizados a trocar o cliente de uma venda confirmada</legend>
              <div className="d-flex flex-wrap gap-3">
                {PERFIS_TROCA.map((p) => (
                  <div className="form-check" key={p}>
                    <input className="form-check-input" type="checkbox" id={`d13-troca-${p}`}
                      checked={perfis.includes(p)} onChange={() => alternar(p)} />
                    <label className="form-check-label" htmlFor={`d13-troca-${p}`}>{PERFIS[p]}</label>
                  </div>
                ))}
              </div>
              <div className="form-text">
                Pendente (nenhum perfil marcado): a troca de cliente fica bloqueada. A troca exige justificativa e não
                altera valores, estoque, pagamentos, comissão nem fechamento.
              </div>
            </fieldset>

            {!ehAdmin ? (
              <div className="form-text">Somente o proprietário pode alterar estas regras.</div>
            ) : (
              <div className="d-grid d-sm-flex justify-content-sm-end">
                <button className="btn btn-success" type="submit" disabled={busy}>
                  {busy ? "Salvando..." : "Salvar regras de clientes"}
                </button>
              </div>
            )}
          </>
        )}
      </div>
    </form>
  );
}
