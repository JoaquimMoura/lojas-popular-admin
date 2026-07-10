package br.com.lojaspopular.domain.store.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "loja_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LojaConfig {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 120)
	private String nome;

	// Branding
	@Column(length = 7)
	private String corPrimaria; // ex: #D11B1B (vermelho)
	@Column(length = 7)
	private String corSecundaria; // ex: #FFD200 (amarelo)

	// Imagens (URLs locais via /uploads/…)
	private String logoUrl; // ex: /uploads/branding/logo.png
	private String bannerUrl; // ex: /uploads/branding/banner.jpg

	// Contato
	@Column(length = 32)
	private String whatsapp; // ex: 1196111-8141

	// Endereço (linha única conforme informado)
	@Column(length = 255)
	private String endereco; // "Avenida Presidente médici 417"

	// CTA
	@Column(length = 80)
	private String ctaTexto; // ex: "Chamar no WhatsApp"

	// Extras
	private LocalDateTime atualizadoEm;

	@PrePersist
	@PreUpdate
	public void onSave() {
		this.atualizadoEm = LocalDateTime.now();
	}
}
