package br.com.lojaspopular.domain.notificacao.model;

import java.time.Instant;

import br.com.lojaspopular.domain.notificacao.enums.WhatsAppMensagemStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "whatsapp_mensagem")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatsAppMensagem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String destinatario; // ex: +55(11)99999-0000
	private String conteudo;

	private String canal = "whatsapp";
	private String provedor = "zenvia";
	private String mensagemId; // id retornado pela Zenvia

	private Instant dataEnvio = Instant.now();

	@Enumerated(EnumType.STRING)
	private WhatsAppMensagemStatus status = WhatsAppMensagemStatus.PENDENTE;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "notificacao_evento_id")
	private NotificacaoEvento notificacaoEvento;
}
