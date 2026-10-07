import { useEffect, useState } from "react";
import { clientesApi } from "../../services/clientesApi";
import ErroAlert from "./ErroAlert";

const ENDERECO_VAZIO = {
  apelido: "", cep: "", logradouro: "", numero: "", complemento: "", bairro: "", cidade: "", uf: "", principal: false,
};

function Duplicados({ lista, titulo, variant = "warning" }) {
  if (!lista || lista.length === 0) return null;
  return (
    <div className={`alert alert-${variant}`} role="alert">
      <div className="fw-semibold mb-1">{titulo}</div>
      <ul className="mb-0 ps-3">
        {lista.map((c) => (
          <li key={c.id}>
            {c.nome}
            {c.telefone ? ` · ${c.telefone}` : ""}
            {c.cpf ? ` · CPF ${c.cpf}` : ""}
          </li>
        ))}
      </ul>
    </div>
  );
}

/**
 * Formulário de cliente (criar/editar). Reutilizado em ClientesPage e no modal da Nova venda.
 * onSaved(cliente) é chamado após salvar com sucesso.
 */
export default function ClienteForm({ initial, onSaved, onCancel }) {
  const [form, setForm] = useState({
    nome: initial?.nome ?? "",
    cpf: initial?.cpf ?? "",
    telefone: initial?.telefone ?? "",
    email: initial?.email ?? "",
    observacoes: initial?.observacoes ?? "",
  });
  const [enderecos, setEnderecos] = useState(
    (initial?.enderecos ?? []).map((e) => ({
      apelido: e.apelido ?? "", cep: e.cep ?? "", logradouro: e.logradouro ?? "", numero: e.numero ?? "",
      complemento: e.complemento ?? "", bairro: e.bairro ?? "", cidade: e.cidade ?? "", uf: e.uf ?? "",
      principal: !!e.principal,
    })),
  );
  const [duplicados, setDuplicados] = useState([]);
  const [saving, setSaving] = useState(false);
  const [erro, setErro] = useState(null);
  const [conflito, setConflito] = useState(null); // { code, message, duplicados }

  // Alerta de possíveis duplicados enquanto digita
  useEffect(() => {
    const cpf = form.cpf.trim();
    const telefone = form.telefone.trim();
    const nome = form.nome.trim();
    if (!cpf && !telefone && nome.length < 3) return undefined;
    let ativo = true;
    const t = setTimeout(() => {
      clientesApi
        .duplicidades({
          cpf: cpf || undefined,
          telefone: telefone || undefined,
          nome: nome.length >= 3 ? nome : undefined,
          ignorarId: initial?.id,
        })
        .then((lista) => {
          if (ativo) setDuplicados(Array.isArray(lista) ? lista : []);
        })
        .catch(() => {
          if (ativo) setDuplicados([]);
        });
    }, 500);
    return () => {
      ativo = false;
      clearTimeout(t);
    };
  }, [form.cpf, form.telefone, form.nome, initial?.id]);

  function change(e) {
    const { name, value } = e.target;
    setForm((f) => ({ ...f, [name]: value }));
    setConflito(null);
  }

  function addEndereco() {
    setEnderecos((l) => [...l, { ...ENDERECO_VAZIO, principal: l.length === 0 }]);
  }
  function changeEndereco(i, campo, valor) {
    setEnderecos((l) => l.map((e, idx) => (idx === i ? { ...e, [campo]: valor } : e)));
  }
  function setPrincipal(i) {
    setEnderecos((l) => l.map((e, idx) => ({ ...e, principal: idx === i })));
  }
  function removeEndereco(i) {
    setEnderecos((l) => {
      const novo = l.filter((_, idx) => idx !== i);
      if (novo.length > 0 && !novo.some((e) => e.principal)) novo[0] = { ...novo[0], principal: true };
      return novo;
    });
  }

  async function salvar(confirmarDuplicidade) {
    if (saving) return;
    setSaving(true);
    setErro(null);
    const payload = {
      nome: form.nome.trim(),
      cpf: form.cpf.trim() || null,
      telefone: form.telefone.trim() || null,
      email: form.email.trim() || null,
      observacoes: form.observacoes.trim() || null,
      enderecos: enderecos.map((e) => ({ ...e, uf: e.uf.trim().toUpperCase() })),
      confirmarDuplicidade,
    };
    try {
      const salvo = initial?.id
        ? await clientesApi.atualizar(initial.id, payload)
        : await clientesApi.criar(payload);
      setConflito(null);
      onSaved?.(salvo);
    } catch (err) {
      const data = err?.response?.data;
      if (err?.response?.status === 409 && (data?.code === "CLIENTE_DUPLICADO" || data?.code === "CLIENTE_POSSIVEL_DUPLICADO")) {
        setConflito({ code: data.code, message: data.message, duplicados: data.duplicados ?? [] });
      } else {
        setErro(err);
      }
    } finally {
      setSaving(false);
    }
  }

  function submit(e) {
    e.preventDefault();
    salvar(false);
  }

  return (
    <form onSubmit={submit}>
      {!conflito && (
        <Duplicados
          lista={duplicados}
          titulo="Atenção: já existem clientes parecidos (mesmo CPF, telefone ou nome)"
        />
      )}

      {conflito?.code === "CLIENTE_DUPLICADO" && (
        <Duplicados
          variant="danger"
          lista={conflito.duplicados}
          titulo={`${conflito.message} Cadastro bloqueado: use o cliente existente.`}
        />
      )}
      {conflito?.code === "CLIENTE_POSSIVEL_DUPLICADO" && (
        <div>
          <Duplicados lista={conflito.duplicados} titulo={conflito.message} />
          <button
            type="button"
            className="btn btn-warning w-100 mb-3"
            disabled={saving}
            onClick={() => salvar(true)}
          >
            {saving ? "Salvando..." : "Cadastrar mesmo assim"}
          </button>
        </div>
      )}

      <ErroAlert erro={erro} onClose={() => setErro(null)} />

      <div className="row g-2">
        <div className="col-12">
          <label className="form-label">Nome <span className="text-danger">*</span></label>
          <input name="nome" className="form-control" value={form.nome} onChange={change} required maxLength={150} />
        </div>
        <div className="col-12 col-md-4">
          <label className="form-label">CPF</label>
          <input name="cpf" className="form-control" inputMode="numeric" value={form.cpf} onChange={change} />
        </div>
        <div className="col-12 col-md-4">
          <label className="form-label">Telefone</label>
          <input name="telefone" className="form-control" inputMode="tel" value={form.telefone} onChange={change} />
        </div>
        <div className="col-12 col-md-4">
          <label className="form-label">E-mail</label>
          <input name="email" type="email" className="form-control" value={form.email} onChange={change} maxLength={150} />
        </div>
        <div className="col-12">
          <label className="form-label">Observações</label>
          <textarea name="observacoes" className="form-control" rows={2} value={form.observacoes} onChange={change} maxLength={500} />
        </div>
      </div>

      <div className="d-flex justify-content-between align-items-center mt-4 mb-2">
        <h6 className="mb-0">Endereços</h6>
        <button type="button" className="btn btn-outline-primary btn-sm" onClick={addEndereco}>
          + Endereço
        </button>
      </div>
      {enderecos.length === 0 && <div className="text-muted small mb-2">Nenhum endereço cadastrado.</div>}
      {enderecos.map((e, i) => (
        <div key={i} className="border rounded p-2 mb-2">
          <div className="row g-2">
            <div className="col-12 col-md-4">
              <label className="form-label small">Apelido</label>
              <input className="form-control" placeholder="Casa, trabalho..." value={e.apelido}
                onChange={(ev) => changeEndereco(i, "apelido", ev.target.value)} />
            </div>
            <div className="col-6 col-md-3">
              <label className="form-label small">CEP</label>
              <input className="form-control" inputMode="numeric" value={e.cep}
                onChange={(ev) => changeEndereco(i, "cep", ev.target.value)} maxLength={9} />
            </div>
            <div className="col-12 col-md-5">
              <label className="form-label small">Logradouro *</label>
              <input className="form-control" value={e.logradouro} required
                onChange={(ev) => changeEndereco(i, "logradouro", ev.target.value)} />
            </div>
            <div className="col-4 col-md-2">
              <label className="form-label small">Número</label>
              <input className="form-control" value={e.numero}
                onChange={(ev) => changeEndereco(i, "numero", ev.target.value)} />
            </div>
            <div className="col-8 col-md-4">
              <label className="form-label small">Complemento</label>
              <input className="form-control" value={e.complemento}
                onChange={(ev) => changeEndereco(i, "complemento", ev.target.value)} />
            </div>
            <div className="col-12 col-md-6">
              <label className="form-label small">Bairro</label>
              <input className="form-control" value={e.bairro}
                onChange={(ev) => changeEndereco(i, "bairro", ev.target.value)} />
            </div>
            <div className="col-8 col-md-8">
              <label className="form-label small">Cidade *</label>
              <input className="form-control" value={e.cidade} required
                onChange={(ev) => changeEndereco(i, "cidade", ev.target.value)} />
            </div>
            <div className="col-4 col-md-4">
              <label className="form-label small">UF *</label>
              <input className="form-control text-uppercase" value={e.uf} required minLength={2} maxLength={2}
                onChange={(ev) => changeEndereco(i, "uf", ev.target.value)} />
            </div>
          </div>
          <div className="d-flex justify-content-between align-items-center mt-2">
            <div className="form-check">
              <input className="form-check-input" type="radio" name="endereco-principal" id={`principal-${i}`}
                checked={e.principal} onChange={() => setPrincipal(i)} />
              <label className="form-check-label" htmlFor={`principal-${i}`}>Principal</label>
            </div>
            <button type="button" className="btn btn-outline-danger btn-sm" onClick={() => removeEndereco(i)}>
              Remover
            </button>
          </div>
        </div>
      ))}

      <div className="d-grid d-sm-flex justify-content-sm-end gap-2 mt-3">
        {onCancel && (
          <button type="button" className="btn btn-outline-secondary" onClick={onCancel} disabled={saving}>
            Cancelar
          </button>
        )}
        <button type="submit" className="btn btn-success" disabled={saving}>
          {saving ? "Salvando..." : initial?.id ? "Salvar alterações" : "Cadastrar cliente"}
        </button>
      </div>
    </form>
  );
}
