import { useEffect, useState } from "react";
import { fanpageApi } from "../services/fanpageApi";

const DEFAULT_FORM = {
  heroTitle: "",
  heroSubtitle: "",
  heroDescription: "",
  heroPrimaryLabel: "",
  heroPrimaryMessage: "",
  heroSecondaryLabel: "",
  heroSecondaryUrl: "/loja",
  heroBannerUrl: "",
  benefitsText: "",
  collectionsText: "",
  offersTitle: "",
  offersDescription: "",
  combosTitle: "",
  combosDescription: "",
  ctaTitle: "",
  ctaDescription: "",
  ctaHighlightsText: "",
};

export default function FanpageConfigPage() {
  const [form, setForm] = useState(DEFAULT_FORM);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [feedback, setFeedback] = useState(null);

  useEffect(() => {
    async function load() {
      setLoading(true);
      try {
        const data = await fanpageApi.get();
        if (!data) return;
        setForm({
          heroTitle: data.heroTitle ?? "",
          heroSubtitle: data.heroSubtitle ?? "",
          heroDescription: data.heroDescription ?? "",
          heroPrimaryLabel: data.heroPrimaryLabel ?? "",
          heroPrimaryMessage: data.heroPrimaryMessage ?? "",
          heroSecondaryLabel: data.heroSecondaryLabel ?? "",
          heroSecondaryUrl: data.heroSecondaryUrl ?? "/loja",
          heroBannerUrl: data.heroBannerUrl ?? "",
          benefitsText: (data.benefits ?? [])
            .map((b) => `${b.title ?? ""}|${b.description ?? ""}`.trim())
            .join("\n"),
          collectionsText: (data.collections ?? [])
            .map((c) => `${c.name ?? ""}|${c.description ?? ""}|${c.imageUrl ?? ""}`.trim())
            .join("\n"),
          offersTitle: data.offersTitle ?? "",
          offersDescription: data.offersDescription ?? "",
          combosTitle: data.combosTitle ?? "",
          combosDescription: data.combosDescription ?? "",
          ctaTitle: data.ctaTitle ?? "",
          ctaDescription: data.ctaDescription ?? "",
          ctaHighlightsText: (data.ctaHighlights ?? []).join("\n"),
        });
      } catch (err) {
        console.error("Erro ao carregar configuracao da fanpage", err);
        setFeedback({ type: "danger", message: "Nao foi possivel carregar os dados atuais." });
      } finally {
        setLoading(false);
      }
    }

    load();
  }, []);

  function handleChange(event) {
    const { name, value } = event.target;
    setForm((prev) => ({ ...prev, [name]: value }));
    setFeedback(null);
  }

  function parseBenefits(text) {
    return text
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean)
      .map((line) => {
        const [title, description] = line.split("|");
        return {
          title: (title ?? "").trim(),
          description: (description ?? "").trim(),
        };
      })
      .filter((item) => item.title.length > 0);
  }

  function parseCollections(text) {
    return text
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean)
      .map((line) => {
        const [name, description, imageUrl] = line.split("|");
        return {
          name: (name ?? "").trim(),
          description: (description ?? "").trim(),
          imageUrl: (imageUrl ?? "").trim(),
        };
      })
      .filter((item) => item.name.length > 0);
  }

  function parseHighlights(text) {
    return text
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean);
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setSaving(true);
    setFeedback(null);
    try {
      const payload = {
        heroTitle: form.heroTitle.trim(),
        heroSubtitle: form.heroSubtitle.trim(),
        heroDescription: form.heroDescription.trim(),
        heroBannerUrl: form.heroBannerUrl.trim() || undefined,
        heroPrimaryLabel: form.heroPrimaryLabel.trim(),
        heroPrimaryMessage: form.heroPrimaryMessage.trim(),
        heroSecondaryLabel: form.heroSecondaryLabel.trim(),
        heroSecondaryUrl: form.heroSecondaryUrl.trim() || "/loja",
        benefits: parseBenefits(form.benefitsText),
        collections: parseCollections(form.collectionsText),
        offersTitle: form.offersTitle.trim(),
        offersDescription: form.offersDescription.trim(),
        combosTitle: form.combosTitle.trim(),
        combosDescription: form.combosDescription.trim(),
        ctaTitle: form.ctaTitle.trim(),
        ctaDescription: form.ctaDescription.trim(),
        ctaHighlights: parseHighlights(form.ctaHighlightsText),
      };

      const updated = await fanpageApi.update(payload);
      setFeedback({ type: "success", message: "Fanpage atualizada com sucesso." });

      setForm((prev) => ({
        ...prev,
        heroBannerUrl: updated.heroBannerUrl ?? prev.heroBannerUrl,
      }));
    } catch (err) {
      console.error("Erro ao salvar configuracao da fanpage", err);
      const status = err.response?.status;
      const serverMsg = err.response?.data?.message || err.response?.data?.error;
      let message;
      if (!err.response) {
        message = "Erro de conexao. Verifique se o servidor esta rodando.";
      } else if (status === 403) {
        message = "Sem permissao. Certifique-se de estar logado como ADMIN.";
      } else if (status === 400) {
        message = serverMsg ? `Campos invalidos: ${serverMsg}` : "Campos invalidos. Verifique os dados e tente novamente.";
      } else {
        message = serverMsg || `Erro ${status || ""}: Nao foi possivel salvar. Tente novamente.`;
      }
      setFeedback({ type: "danger", message });
    } finally {
      setSaving(false);
    }
  }

  return (
    <div>
      <h2 className="mb-4">Configuracao da Fanpage</h2>

      <p className="text-muted">
        Atualize os textos e destaques exibidos na pagina inicial/fanpage. Utilize os formatos
        indicados para listar beneficios, colecoes e destaques.
      </p>

      {feedback && (
        <div className={`alert alert-${feedback.type}`} role="alert">
          {feedback.message}
        </div>
      )}

      <form className="row g-3" onSubmit={handleSubmit}>
        <div className="col-md-6">
          <label className="form-label">Titulo do Hero*</label>
          <input
            className="form-control"
            name="heroTitle"
            value={form.heroTitle}
            onChange={handleChange}
            required
            disabled={loading || saving}
          />
        </div>

        <div className="col-md-6">
          <label className="form-label">Subtitulo do Hero</label>
          <input
            className="form-control"
            name="heroSubtitle"
            value={form.heroSubtitle}
            onChange={handleChange}
            disabled={loading || saving}
          />
        </div>

        <div className="col-12">
          <label className="form-label">Descricao do Hero</label>
          <textarea
            className="form-control"
            name="heroDescription"
            rows={3}
            value={form.heroDescription}
            onChange={handleChange}
            disabled={loading || saving}
          />
        </div>

        <div className="col-md-4">
          <label className="form-label">Rotulo do botao principal*</label>
          <input
            className="form-control"
            name="heroPrimaryLabel"
            value={form.heroPrimaryLabel}
            onChange={handleChange}
            required
            disabled={loading || saving}
          />
        </div>

        <div className="col-md-8">
          <label className="form-label">Mensagem enviada no WhatsApp*</label>
          <input
            className="form-control"
            name="heroPrimaryMessage"
            value={form.heroPrimaryMessage}
            onChange={handleChange}
            required
            disabled={loading || saving}
          />
        </div>

        <div className="col-md-4">
          <label className="form-label">Rotulo do botao secundario</label>
          <input
            className="form-control"
            name="heroSecondaryLabel"
            value={form.heroSecondaryLabel}
            onChange={handleChange}
            disabled={loading || saving}
          />
        </div>

        <div className="col-md-4">
          <label className="form-label">Link do botao secundario</label>
          <input
            className="form-control"
            name="heroSecondaryUrl"
            value={form.heroSecondaryUrl}
            onChange={handleChange}
            disabled={loading || saving}
          />
          <div className="form-text">Use rotas internas (ex: /loja) ou URLs completas.</div>
        </div>

        <div className="col-md-4">
          <label className="form-label">URL do banner</label>
          <input
            className="form-control"
            name="heroBannerUrl"
            value={form.heroBannerUrl}
            onChange={handleChange}
            disabled={loading || saving}
          />
          <div className="form-text">Opcional. Informe uma URL absoluta ou caminho relativo.</div>
        </div>

        <div className="col-12">
          <label className="form-label">Beneficios (um por linha)</label>
          <textarea
            className="form-control"
            name="benefitsText"
            rows={3}
            value={form.benefitsText}
            onChange={handleChange}
            disabled={loading || saving}
          />
          <div className="form-text">Formato: Titulo|Descricao</div>
        </div>

        <div className="col-12">
          <label className="form-label">Colecoes (uma por linha)</label>
          <textarea
            className="form-control"
            name="collectionsText"
            rows={3}
            value={form.collectionsText}
            onChange={handleChange}
            disabled={loading || saving}
          />
          <div className="form-text">Formato: Nome|Descricao|ImagemURL</div>
        </div>

        <div className="col-md-6">
          <label className="form-label">Titulo das ofertas</label>
          <input
            className="form-control"
            name="offersTitle"
            value={form.offersTitle}
            onChange={handleChange}
            disabled={loading || saving}
          />
        </div>

        <div className="col-md-6">
          <label className="form-label">Descricao das ofertas</label>
          <input
            className="form-control"
            name="offersDescription"
            value={form.offersDescription}
            onChange={handleChange}
            disabled={loading || saving}
          />
        </div>

        <div className="col-md-6">
          <label className="form-label">Titulo dos combos</label>
          <input
            className="form-control"
            name="combosTitle"
            value={form.combosTitle}
            onChange={handleChange}
            disabled={loading || saving}
          />
        </div>

        <div className="col-md-6">
          <label className="form-label">Descricao dos combos</label>
          <input
            className="form-control"
            name="combosDescription"
            value={form.combosDescription}
            onChange={handleChange}
            disabled={loading || saving}
          />
        </div>

        <div className="col-md-6">
          <label className="form-label">Titulo da chamada final</label>
          <input
            className="form-control"
            name="ctaTitle"
            value={form.ctaTitle}
            onChange={handleChange}
            disabled={loading || saving}
          />
        </div>

        <div className="col-md-6">
          <label className="form-label">Descricao da chamada final</label>
          <input
            className="form-control"
            name="ctaDescription"
            value={form.ctaDescription}
            onChange={handleChange}
            disabled={loading || saving}
          />
        </div>

        <div className="col-12">
          <label className="form-label">Destaques finais (um por linha)</label>
          <textarea
            className="form-control"
            name="ctaHighlightsText"
            rows={3}
            value={form.ctaHighlightsText}
            onChange={handleChange}
            disabled={loading || saving}
          />
        </div>

        <div className="col-12 d-flex justify-content-end">
          <button type="submit" className="btn btn-primary" disabled={saving || loading}>
            {saving ? "Salvando..." : "Salvar configuracao"}
          </button>
        </div>
      </form>
    </div>
  );
}
