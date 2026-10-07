// src/services/financeiroApi.js — Etapa 3 (financeiro e gestão)
import { api } from "./api";

const limpar = (p = {}) =>
  Object.fromEntries(Object.entries(p).filter(([, v]) => v !== "" && v !== null && v !== undefined));
const idem = (chave) => ({ headers: { "Idempotency-Key": chave } });
const dados = (r) => r.data;

export const financeiroApi = {
  // recebimentos (respostas = detalhe da venda atualizado)
  registrarRecebimento: (vendaId, body, chave) =>
    api.post(`/vendas/${vendaId}/recebimentos`, body, idem(chave)).then(dados),
  estornarRecebimento: (id, motivo, chave) =>
    api.post(`/recebimentos/${id}/estornar`, { motivo }, idem(chave)).then(dados),

  // caixa físico
  caixaAtual: () => api.get("/financeiro/caixa/atual").then(dados),
  abrirCaixa: (saldoInicial) => api.post("/financeiro/caixa/abrir", { saldoInicial }).then(dados),
  movimentarCaixa: (body, chave) => api.post("/financeiro/caixa/movimentos", body, idem(chave)).then(dados),
  fecharCaixa: (body) => api.post("/financeiro/caixa/fechar", body).then(dados),
  sessoesCaixa: (limite = 30) => api.get("/financeiro/caixa/sessoes", { params: { limite } }).then(dados),
  sessaoCaixa: (id) => api.get(`/financeiro/caixa/sessoes/${id}`).then(dados),
  lancamentos: (params) => api.get("/financeiro/lancamentos", { params: limpar(params) }).then(dados),

  // cartão
  taxas: () => api.get("/financeiro/taxas-cartao").then(dados),
  criarTaxa: (body) => api.post("/financeiro/taxas-cartao", body).then(dados),
  atualizarTaxa: (id, body) => api.put(`/financeiro/taxas-cartao/${id}`, body).then(dados),
  recebiveis: (params) => api.get("/financeiro/recebiveis", { params: limpar(params) }).then(dados),
  liquidar: (id, body, chave) => api.post(`/financeiro/recebiveis/${id}/liquidar`, body, idem(chave)).then(dados),
  estornarLiquidacao: (id, motivo, chave) =>
    api.post(`/financeiro/recebiveis/${id}/estornar-liquidacao`, { motivo }, idem(chave)).then(dados),

  // contas a pagar e a receber
  contas: (params) => api.get("/financeiro/contas", { params: limpar(params) }).then(dados),
  conta: (id) => api.get(`/financeiro/contas/${id}`).then(dados),
  criarConta: (body) => api.post("/financeiro/contas", body).then(dados),
  atualizarConta: (id, body) => api.put(`/financeiro/contas/${id}`, body).then(dados),
  baixarConta: (id, body, chave) => api.post(`/financeiro/contas/${id}/baixar`, body, idem(chave)).then(dados),
  estornarConta: (id, motivo, chave) =>
    api.post(`/financeiro/contas/${id}/estornar`, { motivo }, idem(chave)).then(dados),
  cancelarConta: (id, motivo) => api.post(`/financeiro/contas/${id}/cancelar`, { motivo }).then(dados),

  // restituições (devolução financeira)
  restituicoes: (status) => api.get("/financeiro/restituicoes", { params: limpar({ status }) }).then(dados),
  solicitarRestituicao: (ocorrenciaId, body) =>
    api.post(`/ocorrencias/${ocorrenciaId}/restituicoes`, body).then(dados),
  cobrarDiferenca: (ocorrenciaId, vencimento) =>
    api.post(`/ocorrencias/${ocorrenciaId}/cobrar-diferenca`, { vencimento }).then(dados),
  autorizarRestituicao: (id) => api.post(`/restituicoes/${id}/autorizar`).then(dados),
  efetivarRestituicao: (id, body, chave) =>
    api.post(`/restituicoes/${id}/efetivar`, body ?? {}, idem(chave)).then(dados),
  cancelarRestituicao: (id, motivo) => api.post(`/restituicoes/${id}/cancelar`, { motivo }).then(dados),

  // comissões e metas
  comissoes: (params) => api.get("/financeiro/comissoes", { params: limpar(params) }).then(dados),
  minhasComissoes: () => api.get("/financeiro/comissoes/minhas").then(dados),
  gerarPrevisoes: () => api.post("/financeiro/comissoes/gerar-previsoes").then(dados),
  gerarPagamentoComissao: (body) => api.post("/financeiro/comissoes/pagamento", body).then(dados),
  metas: (mes) => api.get("/financeiro/metas", { params: { mes } }).then(dados),
  minhaMeta: (mes) => api.get("/financeiro/metas/minha", { params: { mes } }).then(dados),
  definirMeta: (body) => api.put("/financeiro/metas", body).then(dados),

  // fechamento mensal
  previaFechamento: (mes) => api.get("/financeiro/fechamento/previa", { params: { mes } }).then(dados),
  aprovarFechamento: (mes) => api.post("/financeiro/fechamento/aprovar", { mes }).then(dados),
  reabrirFechamento: (mes, justificativa) =>
    api.post("/financeiro/fechamento/reabrir", { mes, justificativa }).then(dados),
};
