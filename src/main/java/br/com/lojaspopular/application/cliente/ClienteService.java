package br.com.lojaspopular.application.cliente;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.lojaspopular.application.auditoria.AuditoriaService;
import br.com.lojaspopular.domain.auditoria.enums.AuditoriaTipo;
import br.com.lojaspopular.domain.cliente.model.Cliente;
import br.com.lojaspopular.domain.cliente.model.EnderecoCliente;
import br.com.lojaspopular.domain.cliente.repository.ClienteRepository;
import br.com.lojaspopular.exception.DuplicidadeClienteException;
import br.com.lojaspopular.exception.NegocioException;
import br.com.lojaspopular.exception.NotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClienteService {

  public record DadosCliente(String nome, String cpf, String telefone, String email, String observacoes,
      List<DadosEndereco> enderecos) {
  }

  public record DadosEndereco(String apelido, String cep, String logradouro, String numero, String complemento,
      String bairro, String cidade, String uf, boolean principal) {
  }

  private final ClienteRepository repo;
  private final AuditoriaService auditoria;

  @Transactional(readOnly = true)
  public List<Cliente> buscar(String q) {
    String termo = q == null ? "" : q.trim();
    String digitos = Documentos.somenteDigitos(termo);
    // sem dígitos, usa um valor que nunca casa com CPF/telefone (só dígitos): evita que "%%" case todos
    var lista = termo.isEmpty() ? repo.buscar("", "", PageRequest.of(0, 30))
        : repo.buscar(termo, digitos.isEmpty() ? "x" : digitos, PageRequest.of(0, 30));
    lista.forEach(c -> c.getEnderecos().size());
    return lista;
  }

  @Transactional(readOnly = true)
  public Cliente obter(Long id) {
    var c = repo.findById(id).orElseThrow(() -> new NotFoundException("Cliente não encontrado"));
    c.getEnderecos().size();
    return c;
  }

  /** Possíveis duplicados por CPF, telefone ou nome idêntico (para alertar antes de cadastrar). */
  @Transactional(readOnly = true)
  public List<Cliente> duplicidades(String cpf, String telefone, String nome, Long ignorarId) {
    String cpfDig = Documentos.somenteDigitos(cpf);
    String telDig = Documentos.somenteDigitos(telefone);
    Stream<Cliente> porCpf = cpfDig.isEmpty() ? Stream.empty() : repo.findByCpf(cpfDig).stream();
    Stream<Cliente> porTel = telDig.isEmpty() ? Stream.empty() : repo.findByTelefone(telDig).stream();
    Stream<Cliente> porNome = nome == null || nome.isBlank() ? Stream.empty()
        : repo.findByNomeIgnoreCase(nome.trim()).stream();
    var lista = Stream.of(porCpf, porTel, porNome).flatMap(s -> s)
        .filter(c -> ignorarId == null || !c.getId().equals(ignorarId))
        .distinct().toList();
    lista.forEach(c -> c.getEnderecos().size());
    return lista;
  }

  @Transactional
  public Cliente criar(DadosCliente dados, boolean confirmarDuplicidade) {
    var normalizado = normalizar(dados);
    verificarDuplicidade(normalizado, null, confirmarDuplicidade);

    Cliente c = Cliente.builder()
        .nome(normalizado.nome()).cpf(vazioParaNulo(normalizado.cpf()))
        .telefone(vazioParaNulo(normalizado.telefone())).email(normalizado.email())
        .observacoes(normalizado.observacoes()).build();
    aplicarEnderecos(c, normalizado.enderecos());
    c = repo.save(c);
    auditoria.registrar(AuditoriaTipo.CLIENTE_CRIADO, "Cliente cadastrado: " + c.getNome(), "CLIENTE", c.getId());
    return c;
  }

  @Transactional
  public Cliente atualizar(Long id, DadosCliente dados, boolean confirmarDuplicidade) {
    Cliente c = repo.findById(id).orElseThrow(() -> new NotFoundException("Cliente não encontrado"));
    var normalizado = normalizar(dados);
    verificarDuplicidade(normalizado, id, confirmarDuplicidade);

    c.setNome(normalizado.nome());
    c.setCpf(vazioParaNulo(normalizado.cpf()));
    c.setTelefone(vazioParaNulo(normalizado.telefone()));
    c.setEmail(normalizado.email());
    c.setObservacoes(normalizado.observacoes());
    if (normalizado.enderecos() != null) {
      aplicarEnderecos(c, normalizado.enderecos());
    }
    auditoria.registrar(AuditoriaTipo.CLIENTE_ALTERADO, "Cliente alterado: " + c.getNome(), "CLIENTE", c.getId());
    return c;
  }

  @Transactional
  public Cliente desativar(Long id) {
    Cliente c = repo.findById(id).orElseThrow(() -> new NotFoundException("Cliente não encontrado"));
    c.setAtivo(false);
    c.getEnderecos().size();
    auditoria.registrar(AuditoriaTipo.CLIENTE_ALTERADO, "Cliente desativado: " + c.getNome(), "CLIENTE", c.getId());
    return c;
  }

  // ---- internos ----

  private DadosCliente normalizar(DadosCliente d) {
    if (d.nome() == null || d.nome().isBlank()) {
      throw new NegocioException("Nome do cliente é obrigatório.");
    }
    String cpf = Documentos.somenteDigitos(d.cpf());
    if (!cpf.isEmpty() && !Documentos.cpfValido(cpf)) {
      throw new NegocioException("CPF inválido.");
    }
    String tel = Documentos.somenteDigitos(d.telefone());
    if (!tel.isEmpty() && (tel.length() < 10 || tel.length() > 13)) {
      throw new NegocioException("Telefone inválido: informe DDD e número.");
    }
    String email = d.email() == null || d.email().isBlank() ? null : d.email().trim();
    if (email != null && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
      throw new NegocioException("E-mail inválido.");
    }
    return new DadosCliente(d.nome().trim(), cpf, tel, email,
        d.observacoes() == null || d.observacoes().isBlank() ? null : d.observacoes().trim(), d.enderecos());
  }

  private void verificarDuplicidade(DadosCliente d, Long idAtual, boolean confirmar) {
    if (!d.cpf().isEmpty()) {
      var porCpf = repo.findByCpf(d.cpf()).filter(c -> !c.getId().equals(idAtual));
      if (porCpf.isPresent()) {
        throw new DuplicidadeClienteException("Já existe um cliente com este CPF: " + porCpf.get().getNome() + ".",
            List.of(resumo(porCpf.get())), true);
      }
    }
    if (!confirmar) {
      var provaveis = duplicidades(null, d.telefone(), d.nome(), idAtual);
      if (!provaveis.isEmpty()) {
        throw new DuplicidadeClienteException(
            "Existem clientes com o mesmo telefone ou nome. Confira antes de cadastrar outro.",
            provaveis.stream().map(this::resumo).toList(), false);
      }
    }
  }

  private Map<String, Object> resumo(Cliente c) {
    return Map.of("id", c.getId(), "nome", c.getNome(),
        "telefone", c.getTelefone() == null ? "" : c.getTelefone(),
        "cpf", c.getCpf() == null ? "" : c.getCpf());
  }

  private void aplicarEnderecos(Cliente c, List<DadosEndereco> novos) {
    c.getEnderecos().clear();
    if (novos == null) {
      return;
    }
    boolean temPrincipal = novos.stream().anyMatch(DadosEndereco::principal);
    int i = 0;
    for (DadosEndereco e : novos) {
      if (e.logradouro() == null || e.logradouro().isBlank() || e.cidade() == null || e.cidade().isBlank()
          || e.uf() == null || e.uf().trim().length() != 2) {
        throw new NegocioException("Endereço incompleto: informe logradouro, cidade e UF (2 letras).");
      }
      boolean principal = temPrincipal ? e.principal() && c.getEnderecos().stream().noneMatch(EnderecoCliente::isPrincipal)
          : i == 0;
      c.getEnderecos().add(EnderecoCliente.builder().cliente(c)
          .apelido(e.apelido()).cep(Documentos.somenteDigitos(e.cep()))
          .logradouro(e.logradouro().trim()).numero(e.numero()).complemento(e.complemento())
          .bairro(e.bairro()).cidade(e.cidade().trim()).uf(e.uf().trim().toUpperCase())
          .principal(principal).build());
      i++;
    }
  }

  private static String vazioParaNulo(String s) {
    return s == null || s.isBlank() ? null : s;
  }
}
