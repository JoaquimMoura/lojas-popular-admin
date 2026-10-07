/** Exibe o erro de uma chamada da API; HTTP 422 (CONFIGURACAO_PENDENTE) lista as pendências. */
export default function ErroAlert({ erro, onClose }) {
  if (!erro) return null;
  const data = erro?.response?.data;
  const mensagem =
    data?.message || erro?.message || "Não foi possível concluir a operação. Tente novamente.";
  const pendente = erro?.response?.status === 422 && data?.code === "CONFIGURACAO_PENDENTE";
  const pendencias = Array.isArray(data?.pendencias) ? data.pendencias : [];
  return (
    <div className={`alert ${pendente ? "alert-warning" : "alert-danger"} alert-dismissible`} role="alert">
      <div>{mensagem}</div>
      {pendente && pendencias.length > 0 && (
        <ul className="mb-0 mt-2 ps-3">
          {pendencias.map((p, i) => (
            <li key={p.codigo ?? i}>
              {p.codigo ? <strong>{p.codigo} — </strong> : null}
              {p.descricao ?? String(p)}
            </li>
          ))}
        </ul>
      )}
      {onClose && <button type="button" className="btn-close" aria-label="Fechar" onClick={onClose} />}
    </div>
  );
}
