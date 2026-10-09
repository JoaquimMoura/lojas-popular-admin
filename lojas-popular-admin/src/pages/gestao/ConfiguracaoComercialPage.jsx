import { useCallback, useEffect, useState } from "react";
import { toast } from "react-toastify";
import { comercialApi } from "../../services/comercialApi";
import { useAuth } from "../../context/AuthContext";
import PendenciasAlert from "../../components/gestao/PendenciasAlert";
import ErroAlert from "../../components/gestao/ErroAlert";
import DecisoesFinanceiras from "../../components/gestao/financeiro/DecisoesFinanceiras";
import ClientesConfig from "../../components/gestao/ClientesConfig";
import PermissoesFinanceiras from "../../components/gestao/financeiro/PermissoesFinanceiras";
import { ARREDONDAMENTOS, FORMAS, PERFIS, fmtPercent } from "../../utils/format";

const PERFIS_CANCELAMENTO = ["ADMIN", "GERENTE", "VENDEDOR"];

function CondicaoLinha({ c, onSalvar, busy }) {
  const [ajuste, setAjuste] = useState(String(c.ajustePercentual ?? ""));
  const [ativa, setAtiva] = useState(!!c.ativa);
  const alterada = Number(ajuste) !== Number(c.ajustePercentual) || ativa !== !!c.ativa;
  return (
    <div className="border rounded p-2 mb-2">
      <div className="d-flex justify-content-between align-items-center mb-2">
        <strong>
          {FORMAS[c.forma] ?? c.forma} — {c.parcelas}x
        </strong>
        <span className="small text-muted">Atual: {fmtPercent(c.ajustePercentual)}</span>
      </div>
      <div className="row g-2 align-items-end">
        <div className="col-6 col-md-4">
          <label className="form-label small">Ajuste (%)</label>
          <input type="number" step="0.01" className="form-control" value={ajuste}
            onChange={(e) => setAjuste(e.target.value)} />
        </div>
        <div className="col-6 col-md-4">
          <div className="form-check form-switch mb-2">
            <input className="form-check-input" type="checkbox" id={`ativa-${c.id}`} checked={ativa}
              onChange={(e) => setAtiva(e.target.checked)} />
            <label className="form-check-label" htmlFor={`ativa-${c.id}`}>Ativa</label>
          </div>
        </div>
        <div className="col-12 col-md-4 d-grid">
          <button className="btn btn-outline-primary" disabled={busy || !alterada || ajuste === ""}
            onClick={() => onSalvar(c.id, { ajustePercentual: Number(ajuste), ativa })}>
            Salvar condição
          </button>
        </div>
      </div>
    </div>
  );
}

export default function ConfiguracaoComercialPage() {
  const { user } = useAuth();
  const ehAdmin = user?.roles?.includes("ADMIN");
  const [cfg, setCfg] = useState(null);
  const [limite, setLimite] = useState("");
  const [arred, setArred] = useState("");
  const [perfis, setPerfis] = useState([]);
  const [d05, setD05] = useState("null");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [erro, setErro] = useState(null);
  const [nova, setNova] = useState({ forma: "", parcelas: "", ajustePercentual: "" });

  const aplicar = useCallback((data) => {
    setCfg(data);
    setLimite(data.limiteDescontoPercentual ?? "");
    setArred(data.arredondamento ?? "");
    setPerfis(data.perfisCancelamento ?? []);
    setD05(data.exigePagamentoExpedir === true ? "true" : data.exigePagamentoExpedir === false ? "false" : "null");
  }, []);

  useEffect(() => {
    comercialApi
      .obter()
      .then(aplicar)
      .catch(setErro)
      .finally(() => setLoading(false));
  }, [aplicar]);

  async function executar(fn, sucesso) {
    if (busy) return false;
    setBusy(true);
    setErro(null);
    try {
      await fn();
      if (sucesso) toast.success(sucesso);
      aplicar(await comercialApi.obter());
      return true;
    } catch (err) {
      setErro(err);
      return false;
    } finally {
      setBusy(false);
    }
  }

  function salvarConfig(e) {
    e.preventDefault();
    executar(
      () =>
        comercialApi.atualizar({
          limiteDescontoPercentual: limite === "" ? null : Number(limite),
          arredondamento: arred || null,
          perfisCancelamento: perfis,
          exigePagamentoExpedir: d05 === "true" ? true : d05 === "false" ? false : null,
        }),
      "Configuração salva.",
    );
  }

  function togglePerfil(p) {
    setPerfis((l) => (l.includes(p) ? l.filter((x) => x !== p) : [...l, p]));
  }

  async function criarCondicao(e) {
    e.preventDefault();
    const ok = await executar(
      () =>
        comercialApi.criarCondicao({
          forma: nova.forma,
          parcelas: Number(nova.parcelas),
          ajustePercentual: Number(nova.ajustePercentual),
          ativa: true,
        }),
      "Condição criada.",
    );
    if (ok) setNova({ forma: "", parcelas: "", ajustePercentual: "" });
  }

  if (loading) return <div className="text-center text-muted py-5">Carregando configuração...</div>;

  return (
    <div>
      <h3 className="mb-3">Configuração comercial</h3>

      <PendenciasAlert pendencias={(cfg?.pendencias ?? []).filter((p) => p.area !== "FINANCEIRO")} titulo="Pendências de configuração" />
      <ErroAlert erro={erro} onClose={() => setErro(null)} />

      <form className="card mb-4" onSubmit={salvarConfig}>
        <div className="card-header">Regras gerais</div>
        <div className="card-body">
          <div className="row g-3">
            <div className="col-12 col-md-6">
              <label className="form-label">Limite de desconto sem aprovação (%)</label>
              <input type="number" step="0.01" min="0" max="100" className="form-control" value={limite}
                onChange={(e) => setLimite(e.target.value)} />
              <div className="form-text">
                Descontos acima deste percentual exigem aprovação de gerente ou proprietário. Vazio = não configurado.
              </div>
            </div>
            <div className="col-12 col-md-6">
              <label className="form-label">Arredondamento</label>
              <select className="form-select" value={arred} onChange={(e) => setArred(e.target.value)}>
                <option value="">— não configurado —</option>
                {Object.entries(ARREDONDAMENTOS).map(([k, v]) => (
                  <option key={k} value={k}>{v}</option>
                ))}
              </select>
            </div>
            <div className="col-12 col-md-6">
              <label className="form-label">Pagamento para a saída da entrega (D05)</label>
              <select className="form-select" value={d05} onChange={(e) => setD05(e.target.value)}>
                <option value="null">Não definido (saída bloqueada)</option>
                <option value="true">Exigir pagamento quitado para a saída</option>
                <option value="false">Não exigir pagamento para a saída</option>
              </select>
              <div className="form-text">
                Enquanto não for definido, o registro de saída das entregas permanece bloqueado.
              </div>
            </div>
            <div className="col-12">
              <label className="form-label d-block">Perfis autorizados a cancelar vendas</label>
              <div className="d-flex flex-wrap gap-3">
                {PERFIS_CANCELAMENTO.map((p) => (
                  <div className="form-check" key={p}>
                    <input className="form-check-input" type="checkbox" id={`perfil-${p}`}
                      checked={perfis.includes(p)} disabled={!ehAdmin} onChange={() => togglePerfil(p)} />
                    <label className="form-check-label" htmlFor={`perfil-${p}`}>{PERFIS[p]}</label>
                  </div>
                ))}
              </div>
              {!ehAdmin && (
                <div className="form-text">Somente o proprietário pode definir quem cancela vendas.</div>
              )}
            </div>
          </div>
          <div className="d-grid d-sm-flex justify-content-sm-end mt-3">
            <button className="btn btn-success" type="submit" disabled={busy}>
              {busy ? "Salvando..." : "Salvar regras"}
            </button>
          </div>
        </div>
      </form>

      <DecisoesFinanceiras key={JSON.stringify(cfg?.financeiro ?? {})} cfg={cfg} ehAdmin={!!ehAdmin} onSalvo={aplicar} />
      <PermissoesFinanceiras ehAdmin={!!ehAdmin} />
      <ClientesConfig ehAdmin={!!ehAdmin} />

      <div className="card mb-4">
        <div className="card-header">Condições de pagamento</div>
        <div className="card-body">
          <div className="alert alert-info">
            Alterar condições não muda as vendas já registradas: cada venda guarda o ajuste vigente no momento do registro.
          </div>

          {(cfg?.condicoes ?? []).length === 0 ? (
            <div className="text-muted mb-3">Nenhuma condição cadastrada.</div>
          ) : (
            cfg.condicoes.map((c) => (
              <CondicaoLinha key={`${c.id}-${c.ajustePercentual}-${c.ativa}`} c={c} busy={busy}
                onSalvar={(id, body) => executar(() => comercialApi.atualizarCondicao(id, body), "Condição atualizada.")} />
            ))
          )}

          <form className="border-top pt-3 mt-3" onSubmit={criarCondicao}>
            <h6>Nova condição</h6>
            <div className="row g-2 align-items-end">
              <div className="col-12 col-md-3">
                <label className="form-label small">Forma</label>
                <select className="form-select" required value={nova.forma}
                  onChange={(e) => setNova((n) => ({ ...n, forma: e.target.value }))}>
                  <option value="">— selecione —</option>
                  {Object.entries(FORMAS).map(([k, v]) => (
                    <option key={k} value={k}>{v}</option>
                  ))}
                </select>
              </div>
              <div className="col-6 col-md-3">
                <label className="form-label small">Parcelas</label>
                <input type="number" min="1" className="form-control" required value={nova.parcelas}
                  onChange={(e) => setNova((n) => ({ ...n, parcelas: e.target.value }))} />
              </div>
              <div className="col-6 col-md-3">
                <label className="form-label small">Ajuste (%)</label>
                <input type="number" step="0.01" className="form-control" required value={nova.ajustePercentual}
                  onChange={(e) => setNova((n) => ({ ...n, ajustePercentual: e.target.value }))} />
              </div>
              <div className="col-12 col-md-3 d-grid">
                <button className="btn btn-primary" type="submit" disabled={busy}>Adicionar</button>
              </div>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}
