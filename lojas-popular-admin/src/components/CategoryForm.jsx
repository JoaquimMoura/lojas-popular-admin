import { useEffect, useState } from "react";
import { MATERIALS } from "../constants/materials";

export default function CategoryForm({ initial, onSubmit, onCancel }) {
  const [form, setForm] = useState({
    nome: "",
    descricao: "",
    material: MATERIALS[0] // valor inicial padrão
  });

  useEffect(() => {
    if (initial) setForm({ ...initial });
  }, [initial]);

  function handleChange(e) {
    const { name, value } = e.target;
    setForm((f) => ({ ...f, [name]: value }));
  }

  function submit(e) {
    e.preventDefault();
    onSubmit(form);
  }

  return (
    <form onSubmit={submit}>
      <div className="row g-2">
        <div className="col-md-6">
          <label className="form-label">Nome</label>
          <input
            name="nome"
            className="form-control"
            value={form.nome}
            onChange={handleChange}
            required
          />
        </div>

        <div className="col-md-6">
          <label className="form-label">Material</label>
          <select
            name="material"
            className="form-control"
            value={form.material}
            onChange={handleChange}
            required
          >
            {MATERIALS.map((m) => (
              <option key={m} value={m}>
                {m}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="mt-3">
        <label className="form-label">Descrição</label>
        <textarea
          name="descricao"
          className="form-control"
          rows={2}
          value={form.descricao}
          onChange={handleChange}
        />
      </div>

      <div className="mt-3 d-flex gap-2">
        <button className="btn btn-primary" type="submit">
          Salvar
        </button>
        <button className="btn btn-secondary" type="button" onClick={onCancel}>
          Cancelar
        </button>
      </div>
    </form>
  );
}
