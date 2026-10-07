import { useMemo, useState } from "react";
import { toast } from "react-toastify";
import { useAuth } from "../../../context/AuthContext";
import { financeiroApi } from "../../../services/financeiroApi";
import ErroAlert from "../../../components/gestao/ErroAlert";
import FormModal from "../../../components/gestao/FormModal";
import ModalShell from "../../../components/gestao/ModalShell";
import { Secao } from "../../../components/gestao/Secao";
import { useCarga } from "../../../components/gestao/useCarga";
import { Barra, CampoValor, Carregando, TabelaCards } from "../../../components/gestao/financeiro/Comuns";
import { fmtDate, fmtDateTime, fmtMoney, isAdmin } from "../../../utils/format";

const nomeLinha = (l) => `${l.produto}${l.variacao ? ` — ${l.variacao}` : ""}${l.sku ? ` (${l.sku})` : ""}`;

function Custo({ valor }) {
  return valor == null ? <span className="badge text-bg-warning text-dark">Não informado</span> : <>{fmtMoney(valor)}</>;
}

function RegistrarCustoModal({ linhas, inicial, onClose, onFeito }) {
  const [idx, setIdx] = useState(inicial >= 0 ? String(inicial) : "");
  const [custo, setCusto] = useState("");
  const [desde, setDesde] = useState("");
  const [motivo, setMotivo] = useState("");
  const alvo = idx === "" ? null : linhas[Number(idx)];
  return (
    <FormModal titulo="Registrar custo" submitLabel="Registrar custo" size="md"
      submitDisabled={!alvo || custo === "" || Number(custo) < 0 || motivo.trim() === ""} onClose={onClose}
      onSubmit={async () => {
        await financeiroApi.registrarCusto({
          produtoId: alvo.produtoId,
          variacaoId: alvo.variacaoId ?? null,
          custo: Number(custo),
          vigenteDesde: desde || null,
          motivo: motivo.trim(),
        });
        toast.success("Custo registrado.");
        onFeito();
      }}>
      <div className="alert alert-info">
        O novo custo vale para as vendas confirmadas <strong>daqui em diante</strong> (a partir da vigência). Vendas já
        confirmadas <strong>não mudam</strong>. O registro anterior é mantido no histórico.
      </div>
      <div className="row g-3">
        <div className="col-12">
          <label className="form-label">Produto / variação <span className="text-danger">*</span></label>
          <select className="form-select" value={idx} onChange={(e) => setIdx(e.target.value)}>
            <option value="">Selecione...</option>
            {linhas.map((l, i) => <option key={`${l.produtoId}-${l.variacaoId ?? 0}`} value={i}>{nomeLinha(l)}</option>)}
          </select>
        </div>
        <div className="col-12 col-sm-6">
          <CampoValor label="Custo unitário" obrigatorio min="0" value={custo} onChange={setCusto}
            ajuda="Informe 0 apenas se o custo for de fato zero." />
        </div>
        <div className="col-12 col-sm-6">
          <label className="form-label">Vigente desde</label>
          <input type="date" className="form-control" value={desde} onChange={(e) => setDesde(e.target.value)} />
          <div className="form-text">Em branco = hoje.</div>
        </div>
        <div className="col-12">
          <label className="form-label">Motivo <span className="text-danger">*</span></label>
          <textarea className="form-control" rows={2} maxLength={300} value={motivo} onChange={(e) => setMotivo(e.target.value)}
            placeholder="Ex.: custo conforme nota de compra" />
        </div>
      </div>
    </FormModal>
  );
}

function InformarItemModal({ item, onClose, onFeito }) {
  const [custo, setCusto] = useState("");
  const [motivo, setMotivo] = useState("");
  return (
    <FormModal titulo={`Informar custo — pedido #${item.pedidoId}`} submitLabel="Informar custo" variant="danger" size="md"
      submitDisabled={custo === "" || Number(custo) < 0 || motivo.trim() === ""} onClose={onClose}
      onSubmit={async () => {
        await financeiroApi.informarCustoItem(item.itemId, { custo: Number(custo), motivo: motivo.trim() });
        toast.success("Custo do item informado.");
        onFeito();
      }}>
      <div className="alert alert-warning">
        <strong>Atenção: depois de informado, o custo deste item não pode ser alterado.</strong> Ele fica gravado na venda
        e passa a compor a margem. Confirme o valor com a nota de compra.
      </div>
      <div className="mb-3">{item.descricao} · {item.quantidade} un.</div>
      <div className="row g-3">
        <div className="col-12"><CampoValor label="Custo unitário" obrigatorio min="0" value={custo} onChange={setCusto} autoFocus /></div>
        <div className="col-12">
          <label className="form-label">Motivo <span className="text-danger">*</span></label>
          <textarea className="form-control" rows={2} maxLength={300} value={motivo} onChange={(e) => setMotivo(e.target.value)} />
        </div>
      </div>
    </FormModal>
  );
}

function HistoricoModal({ linha, onClose }) {
  const { dados, loading, erro } = useCarga(() => financeiroApi.historicoCusto(linha.produtoId), [linha.produtoId]);
  return (
    <ModalShell titulo={`Histórico de custo — ${linha.produto}`} onClose={onClose}>
      {loading && <Carregando />}
      <ErroAlert erro={erro} />
      {dados && (
        <TabelaCards
          linhas={dados}
          vazio="Nenhum custo registrado para este produto."
          colunas={[
            { titulo: "Vigente desde", render: (h) => fmtDate(h.vigenteDesde) },
            { titulo: "Escopo", render: (h) => (h.variacaoId ? `Variação #${h.variacaoId}` : "Produto todo") },
            { titulo: "Custo", fim: true, render: (h) => fmtMoney(h.custo) },
            { titulo: "Motivo", render: (h) => h.motivo ?? "—" },
            { titulo: "Registrado por", render: (h) => h.registradoPor ?? "—" },
          ]}
        />
      )}
    </ModalShell>
  );
}

export default function CustosPage() {
  const { user } = useAuth();
  const admin = isAdmin(user);
  const lista = useCarga(() => financeiroApi.custos(), []);
  const sem = useCarga(() => financeiroApi.itensSemCusto(), []);
  const [filtro, setFiltro] = useState("");
  const [registrar, setRegistrar] = useState(null); // índice na lista completa (-1 = vazio)
  const [item, setItem] = useState(null);
  const [hist, setHist] = useState(null);

  const todas = useMemo(() => lista.dados ?? [], [lista.dados]);
  const visiveis = useMemo(() => {
    const t = filtro.trim().toLowerCase();
    return t ? todas.filter((l) => nomeLinha(l).toLowerCase().includes(t)) : todas;
  }, [todas, filtro]);

  function feito() {
    setRegistrar(null);
    setItem(null);
    lista.recarregar();
    sem.recarregar();
  }

  return (
    <div>
      <div className="small text-muted mb-3">
        Custo nulo significa &quot;Não informado&quot; (nunca zero). Margem só é calculada quando todos os itens têm custo.
        {!admin && " Somente o proprietário registra ou informa custos."}
      </div>

      <Secao titulo="Itens vendidos sem custo">
        <ErroAlert erro={sem.erro} />
        {sem.loading && !sem.dados ? <Carregando /> : (
          <TabelaCards
            linhas={sem.dados ?? []}
            chave={(l) => l.itemId}
            vazio="Nenhum item vendido sem custo."
            colunas={[
              { titulo: "Pedido", render: (l) => `#${l.pedidoId}` },
              { titulo: "Item", render: (l) => l.descricao },
              { titulo: "Qtd.", fim: true, render: (l) => l.quantidade },
              { titulo: "Confirmado em", render: (l) => fmtDateTime(l.confirmadoEm) },
              ...(admin ? [{
                titulo: "Ação",
                render: (l) => <button className="btn btn-sm btn-outline-danger" onClick={() => setItem(l)}>Informar custo</button>,
              }] : []),
            ]}
          />
        )}
      </Secao>

      <Secao titulo="Custo vigente por produto">
        <Barra>
          <input className="form-control" style={{ maxWidth: 360 }} placeholder="Buscar produto, variação ou SKU"
            value={filtro} onChange={(e) => setFiltro(e.target.value)} />
          {admin && <button className="btn btn-primary" onClick={() => setRegistrar(-1)}>Registrar custo</button>}
        </Barra>
        <ErroAlert erro={lista.erro} />
        {lista.loading && !lista.dados ? <Carregando /> : (
          <TabelaCards
            linhas={visiveis}
            chave={(l) => `${l.produtoId}-${l.variacaoId ?? 0}`}
            vazio="Nenhum produto encontrado."
            colunas={[
              { titulo: "Produto", render: nomeLinha },
              { titulo: "Custo vigente", fim: true, render: (l) => <Custo valor={l.custo} /> },
              { titulo: "Desde", render: (l) => fmtDate(l.vigenteDesde) },
              { titulo: "Origem", render: (l) => (l.custo == null ? "—" : l.herdadoDoProduto ? "Herdado do produto" : "Da variação") },
              {
                titulo: "Ações",
                render: (l) => (
                  <div className="d-flex flex-wrap gap-1 justify-content-end">
                    <button className="btn btn-sm btn-outline-secondary" onClick={() => setHist(l)}>Histórico</button>
                    {admin && <button className="btn btn-sm btn-outline-primary" onClick={() => setRegistrar(todas.indexOf(l))}>Registrar custo</button>}
                  </div>
                ),
              },
            ]}
          />
        )}
      </Secao>

      {registrar !== null && admin && <RegistrarCustoModal linhas={todas} inicial={registrar} onClose={() => setRegistrar(null)} onFeito={feito} />}
      {item && admin && <InformarItemModal item={item} onClose={() => setItem(null)} onFeito={feito} />}
      {hist && <HistoricoModal linha={hist} onClose={() => setHist(null)} />}
    </div>
  );
}
