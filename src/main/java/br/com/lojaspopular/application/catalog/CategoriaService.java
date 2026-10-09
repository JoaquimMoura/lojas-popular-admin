package br.com.lojaspopular.application.catalog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import br.com.lojaspopular.application.auth.UsuarioAtual;
import br.com.lojaspopular.domain.catalog.Normalizacao;
import br.com.lojaspopular.domain.catalog.model.Categoria;
import br.com.lojaspopular.domain.catalog.model.Material;
import br.com.lojaspopular.domain.catalog.repository.CategoriaRepository;
import br.com.lojaspopular.domain.catalog.repository.ProdutoRepository;
import br.com.lojaspopular.exception.NegocioException;
import br.com.lojaspopular.web.catalog.dto.CategoriaRequest;
import br.com.lojaspopular.web.catalog.dto.CategoriaResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoriaService {

    private final CategoriaRepository repo;
    private final ProdutoRepository produtos;
    private final CatalogoConfigService catalogo;
    private final UsuarioAtual usuarioAtual;

    @Value("${app.upload-dir:uploads}")
    private String baseUploadDir;

    @Transactional(readOnly = true)
    public List<Categoria> listar() {
        return repo.findByAtivaTrue();
    }

    @Transactional(readOnly = true)
    public Categoria buscar(Long id) {
        return repo.findById(id).orElseThrow(() -> new NegocioException("Categoria não encontrada"));
    }

    @Transactional
    public Categoria criar(CategoriaRequest req) {
        String nome = nomeValido(req.getNome());
        exigirNomeLivre(nome, null);
        exigirPermissaoDeConfiguracao(req);
        Categoria c = repo.save(Categoria.builder().nome(nome).descricao(vazioParaNulo(req.getDescricao())).build());
        catalogo.configurarCategoria(c, req.getMaterialIds(), req.getCaracteristicas());
        return c;
    }

    @Transactional
    public Categoria atualizar(Long id, CategoriaRequest req) {
        Categoria atual = buscar(id);
        String nome = nomeValido(req.getNome());
        if (!Normalizacao.normalizar(nome).equals(Normalizacao.normalizar(atual.getNome()))) {
            exigirNomeLivre(nome, id);   // só valida se o nome mudou (categorias antigas com nome repetido continuam editáveis)
        }
        exigirPermissaoDeConfiguracao(req);
        atual.setNome(nome);
        atual.setDescricao(vazioParaNulo(req.getDescricao()));
        catalogo.configurarCategoria(atual, req.getMaterialIds(), req.getCaracteristicas());
        return repo.save(atual);
    }

    /** Salva a categoria sem revalidar a configuração (usado, por ex., ao anexar a imagem). */
    @Transactional
    public Categoria salvar(Categoria c) {
        return repo.save(c);
    }

    @Transactional
    public void excluir(Long id) {
        Categoria c = buscar(id);
        if (!produtos.findAllByCategoriaId(id).isEmpty()) {
            throw new NegocioException("Esta categoria possui produtos. Mova os produtos para outra categoria antes de excluir.");
        }
        repo.delete(c);
    }

    @Transactional(readOnly = true)
    public CategoriaResponse toResponse(Categoria recebida) {
        // recarrega dentro da transação: a entidade pode chegar desanexada (coleções lazy)
        Categoria c = repo.findById(recebida.getId()).orElse(recebida);
        return CategoriaResponse.builder().id(c.getId()).nome(c.getNome()).descricao(c.getDescricao()).imagemUrl(c.getImagemUrl())
            .ativa(c.isAtiva())
            .materiais(c.getMateriais().stream().sorted(Comparator.comparing(Material::getNome)).map(catalogo::ref).toList())
            .caracteristicas(catalogo.caracteristicasDaCategoria(c))
            .produtosPendentes(catalogo.pendentesDaCategoria(c.getId()).size()).build();
    }

    // ---- internos ----

    private void exigirPermissaoDeConfiguracao(CategoriaRequest req) {
        boolean configura = req.getMaterialIds() != null || req.getCaracteristicas() != null;
        if (configura && !UsuarioAtual.isGestor(usuarioAtual.get())) {
            throw new AccessDeniedException("Somente gerente ou proprietário configura materiais e características da categoria.");
        }
    }

    private void exigirNomeLivre(String nome, Long idEdicao) {
        String norm = Normalizacao.normalizar(nome);
        repo.findAll().stream().filter(c -> idEdicao == null || !c.getId().equals(idEdicao))
            .filter(c -> Normalizacao.normalizar(c.getNome()).equals(norm)).findFirst().ifPresent(c -> {
                throw new NegocioException("Já existe uma categoria chamada \"" + c.getNome() + "\".");
            });
    }

    private static String nomeValido(String nome) {
        String n = Normalizacao.limpar(nome);
        if (n == null || n.isEmpty()) {
            throw new NegocioException("O nome da categoria é obrigatório.");
        }
        if (n.length() > 100) {
            throw new NegocioException("O nome deve ter no máximo 100 caracteres.");
        }
        return n;
    }

    private static String vazioParaNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public String salvarImagemCategoria(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Arquivo inválido");
        }
        Path dir = Path.of(baseUploadDir).resolve("categorias");
        Files.createDirectories(dir);
        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path destino = dir.resolve(fileName);
        try (var in = file.getInputStream()) {
            Files.copy(in, destino, StandardCopyOption.REPLACE_EXISTING);
        }
        return "/uploads/categorias/" + fileName;
    }
}
