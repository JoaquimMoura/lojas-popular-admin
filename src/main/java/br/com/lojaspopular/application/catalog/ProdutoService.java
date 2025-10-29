package br.com.lojaspopular.application.catalog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import br.com.lojaspopular.domain.catalog.model.Categoria;
import br.com.lojaspopular.domain.catalog.model.Produto;
import br.com.lojaspopular.domain.catalog.model.ProdutoVariacao;
import br.com.lojaspopular.domain.catalog.repository.CategoriaRepository;
import br.com.lojaspopular.domain.catalog.repository.ProdutoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProdutoService {

	private final ProdutoRepository produtoRepo;
	private final CategoriaRepository categoriaRepo;
	
    @Value("${app.upload-dir:uploads}")
    private String baseUploadDir;

	public Categoria buscarCategoria(Long id) {
		return categoriaRepo.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("Categoria não encontrada: " + id));
	}

	public Produto buscar(Long id) {
		return produtoRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Produto não encontrado: " + id));
	}

	public List<Produto> listar(String nome, Pageable pageable) {
		return produtoRepo.findAll(pageable).getContent();
	}

	@Transactional
	public Produto salvar(Produto p) {
		return produtoRepo.save(p);
	}

	@Transactional
	public Produto atualizar(Long id, Produto novo) {
		var atual = buscar(id);
		atual.setNome(novo.getNome());
		atual.setDescricao(novo.getDescricao());
		atual.setPreco(novo.getPreco());
		atual.setEstoque(novo.getEstoque());
		atual.setSku(novo.getSku());
		atual.setCategoria(novo.getCategoria());

		// reset de variações
		atual.getVariacoes().clear();
		if (novo.getVariacoes() != null) {
			for (ProdutoVariacao v : novo.getVariacoes()) {
				atual.addVariacao(v);
			}
		}
		return atual;
	}

	// ===== Uploads locais =====
	private static final Path ROOT = Path.of("uploads");
	private static final Path PROD_DIR = ROOT.resolve("produtos");
	private static final Path GAL_DIR = ROOT.resolve("produtos/galeria");

	private void ensureDirs() throws IOException {
		Files.createDirectories(PROD_DIR);
		Files.createDirectories(GAL_DIR);
	}

	public String salvarImagemCapa(MultipartFile file) throws IOException {
	    if (file.isEmpty()) throw new IllegalArgumentException("Arquivo vazio");

	    // 3.1 - normaliza nome do arquivo para evitar espaços/acentos
	    String original = file.getOriginalFilename();
	    String safeName = java.text.Normalizer.normalize(original, java.text.Normalizer.Form.NFD)
	            .replaceAll("[^\\p{ASCII}]", "")     // remove acentos
	            .replaceAll("[^a-zA-Z0-9\\.\\-_]", "-"); // troca espaços/qualquer coisa por '-'

	    String fileName = java.util.UUID.randomUUID() + "_" + safeName;

	    java.nio.file.Path base = java.nio.file.Paths.get("C:/Users/jmoura/developer/tools/projects/lojas-popular-backend/uploads/produtos");
	    java.nio.file.Files.createDirectories(base);
	    java.nio.file.Path target = base.resolve(fileName);

	    try (var in = file.getInputStream()) {
	        java.nio.file.Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
	    }

	    // 3.2 - retorna a URL sem precisar de encode (pois não tem espaço)
	    return "/uploads/produtos/" + fileName;
	}
	
	public void removerImagemGaleria(Long produtoId, String url) {
        var p = buscar(produtoId);
        // remove do modelo
        p.removeImagemByUrl(url);
        salvar(p);

        // tenta remover arquivo físico (opcional)
        try {
            if (url.startsWith("/")) url = url.substring(1);
            Path path = Paths.get(url);
            if (!path.isAbsolute()) path = Paths.get(baseUploadDir).getParent().resolve(url).normalize();
            // fallback simples:
            Path local = Paths.get(url);
            if (Files.exists(local)) Files.delete(local);
        } catch (Exception ignored) {}
    }

	public String salvarImagemGaleria(MultipartFile file) throws IOException {
		ensureDirs();
		String clean = Path.of(file.getOriginalFilename()).getFileName().toString();
		String filename = java.util.UUID.randomUUID() + "_" + clean;
		Path dest = GAL_DIR.resolve(filename);
		Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
		return "/uploads/produtos/galeria/" + filename;
	}
	
	public void reordenarGaleria(Long produtoId, List<String> urlsNaOrdem) {
        var p = buscar(produtoId);
        p.reorderGaleria(urlsNaOrdem);
        salvar(p);
    }
}
