import { useState } from "react";
import { toast } from "react-toastify";
import { comercialApi } from "../../../services/comercialApi";
import ErroAlert from "../ErroAlert";
import PendenciasAlert from "../PendenciasAlert";
import { PERFIS } from "../../../utils/format";

const PERFIS_FIN = ["ADMIN", "GERENTE"];
const PENDENTE = "Pendente (não decidido)";

const AQUISICAO = {
  CONFIRMACAO: "Na confirmação da venda",
  QUITACAO: "Na quitação (pagamento total recebido)",
  ENTREGA: "Na entrega ao cliente",
};
const COMPETENCIA = {
  CONFIRMACAO: "Data de confirmação da venda",
  ENTREGA: "Data de entrega ao cliente",
};

const triToStr = (v) => (v === true ? "true" : v === false ? "false" : "");
const strToTri = (s) => (s === "true" ? true : s === "false" ? false : null);

function Tri({ id, label, ajuda, value, onChange, disabled, sim, nao }) {
  return (
    <div className="col-12 col-md-6">
      <label className="form-label" htmlFor={id}>{label}</label>
      <select id={id} className="form-select" value={value} disabled={disabled} onChange={(e) => onChange(e.target.value)}>
        <option value="">{PENDENTE}</option>
        <option value="true">{sim}</option>
        <option value="false">{nao}</option>
      </select>
      <div className="form-text">{ajuda}</div>
    </div>
  );
}

function PerfisCampo({ label, ajuda, value, onChange, disabled, prefixo }) {
  return (
    <div className="col-12 col-md-6">
      <div className="form-label">{label}</div>
      <div className="d-flex flex-wrap gap-3">
        {PERFIS_FIN.map((p) => (
          <div className="form-check" key={p}>
            <input className="form-check-input" type="checkbox" id={`${prefixo}-${p}`} disabled={disabled}
              checked={value.includes(p)}
              onChange={() => onChange(value.includes(p) ? value.filter((x) => x !== p) : [...value, p])} />
            <label className="form-check-label" htmlFor={`${prefixo}-${p}`}>{PERFIS[p]}</label>
          </div>
        ))}
      </div>
      <div className="form-text">
        {value.length === 0 ? <strong className="text-warning-emphasis">{PENDENTE}. </strong> : null}
        {ajuda} Somente Proprietário e Gerente podem ser autorizados.
      </div>
    </div>
  );
}

/** Decisões financeiras (D01, D02, D06, D07, D09, D10): todas nulas = pendentes; nada é presumido. */
export default function DecisoesFinanceiras({ cfg, ehAdmin, onSalvo }) {
  const f = cfg?.financeiro ?? {};
  const [pct, setPct] = useState(f.comissaoPercentual != null ? String(f.comissaoPercentual) : "");
  const [aquisicao, setAquisicao] = useState(f.comissaoAquisicao ?? "");
  const [competencia, setCompetencia] = useState(f.competenciaReceita ?? "");
  const [reabertura, setReabertura] = useState(f.perfisReabertura ?? []);
  const [restituicao, setRestituicao] = useState(f.perfisRestituicao ?? []);
  const [permiteRest, setPermiteRest] = useState(triToStr(f.permiteRestituicao));
  const [permiteDif, setPermiteDif] = useState(triToStr(f.permiteCobrancaDiferenca));
  const [metaDesc, setMetaDesc] = useState(triToStr(f.metaDescontaDevolucoes));
  const [exigeSem, setExigeSem] = useState(triToStr(f.fechamentoExigeSemPendencias));
  const [busy, setBusy] = useState(false);
  const [erro, setErro] = useState(null);

  const pendencias = (cfg?.pendencias ?? []).filter((p) => p.area === "FINANCEIRO");
  const pctInvalido = pct !== "" && (Number(pct) < 0 || Number(pct) > 100);

  async function salvar(e) {
    e.preventDefault();
    if (busy || !ehAdmin || pctInvalido) return;
    setBusy(true);
    setErro(null);
    try {
      const data = await comercialApi.atualizarFinanceiro({
        comissaoPercentual: pct === "" ? null : Number(pct),
        comissaoAquisicao: aquisicao || null,
        competenciaReceita: competencia || null,
        perfisReabertura: reabertura,
        perfisRestituicao: restituicao,
        permiteRestituicao: strToTri(permiteRest),
        permiteCobrancaDiferenca: strToTri(permiteDif),
        metaDescontaDevolucoes: strToTri(metaDesc),
        fechamentoExigeSemPendencias: strToTri(exigeSem),
      });
      toast.success("Decisões financeiras salvas.");
      onSalvo(data);
    } catch (err) {
      setErro(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="card mb-4" onSubmit={salvar}>
      <div className="card-header">Decisões financeiras</div>
      <div className="card-body">
        <PendenciasAlert pendencias={pendencias} titulo="Decisões financeiras pendentes (o que cada uma bloqueia)" />
        <div className="alert alert-info small">
          Nada aqui tem valor padrão: enquanto uma decisão estiver como <strong>{PENDENTE}</strong>, o recurso
          correspondente permanece bloqueado ou apenas provisório. Cada salvamento grava todas as decisões abaixo.
          {!ehAdmin && " Somente o proprietário altera estas decisões."}
        </div>
        <ErroAlert erro={erro} onClose={() => setErro(null)} />
        <fieldset disabled={!ehAdmin || busy} className="border-0 p-0 m-0">
          <div className="row g-3">
            <div className="col-12 col-md-6">
              <label className="form-label" htmlFor="fin-pct">Comissão do vendedor (%) — D01</label>
              <input id="fin-pct" type="number" inputMode="decimal" step="0.01" min="0" max="100" className="form-control"
                placeholder={PENDENTE} value={pct} onChange={(e) => setPct(e.target.value)} />
              <div className="form-text">
                {pct === "" ? <strong className="text-warning-emphasis">{PENDENTE}: nenhuma comissão é calculada. </strong> : null}
                Entre 0 e 100. Vazio = pendente.
              </div>
              {pctInvalido && <div className="text-danger small" role="alert">O percentual deve estar entre 0 e 100.</div>}
            </div>
            <div className="col-12 col-md-6">
              <label className="form-label" htmlFor="fin-aq">Quando a comissão passa a ser devida — D02</label>
              <select id="fin-aq" className="form-select" value={aquisicao} onChange={(e) => setAquisicao(e.target.value)}>
                <option value="">{PENDENTE}</option>
                {Object.entries(AQUISICAO).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
              </select>
              <div className="form-text">Sem decisão, as comissões ficam apenas como previsão.</div>
            </div>
            <div className="col-12 col-md-6">
              <label className="form-label" htmlFor="fin-comp">Competência da receita — D06</label>
              <select id="fin-comp" className="form-select" value={competencia} onChange={(e) => setCompetencia(e.target.value)}>
                <option value="">{PENDENTE}</option>
                {Object.entries(COMPETENCIA).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
              </select>
              <div className="form-text">Sem decisão, o resultado do mês é sempre provisório.</div>
            </div>
            <div className="col-12 d-none d-md-block" />
            <PerfisCampo prefixo="fin-reab" label="Perfis que podem reabrir mês fechado — D07" value={reabertura}
              onChange={setReabertura} disabled={!ehAdmin || busy} ajuda="Sem perfis, a reabertura fica bloqueada." />
            <PerfisCampo prefixo="fin-rest" label="Perfis que autorizam restituições — D07" value={restituicao}
              onChange={setRestituicao} disabled={!ehAdmin || busy} ajuda="Sem perfis, nenhuma restituição pode ser autorizada." />
            <Tri id="fin-prest" label="Política de restituição ao cliente — D09" value={permiteRest} onChange={setPermiteRest}
              sim="Permitir restituir valores ao cliente" nao="Não restituir valores"
              ajuda="Pendente: devoluções e trocas seguem só com o controle físico." />
            <Tri id="fin-pdif" label="Cobrança da diferença de troca — D09" value={permiteDif} onChange={setPermiteDif}
              sim="Permitir cobrar a diferença" nao="Não cobrar a diferença"
              ajuda="Pendente: a diferença calculada é só informativa." />
            <Tri id="fin-meta" label="Devoluções na meta do vendedor — D09" value={metaDesc} onChange={setMetaDesc}
              sim="Descontar devoluções da meta" nao="Não descontar devoluções"
              ajuda="Pendente: o atingimento é exibido como provisório." />
            <Tri id="fin-fech" label="Aprovação do fechamento com pendências — D10" value={exigeSem} onChange={setExigeSem}
              sim="Exigir zero pendências para aprovar" nao="Permitir aprovar com pendências"
              ajuda="Pendente: a prévia segue disponível, mas a aprovação fica bloqueada." />
          </div>
        </fieldset>
        {ehAdmin && (
          <div className="d-grid d-sm-flex justify-content-sm-end mt-3">
            <button className="btn btn-success" type="submit" disabled={busy || pctInvalido}>
              {busy ? "Salvando..." : "Salvar decisões financeiras"}
            </button>
          </div>
        )}
      </div>
    </form>
  );
}
