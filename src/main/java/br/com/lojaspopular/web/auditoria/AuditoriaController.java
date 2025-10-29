package br.com.lojaspopular.web.auditoria;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.lojaspopular.application.auditoria.AuditoriaService;
import br.com.lojaspopular.domain.auditoria.model.AuditoriaEvento;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auditoria")
@RequiredArgsConstructor
public class AuditoriaController {

	private final AuditoriaService auditoriaService;

	@GetMapping
	public ResponseEntity<List<AuditoriaEvento>> listar() {
		return ResponseEntity.ok(auditoriaService.listarTodos());
	}
}
