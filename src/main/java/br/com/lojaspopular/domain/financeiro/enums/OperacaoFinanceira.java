package br.com.lojaspopular.domain.financeiro.enums;

/**
 * D12: operações financeiras que o proprietário pode (ou não) delegar ao gerente. Enquanto a decisão não existe,
 * somente o proprietário (ADMIN) opera: nada é liberado implicitamente ao gerente.
 *
 * <ul>
 *   <li>CONSULTAR: caixa, contas, cartão, comissões, metas, fechamento (prévia), relatórios e custos;
 *   <li>RECEBER: registrar recebimento, operar o caixa (abrir, suprimento, fechar), liquidar cartão, baixar conta a receber;
 *   <li>PAGAR: criar/alterar/cancelar contas, baixar conta a pagar, retirada de caixa;
 *   <li>ESTORNAR: estornar recebimento, liquidação de cartão e baixa de conta;
 *   <li>RESTITUIR: solicitar/autorizar/efetivar/cancelar restituição e cobrar diferença de troca.
 * </ul>
 *
 * Aprovar fechamento, pagar comissões, cadastrar custos e taxas de cartão e alterar decisões são sempre só do proprietário.
 */
public enum OperacaoFinanceira {
  CONSULTAR("consultar"),
  RECEBER("receber"),
  PAGAR("pagar"),
  ESTORNAR("estornar"),
  RESTITUIR("restituir");

  private final String rotulo;

  OperacaoFinanceira(String rotulo) {
    this.rotulo = rotulo;
  }

  public String rotulo() {
    return rotulo;
  }
}
