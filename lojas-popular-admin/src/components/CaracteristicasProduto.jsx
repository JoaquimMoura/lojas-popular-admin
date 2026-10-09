// src/components/CaracteristicasProduto.jsx
// Campos dinâmicos do produto: materiais e características da categoria escolhida.
import MaterialSelect from "./MaterialSelect";

function Campo({ c, v, onChange, invalido, opcoesExtras }) {
  const id = `car-${c.id}`;
  const cls = invalido ? " is-invalid" : "";
  // opções ativas + as que o produto já tem (mesmo inativas, marcadas como tal)
  const selecionadas = v?.opcaoIds ?? [];
  const opcoes = [...(c.opcoes ?? [])].sort((a, b) => a.ordem - b.ordem).filter(
    (o) => o.ativa || selecionadas.includes(o.id),
  );
  for (const o of opcoesExtras ?? []) {
    if (!opcoes.some((x) => x.id === o.id) && selecionadas.includes(o.id)) opcoes.push(o);
  }
  const rotulo = (o) => (o.ativa ? o.valor : `${o.valor} (inativa)`);

  return (
    <div className="col-12 col-md-6">
      <label className="form-label" htmlFor={id}>
        {c.nome} {c.obrigatoria && <span className="text-danger" title="Obrigatório">*</span>}
      </label>

      {c.tipo === "TEXTO" && (
        <input
          id={id}
          className={`form-control${cls}`}
          maxLength={300}
          value={v?.valorTexto ?? ""}
          onChange={(e) => onChange({ valorTexto: e.target.value })}
        />
      )}

      {c.tipo === "NUMERO" && (
        <div className="input-group">
          <input
            id={id}
            type="number"
            step="any"
            min="0"
            inputMode="decimal"
            className={`form-control${cls}`}
            value={v?.valorNumero ?? ""}
            onChange={(e) => onChange({ valorNumero: e.target.value })}
          />
          {c.unidade && <span className="input-group-text">{c.unidade}</span>}
        </div>
      )}

      {c.tipo === "SELECAO_UNICA" && (
        <select
          id={id}
          className={`form-select${cls}`}
          value={selecionadas[0] ?? ""}
          onChange={(e) => onChange({ opcaoIds: e.target.value ? [Number(e.target.value)] : [] })}
        >
          <option value="">— escolher —</option>
          {opcoes.map((o) => (
            <option key={o.id} value={o.id}>{rotulo(o)}</option>
          ))}
        </select>
      )}

      {c.tipo === "SELECAO_MULTIPLA" && (
        <div id={id} className={`d-flex flex-wrap gap-2${invalido ? " border border-danger rounded p-2" : ""}`}>
          {opcoes.map((o) => {
            const marcado = selecionadas.includes(o.id);
            return (
              <label
                key={o.id}
                className="d-inline-flex align-items-center gap-2 border rounded-pill px-3 py-2"
                style={marcado ? { background: "var(--color-brand-yellow-light)", borderColor: "var(--color-brand-yellow-dark)" } : undefined}
              >
                <input
                  type="checkbox"
                  className="form-check-input m-0"
                  checked={marcado}
                  onChange={() =>
                    onChange({ opcaoIds: marcado ? selecionadas.filter((x) => x !== o.id) : [...selecionadas, o.id] })
                  }
                />
                <span>{rotulo(o)}</span>
              </label>
            );
          })}
          {opcoes.length === 0 && <span className="text-muted small">Sem opções cadastradas.</span>}
        </div>
      )}

      {invalido && <div className="text-danger small mt-1">Preencha este campo.</div>}
    </div>
  );
}

/**
 * Props:
 *  - categoria: detalhe da categoria (materiais, caracteristicas)
 *  - materialIds / onMaterialIds, materiaisDisponiveis
 *  - valores (mapa caracteristicaId -> {opcaoIds, valorTexto, valorNumero}) / onValor(id, parcial)
 *  - destacar: Set de ids de características obrigatórias vazias a destacar
 *  - opcoesDoProduto: opções já salvas no produto (podem estar inativas)
 *  - podeCadastrar, onMaterialCriado
 *  - avisoMaterial: nó React exibido abaixo dos materiais
 */
export default function CaracteristicasProduto({
  categoria, materialIds, onMaterialIds, materiaisDisponiveis, valores, onValor, destacar,
  opcoesDoProduto, podeCadastrar, onMaterialCriado, avisoMaterial,
}) {
  const cars = (categoria?.caracteristicas ?? [])
    .filter((c) => c.ativa)
    .sort((a, b) => a.ordem - b.ordem || a.nome.localeCompare(b.nome, "pt-BR"));
  const temMateriais = materiaisDisponiveis.length > 0 || materialIds.length > 0 || podeCadastrar;
  if (!temMateriais && cars.length === 0) return null;

  return (
    <div className="card mb-3">
      <div className="card-header fw-semibold text-danger">Características do produto</div>
      <div className="card-body">
        <p className="text-muted small">
          Cor e tamanho que mudam preço ou estoque ficam em Variações. Medidas e peso ficam em Dimensões.
          Aqui vão só as informações que descrevem o produto.
        </p>
        <div className="row g-3">
          {temMateriais && (
            <div className="col-12">
              <label className="form-label">Materiais do produto</label>
              <MaterialSelect
                value={materialIds}
                onChange={onMaterialIds}
                materiaisDisponiveis={materiaisDisponiveis}
                podeCadastrar={podeCadastrar}
                onCriado={onMaterialCriado}
                placeholder="Buscar material da categoria..."
              />
              {materiaisDisponiveis.length === 0 && (
                <div className="form-text">Esta categoria ainda não tem materiais liberados.</div>
              )}
              {avisoMaterial}
            </div>
          )}
          {cars.map((c) => (
            <Campo
              key={c.id}
              c={c}
              v={valores[c.id]}
              onChange={(parcial) => onValor(c.id, parcial)}
              invalido={destacar.has(c.id)}
              opcoesExtras={opcoesDoProduto[c.id]}
            />
          ))}
        </div>
      </div>
    </div>
  );
}
