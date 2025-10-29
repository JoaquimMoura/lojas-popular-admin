package br.com.lojaspopular.application.auditoria;


import java.util.List;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import br.com.lojaspopular.domain.auditoria.enums.AuditoriaTipo;
import br.com.lojaspopular.domain.auditoria.model.AuditoriaEvento;
import br.com.lojaspopular.domain.auditoria.repositoty.AuditoriaEventoRepository;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuditoriaService {

  private final AuditoriaEventoRepository auditoriaRepo;
  private final UserRepository userRepo;

  public void registrar(AuditoriaTipo tipo, String descricao) {
    User usuario = null;
    try {
      var auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth != null && auth.isAuthenticated()) {
        usuario = userRepo.findByEmail(auth.getName()).orElse(null);
      }
    } catch (Exception ignored) {}

    AuditoriaEvento evento = AuditoriaEvento.builder()
        .tipo(tipo)
        .descricao(descricao)
        .usuario(usuario)
        .build();

    auditoriaRepo.save(evento);
  }

  public List<AuditoriaEvento> listarTodos() {
    return auditoriaRepo.findAllByOrderByDataEventoDesc();
  }
}

