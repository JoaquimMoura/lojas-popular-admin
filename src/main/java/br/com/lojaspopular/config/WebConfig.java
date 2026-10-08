package br.com.lojaspopular.config;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  private final br.com.lojaspopular.web.financeiro.PermissaoFinanceiraInterceptor permissaoFinanceira;

  public WebConfig(br.com.lojaspopular.web.financeiro.PermissaoFinanceiraInterceptor permissaoFinanceira) {
    this.permissaoFinanceira = permissaoFinanceira;
  }

  @Override
  public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
    registry.addInterceptor(permissaoFinanceira).addPathPatterns("/api/v1/financeiro/**");
  }

  @Value("${app.upload-dir:uploads}")
  private String uploadDir;

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    String location = Path.of(uploadDir).toAbsolutePath().normalize().toUri().toString();
    registry.addResourceHandler("/uploads/**")
            .addResourceLocations(location)
            .resourceChain(true);
  }
}
