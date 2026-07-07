package br.com.lojaspopular.application.catalog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
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

	private final ProdutoRepository repository;
	private final CategoriaRepository categoriaRepository;
	
    @Value("${app.upload-dir:uploads}")
    private String baseUploadDir;

	public Categoria buscarCategoria(@NonNull Long id) {
		return categoriaRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("Categoria não encontrada: " + id));
	}

	public Produto buscar(@NonNull Long id) {
		return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Produto não encontrado: " + id));
	}

	public Page<Produto> listar(String nome, Long categoriaId, String categoriaNome, @NonNull Pageable pageable) {
	    if (categoriaId != null) {
	        return repository.findByCategoriaId(categoriaId, pageable);
	    }
	    if (categoriaNome != null && !categoriaNome.isBlank()) {
	        return repository.findByCategoriaNomeContainingIgnoreCase(categoriaNome, pageable);
	    }
	    if (nome != null && !nome.isBlank()) {
	        return repository.findByNomeContainingIgnoreCase(nome, pageable);
	    }
	    return repository.findAll(pageable);
	}

	@Transactional
	public Produto salvar(@NonNull Produto p) {
		return repository.save(p);
	}

	@Transactional
	public Produto atualizar(@NonNull Long id, Produto novo) {
		var atual = buscar(id);

		// Preserva imagemUrl das variações existentes por SKU
		Map<String, String> imagesBySku = atual.getVariacoes().stream()
			.filter(v -> v.getSku() != null && v.getImagemUrl() != null)
			.collect(java.util.stream.Collectors.toMap(
				ProdutoVariacao::getSku,
				ProdutoVariacao::getImagemUrl,
				(a, b) -> a));

		atual.setNome(novo.getNome());
		atual.setDescricao(novo.getDescricao());
		atual.setPreco(novo.getPreco());
		atual.setPrecoOriginal(novo.getPrecoOriginal());
		atual.setEstoque(novo.getEstoque());
		atual.setSku(novo.getSku());
		atual.setCodigo(novo.getCodigo());
		atual.setCategoria(novo.getCategoria());
		atual.setLargura(novo.getLargura());
		atual.setAltura(novo.getAltura());
		atual.setProfundidade(novo.getProfundidade());
		atual.setPeso(novo.getPeso());
		atual.setVolumes(novo.getVolumes());

		atual.getDiferenciais().clear();
		atual.getDiferenciais().addAll(novo.getDiferenciais() != null ? novo.getDiferenciais() : List.of());

		atual.getVariacoes().clear();
		if (novo.getVariacoes() != null) {
			for (ProdutoVariacao v : novo.getVariacoes()) {
				// Restaura imagem existente se não veio nova no request
				if (v.getImagemUrl() == null && v.getSku() != null && imagesBySku.containsKey(v.getSku())) {
					v.setImagemUrl(imagesBySku.get(v.getSku()));
				}
				atual.addVariacao(v);
			}
		}
		return atual;
	}
	
	@Transactional
	public void excluir(@NonNull Long id) {
		
		var p = buscar(id);
		p.setAtiva(false);
		p.setDataAtualizacao(LocalDateTime.now());
		repository.saveAndFlush(p);
		
	}

	private static final Path ROOT = Path.of("uploads");
	private static final Path PROD_DIR = ROOT.resolve("produtos");
	private static final Path GAL_DIR = ROOT.resolve("produtos/galeria");

	private void ensureDirs() throws IOException {
		Files.createDirectories(PROD_DIR);
		Files.createDirectories(GAL_DIR);
	}

	public String salvarImagemCapa(MultipartFile file) throws IOException {
	    if (file.isEmpty()) throw new IllegalArgumentException("Arquivo vazio");

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
	
    @Transactional
    public void removerImagemGaleria(@NonNull Long produtoId, String url) {
        var p = buscar(produtoId);
        // remove do modelo
        p.removeImagemByUrl(url);
        salvar(p);

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
	
    @Transactional
    public void reordenarGaleria(@NonNull Long produtoId, List<String> urlsNaOrdem) {
        var p = buscar(produtoId);
        p.reorderGaleria(urlsNaOrdem);
        salvar(p);
    }

	@Transactional
	public String salvarImagemVariacao(@NonNull Long produtoId, @NonNull Long variacaoId, MultipartFile file) throws IOException {
		var produto = buscar(produtoId);
		var variacao = produto.getVariacoes().stream()
			.filter(v -> v.getId() != null && v.getId().equals(variacaoId))
			.findFirst()
			.orElseThrow(() -> new EntityNotFoundException("Variação não encontrada: " + variacaoId));

		var url = salvarImagemCapa(file); // reutiliza o mesmo diretório de uploads
		variacao.setImagemUrl(url);
		salvar(produto);
		return url;
	}
}
