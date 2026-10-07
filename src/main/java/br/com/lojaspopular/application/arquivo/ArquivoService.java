package br.com.lojaspopular.application.arquivo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import br.com.lojaspopular.exception.NegocioException;
import br.com.lojaspopular.exception.NotFoundException;

/**
 * Armazena comprovantes e evidências (fotos/PDF) em área PRIVADA: ficam em {@code <upload-dir>/privado}
 * (o mesmo volume dos uploads) e só saem por endpoint autenticado; o caminho público /uploads/privado
 * é negado na configuração de segurança.
 */
@Service
public class ArquivoService {

  public static final long TAMANHO_MAXIMO = 5L * 1024 * 1024;
  private static final String RAIZ_PRIVADA = "privado";
  private static final Map<String, String> TIPOS = Map.of(
      "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "webp", "image/webp", "pdf", "application/pdf");

  @Value("${app.upload-dir:uploads}")
  private String baseUploadDir;

  /** Salva o arquivo em {@code privado/<pasta>/<uuid>.<ext>} e devolve o caminho relativo a gravar no banco. */
  public String salvar(MultipartFile arquivo, String pasta) {
    if (arquivo == null || arquivo.isEmpty()) {
      throw new NegocioException("Arquivo vazio.");
    }
    if (arquivo.getSize() > TAMANHO_MAXIMO) {
      throw new NegocioException("O arquivo excede o limite de 5 MB.");
    }
    String ext = extensao(arquivo.getOriginalFilename());
    if (!TIPOS.containsKey(ext)) {
      throw new NegocioException("Tipo de arquivo não aceito. Envie JPG, PNG, WEBP ou PDF.");
    }
    try (InputStream in = arquivo.getInputStream()) {
      byte[] cabecalho = in.readNBytes(12);
      if (!assinaturaConfere(ext, cabecalho)) {
        throw new NegocioException("O conteúdo do arquivo não corresponde ao tipo informado.");
      }
      String pastaSegura = pasta.replaceAll("[^a-zA-Z0-9/_-]", "").replaceAll("/+", "/").replaceAll("^/|/$", "");
      if (pastaSegura.isEmpty()) {
        pastaSegura = "geral";
      }
      Path dir = raizPrivada().resolve(pastaSegura).normalize();
      if (!dir.startsWith(raizPrivada())) {
        throw new NegocioException("Pasta inválida.");
      }
      Files.createDirectories(dir);
      String nome = UUID.randomUUID() + "." + ext;
      Path destino = dir.resolve(nome);
      try (InputStream completo = arquivo.getInputStream()) {
        Files.copy(completo, destino, StandardCopyOption.REPLACE_EXISTING);
      }
      return RAIZ_PRIVADA + "/" + pastaSegura + "/" + nome;
    } catch (IOException e) {
      throw new NegocioException("Não foi possível gravar o arquivo. Tente novamente.");
    }
  }

  /** Abre um arquivo privado; o caminho precisa estar dentro da área privada (sem path traversal). */
  public Resource ler(String caminho) {
    Path arquivo = resolver(caminho);
    if (!Files.isRegularFile(arquivo)) {
      throw new NotFoundException("Arquivo não encontrado");
    }
    return new PathResource(arquivo);
  }

  public String tipoDe(String caminho) {
    return TIPOS.getOrDefault(extensao(caminho), "application/octet-stream");
  }

  private Path resolver(String caminho) {
    if (caminho == null || !caminho.startsWith(RAIZ_PRIVADA + "/")) {
      throw new NotFoundException("Arquivo não encontrado");
    }
    Path base = Path.of(baseUploadDir).toAbsolutePath().normalize();
    Path alvo = base.resolve(caminho).normalize();
    if (!alvo.startsWith(raizPrivada())) {
      throw new NotFoundException("Arquivo não encontrado");
    }
    return alvo;
  }

  private Path raizPrivada() {
    return Path.of(baseUploadDir).toAbsolutePath().normalize().resolve(RAIZ_PRIVADA);
  }

  private static String extensao(String nome) {
    if (nome == null || !nome.contains(".")) {
      return "";
    }
    return nome.substring(nome.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
  }

  private static boolean assinaturaConfere(String ext, byte[] b) {
    if (b.length < 4) {
      return false;
    }
    return switch (ext) {
      case "jpg", "jpeg" -> (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8;
      case "png" -> (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
      case "pdf" -> b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F';
      case "webp" -> b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
          && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
      default -> false;
    };
  }
}
