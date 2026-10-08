import { useEffect, useState } from "react";
import { toast } from "react-toastify";
import { api } from "../../../services/api";
import ErroAlert from "../ErroAlert";
import { invalidarPermissoesFinanceiras } from "./useFinanceiroPermissoes";

const OPERACOES = [
  { chave: "consultar", rotulo: "Consultar",
    ajuda: "Caixa, contas, cartão, comissões, metas, fechamento (prévia), relatórios e custos." },
  { chave: "receber", rotulo: "Receber",
    ajuda: "Registrar recebimento, operar o caixa (abrir, suprimento, fechar), liquidar cartão e baixar conta a receber." },
  { chave: "pagar", rotulo: "Pagar",
    ajuda: "Criar, alterar e cancelar contas, baixar conta a pagar e retirada de caixa." },
  { chave: "estornar", rotulo: "Estornar",
    ajuda: "Estornar recebimento, liquidação de cartão e baixa de conta." },
  { chave: "restituir", rotulo: "Restituir",
    ajuda: "Solicitar, autorizar, efetivar e cancelar restituição e cobrar diferença de troca." },
];

const OPCOES = [
  { valor: "PENDENTE", texto: "Pendente (só o proprietário)" },
  { valor: "ADMIN", texto: "Somente o proprietário" },
  { valor: "AMBOS", texto: "Proprietário e gerente" },
];

const paraOpcao = (perfis) =>
  (perfis ?? []).includes("GERENTE") ? "AMBOS" : (perfis ?? []).includes("ADMIN") ? "ADMIN" : "PENDENTE";
const paraPerfis = (op) => (op === "AMBOS" ? ["ADMIN", "GERENTE"] : op === "ADMIN" ? ["ADMIN"] : []);

/** D12: o que o gerente pode fazer no financeiro. Sem decisão, o gerente NÃO tem acesso. */
export default function PermissoesFinanceiras({ ehAdmin }) {
  const [valores, setValores] = useState(null);
  const [busy, setBusy] = useState(false);
  const [erro, setErro] = useState(null);

  const aplicar = (d) =>
    setValores(Object.fromEntries(OPERACOES.map((o) => [o.chave, paraOpcao(d?.[o.chave])])));

  useEffect(() => {
    let ativo = true;
    api.get("/config/comercial/permissoes-financeiras")
      .then((r) => ativo && aplicar(r.data))
      .catch((e) => ativo && setErro(e));
    return () => {
      ativo = false;
    };
  }, []);

  async function salvar(e) {
    e.preventDefault();
    if (busy || !ehAdmin || !valores) return;
    setBusy(true);
    setErro(null);
    try {
      const body = Object.fromEntries(OPERACOES.map((o) => [o.chave, paraPerfis(valores[o.chave])]));
      const r = await api.put("/config/comercial/permissoes-financeiras", body);
      aplicar(r.data);
      invalidarPermissoesFinanceiras();
      toast.success("Permissões financeiras salvas.");
    } catch (err) {
      setErro(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="card mb-4" onSubmit={salvar}>
      <div className="card-header">Permissões financeiras do gerente (D12)</div>
      <div className="card-body">
        <div className="alert alert-info small">
          O proprietário sempre pode todas as operações. Para o gerente, <strong>sem decisão o acesso é negado</strong>:
          enquanto uma operação estiver como <strong>Pendente</strong>, somente o proprietário a realiza. Aprovar
          fechamento, pagar comissões, cadastrar custos e taxas de cartão e alterar estas decisões são sempre só do
          proprietário.
          {!ehAdmin && " Somente o proprietário altera estas permissões."}
        </div>
        <ErroAlert erro={erro} onClose={() => setErro(null)} />
        {!valores && !erro && <div className="text-muted">Carregando...</div>}
        {valores && (
          <fieldset disabled={!ehAdmin || busy} className="border-0 p-0 m-0">
            <div className="row g-3">
              {OPERACOES.map((o) => (
                <div className="col-12 col-md-6" key={o.chave}>
                  <label className="form-label" htmlFor={`perm-${o.chave}`}>{o.rotulo}</label>
                  <select id={`perm-${o.chave}`} className="form-select" value={valores[o.chave]}
                    onChange={(e) => setValores((v) => ({ ...v, [o.chave]: e.target.value }))}>
                    {OPCOES.map((p) => <option key={p.valor} value={p.valor}>{p.texto}</option>)}
                  </select>
                  <div className="form-text">
                    {o.ajuda}
                    {valores[o.chave] === "PENDENTE" && (
                      <strong className="text-warning-emphasis"> Pendente: o gerente não tem acesso.</strong>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </fieldset>
        )}
        {ehAdmin && valores && (
          <div className="d-grid d-sm-flex justify-content-sm-end mt-3">
            <button className="btn btn-success" type="submit" disabled={busy}>
              {busy ? "Salvando..." : "Salvar permissões financeiras"}
            </button>
          </div>
        )}
      </div>
    </form>
  );
}
