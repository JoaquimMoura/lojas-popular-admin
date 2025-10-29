package br.com.lojaspopular.web.notificacao.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.lojaspopular.domain.notificacao.model.NotificacaoEvento;
import br.com.lojaspopular.domain.notificacao.repository.NotificacaoEventoRepository;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/notificacoes")
@RequiredArgsConstructor
public class NotificacaoController {

  private final NotificacaoEventoRepository repo;

  @GetMapping
  public ResponseEntity<List<NotificacaoEvento>> listar() {
    return ResponseEntity.ok(repo.findAll());
  }
}
