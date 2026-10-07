package br.com.lojaspopular.application.financeiro;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

import br.com.lojaspopular.domain.financeiro.enums.StatusFechamento;
import br.com.lojaspopular.domain.financeiro.repository.FechamentoMensalRepository;
import br.com.lojaspopular.exception.NegocioException;
import lombok.RequiredArgsConstructor;

/**
 * Guarda de períodos fechados. Todo serviço que grava lançamento com data própria (recebimento, liquidação,
 * baixa de conta, restituição...) chama {@link #exigirAberto(LocalDate)}: esconder o botão na tela não basta.
 * Eventos posteriores ao fechamento (ex.: devolução) entram no período atual, que está aberto.
 */
@Service
@RequiredArgsConstructor
public class PeriodoService {

  private final FechamentoMensalRepository fechamentos;

  public boolean fechado(LocalDate qualquerDiaDoMes) {
    return fechamentos.existsByMesAndStatus(Relogio.primeiroDia(qualquerDiaDoMes), StatusFechamento.APROVADO);
  }

  public void exigirAberto(LocalDate data) {
    if (fechado(data)) {
      throw new NegocioException("O período " + Relogio.texto(Relogio.primeiroDia(data)) + " está fechado: não são "
          + "aceitos lançamentos nele. Lance com a data atual ou reabra o período (se autorizado).");
    }
  }
}
