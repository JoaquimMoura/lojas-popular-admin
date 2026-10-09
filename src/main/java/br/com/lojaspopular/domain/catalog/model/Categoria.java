package br.com.lojaspopular.domain.catalog.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "categorias")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "O nome da categoria é obrigatório.")
    @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.")
    @Column(nullable = false, length = 100)
    private String nome;

    @Size(max = 255, message = "A descrição deve ter no máximo 255 caracteres.")
    private String descricao;

    /** Materiais disponíveis para os produtos desta categoria (cadastro compartilhado). Pode ser vazio. */
    @jakarta.persistence.ManyToMany
    @jakarta.persistence.JoinTable(name = "categoria_materiais", joinColumns = @jakarta.persistence.JoinColumn(name = "categoria_id"),
        inverseJoinColumns = @jakarta.persistence.JoinColumn(name = "material_id"))
    @Builder.Default
    private java.util.Set<Material> materiais = new java.util.LinkedHashSet<>();

    @Column(name = "imagem_url", length = 500)
    private String imagemUrl;
    
    @Builder.Default
    private boolean ativa = true;

    @Column(updatable = false)
    private LocalDateTime dataCriacao;

    private LocalDateTime dataAtualizacao;

    @PrePersist
    protected void onCreate() {
        dataCriacao = LocalDateTime.now();
        dataAtualizacao = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        dataAtualizacao = LocalDateTime.now();
    }
}
