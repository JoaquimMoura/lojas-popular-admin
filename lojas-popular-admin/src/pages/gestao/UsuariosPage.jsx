import { useCallback, useEffect, useState } from "react";
import { toast } from "react-toastify";
import { usuariosApi } from "../../services/usuariosApi";
import ErroAlert from "../../components/gestao/ErroAlert";
import ModalShell from "../../components/gestao/ModalShell";
import { PERFIS, fmtDateTime } from "../../utils/format";

const PERFIS_USUARIO = ["ADMIN", "GERENTE", "VENDEDOR"];

function perfilDe(u) {
  return (u.perfis ?? []).find((p) => PERFIS_USUARIO.includes(p)) ?? "";
}

function UsuarioForm({ usuario, onSaved, onCancel }) {
  const editando = !!usuario;
  const [form, setForm] = useState({
    nome: usuario?.nome ?? "",
    email: usuario?.email ?? "",
    senha: "",
    perfil: usuario ? perfilDe(usuario) : "VENDEDOR",
    telefone: usuario?.telefone ?? "",
  });
  const [saving, setSaving] = useState(false);
  const [erro, setErro] = useState(null);

  function change(e) {
    setForm((f) => ({ ...f, [e.target.name]: e.target.value }));
  }

  async function submit(e) {
    e.preventDefault();
    if (saving) return;
    setSaving(true);
    setErro(null);
    try {
      if (editando) {
        await usuariosApi.atualizar(usuario.id, {
          nome: form.nome.trim(),
          perfil: form.perfil,
          telefone: form.telefone.trim() || null,
        });
      } else {
        await usuariosApi.criar({
          nome: form.nome.trim(),
          email: form.email.trim(),
          senha: form.senha,
          perfil: form.perfil,
          telefone: form.telefone.trim() || null,
        });
      }
      onSaved();
    } catch (err) {
      setErro(err);
    } finally {
      setSaving(false);
    }
  }

  return (
    <form onSubmit={submit}>
      <ErroAlert erro={erro} onClose={() => setErro(null)} />
      <div className="row g-2">
        <div className="col-12">
          <label className="form-label">Nome *</label>
          <input name="nome" className="form-control" required value={form.nome} onChange={change} />
        </div>
        <div className="col-12">
          <label className="form-label">E-mail *</label>
          <input name="email" type="email" className="form-control" required disabled={editando}
            value={form.email} onChange={change} />
        </div>
        {!editando && (
          <div className="col-12">
            <label className="form-label">Senha * (mínimo 8 caracteres)</label>
            <input name="senha" type="password" className="form-control" required minLength={8}
              autoComplete="new-password" value={form.senha} onChange={change} />
          </div>
        )}
        <div className="col-12 col-md-6">
          <label className="form-label">Perfil *</label>
          <select name="perfil" className="form-select" required value={form.perfil} onChange={change}>
            {PERFIS_USUARIO.map((p) => (
              <option key={p} value={p}>{PERFIS[p]}</option>
            ))}
          </select>
        </div>
        <div className="col-12 col-md-6">
          <label className="form-label">Telefone</label>
          <input name="telefone" className="form-control" inputMode="tel" value={form.telefone} onChange={change} />
        </div>
      </div>
      <div className="d-grid d-sm-flex justify-content-sm-end gap-2 mt-3">
        <button type="button" className="btn btn-outline-secondary" onClick={onCancel} disabled={saving}>Cancelar</button>
        <button type="submit" className="btn btn-success" disabled={saving}>
          {saving ? "Salvando..." : "Salvar"}
        </button>
      </div>
    </form>
  );
}

function SenhaForm({ usuario, onSaved, onCancel }) {
  const [senha, setSenha] = useState("");
  const [saving, setSaving] = useState(false);
  const [erro, setErro] = useState(null);

  async function submit(e) {
    e.preventDefault();
    if (saving) return;
    setSaving(true);
    setErro(null);
    try {
      await usuariosApi.redefinirSenha(usuario.id, senha);
      onSaved();
    } catch (err) {
      setErro(err);
    } finally {
      setSaving(false);
    }
  }

  return (
    <form onSubmit={submit}>
      <ErroAlert erro={erro} onClose={() => setErro(null)} />
      <label className="form-label">Nova senha (mínimo 8 caracteres)</label>
      <input type="password" className="form-control" required minLength={8} autoComplete="new-password"
        value={senha} onChange={(e) => setSenha(e.target.value)} />
      <div className="d-grid d-sm-flex justify-content-sm-end gap-2 mt-3">
        <button type="button" className="btn btn-outline-secondary" onClick={onCancel} disabled={saving}>Cancelar</button>
        <button type="submit" className="btn btn-primary" disabled={saving}>
          {saving ? "Salvando..." : "Redefinir senha"}
        </button>
      </div>
    </form>
  );
}

export default function UsuariosPage() {
  const [lista, setLista] = useState([]);
  const [loading, setLoading] = useState(true);
  const [erro, setErro] = useState(null);
  const [modal, setModal] = useState(null); // { tipo: 'form'|'senha', usuario? }
  const [busyId, setBusyId] = useState(null);

  const carregar = useCallback(async () => {
    setLoading(true);
    try {
      const d = await usuariosApi.listar();
      setLista(Array.isArray(d) ? d : []);
    } catch (err) {
      setErro(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    carregar();
  }, [carregar]);

  async function alternar(u) {
    if (busyId) return;
    setBusyId(u.id);
    setErro(null);
    try {
      if (u.ativo) await usuariosApi.desativar(u.id);
      else await usuariosApi.ativar(u.id);
      toast.success(u.ativo ? "Usuário desativado." : "Usuário ativado.");
      await carregar();
    } catch (err) {
      setErro(err);
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div>
      <div className="d-flex justify-content-between align-items-center mb-3 gap-2">
        <h3 className="mb-0">Usuários</h3>
        <button className="btn btn-success" onClick={() => setModal({ tipo: "form" })}>+ Novo usuário</button>
      </div>

      <ErroAlert erro={erro} onClose={() => setErro(null)} />

      {loading ? (
        <div className="text-center text-muted py-5">Carregando usuários...</div>
      ) : lista.length === 0 ? (
        <div className="text-center text-muted py-5">Nenhum usuário.</div>
      ) : (
        <div className="row g-2">
          {lista.map((u) => (
            <div key={u.id} className="col-12 col-lg-6">
              <div className="card h-100">
                <div className="card-body">
                  <div className="d-flex justify-content-between align-items-start gap-2">
                    <div>
                      <strong>{u.nome || u.email}</strong>
                      <div className="small text-muted">{u.email}</div>
                      {u.telefone && <div className="small text-muted">{u.telefone}</div>}
                    </div>
                    <div className="text-end">
                      <span className="badge text-bg-dark me-1">{PERFIS[perfilDe(u)] ?? (u.perfis ?? []).join(", ")}</span>
                      <span className={`badge text-bg-${u.ativo ? "success" : "secondary"}`}>
                        {u.ativo ? "Ativo" : "Inativo"}
                      </span>
                    </div>
                  </div>
                  <div className="small text-muted mt-1">Criado em {fmtDateTime(u.criadoEm)}</div>
                  <div className="d-grid d-sm-flex gap-2 mt-3">
                    <button className="btn btn-outline-primary btn-sm" onClick={() => setModal({ tipo: "form", usuario: u })}>
                      Editar
                    </button>
                    <button className="btn btn-outline-secondary btn-sm" onClick={() => setModal({ tipo: "senha", usuario: u })}>
                      Redefinir senha
                    </button>
                    <button
                      className={`btn btn-sm ${u.ativo ? "btn-outline-danger" : "btn-outline-success"}`}
                      disabled={busyId === u.id}
                      onClick={() => alternar(u)}
                    >
                      {u.ativo ? "Desativar" : "Ativar"}
                    </button>
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {modal?.tipo === "form" && (
        <ModalShell titulo={modal.usuario ? "Editar usuário" : "Novo usuário"} size="md" onClose={() => setModal(null)}>
          <UsuarioForm
            usuario={modal.usuario}
            onCancel={() => setModal(null)}
            onSaved={() => {
              setModal(null);
              toast.success("Usuário salvo.");
              carregar();
            }}
          />
        </ModalShell>
      )}
      {modal?.tipo === "senha" && (
        <ModalShell titulo={`Redefinir senha — ${modal.usuario.nome || modal.usuario.email}`} size="md" onClose={() => setModal(null)}>
          <SenhaForm
            usuario={modal.usuario}
            onCancel={() => setModal(null)}
            onSaved={() => {
              setModal(null);
              toast.success("Senha redefinida.");
            }}
          />
        </ModalShell>
      )}
    </div>
  );
}
