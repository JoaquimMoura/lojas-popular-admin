package br.com.lojaspopular.domain.cliente.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Comprador pessoa física. Não precisa ter login no sistema. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "clientes")
public class Cliente {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version
  @Column(nullable = false)
  private Long version;

  @Column(nullable = false, length = 150)
  private String nome;

  /** Apenas dígitos; único quando informado. */
  @Column(length = 11)
  private String cpf;

  /** Apenas dígitos (com DDI opcional). */
  @Column(length = 20)
  private String telefone;

  @Column(length = 150)
  private String email;

  @Column(length = 500)
  private String observacoes;

  @Column(nullable = false)
  @Builder.Default
  private boolean ativo = true;

  @Column(nullable = false, updatable = false)
  @Builder.Default
  private Instant criadoEm = Instant.now();

  private Instant atualizadoEm;

  @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("principal DESC, id ASC")
  @Builder.Default
  private List<EnderecoCliente> enderecos = new ArrayList<>();

  @PreUpdate
  void preUpdate() {
    atualizadoEm = Instant.now();
  }
}
