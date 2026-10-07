import { useEffect, useState } from "react";
import { toast } from "react-toastify";
import { financeiroApi } from "../../../services/financeiroApi";
import { usuariosApi } from "../../../services/usuariosApi";
import ErroAlert from "../../../components/gestao/ErroAlert";
import { useCarga } from "../../../components/gestao/useCarga";
import { Carregando, TabelaCards } from "../../../components/gestao/financeiro/Comuns";
import { fmtMoney, fmtPercent, mesAtual } from "../../../utils/format";

function Provisorio({ m }) {
  if (!m?.provisorio) return null;
  return (
    <div className="alert alert-warning mb-0 mt-2" role="status">
      <strong>Atingimento provisório (D09).</strong>{" "}
      {m.observacao ?? "A política de devoluções na meta ainda não foi definida; o valor pode mudar."}
    </div>
  );
}

function Barra({ pct }) {
  if (pct === null || pct === undefined) return <span className="text-muted">—</span>;
  const n = Number(pct);
  return (
    <div style={{ minWidth: 120 }}>
      <div>{fmtPercent(n)}</div>
      <div className="progress" role="progressbar" aria-valuenow={Math.min(n, 100)} aria-valuemin={0} aria-valuemax={100} style={{ height: 6 }}>
        <div className="progress-bar" style={{ width: `${Math.min(Math.max(n, 0), 100)}%` }} />
      </div>
    </div>
  );
}

function CampoMes({ mes, setMes }) {
  return (
    <div className="mb-3" style={{ maxWidth: 240 }}>
      <label className="form-label small mb-1">Mês</label>
      <input type="month" className="form-control" value={mes} onChange={(e) => e.target.value && setMes(e.target.value)} />
    </div>
  );
}

function DefinirMeta({ vendedorId, mes, atual, onFeito }) {
  const [valor, setValor] = useState(atual != null ? String(atual) : "");
  const [busy, setBusy] = useState(false);
  const [erro, setErro] = useState(null);
  async function salvar(e) {
    e.preventDefault();
    if (busy || !(Number(valor) > 0)) return;
    setBusy(true);
    setErro(null);
    try {
      await financeiroApi.definirMeta({ vendedorId, mes, valor: Number(valor) });
      toast.success("Meta definida.");
      onFeito();
    } catch (err) {
      setErro(err);
    } finally {
      setBusy(false);
    }
  }
  return (
    <form onSubmit={salvar} className="d-flex gap-2 align-items-start">
      <div style={{ minWidth: 120 }}>
        <input type="number" inputMode="decimal" step="0.01" min="0.01" className="form-control" aria-label="Valor da meta"
          value={valor} onChange={(e) => setValor(e.target.value)} />
        {erro && <div className="text-danger small" role="alert">{erro?.response?.data?.message ?? "Não foi possível salvar."}</div>}
      </div>
      <button className="btn btn-outline-primary" type="submit" disabled={busy || !(Number(valor) > 0)}>
        {busy ? "..." : "Definir"}
      </button>
    </form>
  );
}

export default function MetasPage() {
  const [mes, setMes] = useState(mesAtual());
  const [vendedores, setVendedores] = useState([]);
  const { dados, loading, erro, recarregar } = useCarga(() => financeiroApi.metas(mes), [mes]);
  useEffect(() => {
    usuariosApi.vendedores().then((v) => setVendedores(Array.isArray(v) ? v : [])).catch(() => {});
  }, []);

  const metas = Array.isArray(dados) ? dados : [];
  const porVendedor = new Map(metas.map((m) => [m.vendedorId, m]));
  const ids = new Set([...vendedores.map((v) => v.id), ...metas.map((m) => m.vendedorId)]);
  const linhas = [...ids].map((id) => {
    const m = porVendedor.get(id);
    return { id, ...(m ?? {}), vendedor: m?.vendedor ?? vendedores.find((v) => v.id === id)?.nome ?? `Vendedor ${id}` };
  });
  const algumProvisorio = metas.find((m) => m.provisorio);

  return (
    <div>
      <CampoMes mes={mes} setMes={setMes} />
      <div className="small text-muted mb-3">
        O vendido e o atingimento vêm do servidor. Como as devoluções entram na meta depende da política D09 (decisão do proprietário).
      </div>
      <ErroAlert erro={erro} />
      {algumProvisorio && <Provisorio m={algumProvisorio} />}
      <div className="mt-3">
        {loading && !dados ? <Carregando /> : (
          <TabelaCards
            linhas={linhas}
            vazio="Nenhum vendedor ativo."
            colunas={[
              { titulo: "Vendedor", render: (m) => m.vendedor },
              { titulo: "Meta", fim: true, render: (m) => (m.meta != null ? fmtMoney(m.meta) : <span className="text-muted">Sem meta</span>) },
              { titulo: "Vendido", fim: true, render: (m) => fmtMoney(m.vendido) },
              { titulo: "Restituído no mês", fim: true, render: (m) => fmtMoney(m.restituidoNoMes) },
              { titulo: "Atingimento", render: (m) => (
                <span><Barra pct={m.atingimentoPercentual} />{m.provisorio && <span className="badge text-bg-warning text-dark mt-1">Provisório</span>}</span>
              ) },
              { titulo: "Definir meta (R$)", render: (m) => (
                <DefinirMeta key={`${m.id}-${mes}-${m.meta}`} vendedorId={m.id} mes={mes} atual={m.meta} onFeito={recarregar} />
              ) },
            ]}
          />
        )}
      </div>
    </div>
  );
}

/** Página do vendedor: a própria meta do mês. */
export function MinhaMetaPage() {
  const [mes, setMes] = useState(mesAtual());
  const { dados: m, loading, erro } = useCarga(() => financeiroApi.minhaMeta(mes), [mes]);
  return (
    <div>
      <h3 className="mb-3">Minha meta</h3>
      <CampoMes mes={mes} setMes={setMes} />
      <ErroAlert erro={erro} />
      {loading && !m ? <Carregando /> : m && (
        <div className="card">
          <div className="card-body">
            <div className="row g-3">
              <div className="col-6 col-md-3"><div className="small text-muted">Meta</div><strong>{m.meta != null ? fmtMoney(m.meta) : "Sem meta definida"}</strong></div>
              <div className="col-6 col-md-3"><div className="small text-muted">Vendido</div><strong>{fmtMoney(m.vendido)}</strong></div>
              <div className="col-6 col-md-3"><div className="small text-muted">Restituído no mês</div><strong>{fmtMoney(m.restituidoNoMes)}</strong></div>
              <div className="col-6 col-md-3"><div className="small text-muted">Atingimento</div><Barra pct={m.atingimentoPercentual} /></div>
            </div>
            <Provisorio m={m} />
          </div>
        </div>
      )}
    </div>
  );
}
