package br.com.lojaspopular.web.expedicao;

import java.util.regex.Pattern;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.lojaspopular.application.arquivo.ArquivoService;
import br.com.lojaspopular.application.auth.UsuarioAtual;
import br.com.lojaspopular.application.venda.VendaAcesso;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.exception.NotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * Entrega de comprovantes e evidências privados. Gerente e proprietário acessam todos; o vendedor só
 * os comprovantes de entrega e montagem das vendas em que é o responsável.
 */
@RestController
@RequestMapping("/api/v1/arquivos")
@PreAuthorize("hasAnyRole('ADMIN','GERENTE','VENDEDOR')")
@RequiredArgsConstructor
public class ArquivoController {

  private static final Pattern DE_VENDA = Pattern.compile("^privado/(entregas|montagens)/(\\d+)/[A-Za-z0-9._-]+$");

  private final ArquivoService arquivos;
  private final UsuarioAtual usuarioAtual;
  private final VendaAcesso acesso;

  @GetMapping
  public ResponseEntity<Resource> baixar(@RequestParam("caminho") String caminho) {
    autorizar(caminho);
    Resource recurso = arquivos.ler(caminho);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(arquivos.tipoDe(caminho)))
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
        .header("X-Content-Type-Options", "nosniff")
        .cacheControl(CacheControl.noStore())
        .body(recurso);
  }

  private void autorizar(String caminho) {
    User ator = usuarioAtual.get();
    if (UsuarioAtual.isGestor(ator)) {
      return;
    }
    var m = DE_VENDA.matcher(caminho == null ? "" : caminho);
    if (!m.matches()) {
      throw new NotFoundException("Arquivo não encontrado");
    }
    acesso.carregar(Long.parseLong(m.group(2)), ator); // 404 se a venda não for do vendedor
  }
}
