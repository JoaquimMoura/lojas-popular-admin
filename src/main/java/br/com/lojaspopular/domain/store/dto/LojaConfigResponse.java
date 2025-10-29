package br.com.lojaspopular.domain.store.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LojaConfigResponse {
	private Long id;
	private String nome;
	private String corPrimaria;
	private String corSecundaria;
	private String logoUrl;
	private String bannerUrl;
	private String whatsapp;
	private String endereco;
	private String ctaTexto;
}
