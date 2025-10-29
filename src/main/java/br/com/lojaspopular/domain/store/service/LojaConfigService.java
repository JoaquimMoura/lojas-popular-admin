package br.com.lojaspopular.domain.store.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import br.com.lojaspopular.domain.store.model.LojaConfig;
import br.com.lojaspopular.domain.store.repository.LojaConfigRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LojaConfigService {

 private final LojaConfigRepository repository;

 public LojaConfig getOrCreateDefault() {
     return repository.findAll().stream().findFirst().orElseGet(() -> {
         LojaConfig cfg = LojaConfig.builder()
             .nome("Popular movies")
             .corPrimaria("#D11B1B") // vermelho
             .corSecundaria("#FFD200") // amarelo
             .whatsapp("98678-9299")
             .endereco("Avenida Presidente médici 417")
             .ctaTexto("Chamar no WhatsApp")
             .logoUrl(null)
             .bannerUrl(null)
             .build();
         return repository.save(cfg);
     });
 }

 public LojaConfig atualizar(LojaConfig in) {
     LojaConfig current = getOrCreateDefault();
     current.setNome(Objects.requireNonNullElse(in.getNome(), current.getNome()));
     current.setCorPrimaria(Objects.requireNonNullElse(in.getCorPrimaria(), current.getCorPrimaria()));
     current.setCorSecundaria(Objects.requireNonNullElse(in.getCorSecundaria(), current.getCorSecundaria()));
     current.setWhatsapp(Objects.requireNonNullElse(in.getWhatsapp(), current.getWhatsapp()));
     current.setEndereco(Objects.requireNonNullElse(in.getEndereco(), current.getEndereco()));
     current.setCtaTexto(Objects.requireNonNullElse(in.getCtaTexto(), current.getCtaTexto()));
     return repository.save(current);
 }

 public String salvarLogo(MultipartFile file) throws IOException {
     if (file == null || file.isEmpty()) throw new IllegalArgumentException("Arquivo inválido");
     LojaConfig cfg = getOrCreateDefault();
     String folder = "uploads/branding/";
     Files.createDirectories(Path.of(folder));
     String filename = "logo_" + System.currentTimeMillis() + "_" + file.getOriginalFilename();
     Path dest = Path.of(folder, filename);
     Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
     String url = "/uploads/branding/" + filename;
     cfg.setLogoUrl(url);
     repository.save(cfg);
     return url;
 }

 public String salvarBanner(MultipartFile file) throws IOException {
     if (file == null || file.isEmpty()) throw new IllegalArgumentException("Arquivo inválido");
     LojaConfig cfg = getOrCreateDefault();
     String folder = "uploads/branding/";
     Files.createDirectories(Path.of(folder));
     String filename = "banner_" + System.currentTimeMillis() + "_" + file.getOriginalFilename();
     Path dest = Path.of(folder, filename);
     Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
     String url = "/uploads/branding/" + filename;
     cfg.setBannerUrl(url);
     repository.save(cfg);
     return url;
 }
}

