import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { materiaisApi, normalizarTexto } from "../services/materiaisApi";

function mensagemErro(err) {
  return err?.response?.data?.message || err?.message || "Não foi possível cadastrar o material. Tente novamente.";
}

/** Janela simples para cadastrar um material sem sair do formulário (sem <form>, para não enviar o formulário de trás). */
function NovoMaterialModal({ nomeInicial, onClose, onCriado }) {
  const [nome, setNome] = useState(nomeInicial ?? "");
  const [busy, setBusy] = useState(false);
  const [erro, setErro] = useState("");

  async function salvar() {
    if (busy) return;
    if (!nome.trim()) {
      setErro("Informe o nome do material.");
      return;
    }
    setBusy(true);
    setErro("");
    try {
      const criado = await materiaisApi.criar(nome.trim());
      onCriado(criado);
    } catch (e) {
      setErro(mensagemErro(e));
      setBusy(false);
    }
  }

  return createPortal(
    <>
      <div className="modal d-block" tabIndex={-1} role="dialog" aria-modal="true">
        <div className="modal-dialog modal-dialog-centered">
          <div className="modal-content">
            <div className="modal-header">
              <h5 className="modal-title">Cadastrar material</h5>
              <button type="button" className="btn-close" aria-label="Fechar" onClick={onClose} disabled={busy} />
            </div>
            <div className="modal-body">
              {erro && (
                <div className="alert alert-danger py-2" role="alert">
                  {erro}
                </div>
              )}
              <label className="form-label" htmlFor="novo-material-nome">
                Nome do material
              </label>
              <input
                id="novo-material-nome"
                className="form-control"
                autoFocus
                maxLength={100}
                value={nome}
                placeholder="Ex.: Madeira maciça"
                onChange={(e) => setNome(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    e.preventDefault();
                    e.stopPropagation();
                    salvar();
                  }
                }}
              />
            </div>
            <div className="modal-footer flex-column flex-sm-row">
              <button type="button" className="btn btn-outline-secondary w-100 w-sm-auto" onClick={onClose} disabled={busy}>
                Voltar
              </button>
              <button type="button" className="btn btn-primary w-100 w-sm-auto" onClick={salvar} disabled={busy}>
                {busy ? "Aguarde..." : "Cadastrar e selecionar"}
              </button>
            </div>
          </div>
        </div>
      </div>
      <div className="modal-backdrop show" />
    </>,
    document.body,
  );
}

/**
 * Seleção de materiais com busca (sem acento/maiúscula), chips e cadastro rápido.
 * Props:
 *  - value: ids selecionados (array)
 *  - onChange(ids)
 *  - materiaisDisponiveis?: [{id,nome,ativo}] restringe as opções (ex.: materiais da categoria); sem isso busca no cadastro
 *  - podeCadastrar: mostra "+ Cadastrar material"
 *  - single: escolha única
 *  - onCriado?(material): avisa o formulário quando um material novo é cadastrado aqui
 */
export default function MaterialSelect({
  value = [],
  onChange,
  materiaisDisponiveis,
  podeCadastrar = false,
  single = false,
  placeholder,
  disabled = false,
  onCriado,
}) {
  const [carregados, setCarregados] = useState([]);
  const [extras, setExtras] = useState([]); // criados agora
  const [busca, setBusca] = useState("");
  const [aberto, setAberto] = useState(false);
  const [modal, setModal] = useState(false);
  const [erroCarga, setErroCarga] = useState(false);
  const raiz = useRef(null);
  const restrito = Array.isArray(materiaisDisponiveis);

  useEffect(() => {
    if (restrito) return;
    let vivo = true;
    materiaisApi
      .listar({ incluirInativos: true })
      .then((r) => vivo && setCarregados(Array.isArray(r) ? r : []))
      .catch(() => vivo && setErroCarga(true));
    return () => {
      vivo = false;
    };
  }, [restrito]);

  useEffect(() => {
    if (!aberto) return;
    const fora = (e) => {
      if (raiz.current && !raiz.current.contains(e.target)) setAberto(false);
    };
    document.addEventListener("mousedown", fora);
    document.addEventListener("touchstart", fora);
    return () => {
      document.removeEventListener("mousedown", fora);
      document.removeEventListener("touchstart", fora);
    };
  }, [aberto]);

  const todos = useMemo(() => {
    const base = restrito ? materiaisDisponiveis : carregados;
    const mapa = new Map();
    [...base, ...extras].forEach((m) => mapa.set(m.id, { id: m.id, nome: m.nome, ativo: m.ativo !== false }));
    return [...mapa.values()].sort((a, b) => a.nome.localeCompare(b.nome, "pt-BR"));
  }, [restrito, materiaisDisponiveis, carregados, extras]);

  const selecionados = useMemo(
    () => value.map((id) => todos.find((m) => m.id === id) ?? { id, nome: `Material ${id}`, ativo: true }),
    [value, todos],
  );

  const termo = normalizarTexto(busca);
  const sugestoes = todos.filter((m) => !value.includes(m.id) && (!termo || normalizarTexto(m.nome).includes(termo)));
  const jaExiste = termo && todos.some((m) => normalizarTexto(m.nome) === termo);
  const mostrarCadastrar = podeCadastrar && busca.trim().length > 0 && !jaExiste;

  const escolher = useCallback(
    (m) => {
      if (!m.ativo) return;
      onChange(single ? [m.id] : [...value, m.id]);
      setBusca("");
      if (single) setAberto(false);
    },
    [onChange, single, value],
  );

  function remover(id) {
    onChange(value.filter((v) => v !== id));
  }

  function criado(m) {
    setExtras((e) => [...e, m]);
    onCriado?.(m);
    onChange(single ? [m.id] : [...value, m.id]);
    setBusca("");
    setModal(false);
    setAberto(false);
  }

  function teclas(e) {
    if (e.key === "Enter") {
      e.preventDefault(); // não envia o formulário
      const primeiro = sugestoes.find((m) => m.ativo);
      if (primeiro && termo) escolher(primeiro);
      else if (mostrarCadastrar) setModal(true);
    } else if (e.key === "Escape") {
      setAberto(false);
    } else if (e.key === "Backspace" && !busca && value.length && !single) {
      remover(value[value.length - 1]);
    }
  }

  return (
    <div ref={raiz} className="material-select">
      {selecionados.length > 0 && (
        <div className="d-flex flex-wrap gap-2 mb-2">
          {selecionados.map((m) => (
            <span
              key={m.id}
              className="badge rounded-pill d-inline-flex align-items-center gap-2 py-2 px-3"
              style={{ background: "var(--color-brand-yellow-light)", color: "var(--color-ink)", fontSize: ".9rem" }}
            >
              {m.nome}
              {!m.ativo && <span className="fw-normal fst-italic">(inativo)</span>}
              {!disabled && (
                <button
                  type="button"
                  className="btn-close"
                  style={{ fontSize: ".6rem" }}
                  aria-label={`Tirar ${m.nome}`}
                  onClick={() => remover(m.id)}
                />
              )}
            </span>
          ))}
        </div>
      )}

      <input
        type="text"
        className="form-control"
        value={busca}
        disabled={disabled}
        placeholder={placeholder ?? (single ? "Buscar material..." : "Buscar ou adicionar material...")}
        onChange={(e) => {
          setBusca(e.target.value);
          setAberto(true);
        }}
        onFocus={() => setAberto(true)}
        onKeyDown={teclas}
        autoComplete="off"
        aria-label="Buscar material"
      />

      {erroCarga && <div className="form-text text-danger">Não foi possível carregar os materiais.</div>}

      {aberto && !disabled && (
        <div className="list-group mt-1" style={{ maxHeight: 240, overflowY: "auto" }} role="listbox">
          {sugestoes.map((m) => (
            <button
              type="button"
              key={m.id}
              role="option"
              className="list-group-item list-group-item-action d-flex justify-content-between align-items-center"
              disabled={!m.ativo}
              onClick={() => escolher(m)}
            >
              <span>{m.nome}</span>
              {!m.ativo && <span className="badge text-bg-secondary">inativo</span>}
            </button>
          ))}
          {sugestoes.length === 0 && !mostrarCadastrar && (
            <div className="list-group-item text-muted small">
              {termo ? "Nenhum material encontrado." : "Nenhum outro material disponível."}
            </div>
          )}
          {mostrarCadastrar && (
            <button
              type="button"
              className="list-group-item list-group-item-action fw-semibold"
              style={{ color: "var(--color-primary)" }}
              onClick={() => setModal(true)}
            >
              + Cadastrar material "{busca.trim()}"
            </button>
          )}
        </div>
      )}

      {modal && <NovoMaterialModal nomeInicial={busca.trim()} onClose={() => setModal(false)} onCriado={criado} />}
    </div>
  );
}
