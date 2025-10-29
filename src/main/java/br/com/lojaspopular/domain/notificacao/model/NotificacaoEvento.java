package br.com.lojaspopular.domain.notificacao.model;

import java.time.Instant;

import br.com.lojaspopular.domain.notificacao.enums.NotificacaoStatus;
import br.com.lojaspopular.domain.notificacao.enums.NotificacaoTipo;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "notificacao_evento")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class NotificacaoEvento {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  private NotificacaoTipo tipo;

  private String destinatario;
  private String assunto;

  @Enumerated(EnumType.STRING)
  private NotificacaoStatus status;

  private Instant dataEnvio = Instant.now();

  private String detalheErro;

  /** 🔗 Vinculação com WhatsApp (1:1) */
  @OneToOne(mappedBy = "notificacaoEvento", cascade = CascadeType.ALL)
  private WhatsAppMensagem whatsappMensagem;
}


