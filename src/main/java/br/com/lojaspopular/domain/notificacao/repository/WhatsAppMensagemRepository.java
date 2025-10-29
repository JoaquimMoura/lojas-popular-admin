package br.com.lojaspopular.domain.notificacao.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.lojaspopular.domain.notificacao.model.WhatsAppMensagem;

@Repository
public interface WhatsAppMensagemRepository extends JpaRepository<WhatsAppMensagem, Long> {

	List<WhatsAppMensagem> findByDestinatarioOrderByDataEnvioDesc(String destinatario);

	List<WhatsAppMensagem> findByStatusOrderByDataEnvioDesc(Enum<?> status);
}
