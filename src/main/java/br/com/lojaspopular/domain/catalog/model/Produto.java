package br.com.lojaspopular.domain.catalog.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.var;

@Entity
@Table(name = "produtos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Produto {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 150)
	private String nome;

	@Column(length = 500)
	private String descricao;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal preco;

	@Column(nullable = false)
	private Integer estoque;

	// SKU “base” do produto (opcional; variações terão SKU próprio)
	@Column(length = 60, unique = true)
	private String sku;

	private String imagemUrl; // capa principal

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "categoria_id")
	private Categoria categoria;

	@OneToMany(mappedBy = "produto", cascade = CascadeType.ALL, orphanRemoval = true)
	@Builder.Default
	private List<ProdutoVariacao> variacoes = new ArrayList<>();

	@OneToMany(mappedBy = "produto", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("ordem ASC")
	@Builder.Default
	private List<ProdutoImagem> galeria = new ArrayList<>();

	public void addVariacao(ProdutoVariacao v) {
		v.setProduto(this);
		this.variacoes.add(v);
	}

	public void addImagem(ProdutoImagem img) {
		img.setProduto(this);
		if (img.getOrdem() == null)
			img.setOrdem(galeria.size());
		galeria.add(img);
	}
	
	public void removeImagemByUrl(String url) {
	    galeria.removeIf(g -> g.getUrl().equals(url));
	    // normaliza ordens:
	    for (int i = 0; i < galeria.size(); i++) galeria.get(i).setOrdem(i);
	}
	
	public void reorderGaleria(List<String> urlsNaOrdem) {
	    Map<String, ProdutoImagem> map = galeria.stream()
	            .collect(java.util.stream.Collectors.toMap(ProdutoImagem::getUrl, g -> g));
	    List<ProdutoImagem> nova = new java.util.ArrayList<>();
	    for (int i = 0; i < urlsNaOrdem.size(); i++) {
	        var gi = map.get(urlsNaOrdem.get(i));
	        if (gi != null) { gi.setOrdem(i); nova.add(gi); }
	    }
	    galeria.clear();
	    galeria.addAll(nova);
	}
}
