import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { toast } from "react-toastify";
import { vendasApi } from "../../services/vendasApi";
import { clientesApi } from "../../services/clientesApi";
import { usuariosApi } from "../../services/usuariosApi";
import { estoqueApi } from "../../services/estoqueApi";
import { productsApi } from "../../services/productsApi";
import { useAuth } from "../../context/AuthContext";
import ClienteForm from "../../components/gestao/ClienteForm";
import ErroAlert from "../../components/gestao/ErroAlert";
import ModalShell from "../../components/gestao/ModalShell";
import PendenciasAlert from "../../components/gestao/PendenciasAlert";
import {
  CANAIS,
  FORMAS,
  MODALIDADES_ITEM,
  TIPOS_ENTREGA,
  fmtMoney,
  fmtPercent,
  isGestor,
  novaChave,
} from "../../utils/format";

const ETAPAS = ["Cliente", "Itens", "Pagamento e entrega", "Revisão"];

function opcoesModalidade(produto) {
  const m = produto?.modalidade ?? "PRONTA_ENTREGA";
  if (m === "AMBAS") return ["PRONTA_ENTREGA", "ENCOMENDA"];
  return [m];
}

function descVariacao(v) {
  return [v.cor, v.tamanho].filter(Boolean).join(" / ") || v.sku || `Variação ${v.id}`;
}

function enderecoTexto(e) {
  return `${e.apelido ? `${e.apelido}: ` : ""}${e.logradouro}${e.numero ? `, ${e.numero}` : ""} — ${e.cidade}/${e.uf}`;
}

export default function NovaVendaPage() {
  const { id } = useParams();
  const editando = !!id;
  const navigate = useNavigate();
  const { user } = useAuth();
  const gestor = isGestor(user);

  const [etapa, setEtapa] = useState(0);
  const [config, setConfig] = useState(null);
  const [configErro, setConfigErro] = useState(null);
  const [carregando, setCarregando] = useState(editando);
  const [bloqueioEdicao, setBloqueioEdicao] = useState(null);
  const [erro, setErro] = useState(null);
  const [saving, setSaving] = useState(false);
  const chaveRef = useRef(null);

  // cliente
  const [cliente, setCliente] = useState(null);
  const [buscaCliente, setBuscaCliente] = useState("");
  const [resultadosCliente, setResultadosCliente] = useState(null);
  const [buscandoCliente, setBuscandoCliente] = useState(false);
  const [modalCliente, setModalCliente] = useState(false);

  // itens
  const [itens, setItens] = useState([]);
  const [buscaProduto, setBuscaProduto] = useState("");
  const [resultadosProduto, setResultadosProduto] = useState([]);
  const [buscandoProduto, setBuscandoProduto] = useState(false);
  const [rascunho, setRascunho] = useState(null); // { produto, variacaoId, modalidade, quantidade }
  const [saldos, setSaldos] = useState([]);

  // pagamento e entrega
  const [vendedores, setVendedores] = useState([]);
  const [vendedorId, setVendedorId] = useState("");
  const [canal, setCanal] = useState("LOJA");
  const [tipoEntrega, setTipoEntrega] = useState("RETIRADA");
  const [enderecoId, setEnderecoId] = useState("");
  const [forma, setForma] = useState("");
  const [parcelas, setParcelas] = useState("");
  const [desconto, setDesconto] = useState("");
  const [justificativa, setJustificativa] = useState("");
  const [observacao, setObservacao] = useState("");

  // configuração comercial
  useEffect(() => {
    vendasApi.configuracao().then(setConfig).catch(setConfigErro);
  }, []);

  // vendedores (somente gestor)
  useEffect(() => {
    if (!gestor) return;
    usuariosApi.vendedores().then((l) => setVendedores(Array.isArray(l) ? l : [])).catch(() => setVendedores([]));
  }, [gestor]);

  // carrega venda na edição
  useEffect(() => {
    if (!editando) return;
    let ativo = true;
    (async () => {
      try {
        const v = await vendasApi.obter(id);
        if (!ativo) return;
        if (!v.acoes?.podeEditar) {
          setBloqueioEdicao("Este pedido não pode mais ser editado.");
          return;
        }
        let c = null;
        if (v.cliente?.id) {
          try {
            c = await clientesApi.obter(v.cliente.id);
          } catch {
            c = { id: v.cliente.id, nome: v.cliente.nome, telefone: v.cliente.telefone, enderecos: [] };
          }
        }
        if (!ativo) return;
        setCliente(c);
        setItens(
          (v.itens ?? []).map((i) => ({
            key: `i-${i.id}`,
            produtoId: i.produtoId,
            variacaoId: i.variacaoId ?? null,
            descricao: i.descricao,
            modalidade: i.modalidade,
            quantidade: i.quantidade,
            precoRef: Number(i.precoBase ?? 0),
          })),
        );
        setVendedorId(v.vendedor?.id ? String(v.vendedor.id) : "");
        setCanal(v.canal ?? "LOJA");
        setTipoEntrega(v.tipoEntrega ?? "RETIRADA");
        if (v.tipoEntrega === "ENTREGA" && v.endereco && c?.enderecos) {
          const e = v.endereco;
          const m = c.enderecos.find(
            (x) => x.logradouro === e.logradouro && (x.numero ?? "") === (e.numero ?? "") && (x.cep ?? "") === (e.cep ?? ""),
          );
          if (m) setEnderecoId(String(m.id));
        }
        setForma(v.formaPagamento ?? "");
        setParcelas(v.parcelas != null ? String(v.parcelas) : "");
        setDesconto(v.desconto && Number(v.desconto) > 0 ? String(v.desconto) : "");
        const ultima = (v.descontos ?? [])[0];
        setJustificativa(ultima?.justificativa ?? "");
        setObservacao(v.observacao ?? "");
      } catch (err) {
        if (ativo) setErro(err);
      } finally {
        if (ativo) setCarregando(false);
      }
    })();
    return () => {
      ativo = false;
    };
  }, [editando, id]);

  // busca de clientes (debounce)
  useEffect(() => {
    const t = buscaCliente.trim();
    if (t.length < 2) {
      setResultadosCliente(null);
      return undefined;
    }
    let ativo = true;
    setBuscandoCliente(true);
    const timer = setTimeout(() => {
      clientesApi
        .buscar(t)
        .then((l) => ativo && setResultadosCliente(Array.isArray(l) ? l : []))
        .catch(() => ativo && setResultadosCliente([]))
        .finally(() => ativo && setBuscandoCliente(false));
    }, 400);
    return () => {
      ativo = false;
      clearTimeout(timer);
    };
  }, [buscaCliente]);

  // busca de produtos (debounce)
  useEffect(() => {
    const t = buscaProduto.trim();
    if (t.length < 2) {
      setResultadosProduto([]);
      return undefined;
    }
    let ativo = true;
    setBuscandoProduto(true);
    const timer = setTimeout(() => {
      productsApi
        .list({ page: 0, size: 10, nome: t })
        .then((r) => {
          const c = r.data?.content ?? r.data;
          if (ativo) setResultadosProduto(Array.isArray(c) ? c : []);
        })
        .catch(() => ativo && setResultadosProduto([]))
        .finally(() => ativo && setBuscandoProduto(false));
    }, 400);
    return () => {
      ativo = false;
      clearTimeout(timer);
    };
  }, [buscaProduto]);

  // disponibilidade do produto em edição de item
  const produtoRascunhoId = rascunho?.produto?.id;
  useEffect(() => {
    if (!produtoRascunhoId) return undefined;
    let ativo = true;
    estoqueApi
      .saldos({ produtoId: produtoRascunhoId })
      .then((l) => ativo && setSaldos(Array.isArray(l) ? l : []))
      .catch(() => ativo && setSaldos([]));
    return () => {
      ativo = false;
      setSaldos([]);
    };
  }, [produtoRascunhoId]);

  const condicoes = useMemo(() => config?.condicoes ?? [], [config]);
  const formasDisponiveis = useMemo(() => [...new Set(condicoes.map((c) => c.forma))], [condicoes]);
  const parcelasDisponiveis = useMemo(
    () => condicoes.filter((c) => c.forma === forma).map((c) => c.parcelas).sort((a, b) => a - b),
    [condicoes, forma],
  );
  const configPendente = !config || condicoes.length === 0 || config.arredondamento == null;
  const limite = config?.limiteDescontoPercentual ?? null;

  const subtotalEstimado = itens.reduce((s, i) => s + i.precoRef * i.quantidade, 0);
  const descontoNum = Number(desconto) || 0;
  const percentualEstimado = subtotalEstimado > 0 ? (descontoNum / subtotalEstimado) * 100 : 0;
  const excedeLimite = limite != null && descontoNum > 0 && percentualEstimado > Number(limite);

  const saldoRascunho = useMemo(() => {
    if (!rascunho) return null;
    const vid = rascunho.variacaoId ? Number(rascunho.variacaoId) : null;
    return saldos.find((s) => (s.variacaoId ?? null) === vid) ?? null;
  }, [rascunho, saldos]);

  function selecionarProduto(p) {
    const opcoes = opcoesModalidade(p);
    setRascunho({
      produto: p,
      variacaoId: "",
      modalidade: opcoes[0],
      quantidade: 1,
    });
  }

  function adicionarItem() {
    if (!rascunho) return;
    const { produto, variacaoId, modalidade, quantidade } = rascunho;
    const variacoes = produto.variacoes ?? [];
    if (variacoes.length > 0 && !variacaoId) {
      toast.warn("Escolha a variação do produto.");
      return;
    }
    const q = Number(quantidade);
    if (!Number.isInteger(q) || q <= 0) {
      toast.warn("A quantidade deve ser um número inteiro positivo.");
      return;
    }
    const v = variacoes.find((x) => String(x.id) === String(variacaoId));
    setItens((l) => [
      ...l,
      {
        key: `n-${Date.now()}-${l.length}`,
        produtoId: produto.id,
        variacaoId: v ? v.id : null,
        descricao: v ? `${produto.nome} — ${descVariacao(v)}` : produto.nome,
        modalidade,
        quantidade: q,
        precoRef: Number(produto.preco ?? 0) + Number(v?.adicionalPreco ?? 0),
      },
    ]);
    setRascunho(null);
    setBuscaProduto("");
    setResultadosProduto([]);
  }

  function removerItem(key) {
    setItens((l) => l.filter((i) => i.key !== key));
  }

  function alterarQtd(key, valor) {
    const q = Number(valor);
    setItens((l) => l.map((i) => (i.key === key ? { ...i, quantidade: q } : i)));
  }

  function escolherCliente(c) {
    setCliente(c);
    setEnderecoId("");
    setBuscaCliente("");
    setResultadosCliente(null);
  }

  const enderecos = cliente?.enderecos ?? [];

  // se há um único endereço/principal, pré-seleciona ao escolher entrega
  function escolherTipoEntrega(t) {
    setTipoEntrega(t);
    if (t === "ENTREGA" && !enderecoId && enderecos.length > 0) {
      const p = enderecos.find((e) => e.principal) ?? enderecos[0];
      setEnderecoId(String(p.id));
    }
  }

  const itensValidos = itens.length > 0 && itens.every((i) => Number.isInteger(i.quantidade) && i.quantidade > 0);
  const pagamentoValido =
    !!forma && !!parcelas && !!canal && !!tipoEntrega && (tipoEntrega !== "ENTREGA" || !!enderecoId);

  function podeAvancar() {
    if (etapa === 0) return !!cliente;
    if (etapa === 1) return itensValidos;
    if (etapa === 2) return pagamentoValido && !configPendente;
    return true;
  }

  async function salvar() {
    if (saving || configPendente || !cliente || !itensValidos || !pagamentoValido) return;
    if (!chaveRef.current) chaveRef.current = novaChave();
    setSaving(true);
    setErro(null);
    const payload = {
      clienteId: cliente.id,
      canal,
      tipoEntrega,
      enderecoId: tipoEntrega === "ENTREGA" ? Number(enderecoId) : null,
      formaPagamento: forma,
      parcelas: Number(parcelas),
      desconto: limite == null ? 0 : descontoNum,
      justificativaDesconto: descontoNum > 0 && justificativa.trim() ? justificativa.trim() : null,
      observacao: observacao.trim() || null,
      chaveIdempotencia: chaveRef.current,
      itens: itens.map((i) => ({
        produtoId: i.produtoId,
        variacaoId: i.variacaoId,
        quantidade: i.quantidade,
        modalidade: i.modalidade,
      })),
    };
    if (gestor && vendedorId) payload.vendedorId = Number(vendedorId);
    try {
      const salvo = editando ? await vendasApi.atualizar(id, payload) : await vendasApi.registrar(payload);
      toast.success("Rascunho salvo. Confira os valores e confirme a venda.");
      navigate(`/gestao/pedidos/${salvo.id}`);
    } catch (err) {
      setErro(err);
    } finally {
      setSaving(false);
    }
  }

  if (carregando) return <div className="text-center text-muted py-5">Carregando pedido...</div>;
  if (bloqueioEdicao) {
    return (
      <div>
        <div className="alert alert-warning">{bloqueioEdicao}</div>
        <Link className="btn btn-outline-secondary" to={`/gestao/pedidos/${id}`}>Voltar ao pedido</Link>
      </div>
    );
  }

  const variacoesRascunho = rascunho?.produto?.variacoes ?? [];

  return (
    <div>
      <h3 className="mb-3">{editando ? `Editar venda #${id}` : "Nova venda"}</h3>

      <div className="d-flex mb-3">
        {ETAPAS.map((nome, i) => (
          <div key={nome} className={`gestao-step ${i === etapa ? "ativo" : i < etapa ? "feito" : ""}`}>
            {i + 1}. {nome}
          </div>
        ))}
      </div>

      {configErro && <ErroAlert erro={configErro} />}
      {configPendente && config && (
        <div className="alert alert-warning border-warning">
          <div className="fw-semibold">Configuração comercial pendente</div>
          <div>
            Não é possível salvar a venda enquanto não houver condições de pagamento ativas e o arredondamento definido.
            {gestor ? " Ajuste em Configuração comercial." : " Avise um gerente ou o proprietário."}
          </div>
          <PendenciasAlert pendencias={config.pendencias} titulo="Pendências" />
        </div>
      )}
      <ErroAlert erro={erro} onClose={() => setErro(null)} />

      {/* Etapa 1: Cliente */}
      {etapa === 0 && (
        <div className="card">
          <div className="card-header">Cliente</div>
          <div className="card-body">
            {cliente && (
              <div className="alert alert-success d-flex justify-content-between align-items-start gap-2">
                <div>
                  <strong>{cliente.nome}</strong>
                  <div className="small">{cliente.telefone || "Sem telefone"}{cliente.cpf ? ` · CPF ${cliente.cpf}` : ""}</div>
                </div>
                <button className="btn btn-sm btn-outline-secondary" onClick={() => setCliente(null)}>Trocar</button>
              </div>
            )}
            {!cliente && (
              <>
                <label className="form-label">Buscar cliente (nome, CPF ou telefone)</label>
                <input type="search" className="form-control" value={buscaCliente}
                  onChange={(e) => setBuscaCliente(e.target.value)} placeholder="Digite ao menos 2 caracteres" />
                {buscandoCliente && <div className="small text-muted mt-2">Buscando...</div>}
                {resultadosCliente && (
                  <div className="list-group mt-2">
                    {resultadosCliente.length === 0 && !buscandoCliente && (
                      <div className="list-group-item text-muted">Nenhum cliente encontrado.</div>
                    )}
                    {resultadosCliente.map((c) => (
                      <button key={c.id} type="button" className="list-group-item list-group-item-action"
                        onClick={() => escolherCliente(c)}>
                        <strong>{c.nome}</strong>
                        <div className="small text-muted">{c.telefone || "Sem telefone"}{c.cpf ? ` · CPF ${c.cpf}` : ""}</div>
                      </button>
                    ))}
                  </div>
                )}
                <div className="d-grid mt-3">
                  <button className="btn btn-outline-primary" onClick={() => setModalCliente(true)}>
                    + Cadastrar novo cliente
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      )}

      {/* Etapa 2: Itens */}
      {etapa === 1 && (
        <div>
          <div className="card mb-3">
            <div className="card-header">Adicionar produto</div>
            <div className="card-body">
              {!rascunho && (
                <>
                  <input type="search" className="form-control" placeholder="Buscar produto pelo nome"
                    value={buscaProduto} onChange={(e) => setBuscaProduto(e.target.value)} />
                  {buscandoProduto && <div className="small text-muted mt-2">Buscando...</div>}
                  {resultadosProduto.length > 0 && (
                    <div className="list-group mt-2">
                      {resultadosProduto.map((p) => (
                        <button key={p.id} type="button" className="list-group-item list-group-item-action"
                          onClick={() => selecionarProduto(p)}>
                          <div className="d-flex justify-content-between">
                            <strong>{p.nome}</strong>
                            <span>{fmtMoney(p.preco)}</span>
                          </div>
                          <div className="small text-muted">{p.sku ? `SKU ${p.sku}` : ""}</div>
                        </button>
                      ))}
                    </div>
                  )}
                </>
              )}

              {rascunho && (
                <div>
                  <div className="d-flex justify-content-between align-items-start">
                    <strong>{rascunho.produto.nome}</strong>
                    <button className="btn btn-sm btn-outline-secondary" onClick={() => setRascunho(null)}>Trocar</button>
                  </div>
                  <div className="row g-2 mt-1">
                    {variacoesRascunho.length > 0 && (
                      <div className="col-12">
                        <label className="form-label">Variação *</label>
                        <select className="form-select" value={rascunho.variacaoId}
                          onChange={(e) => setRascunho((r) => ({ ...r, variacaoId: e.target.value }))}>
                          <option value="">— selecione —</option>
                          {variacoesRascunho.map((v) => (
                            <option key={v.id} value={v.id}>{descVariacao(v)}</option>
                          ))}
                        </select>
                      </div>
                    )}
                    <div className="col-7">
                      <label className="form-label">Modalidade</label>
                      <select className="form-select" value={rascunho.modalidade}
                        onChange={(e) => setRascunho((r) => ({ ...r, modalidade: e.target.value }))}>
                        {opcoesModalidade(rascunho.produto).map((m) => (
                          <option key={m} value={m}>{MODALIDADES_ITEM[m]}</option>
                        ))}
                      </select>
                    </div>
                    <div className="col-5">
                      <label className="form-label">Quantidade</label>
                      <input type="number" min="1" step="1" className="form-control" value={rascunho.quantidade}
                        onChange={(e) => setRascunho((r) => ({ ...r, quantidade: e.target.value }))} />
                    </div>
                  </div>
                  <div className="mt-2 small">
                    {saldoRascunho ? (
                      <span className={saldoRascunho.disponivel != null && saldoRascunho.disponivel < Number(rascunho.quantidade) && rascunho.modalidade === "PRONTA_ENTREGA" ? "text-danger" : "text-muted"}>
                        Disponível: <strong>{saldoRascunho.disponivel ?? "—"}</strong> (físico {saldoRascunho.fisico ?? "—"}, reservado {saldoRascunho.reservado})
                        {saldoRascunho.alerta ? ` — ${saldoRascunho.alerta}` : ""}
                      </span>
                    ) : (
                      <span className="text-muted">
                        {variacoesRascunho.length > 0 && !rascunho.variacaoId
                          ? "Escolha a variação para ver a disponibilidade."
                          : "Disponibilidade indisponível."}
                      </span>
                    )}
                  </div>
                  <div className="d-grid mt-3">
                    <button className="btn btn-success" onClick={adicionarItem}>Adicionar à venda</button>
                  </div>
                </div>
              )}
            </div>
          </div>

          <div className="card">
            <div className="card-header">Itens da venda</div>
            <div className="card-body">
              {itens.length === 0 ? (
                <div className="text-muted">Nenhum item adicionado.</div>
              ) : (
                <div className="d-grid gap-2">
                  {itens.map((i) => (
                    <div key={i.key} className="border rounded p-2">
                      <div className="fw-semibold">{i.descricao}</div>
                      <div className="small text-muted">{MODALIDADES_ITEM[i.modalidade] ?? i.modalidade}</div>
                      <div className="d-flex align-items-center gap-2 mt-2">
                        <input type="number" min="1" step="1" className="form-control" style={{ maxWidth: 110 }}
                          value={i.quantidade} aria-label="Quantidade"
                          onChange={(e) => alterarQtd(i.key, e.target.value)} />
                        <button className="btn btn-outline-danger ms-auto" onClick={() => removerItem(i.key)}>Remover</button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
              {itens.length > 0 && !itensValidos && (
                <div className="text-danger small mt-2">Todas as quantidades devem ser inteiros positivos.</div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Etapa 3: Pagamento e entrega */}
      {etapa === 2 && (
        <div className="card">
          <div className="card-header">Pagamento e entrega</div>
          <div className="card-body">
            <div className="row g-3">
              {gestor && (
                <div className="col-12">
                  <label className="form-label">Vendedor responsável</label>
                  <select className="form-select" value={vendedorId} onChange={(e) => setVendedorId(e.target.value)}>
                    <option value="">Eu mesmo</option>
                    {vendedores.map((v) => (
                      <option key={v.id} value={v.id}>{v.nome}</option>
                    ))}
                  </select>
                </div>
              )}
              <div className="col-12 col-md-6">
                <label className="form-label">Canal *</label>
                <select className="form-select" value={canal} onChange={(e) => setCanal(e.target.value)}>
                  {Object.entries(CANAIS).map(([k, v]) => (
                    <option key={k} value={k}>{v}</option>
                  ))}
                </select>
              </div>
              <div className="col-12 col-md-6">
                <label className="form-label">Tipo de entrega *</label>
                <select className="form-select" value={tipoEntrega} onChange={(e) => escolherTipoEntrega(e.target.value)}>
                  {Object.entries(TIPOS_ENTREGA).map(([k, v]) => (
                    <option key={k} value={k}>{v}</option>
                  ))}
                </select>
              </div>

              {tipoEntrega === "ENTREGA" && (
                <div className="col-12">
                  <label className="form-label">Endereço de entrega *</label>
                  {enderecos.length === 0 ? (
                    <div className="alert alert-warning mb-0">
                      Este cliente não tem endereço cadastrado. Edite o cliente em Clientes para incluir um endereço.
                    </div>
                  ) : (
                    <div className="d-grid gap-2">
                      {enderecos.map((e) => (
                        <label key={e.id} className={`border rounded p-2 d-flex gap-2 ${String(e.id) === enderecoId ? "border-danger" : ""}`}>
                          <input type="radio" name="endereco" checked={String(e.id) === enderecoId}
                            onChange={() => setEnderecoId(String(e.id))} />
                          <span>
                            {enderecoTexto(e)}
                            {e.principal && <span className="badge text-bg-secondary ms-2">Principal</span>}
                            {(e.bairro || e.cep) && (
                              <span className="d-block small text-muted">{[e.bairro, e.cep].filter(Boolean).join(" · ")}</span>
                            )}
                          </span>
                        </label>
                      ))}
                    </div>
                  )}
                </div>
              )}

              <div className="col-12 col-md-6">
                <label className="form-label">Forma de pagamento *</label>
                <select className="form-select" value={forma} disabled={configPendente}
                  onChange={(e) => { setForma(e.target.value); setParcelas(""); }}>
                  <option value="">— selecione —</option>
                  {formasDisponiveis.map((f) => (
                    <option key={f} value={f}>{FORMAS[f] ?? f}</option>
                  ))}
                </select>
              </div>
              <div className="col-12 col-md-6">
                <label className="form-label">Parcelas *</label>
                <select className="form-select" value={parcelas} disabled={configPendente || !forma}
                  onChange={(e) => setParcelas(e.target.value)}>
                  <option value="">— selecione —</option>
                  {parcelasDisponiveis.map((p) => (
                    <option key={p} value={p}>{p}x</option>
                  ))}
                </select>
                <div className="form-text">Somente condições ativas na configuração comercial.</div>
              </div>

              <div className="col-12 col-md-6">
                <label className="form-label">Desconto (R$)</label>
                <input type="number" min="0" step="0.01" className="form-control" value={desconto}
                  disabled={limite == null} onChange={(e) => setDesconto(e.target.value)} />
                {limite == null ? (
                  <div className="form-text text-danger">Limite de desconto não configurado (D03): desconto indisponível.</div>
                ) : (
                  <div className="form-text">Limite sem aprovação: {fmtPercent(limite)}.</div>
                )}
                {excedeLimite && (
                  <div className="alert alert-warning mt-2 mb-0 py-2">
                    Desconto estimado em {fmtPercent(percentualEstimado.toFixed(2))} (acima do limite de {fmtPercent(limite)}):
                    exigirá aprovação de gerente. O percentual final é calculado pelo servidor.
                  </div>
                )}
              </div>
              {descontoNum > 0 && limite != null && (
                <div className="col-12 col-md-6">
                  <label className="form-label">Justificativa do desconto</label>
                  <input className="form-control" maxLength={300} value={justificativa}
                    onChange={(e) => setJustificativa(e.target.value)} />
                </div>
              )}
              <div className="col-12">
                <label className="form-label">Observação</label>
                <textarea className="form-control" rows={2} maxLength={500} value={observacao}
                  onChange={(e) => setObservacao(e.target.value)} />
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Etapa 4: Revisão */}
      {etapa === 3 && (
        <div className="card">
          <div className="card-header">Revisão</div>
          <div className="card-body">
            <div className="mb-2"><span className="text-muted">Cliente:</span> <strong>{cliente?.nome}</strong></div>
            <div className="mb-2">
              <span className="text-muted">Canal / entrega:</span> {CANAIS[canal]} · {TIPOS_ENTREGA[tipoEntrega]}
              {tipoEntrega === "ENTREGA" && enderecos.find((e) => String(e.id) === enderecoId) && (
                <div className="small">{enderecoTexto(enderecos.find((e) => String(e.id) === enderecoId))}</div>
              )}
            </div>
            <div className="mb-2">
              <span className="text-muted">Pagamento:</span> {FORMAS[forma]} em {parcelas}x
            </div>
            {gestor && vendedorId && (
              <div className="mb-2">
                <span className="text-muted">Vendedor:</span> {vendedores.find((v) => String(v.id) === vendedorId)?.nome}
              </div>
            )}
            <hr />
            {itens.map((i) => (
              <div key={i.key} className="d-flex justify-content-between gap-2 py-1">
                <span>{i.quantidade} x {i.descricao} <span className="small text-muted">({MODALIDADES_ITEM[i.modalidade]})</span></span>
              </div>
            ))}
            {descontoNum > 0 && <div className="mt-2">Desconto solicitado: {fmtMoney(descontoNum)}</div>}
            <div className="alert alert-info mt-3 mb-0">
              Os preços, o ajuste da condição de pagamento, o desconto aplicado e o total são calculados pelo servidor ao
              salvar o rascunho. Confira os valores na tela do pedido antes de confirmar a venda.
              <div className="small mt-1">
                Estimativa pelo preço de tabela (sem ajuste da condição): {fmtMoney(subtotalEstimado)}.
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Navegação */}
      <div className="d-grid d-sm-flex justify-content-sm-between gap-2 mt-3">
        <button className="btn btn-outline-secondary" disabled={saving || etapa === 0}
          onClick={() => setEtapa((e) => e - 1)}>
          Voltar
        </button>
        {etapa < ETAPAS.length - 1 ? (
          <button className="btn btn-primary" disabled={!podeAvancar()} onClick={() => setEtapa((e) => e + 1)}>
            Avançar
          </button>
        ) : (
          <button className="btn btn-success" disabled={saving || configPendente || !pagamentoValido || !itensValidos || !cliente}
            onClick={salvar}>
            {saving ? "Salvando..." : "Salvar rascunho"}
          </button>
        )}
      </div>

      {modalCliente && (
        <ModalShell titulo="Novo cliente" onClose={() => setModalCliente(false)}>
          <ClienteForm
            onCancel={() => setModalCliente(false)}
            onSaved={(c) => {
              setModalCliente(false);
              toast.success(`Cliente ${c.nome} cadastrado.`);
              escolherCliente(c);
            }}
          />
        </ModalShell>
      )}
    </div>
  );
}
