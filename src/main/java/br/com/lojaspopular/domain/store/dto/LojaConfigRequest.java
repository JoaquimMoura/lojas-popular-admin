package br.com.lojaspopular.domain.store.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LojaConfigRequest {
 private String nomeLoja;
 private String corPrimaria;
 private String corSecundaria;
 private String whatsapp;
 private String endereco;
 private String ctaTexto;
}

