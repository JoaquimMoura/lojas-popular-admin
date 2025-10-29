package br.com.lojaspopular.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  // Caminho ABSOLUTO até a pasta uploads (ajuste se necessário)
  private static final Path UPLOAD_DIR = Paths.get("C:/Users/jmoura/developer/tools/projects/lojas-popular-backend/uploads");

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    String location = UPLOAD_DIR.toUri().toString(); // => file:///C:/Users/.../uploads/
    registry.addResourceHandler("/uploads/**")
            .addResourceLocations(location)          // precisa terminar com /
            .resourceChain(true);
  }
}
