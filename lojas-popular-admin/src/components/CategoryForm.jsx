import { useMemo, useState } from "react";
import MaterialSelect from "./MaterialSelect";
import ErroAlert from "./gestao/ErroAlert";
import { normalizarTexto } from "../services/materiaisApi";

const TIPOS = [
  { valor: "TEXTO", rotulo: "Texto" },
  { valor: "NUMERO", rotulo: "Número" },
  { valor: "SELECAO_UNICA", rotulo: "Escolher uma opção" },
  { valor: "SELECAO_MULTIPLA", rotulo: "Escolher várias opções" },
];
const ehSelecao = (t) => t === "SELECAO_UNICA" || t === "SELECAO_MULTIPLA";

let seq = 0;
const nextKey = () => `k${++seq}`;

function daResposta(c) {
  return {
    _k: nextKey(),
    id: c.id,
    nome: c.nome ?? "",
    tipo: c.tipo ?? "TEXTO",
    unidade: c.unidade ?? "",
    obrigatoria: !!c.obrigatoria,
    exibirNaVitrine: !!c.exibirNaVitrine,
    ativa: c.ativa !== false,
    produtosComValor: c.produtosComValor ?? 0,
    opcoes: (c.opcoes ?? [])
      .slice()
      .sort((a, b) => (a.ordem ?? 0) - (b.ordem ?? 0))
      .map((o) => ({ _k: nextKey(), id: o.id, valor: o.valor, ativa: o.ativa !== false })),
  };
}

function mover(lista, i, delta) {
  const j = i + delta;
  if (j < 0 || j >= lista.length) return lista;
  const copia = lista.slice();
  [copia[i], copia[j]] = [copia[j], copia[i]];
  return copia;
}

function Opcoes({ car, onChange }) {
  const [novo, setNovo] = useState("");
  const opcoes = car.opcoes;

  function adicionar() {
    const v = novo.trim();
    if (!v) return;
    if (opcoes.some((o) => normalizarTexto(o.valor) === normalizarTexto(v))) {
      setNovo("");
      return;
    }
    onChange([...opcoes, { _k: nextKey(), valor: v, ativa: true }]);
    setNovo("");
  }

  return (
    <div className="mt-3">
      <label className="form-label fw-semibold mb-1">Opções para escolher</label>
      {opcoes.length === 0 && <div className="text-muted small mb-2">Nenhuma opção ainda. Digite abaixo e aperte Enter.</div>}
      <ul className="list-group mb-2">
        {opcoes.map((o, i) => (
          <li key={o._k} className="list-group-item d-flex align-items-center gap-2 py-1">
            <span className={`flex-grow-1 text-break ${o.ativa ? "" : "text-muted text-decoration-line-through"}`}>
              {o.valor}
              {!o.ativa && <span className="badge text-bg-secondary ms-2">inativa</span>}
            </span>
            <div className="btn-group btn-group-sm flex-shrink-0">
              {!o.ativa && (
                <button
                  type="button"
                  className="btn btn-outline-secondary"
                  onClick={() => onChange(opcoes.map((x) => (x._k === o._k ? { ...x, ativa: true } : x)))}
                >
                  Reativar
                </button>
              )}
              <button type="button" className="btn btn-outline-secondary" aria-label="Subir opção" disabled={i === 0} onClick={() => onChange(mover(opcoes, i, -1))}>
                ↑
              </button>
              <button type="button" className="btn btn-outline-secondary" aria-label="Descer opção" disabled={i === opcoes.length - 1} onClick={() => onChange(mover(opcoes, i, 1))}>
                ↓
              </button>
              <button type="button" className="btn btn-outline-danger" aria-label={`Remover opção ${o.valor}`} onClick={() => onChange(opcoes.filter((x) => x._k !== o._k))}>
                ×
              </button>
            </div>
          </li>
        ))}
      </ul>
      <div className="input-group">
        <input
          className="form-control"
          value={novo}
          maxLength={100}
          placeholder="Nova opção (Enter para adicionar)"
          onChange={(e) => setNovo(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              adicionar();
            }
          }}
        />
        <button type="button" className="btn btn-outline-primary" onClick={adicionar}>
          + Adicionar opção
        </button>
      </div>
      {opcoes.some((o) => o.id) && (
        <div className="form-text">Opção já usada em produtos, ao ser removida, fica inativa e o histórico dos produtos é mantido.</div>
      )}
    </div>
  );
}

function CaracteristicaCard({ car, indice, total, onChange, onMover, onRemover }) {
  const emUso = car.id && car.produtosComValor > 0;
  const set = (patch) => onChange({ ...car, ...patch });

  return (
    <div className="card mb-3" style={{ borderColor: car.ativa ? undefined : "var(--color-brand-yellow-dark)" }}>
      <div className="card-body">
        <div className="d-flex align-items-center gap-2 mb-2 flex-wrap">
          <strong>Característica {indice + 1}</strong>
          {!car.ativa && <span className="badge text-bg-secondary">inativa</span>}
          {emUso && (
            <span className="badge" style={{ background: "var(--color-brand-yellow-light)", color: "var(--color-ink)" }}>
              Usada em {car.produtosComValor} {car.produtosComValor === 1 ? "produto" : "produtos"}
            </span>
          )}
          <div className="btn-group btn-group-sm ms-auto">
            <button type="button" className="btn btn-outline-secondary" aria-label="Subir característica" disabled={indice === 0} onClick={() => onMover(-1)}>
              ↑
            </button>
            <button type="button" className="btn btn-outline-secondary" aria-label="Descer característica" disabled={indice === total - 1} onClick={() => onMover(1)}>
              ↓
            </button>
            <button type="button" className="btn btn-outline-danger" onClick={onRemover}>
              Remover
            </button>
          </div>
        </div>

        <div className="row g-2">
          <div className="col-12 col-md-6">
            <label className="form-label">Nome</label>
            <input
              className="form-control"
              value={car.nome}
              maxLength={100}
              placeholder="Ex.: Densidade"
              onChange={(e) => set({ nome: e.target.value })}
            />
          </div>
          <div className={ car.tipo === "NUMERO" ? "col-7 col-md-4" : "col-12 col-md-6" }>
            <label className="form-label">Tipo de resposta</label>
            <select
              className="form-select"
              value={car.tipo}
              disabled={emUso}
              onChange={(e) => set({ tipo: e.target.value, unidade: e.target.value === "NUMERO" ? car.unidade : "" })}
            >
              {TIPOS.map((t) => (
                <option key={t.valor} value={t.valor}>
                  {t.rotulo}
                </option>
              ))}
            </select>
          </div>
          {car.tipo === "NUMERO" && (
            <div className="col-5 col-md-2">
              <label className="form-label">Unidade</label>
              <input
                className="form-control"
                value={car.unidade}
                maxLength={20}
                placeholder="cm, kg"
                onChange={(e) => set({ unidade: e.target.value })}
              />
            </div>
          )}
        </div>
        {emUso && <div className="form-text">O tipo não pode mudar porque já há produtos preenchidos. Para outro tipo, crie uma nova característica.</div>}

        <div className="d-flex flex-wrap gap-4 mt-3">
          <div className="form-check form-switch">
            <input
              id={`obr-${car._k}`}
              className="form-check-input"
              type="checkbox"
              role="switch"
              checked={car.obrigatoria}
              onChange={(e) => set({ obrigatoria: e.target.checked })}
            />
            <label className="form-check-label" htmlFor={`obr-${car._k}`}>
              Obrigatório
            </label>
          </div>
          <div className="form-check form-switch">
            <input
              id={`vit-${car._k}`}
              className="form-check-input"
              type="checkbox"
              role="switch"
              checked={car.exibirNaVitrine}
              onChange={(e) => set({ exibirNaVitrine: e.target.checked })}
            />
            <label className="form-check-label" htmlFor={`vit-${car._k}`}>
              Mostrar na vitrine
            </label>
          </div>
          {!car.ativa && (
            <button type="button" className="btn btn-sm btn-outline-secondary" onClick={() => set({ ativa: true })}>
              Reativar
            </button>
          )}
        </div>
        {car.obrigatoria && car.id && !car.obrigatoriaOriginal && (
          <div className="form-text">Produtos que ainda não têm este dado vão aparecer como "precisam de complementação".</div>
        )}

        {ehSelecao(car.tipo) && <Opcoes car={car} onChange={(opcoes) => set({ opcoes })} />}
      </div>
    </div>
  );
}

/**
 * Formulário de categoria. `podeConfigurar` (gerente/proprietário) libera materiais e características;
 * para o vendedor só nome e descrição são enviados.
 */
export default function CategoryForm({ initial, onSubmit, onCancel, podeConfigurar = false }) {
  const [nome, setNome] = useState(initial?.nome ?? "");
  const [descricao, setDescricao] = useState(initial?.descricao ?? "");
  const [materialIds, setMaterialIds] = useState(() => (initial?.materiais ?? []).map((m) => m.id));
  const [cars, setCars] = useState(() =>
    (initial?.caracteristicas ?? [])
      .slice()
      .sort((a, b) => (a.ordem ?? 0) - (b.ordem ?? 0))
      .map((c) => ({ ...daResposta(c), obrigatoriaOriginal: !!c.obrigatoria })),
  );
  const [removidas, setRemovidas] = useState([]); // em uso: serão desativadas
  const [busy, setBusy] = useState(false);
  const [erro, setErro] = useState(null);
  const [validacao, setValidacao] = useState("");

  const materiaisIniciais = useMemo(() => initial?.materiais ?? [], [initial]);

  function addCar() {
    setCars((l) => [
      ...l,
      { _k: nextKey(), nome: "", tipo: "TEXTO", unidade: "", obrigatoria: false, exibirNaVitrine: false, ativa: true, produtosComValor: 0, opcoes: [] },
    ]);
  }

  function removerCar(c) {
    setCars((l) => l.filter((x) => x._k !== c._k));
    if (c.id && c.produtosComValor > 0) setRemovidas((r) => [...r, c]);
  }

  function desfazerRemocao(c) {
    setRemovidas((r) => r.filter((x) => x._k !== c._k));
    setCars((l) => [...l, c]);
  }

  function validar() {
    const nomes = new Set();
    for (const [i, c] of cars.entries()) {
      const n = normalizarTexto(c.nome);
      if (!n) return `Dê um nome à característica ${i + 1}.`;
      if (nomes.has(n)) return `A característica "${c.nome.trim()}" está repetida.`;
      nomes.add(n);
      if (ehSelecao(c.tipo) && !c.opcoes.some((o) => o.ativa)) return `Adicione ao menos uma opção em "${c.nome.trim()}".`;
    }
    return "";
  }

  async function submit(e) {
    e.preventDefault();
    if (busy) return;
    setErro(null);
    const v = podeConfigurar ? validar() : "";
    setValidacao(v);
    if (v) return;

    const payload = { nome: nome.trim(), descricao: descricao.trim() };
    if (podeConfigurar) {
      payload.materialIds = materialIds;
      payload.caracteristicas = cars.map((c, i) => ({
        id: c.id ?? null,
        nome: c.nome.trim(),
        tipo: c.tipo,
        unidade: c.tipo === "NUMERO" ? c.unidade.trim() || null : null,
        obrigatoria: c.obrigatoria,
        ordem: i,
        exibirNaVitrine: c.exibirNaVitrine,
        ativa: c.ativa,
        opcoes: ehSelecao(c.tipo)
          ? c.opcoes.map((o) => ({ id: o.id ?? null, valor: o.valor.trim(), ativa: o.ativa }))
          : [],
      }));
    }
    setBusy(true);
    try {
      await onSubmit(payload);
    } catch (err) {
      setErro(err);
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit}>
      <ErroAlert erro={erro} onClose={() => setErro(null)} />
      {validacao && (
        <div className="alert alert-warning" role="alert">
          {validacao}
        </div>
      )}

      <h6 className="mb-2" style={{ color: "var(--color-primary-dark)" }}>
        1. Nome e descrição
      </h6>
      <div className="row g-2">
        <div className="col-12 col-md-6">
          <label className="form-label">Nome da categoria</label>
          <input className="form-control" value={nome} maxLength={120} onChange={(e) => setNome(e.target.value)} required placeholder="Ex.: Guarda-roupas" />
        </div>
        <div className="col-12 col-md-6">
          <label className="form-label">Descrição</label>
          <textarea className="form-control" rows={2} value={descricao} onChange={(e) => setDescricao(e.target.value)} />
        </div>
      </div>

      {podeConfigurar && (
        <>
          <hr />
          <h6 className="mb-1" style={{ color: "var(--color-primary-dark)" }}>
            2. Materiais disponíveis para os produtos
          </h6>
          <p className="text-muted small mb-2">
            Escolha os materiais que os produtos desta categoria podem ter. Se não achar, digite o nome e cadastre.
          </p>
          <MaterialSelect value={materialIds} onChange={setMaterialIds} podeCadastrar />
          {materiaisIniciais.some((m) => m.ativo === false && materialIds.includes(m.id)) && (
            <div className="form-text">Material marcado como inativo continua nos produtos antigos, mas não pode ser escolhido de novo.</div>
          )}

          <hr />
          <h6 className="mb-1" style={{ color: "var(--color-primary-dark)" }}>
            3. Características dos produtos <span className="text-muted fw-normal">(opcional)</span>
          </h6>
          <p className="text-muted small mb-2">
            Dados extras que cada produto desta categoria terá, como "Densidade" ou "Número de portas". Pode deixar em branco.
          </p>

          {removidas.length > 0 && (
            <div className="alert alert-warning" role="alert">
              <div className="fw-semibold mb-1">Ao salvar, estas características serão desativadas (o histórico dos produtos é mantido):</div>
              <ul className="mb-0 ps-3">
                {removidas.map((c) => (
                  <li key={c._k}>
                    {c.nome} - usada em {c.produtosComValor} {c.produtosComValor === 1 ? "produto" : "produtos"}{" "}
                    <button type="button" className="btn btn-link btn-sm p-0 align-baseline" onClick={() => desfazerRemocao(c)}>
                      Desfazer
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          )}

          {cars.map((c, i) => (
            <CaracteristicaCard
              key={c._k}
              car={c}
              indice={i}
              total={cars.length}
              onChange={(novo) => setCars((l) => l.map((x) => (x._k === c._k ? novo : x)))}
              onMover={(d) => setCars((l) => mover(l, i, d))}
              onRemover={() => removerCar(c)}
            />
          ))}
          <button type="button" className="btn btn-outline-primary" onClick={addCar}>
            + Adicionar característica
          </button>
        </>
      )}

      <div className="mt-4 d-flex gap-2 flex-column flex-sm-row">
        <button className="btn btn-primary" type="submit" disabled={busy}>
          {busy ? "Salvando..." : "Salvar"}
        </button>
        <button className="btn btn-outline-secondary" type="button" onClick={onCancel} disabled={busy}>
          Cancelar
        </button>
      </div>
    </form>
  );
}
