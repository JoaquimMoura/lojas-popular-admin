package br.com.lojaspopular.domain.encomenda.model;

import br.com.lojaspopular.domain.estoque.model.MovimentacaoEstoque;
import br.com.lojaspopular.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Cada recebimento (inclusive parcial) do fornecedor. Recebimento parcial do fornecedor NÃO é entrega parcial ao cliente. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "encomenda_recebimentos")
public class EncomendaRecebimento {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "encomenda_id", nullable = false)
  private Encomenda encomenda;

  @Column(nullable = false)
  private Integer quantidade;

  @Column(length = 300)
  private String observacao;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "movimentacao_id")
  private MovimentacaoEstoque movimentacao;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "usuario_id")
  private User usuario;

  @Column(nullable = false, length = 80)
  private String chave;

  @Column(nullable = false)
  @Builder.Default
  private Instant recebidoEm = Instant.now();
}
