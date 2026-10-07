import { useState } from "react";
import { toast } from "react-toastify";
import { arquivosApi } from "../../services/arquivosApi";

/** Abre um arquivo protegido (comprovante/evidência) em nova aba, usando o token do usuário. */
export default function ArquivoLink({ caminho, children = "Abrir arquivo" }) {
  const [busy, setBusy] = useState(false);
  if (!caminho) return null;

  async function abrir() {
    if (busy) return;
    setBusy(true);
    try {
      await arquivosApi.abrir(caminho);
    } catch (err) {
      toast.error(err?.response?.status === 404 ? "Arquivo não encontrado." : "Não foi possível abrir o arquivo.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <button type="button" className="btn btn-link p-0 align-baseline" onClick={abrir} disabled={busy}>
      {busy ? "Abrindo..." : children}
    </button>
  );
}
