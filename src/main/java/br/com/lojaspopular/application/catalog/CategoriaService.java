package br.com.lojaspopular.application.catalog;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import br.com.lojaspopular.domain.catalog.model.Categoria;
import br.com.lojaspopular.domain.catalog.repository.CategoriaRepository;
import br.com.lojaspopular.exception.NegocioException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoriaService {

	private final CategoriaRepository repo;

    @Transactional(readOnly = true)
    public List<Categoria> listar() {
        return repo.findByAtivaTrue();
    }

    @Transactional(readOnly = true)
    public Categoria buscar(Long id) {
        return repo.findById(id)
                   .orElseThrow(() -> new NegocioException("Categoria não encontrada"));
    }

	public Categoria salvar(Categoria c) {
        validarDuplicidade(c, null);
		return repo.save(c);
	}

	public Categoria atualizar(Long id, Categoria c) {
		Categoria atual = buscar(id);
        validarDuplicidade(c, id);

		atual.setNome(c.getNome());
		atual.setDescricao(c.getDescricao());
        atual.setMaterial(c.getMaterial());

		return repo.save(atual);
	}

	public void excluir(Long id) {
		repo.delete(buscar(id));
	}
	
    private void validarDuplicidade(Categoria categoria, Long idEdicao) {
        repo.findByNomeIgnoreCaseAndMaterial(
            categoria.getNome(), categoria.getMaterial()).ifPresent(existente -> {
            if (idEdicao == null || !existente.getId().equals(idEdicao)) {
                throw new NegocioException(String.format(
                    "Já existe uma categoria '%s' com material %s)",
                    categoria.getNome(),
                    categoria.getMaterial()
                ));
            }
        });
    }

    public String salvarImagemCategoria(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Arquivo inválido");
        }

        String uploadDir = "uploads/categorias/";
        File directory = new File(uploadDir);

        if (!directory.exists()) {
            directory.mkdirs(); // cria diretórios se não existirem
        }

        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        File destinationFile = new File(uploadDir + fileName);

        // Salva o arquivo fisicamente no servidor
        file.transferTo(destinationFile);

        // URL para ser acessada pelo front-end
        return "/uploads/categorias/" + fileName;
    }


}
