package br.com.lojaspopular.application.catalog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
import br.com.lojaspopular.exception.ConflitoVersaoException;
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

		if (novo.getVersion() != null && !novo.getVersion().equals(atual.getVersion())) {
			throw new ConflitoVersaoException(
				"Este produto foi modificado por outro usuário. Recarregue os dados e tente novamente.");
		}

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

		deleteFileQuietly(p.getImagemUrl());
		p.getGaleria().forEach(img -> deleteFileQuietly(img.getUrl()));
		p.getVariacoes().forEach(v -> deleteFileQuietly(v.getImagemUrl()));

		repository.delete(p);
	}

	private Path resolveFromUrl(String url) {
		String relative = url.startsWith("/") ? url.substring(1) : url;
		if (relative.startsWith("uploads/")) relative = relative.substring("uploads/".length());
		return Path.of(baseUploadDir).resolve(relative);
	}

	private void deleteFileQuietly(String url) {
		if (url == null || url.isBlank()) return;
		try {
			Files.deleteIfExists(resolveFromUrl(url));
		} catch (IOException ignored) {}
	}

	/** Slug ASCII do nome da categoria (ex.: "Escritório" -> "escritorio"), usado como subpasta de imagens. */
	private static String categoriaSlug(Categoria categoria) {
		String nome = categoria != null ? categoria.getNome() : null;
		if (nome == null || nome.isBlank()) return "sem-categoria";
		String semAcento = java.text.Normalizer.normalize(nome, java.text.Normalizer.Form.NFD)
				.replaceAll("[^\\p{ASCII}]", "");
		String slug = semAcento.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
		return slug.isBlank() ? "sem-categoria" : slug;
	}

	private Path produtosDir() {
		return Path.of(baseUploadDir).resolve("produtos");
	}

	private Path categoriaDir(Categoria categoria) throws IOException {
		Path dir = produtosDir().resolve(categoriaSlug(categoria));
		Files.createDirectories(dir);
		return dir;
	}

	public String salvarImagemCapa(MultipartFile file, Categoria categoria) throws IOException {
	    if (file.isEmpty()) throw new IllegalArgumentException("Arquivo vazio");
	    Path dir = categoriaDir(categoria);

	    String original = file.getOriginalFilename();
	    String safeName = java.text.Normalizer.normalize(original, java.text.Normalizer.Form.NFD)
	            .replaceAll("[^\\p{ASCII}]", "")     // remove acentos
	            .replaceAll("[^a-zA-Z0-9\\.\\-_]", "-"); // troca espaços/qualquer coisa por '-'

	    String fileName = java.util.UUID.randomUUID() + "_" + safeName;
	    Path target = dir.resolve(fileName);

	    try (var in = file.getInputStream()) {
	        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
	    }

	    return "/uploads/produtos/" + categoriaSlug(categoria) + "/" + fileName;
	}

    @Transactional
    public void removerImagemGaleria(@NonNull Long produtoId, String url) {
        var p = buscar(produtoId);
        p.removeImagemByUrl(url);
        salvar(p);
        deleteFileQuietly(url);
    }

	public String salvarImagemGaleria(MultipartFile file, Categoria categoria) throws IOException {
		Path dir = categoriaDir(categoria).resolve("galeria");
		Files.createDirectories(dir);
		String clean = Path.of(file.getOriginalFilename()).getFileName().toString();
		String filename = java.util.UUID.randomUUID() + "_" + clean;
		Path dest = dir.resolve(filename);
		Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
		return "/uploads/produtos/" + categoriaSlug(categoria) + "/galeria/" + filename;
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

		var url = salvarImagemCapa(file, produto.getCategoria());
		variacao.setImagemUrl(url);
		salvar(produto);
		return url;
	}
}
