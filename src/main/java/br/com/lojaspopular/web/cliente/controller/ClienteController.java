package br.com.lojaspopular.web.cliente.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.lojaspopular.application.cliente.ClienteService;
import br.com.lojaspopular.application.cliente.ClienteService.DadosCliente;
import br.com.lojaspopular.application.cliente.ClienteService.DadosEndereco;
import br.com.lojaspopular.domain.cliente.model.Cliente;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/clientes")
@PreAuthorize("hasAnyRole('ADMIN','GERENTE','VENDEDOR')")
@RequiredArgsConstructor
public class ClienteController {

  public record EnderecoRequest(
      @Size(max = 60) String apelido, @Size(max = 9) String cep,
      @NotBlank(message = "Logradouro é obrigatório") @Size(max = 150) String logradouro,
      @Size(max = 20) String numero, @Size(max = 100) String complemento, @Size(max = 100) String bairro,
      @NotBlank(message = "Cidade é obrigatória") @Size(max = 100) String cidade,
      @NotBlank(message = "UF é obrigatória") @Size(min = 2, max = 2, message = "UF deve ter 2 letras") String uf,
      boolean principal) {
  }

  public record ClienteRequest(
      @NotBlank(message = "Nome é obrigatório") @Size(max = 150) String nome,
      String cpf, String telefone, @Size(max = 150) String email, @Size(max = 500) String observacoes,
      @Valid List<EnderecoRequest> enderecos,
      /** Confirma o cadastro mesmo havendo cliente com mesmo telefone/nome. */
      boolean confirmarDuplicidade) {
  }

  public record EnderecoResponse(Long id, String apelido, String cep, String logradouro, String numero,
      String complemento, String bairro, String cidade, String uf, boolean principal) {
  }

  public record ClienteResponse(Long id, String nome, String cpf, String telefone, String email, String observacoes,
      boolean ativo, List<EnderecoResponse> enderecos) {
  }

  private final ClienteService service;
  private final br.com.lojaspopular.application.cliente.ClienteHistoricoService historico;

  /** Busca por nome, CPF ou telefone. Deve ser feita antes de cadastrar. */
  @GetMapping
  public List<ClienteResponse> buscar(@RequestParam(required = false) String q) {
    return service.buscar(q).stream().map(this::toResponse).toList();
  }

  @GetMapping("/duplicidades")
  public List<ClienteResponse> duplicidades(@RequestParam(required = false) String cpf,
      @RequestParam(required = false) String telefone, @RequestParam(required = false) String nome,
      @RequestParam(required = false) Long ignorarId) {
    return service.duplicidades(cpf, telefone, nome, ignorarId).stream().map(this::toResponse).toList();
  }

  @GetMapping("/{id}")
  public ClienteResponse obter(@PathVariable Long id) {
    return toResponse(service.obter(id));
  }

  @PostMapping
  public ClienteResponse criar(@Valid @RequestBody ClienteRequest req) {
    return toResponse(service.criar(toDados(req), req.confirmarDuplicidade()));
  }

  @PutMapping("/{id}")
  public ClienteResponse atualizar(@PathVariable Long id, @Valid @RequestBody ClienteRequest req) {
    return toResponse(service.atualizar(id, toDados(req), req.confirmarDuplicidade()));
  }

  @GetMapping("/{id}/compras")
  public br.com.lojaspopular.application.cliente.ClienteHistoricoService.PaginaCompras compras(@PathVariable Long id,
      @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate de,
      @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate ate,
      @RequestParam(required = false) br.com.lojaspopular.domain.order.enums.StatusComercial situacao,
      @RequestParam(required = false) String produto, @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "10") int tamanho) {
    return historico.compras(id, de, ate, situacao, produto, pagina, tamanho);
  }

  @GetMapping("/{id}/resumo")
  public br.com.lojaspopular.application.cliente.ClienteHistoricoService.Resumo resumo(@PathVariable Long id) {
    return historico.resumo(id);
  }

  /** Só cliente sem vendas; com vendas, use desativar. */
  @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public void excluir(@PathVariable Long id) {
    service.excluir(id);
  }

  @PostMapping("/{id}/desativar")
  @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
  public ClienteResponse desativar(@PathVariable Long id) {
    return toResponse(service.desativar(id));
  }

  private DadosCliente toDados(ClienteRequest r) {
    List<DadosEndereco> ends = r.enderecos() == null ? null
        : r.enderecos().stream().map(e -> new DadosEndereco(e.apelido(), e.cep(), e.logradouro(), e.numero(),
            e.complemento(), e.bairro(), e.cidade(), e.uf(), e.principal())).toList();
    return new DadosCliente(r.nome(), r.cpf(), r.telefone(), r.email(), r.observacoes(), ends);
  }

  private ClienteResponse toResponse(Cliente c) {
    var ends = c.getEnderecos().stream().map(e -> new EnderecoResponse(e.getId(), e.getApelido(), e.getCep(),
        e.getLogradouro(), e.getNumero(), e.getComplemento(), e.getBairro(), e.getCidade(), e.getUf(),
        e.isPrincipal())).toList();
    return new ClienteResponse(c.getId(), c.getNome(), c.getCpf(), c.getTelefone(), c.getEmail(), c.getObservacoes(),
        c.isAtivo(), ends);
  }
}
