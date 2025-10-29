package br.com.lojaspopular.domain.store.controller;

import java.io.IOException;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.com.lojaspopular.domain.store.dto.LojaConfigRequest;
import br.com.lojaspopular.domain.store.dto.LojaConfigResponse;
import br.com.lojaspopular.domain.store.model.LojaConfig;
import br.com.lojaspopular.domain.store.service.LojaConfigService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/config/loja")
@RequiredArgsConstructor
public class LojaConfigController {

 private final LojaConfigService service;

 @GetMapping
 public LojaConfigResponse get() {
     LojaConfig cfg = service.getOrCreateDefault();
     return toResp(cfg);
 }

 @PreAuthorize("hasRole('ADMIN')")
 @PutMapping
 public LojaConfigResponse update(@RequestBody LojaConfigRequest req) {
     LojaConfig in = LojaConfig.builder()
         .nome(req.getNomeLoja())
         .corPrimaria(req.getCorPrimaria())
         .corSecundaria(req.getCorSecundaria())
         .whatsapp(req.getWhatsapp())
         .endereco(req.getEndereco())
         .ctaTexto(req.getCtaTexto())
         .build();
     return toResp(service.atualizar(in));
 }

 @PreAuthorize("hasRole('ADMIN')")
 @PostMapping("/logo")
 public ResponseEntity<String> uploadLogo(@RequestParam("file") MultipartFile file) throws IOException {
     return ResponseEntity.ok(service.salvarLogo(file));
 }

 @PreAuthorize("hasRole('ADMIN')")
 @PostMapping("/banner")
 public ResponseEntity<String> uploadBanner(@RequestParam("file") MultipartFile file) throws IOException {
     return ResponseEntity.ok(service.salvarBanner(file));
 }

 private LojaConfigResponse toResp(LojaConfig c) {
     return LojaConfigResponse.builder()
         .id(c.getId())
         .nome(c.getNome())
         .corPrimaria(c.getCorPrimaria())
         .corSecundaria(c.getCorSecundaria())
         .logoUrl(c.getLogoUrl())
         .bannerUrl(c.getBannerUrl())
         .whatsapp(c.getWhatsapp())
         .endereco(c.getEndereco())
         .ctaTexto(c.getCtaTexto())
         .build();
 }
}

