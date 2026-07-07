package br.com.lojaspopular.web.order.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.lojaspopular.application.order.PedidoService;
import br.com.lojaspopular.domain.catalog.enums.PedidoStatus;
import br.com.lojaspopular.domain.catalog.repository.ProdutoRepository;
import br.com.lojaspopular.domain.order.model.ItemPedido;
import br.com.lojaspopular.domain.order.model.Pedido;
import br.com.lojaspopular.web.order.dto.ItemPedidoResponse;
import br.com.lojaspopular.web.order.dto.PedidoRequest;
import br.com.lojaspopular.web.order.dto.PedidoResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/pedidos")
@RequiredArgsConstructor
public class PedidoController {

  private final PedidoService service;
  private final ProdutoRepository produtoRepository;

  @PostMapping
  @PreAuthorize("hasAnyRole('CLIENTE','ADMIN')")
  public ResponseEntity<PedidoResponse> criar(@Valid @RequestBody PedidoRequest request) {
    log.info("Solicitacao para criar um pedido: {}", request);

    List<ItemPedido> itens = request.itens().stream().map(item -> {
      var produto = produtoRepository.findById(item.produtoId())
          .orElseThrow(() -> new RuntimeException("Produto nao encontrado"));
      return ItemPedido.builder()
          .produto(produto)
          .quantidade(item.quantidade())
          .precoUnitario(produto.getPreco())
          .build();
    }).toList();

    Pedido pedido = service.criarPedido(itens);
    return ResponseEntity.ok(toResponse(pedido));
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('CLIENTE','ADMIN','VENDEDOR')")
  public List<PedidoResponse> listar() {
    return service.listarPedidosDoUsuario().stream()
        .map(this::toResponse)
        .toList();
  }

  @GetMapping("/gerenciar")
  @PreAuthorize("hasAnyRole('ADMIN','VENDEDOR')")
  public List<PedidoResponse> listarTodos() {
    return service.listarTodos().stream()
        .map(this::toResponse)
        .toList();
  }

  @PutMapping("/{id}/status")
  @PreAuthorize("hasAnyRole('ADMIN','VENDEDOR')")
  public PedidoResponse alterarStatus(@PathVariable Long id, @RequestParam PedidoStatus status) {
    Pedido pedido = service.alterarStatus(id, status);
    return toResponse(pedido);
  }

  private PedidoResponse toResponse(Pedido pedido) {
    var itens = pedido.getItens().stream()
        .map(it -> new ItemPedidoResponse(
            it.getProduto().getNome(),
            it.getQuantidade(),
            it.getPrecoUnitario(),
            it.getTotal()))
        .toList();

    return new PedidoResponse(
        pedido.getId(),
        pedido.getStatus(),
        pedido.getSubtotal(),
        pedido.getFrete(),
        pedido.getTotal(),
        pedido.getCriadoEm(),
        itens);
  }
}
