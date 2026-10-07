package br.com.lojaspopular.domain.expedicao.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import br.com.lojaspopular.domain.expedicao.enums.PeriodoAgenda;
import br.com.lojaspopular.domain.expedicao.enums.StatusEntregaRegistro;
import br.com.lojaspopular.domain.order.enums.TipoEntrega;
import br.com.lojaspopular.domain.order.model.Pedido;
import br.com.lojaspopular.domain.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entrega ou retirada de um pedido. Há uma única por pedido e ela cobre todos os itens:
 * não existe entrega parcial. O frete é sempre gratuito (não há valor de frete aqui).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "entregas")
public class Entrega {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version
  @Column(nullable = false)
  private Long version;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "pedido_id", nullable = false, unique = true)
  private Pedido pedido;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TipoEntrega tipo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private StatusEntregaRegistro status;

  @Column(nullable = false)
  private LocalDate dataPrevista;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PeriodoAgenda periodo;

  /** Equipe própria responsável (texto livre; não há cadastro de equipes). */
  @Column(length = 150)
  private String equipe;

  @Column(length = 300)
  private String observacao;

  @Column(nullable = false)
  @Builder.Default
  private Instant criadaEm = Instant.now();

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "criada_por")
  private User criadaPor;

  private Instant saidaEm;
  private Instant concluidaEm;

  @Column(length = 150)
  private String recebedorNome;

  /** Caminho privado do comprovante (foto/PDF), servido apenas a usuários autenticados. */
  @Column(length = 300)
  private String comprovanteArquivo;

  @Column(length = 300)
  private String observacaoConclusao;

  @OneToMany(mappedBy = "entrega", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("id ASC")
  @Builder.Default
  private List<EntregaEvento> eventos = new ArrayList<>();
}
