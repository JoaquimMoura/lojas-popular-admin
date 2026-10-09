package br.com.lojaspopular.application.venda;

import br.com.lojaspopular.domain.catalog.enums.PedidoStatus;
import br.com.lojaspopular.domain.order.enums.StatusComercial;
import br.com.lojaspopular.domain.order.model.Pedido;

/**
 * Critério ÚNICO de "venda válida" (compra que entra em indicadores): o mesmo dos relatórios gerenciais, para que telas
 * diferentes não mostrem totais diferentes. Vendas da gestão: confirmadas e não canceladas. Vendas legadas (checkout online
 * anterior): pagas ou entregues. Valor comprado NÃO é dinheiro recebido.
 */
public final class CriteriosVenda {

  private CriteriosVenda() {
  }

  public static boolean valida(Pedido p) {
    if (p.getStatusComercial() == StatusComercial.CONFIRMADA) {
      return true;
    }
    return p.getStatusComercial() == StatusComercial.LEGADO
        && (p.getStatus() == PedidoStatus.PAGO || p.getStatus() == PedidoStatus.ENTREGUE);
  }
}
