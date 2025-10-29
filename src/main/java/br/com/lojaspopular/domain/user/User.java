package br.com.lojaspopular.domain.user;

import java.time.Instant;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(nullable = false)
  private String passwordHash;

  @ElementCollection(fetch = FetchType.EAGER)
  @Enumerated(EnumType.STRING)
  @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
  @Column(name = "role")
  private Set<Role> roles;

  /** Telefone para envio de notificações WhatsApp (com DDI opcional, ex: +55...) */
  @Column(length = 20)
  private String telefone;

  /** Ativo / inativo */
  private boolean enabled = true;

  /** Data de criação */
  private Instant createdAt = Instant.now();

  /**
   * Retorna telefone normalizado (apenas dígitos) para uso em provedores como Zenvia.
   */
  public String getTelefoneNormalizado() {
    if (telefone == null) return null;
    // remove espaços, parênteses e hifens
    return telefone.replaceAll("[^0-9+]", "");
  }
}
