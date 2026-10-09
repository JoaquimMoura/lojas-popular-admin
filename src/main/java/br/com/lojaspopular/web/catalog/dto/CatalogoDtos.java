package br.com.lojaspopular.web.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

import br.com.lojaspopular.domain.catalog.enums.TipoCaracteristica;

/** Contratos do catálogo dinâmico: materiais, características por categoria e valores no produto. */
public final class CatalogoDtos {

  private CatalogoDtos() {
  }

  // ------------------------------------------------------------------ respostas
  public record MaterialView(Long id, String nome, boolean ativo, long categorias, long produtos) {
  }

  public record MaterialRef(Long id, String nome, boolean ativo) {
  }

  public record OpcaoView(Long id, String valor, boolean ativa, int ordem) {
  }

  public record CaracteristicaView(Long id, String nome, TipoCaracteristica tipo, String unidade, boolean obrigatoria, int ordem,
      boolean exibirNaVitrine, boolean ativa, long produtosComValor, List<OpcaoView> opcoes) {
  }

  /** Valor de uma característica num produto (texto/número ou as opções escolhidas) já formatado para exibição. */
  public record ValorView(Long caracteristicaId, String nome, TipoCaracteristica tipo, String unidade, boolean exibirNaVitrine,
      int ordem, boolean caracteristicaAtiva, String valorTexto, BigDecimal valorNumero, List<OpcaoView> opcoes, String exibicao) {
  }

  /** O que seria descartado ao trocar a categoria de um produto. */
  public record Impacto(List<String> materiais, List<String> caracteristicas) {
    public boolean vazio() {
      return materiais.isEmpty() && caracteristicas.isEmpty();
    }
  }

  public record ProdutoPendente(Long id, String nome, List<String> faltando) {
  }

  // ------------------------------------------------------------------ pedidos
  public record MaterialRequest(String nome, Boolean ativo) {
  }

  public record OpcaoRequest(Long id, String valor, Boolean ativa) {
  }

  public record CaracteristicaRequest(Long id, String nome, TipoCaracteristica tipo, String unidade, Boolean obrigatoria,
      Integer ordem, Boolean exibirNaVitrine, Boolean ativa, List<OpcaoRequest> opcoes) {
  }

  /** Valor informado para uma característica no cadastro do produto. */
  public record ValorRequest(Long caracteristicaId, List<Long> opcaoIds, String valorTexto, BigDecimal valorNumero) {
  }
}
