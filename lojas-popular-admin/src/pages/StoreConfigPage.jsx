// src/pages/StoreConfigPage.jsx
import { useEffect, useState } from "react";
import { storeConfigApi } from "../services/storeConfigApi";

const resolveUrl = (u) => (!u ? null : (u.startsWith("http") ? u : `http://localhost:8080${u}`));

export default function StoreConfigPage() {
  const [cfg, setCfg] = useState(null);
  const [saving, setSaving] = useState(false);

  async function load() {
    const data = await storeConfigApi.get();
    setCfg(data);
  }

  useEffect(() => { load(); }, []);

  function handleChange(e) {
    const { name, value } = e.target;
    setCfg((c) => ({ ...c, [name]: value }));
  }

  async function save(e) {
    e.preventDefault();
    setSaving(true);
    await storeConfigApi.update({
      nomeLoja: cfg.nomeLoja,
      corPrimaria: cfg.corPrimaria,
      corSecundaria: cfg.corSecundaria,
      whatsapp: cfg.whatsapp,
      endereco: cfg.endereco,
      ctaTexto: cfg.ctaTexto,
    });
    setSaving(false);
    await load();
  }

  async function onUploadLogo(e) {
    const f = e.target.files?.[0];
    if (!f) return;
    await storeConfigApi.uploadLogo(f);
    await load();
  }

  async function onUploadBanner(e) {
    const f = e.target.files?.[0];
    if (!f) return;
    await storeConfigApi.uploadBanner(f);
    await load();
  }

  if (!cfg) return <div>Carregando...</div>;

  return (
    <div className="card">
      <div className="card-body">
        <h3>Configurações da Loja</h3>
        <form onSubmit={save} className="mt-3">
          <div className="row g-3">
            <div className="col-md-6">
              <label className="form-label">Nome da Loja</label>
              <input name="nomeLoja" className="form-control" value={cfg.nomeLoja || ""} onChange={handleChange} required />
            </div>
            <div className="col-md-3">
              <label className="form-label">Cor Primária</label>
              <input name="corPrimaria" className="form-control" value={cfg.corPrimaria || ""} onChange={handleChange} placeholder="#D11B1B" />
            </div>
            <div className="col-md-3">
              <label className="form-label">Cor Secundária</label>
              <input name="corSecundaria" className="form-control" value={cfg.corSecundaria || ""} onChange={handleChange} placeholder="#FFD200" />
            </div>
            <div className="col-md-4">
              <label className="form-label">WhatsApp</label>
              <input name="whatsapp" className="form-control" value={cfg.whatsapp || ""} onChange={handleChange} placeholder="98678-9299" />
            </div>
            <div className="col-md-8">
              <label className="form-label">Endereço</label>
              <input name="endereco" className="form-control" value={cfg.endereco || ""} onChange={handleChange} placeholder="Avenida Presidente médici 417" />
            </div>
            <div className="col-md-12">
              <label className="form-label">Texto do Botão (CTA)</label>
              <input name="ctaTexto" className="form-control" value={cfg.ctaTexto || ""} onChange={handleChange} placeholder="Chamar no WhatsApp" />
            </div>

            <div className="col-md-6">
              <label className="form-label">Logo (upload)</label>
              <input type="file" className="form-control" onChange={onUploadLogo} accept="image/*" />
              {cfg.logoUrl && <img src={resolveUrl(cfg.logoUrl)} alt="logo" className="mt-2" height={60} />}
            </div>
            <div className="col-md-6">
              <label className="form-label">Banner (upload)</label>
              <input type="file" className="form-control" onChange={onUploadBanner} accept="image/*" />
              {cfg.bannerUrl && <img src={resolveUrl(cfg.bannerUrl)} alt="banner" className="mt-2" height={80} />}
            </div>
          </div>

          <div className="mt-3 d-flex gap-2">
            <button className="btn btn-primary" type="submit" disabled={saving}>
              {saving ? "Salvando..." : "Salvar"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
