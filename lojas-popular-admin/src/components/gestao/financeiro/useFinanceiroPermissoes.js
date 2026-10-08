import { useEffect, useState } from "react";
import { api } from "../../../services/api";
import { useAuth } from "../../../context/AuthContext";

// Permissões financeiras (D12) do usuário atual: busca GET /financeiro/permissoes uma vez por usuário.
// Enquanto carrega ou em caso de erro, nada é liberado (o servidor sempre confere).
const NENHUMA = { CONSULTAR: false, RECEBER: false, PAGAR: false, ESTORNAR: false, RESTITUIR: false };
let cache = { chave: null, promessa: null };

export function invalidarPermissoesFinanceiras() {
  cache = { chave: null, promessa: null };
}

function buscar(chave) {
  if (cache.chave !== chave || !cache.promessa) {
    const promessa = api.get("/financeiro/permissoes").then((r) => ({ ...NENHUMA, ...r.data }));
    cache = { chave, promessa };
    promessa.catch(() => {
      if (cache.promessa === promessa) invalidarPermissoesFinanceiras();
    });
  }
  return cache.promessa;
}

export function useFinanceiroPermissoes() {
  const { user } = useAuth();
  const chave = user ? String(user.id ?? user.email ?? "u") : null;
  const [estado, setEstado] = useState({ chave: null, perm: NENHUMA, erro: null });

  useEffect(() => {
    if (!chave) return undefined;
    let ativo = true;
    buscar(chave)
      .then((perm) => ativo && setEstado({ chave, perm, erro: null }))
      .catch((erro) => ativo && setEstado({ chave, perm: NENHUMA, erro }));
    return () => {
      ativo = false;
    };
  }, [chave]);

  const pronto = estado.chave === chave && chave !== null;
  return { perm: pronto ? estado.perm : NENHUMA, loading: !pronto, erro: pronto ? estado.erro : null };
}
