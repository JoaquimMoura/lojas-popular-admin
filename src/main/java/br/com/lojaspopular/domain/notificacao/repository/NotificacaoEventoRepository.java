package br.com.lojaspopular.domain.notificacao.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.lojaspopular.domain.notificacao.enums.NotificacaoTipo;
import br.com.lojaspopular.domain.notificacao.model.NotificacaoEvento;

@Repository
public interface NotificacaoEventoRepository extends JpaRepository<NotificacaoEvento, Long> {

	List<NotificacaoEvento> findAllByOrderByDataEnvioDesc();

	List<NotificacaoEvento> findByDestinatarioOrderByDataEnvioDesc(String destinatario);

	List<NotificacaoEvento> findByTipoOrderByDataEnvioDesc(NotificacaoTipo tipo);
	

}
