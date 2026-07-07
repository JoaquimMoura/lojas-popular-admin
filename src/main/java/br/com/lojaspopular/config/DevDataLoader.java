package br.com.lojaspopular.config;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.com.lojaspopular.domain.catalog.enums.MaterialType;
import br.com.lojaspopular.domain.catalog.model.Categoria;
import br.com.lojaspopular.domain.catalog.model.Produto;
import br.com.lojaspopular.domain.catalog.repository.CategoriaRepository;
import br.com.lojaspopular.domain.catalog.repository.ProdutoRepository;
import br.com.lojaspopular.domain.user.Role;
import br.com.lojaspopular.domain.user.User;
import br.com.lojaspopular.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;

@Configuration
@Profile({ "local" })
@RequiredArgsConstructor
public class DevDataLoader implements CommandLineRunner {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final CategoriaRepository categoriaRepository;
  private final ProdutoRepository produtoRepository;

  @Override
  public void run(String... args) {
    seedUser("admin@loja.com",    "123456", Set.of(Role.ADMIN));
    seedUser("vendedor@loja.com", "123456", Set.of(Role.VENDEDOR));
    seedUser("cliente@loja.com",  "123456", Set.of(Role.CLIENTE));

    if (produtoRepository.count() == 0) {
      seedCatalog();
    }
  }

  private void seedUser(String email, String rawPassword, Set<Role> roles) {
    userRepository.findByEmail(email).orElseGet(() -> {
      var user = User.builder()
          .email(email)
          .passwordHash(passwordEncoder.encode(rawPassword))
          .roles(roles)
          .enabled(true)
          .build();
      System.out.println("Usuario " + email + " criado (profile local/dev).");
      return userRepository.save(user);
    });
  }

  // ── Imagens estáticas servidas pelo Spring Boot ──────────────────────────────
  private static final String IMG_DORMITORIO  = "categorias/dormitorio.png";
  private static final String IMG_COZINHA     = "categorias/cozinha.png";
  private static final String IMG_SALA        = "categorias/sala.png";
  private static final String IMG_ESCRITORIO  = "categorias/escritorio.png";
  private static final String IMG_LAVANDERIA  = "categorias/lavanderia.png";
  private static final String IMG_BANHEIRO    = "categorias/banheiro.png";

  // ── Diferenciais reutilizáveis ────────────────────────────────────────────────
  private static final List<String> DIF_GR = List.of(
      "Dobradiças com amortecedor",
      "Corrediças telescópicas de extração total",
      "Cabideiro de alumínio incluso",
      "Amplo espaço interno com prateleiras",
      "MDF 15mm resistente"
  );
  private static final List<String> DIF_COZ = List.of(
      "Corrediças telescópicas",
      "Puxadores em alumínio ou bronze",
      "Prateleiras internas reguláveis",
      "Balcões com tampo de 25mm",
      "Pés em ABS com regulagem de altura"
  );
  private static final List<String> DIF_EST = List.of(
      "Estrutura em madeira maciça",
      "Espuma D28 de alta durabilidade",
      "Tecido removível e lavável",
      "Pés cromados ou em madeira",
      "Disponível em diversas cores"
  );
  private static final List<String> DIF_HOME = List.of(
      "LED incluso com botão liga/desliga",
      "Suporte para TV até 82 polegadas",
      "Prateleiras com régua de LEDs iluminadas",
      "Gavetas com corrediças telescópicas",
      "Tampo superior de 25mm"
  );
  private static final List<String> DIF_ESC = List.of(
      "Passagem de cabos integrada",
      "Gavetas com corrediças metálicas",
      "Acabamento texturizado",
      "MDF 18mm resistente",
      "Pés reguláveis"
  );
  private static final List<String> DIF_MULTI = List.of(
      "MDF 15mm resistente",
      "Pés reguláveis",
      "Prateleiras ajustáveis",
      "Porta com amortecedor",
      "Montagem fácil com manual ilustrado"
  );

  // ── Seed principal ────────────────────────────────────────────────────────────
  private void seedCatalog() {
    System.out.println("[DevDataLoader] Semeando catalogo Popular Moveis...");

    // ── 6 CATEGORIAS ──────────────────────────────────────────────────────────
    Categoria dormitorio = cat("Dormitórios",
        "Guarda-roupas, camas, colchões, penteadeiras e tudo para o seu quarto.",
        MaterialType.MDF, IMG_DORMITORIO);

    Categoria cozinha = cat("Cozinha",
        "Cozinhas moduladas, kits compactos, mesas e cadeiras para cozinha.",
        MaterialType.MDP, IMG_COZINHA);

    Categoria sala = cat("Sala",
        "Sofás, estofados, homes, racks, painéis e mesas de centro para sua sala.",
        MaterialType.MDF, IMG_SALA);

    Categoria escritorio = cat("Escritório",
        "Escrivaninhas, mesas em L, cadeiras e estantes para home office e escritório.",
        MaterialType.MDF, IMG_ESCRITORIO);

    Categoria lavanderia = cat("Lavanderia",
        "Armários multiuso, sapateiras, passadeiras e organizadores para lavanderia.",
        MaterialType.MDF, IMG_LAVANDERIA);

    Categoria banheiro = cat("Banheiro",
        "Gabinetes, espelheiras e kits para banheiro.",
        MaterialType.MDF, IMG_BANHEIRO);

    // ══════════════════════════════════════════════════════════════════════════
    // DORMITÓRIOS
    // ══════════════════════════════════════════════════════════════════════════

    // ── Guarda-Roupas ─────────────────────────────────────────────────────────
    prod("Guarda-Roupa LA BELLE 2,67 8 Portas", "Guarda-roupa 8 portas 2,67m em MDF com espelho, corrediças telescópicas e cabideiro.", "GR-001", new BigDecimal("3299.90"), 6, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa BELGICA 2,71 6 Portas", "Guarda-roupa 6 portas 2,71m com cabideiro duplo e 3 gavetas internas. Acabamento premium.", "GR-002", new BigDecimal("2899.90"), 5, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa ROBUST 2.73 8 Portas", "Guarda-roupa 8 portas 2,73m robusto em MDF com dobradiças amortecidas.", "GR-003", new BigDecimal("3499.90"), 4, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa SOLLO 2.73 Com Espelho", "Guarda-roupa 2,73m com 2 portas espelhadas, corrediças telescópicas e cabideiro.", "GR-004", new BigDecimal("3699.90"), 4, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa GUARAPARI 2,67 8 Portas", "Guarda-roupa 8 portas 2,67m com 16 nichos e cabideiro duplo.", "GR-005", new BigDecimal("3199.90"), 5, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa ATRICE 2.67 6 Portas", "Guarda-roupa 6 portas 2,67m design moderno. Puxador em alumínio anodizado.", "GR-006", new BigDecimal("2799.90"), 6, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa GIARDINO 2.62 8 Portas", "Guarda-roupa 8 portas 2,62m com espelho e módulo de gavetas.", "GR-007", new BigDecimal("3099.90"), 4, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa ARIZONA 2.60 6 Portas", "Guarda-roupa 6 portas 2,60m clássico em MDF com gavetas e cabideiro.", "GR-008", new BigDecimal("2699.90"), 7, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa NEWTON 2.50 6 Portas", "Guarda-roupa 6 portas 2,50m com 4 gavetas internas e cabideiro duplo.", "GR-009", new BigDecimal("2499.90"), 7, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa AUSTRIA 2.28 6 Portas", "Guarda-roupa 6 portas 2,28m compacto para quartos menores.", "GR-010", new BigDecimal("2199.90"), 8, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa DIAMOND 2.41 6 Portas", "Guarda-roupa 6 portas 2,41m acabamento neve. Dobradiças com amortecedor.", "GR-011", new BigDecimal("2399.90"), 7, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa CECILIA 2,35 6 Portas", "Guarda-roupa 6 portas 2,35m feminino com 2 gavetas. Disponível em branco e bege.", "GR-012", new BigDecimal("2199.90"), 8, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa AMAZONAS 2.40 6 Portas", "Guarda-roupa 6 portas 2,40m espaçoso com módulo de gavetas integrado.", "GR-013", new BigDecimal("2299.90"), 7, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa SEVILHA 3 Portas 2.35", "Guarda-roupa 3 portas 2,35m com espelho central e cabideiro.", "GR-014", new BigDecimal("1499.90"), 10, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa DIPLOMATA 2.36 6 Portas", "Guarda-roupa 6 portas 2,36m estilo executivo com puxadores em alumínio escovado.", "GR-015", new BigDecimal("2299.90"), 7, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa DALILA 2.35 6 Portas", "Guarda-roupa 6 portas 2,35m feminino com gavetas internas e espelho.", "GR-016", new BigDecimal("2199.90"), 8, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa VIENNA 2.34 6 Portas", "Guarda-roupa 6 portas 2,34m linha europeia. Acabamento off white premium.", "GR-017", new BigDecimal("2099.90"), 8, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa BURITI 6 Portas 2.32", "Guarda-roupa 6 portas 2,32m robusto com corrediças telescópicas.", "GR-018", new BigDecimal("1999.90"), 9, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa MASTER 8 Portas 6 Gavetas 2.38", "Guarda-roupa 8 portas + 6 gavetas externas 2,38m. Máxima organização.", "GR-019", new BigDecimal("3299.90"), 5, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa TURIM 2.18 6 Portas", "Guarda-roupa 6 portas 2,18m estilo italiano com puxadores embutidos.", "GR-020", new BigDecimal("1899.90"), 9, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa NAPOLES 8 Portas 4 Gavetas 2.12", "Guarda-roupa 8 portas + 4 gavetas 2,12m com módulo espelho.", "GR-021", new BigDecimal("2799.90"), 6, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa LAS VEGAS 2.06 6 Portas", "Guarda-roupa 6 portas 2,06m com 2 gavetas e nichos organizadores.", "GR-022", new BigDecimal("1799.90"), 10, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa COLOMBO 6 Portas 9 Gavetas 1.94", "Guarda-roupa 6 portas + 9 gavetas externas 1,94m.", "GR-023", new BigDecimal("2099.90"), 8, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa LUIZA 1,92 6 Portas", "Guarda-roupa 6 portas 1,92m feminino com espelho e cabideiro.", "GR-024", new BigDecimal("1699.90"), 10, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa NEW MURANO 1.92 6 Portas", "Guarda-roupa 6 portas 1,92m linha New com dobradiças amortecidas.", "GR-025", new BigDecimal("1749.90"), 10, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa NEW SMART 1.88 6 Portas", "Guarda-roupa 6 portas 1,88m Smart com gavetas e nichos organizadores.", "GR-026", new BigDecimal("1599.90"), 11, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa NEW XANGAI 1.80 6 Portas", "Guarda-roupa 6 portas 1,80m com portar correr e abrir. Espelho incluso.", "GR-027", new BigDecimal("1549.90"), 11, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa VERONA 1.78 6 Portas", "Guarda-roupa 6 portas 1,78m italiano com puxadores cromados.", "GR-028", new BigDecimal("1499.90"), 12, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa CAPRI 3 Portas 1.74", "Guarda-roupa 3 portas 1,74m com espelho central e cabideiro duplo.", "GR-029", new BigDecimal("1199.90"), 13, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa ARUBA 1.65 6 Portas", "Guarda-roupa 6 portas 1,65m compacto com 2 gavetas internas.", "GR-030", new BigDecimal("1399.90"), 12, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa LANA 6 Portas 1.64", "Guarda-roupa 6 portas 1,64m linha Lana com acabamento premium.", "GR-031", new BigDecimal("1349.90"), 12, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa ATENAS 1.65 6 Portas", "Guarda-roupa 6 portas 1,65m puxadores dourados. Elegante.", "GR-032", new BigDecimal("1399.90"), 11, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa LITORAL 1.56 4 Portas", "Guarda-roupa 4 portas 1,56m praiano, leve e funcional.", "GR-033", new BigDecimal("999.90"), 15, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa ISABELLY 1.50 4 Portas", "Guarda-roupa 4 portas 1,50m feminino com detalhes rose.", "GR-034", new BigDecimal("949.90"), 14, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Roupeiro MAYA 6 Portas Com Espelho", "Roupeiro 6 portas com 2 portas espelhadas. MDF com puxador revestido.", "GR-035", new BigDecimal("1299.90"), 12, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa BURITI 4 Portas 1.55", "Guarda-roupa 4 portas 1,55m robusto com gavetas. MDF 15mm.", "GR-036", new BigDecimal("1099.90"), 13, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa CAPRI 2 Portas 6 Gavetas 1.16", "Guarda-roupa 2 portas + 6 gavetas externas 1,16m.", "GR-037", new BigDecimal("849.90"), 16, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa SOBRAL 1.10 4 Portas", "Guarda-roupa 4 portas 1,10m compacto. Cabideiro e prateleiras internas.", "GR-038", new BigDecimal("749.90"), 17, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa LANA 4 Portas 1.10", "Guarda-roupa 4 portas 1,10m linha Lana. Design elegante.", "GR-039", new BigDecimal("799.90"), 16, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa TOP 1.09 4 Portas", "Guarda-roupa 4 portas 1,09m econômico e funcional.", "GR-040", new BigDecimal("699.90"), 18, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa LYON 1.04 4 Portas", "Guarda-roupa 4 portas 1,04m com cabideiro de madeira revestido.", "GR-041", new BigDecimal("749.90"), 17, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa CHILE 1.03 2 Portas", "Guarda-roupa 2 portas 1,03m prático com espelho e cabideiro.", "GR-042", new BigDecimal("599.90"), 20, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa MAYA 4 Portas 1.00", "Guarda-roupa 4 portas 1,00m linha Maya. Puxador MDF revestido.", "GR-043", new BigDecimal("699.90"), 18, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa LANA 3 Portas 0.82", "Guarda-roupa 3 portas 0,82m com espelho. Quarto de solteiro.", "GR-044", new BigDecimal("549.90"), 20, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa MAYA 3 Portas 0.75", "Guarda-roupa 3 portas 0,75m linha Maya. Compacto e prático.", "GR-045", new BigDecimal("499.90"), 22, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa LIMA 0.82 2 Portas", "Guarda-roupa 2 portas 0,82m econômico com cabideiro e prateleiras.", "GR-046", new BigDecimal("449.90"), 22, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa LIGHT 0.82 2 Portas", "Guarda-roupa 2 portas 0,82m linha Light. Leve e fácil montagem.", "GR-047", new BigDecimal("429.90"), 24, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa PRINCE 0.75 2 Portas", "Guarda-roupa 2 portas 0,75m infantil/jovem com prateleiras internas.", "GR-048", new BigDecimal("399.90"), 25, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa MELISSA 1.10 6 Portas", "Guarda-roupa 6 portas 1,10m feminino com gavetas e espelho.", "GR-049", new BigDecimal("899.90"), 15, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa LOS ANGELES REFLECTA 8 Portas", "Guarda-roupa 8 portas com espelhadas Reflecta. Alto brilho.", "GR-050", new BigDecimal("3799.90"), 3, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa RIVEIRA 6 Portas", "Guarda-roupa 6 portas inspiração praiana. Acabamento amadeirado.", "GR-051", new BigDecimal("1899.90"), 8, dormitorio, IMG_DORMITORIO, DIF_GR);
    prod("Guarda-Roupa de Canto SONHO MEU", "Guarda-roupa de canto em MDF, aproveita 100% do canto do quarto.", "GR-052", new BigDecimal("2299.90"), 5, dormitorio, IMG_DORMITORIO, DIF_GR);

    // ── Penteadeiras e Cômodas ─────────────────────────────────────────────────
    prod("Penteadeira Camarim GALA com Espelho LED", "Penteadeira camarim com espelho de 1,20m, iluminação LED e 6 gavetas.", "PENT-001", new BigDecimal("1299.90"), 8, dormitorio, IMG_DORMITORIO,
        List.of("Espelho com moldura de LED", "6 gavetas com corrediças", "Tampo de 25mm", "Pés reguláveis", "MDF premium 15mm"));
    prod("Penteadeira CARISMA com Espelho", "Penteadeira com espelho central, 4 gavetas e nicho. Linha elegante.", "PENT-002", new BigDecimal("999.90"), 9, dormitorio, IMG_DORMITORIO,
        List.of("Espelho incluso", "4 gavetas internas", "Corrediças metálicas", "Design elegante", "MDF 15mm"));
    prod("Penteadeira SOFIA com Espelho e Nicho", "Penteadeira Sofia com espelho, 3 gavetas e nichos abertos para decoração.", "PENT-003", new BigDecimal("849.90"), 10, dormitorio, IMG_DORMITORIO,
        List.of("Espelho com moldura", "3 gavetas", "Nichos organizadores", "Pés em MDF", "Fácil montagem"));
    prod("Escrivaninha com Penteadeira JADE", "Multifuncional: escrivaninha + penteadeira com espelho. Ideal para quarto jovem.", "PENT-004", new BigDecimal("1099.90"), 7, dormitorio, IMG_DORMITORIO,
        List.of("Dupla função", "Espelho incluso", "Gavetas e nichos", "MDF resistente", "Design moderno"));
    prod("Escrivaninha com Penteadeira de Canto STAR", "Penteadeira de canto com escrivaninha integrada. Aproveita o canto.", "PENT-005", new BigDecimal("1199.90"), 6, dormitorio, IMG_DORMITORIO,
        List.of("Formato de canto", "Escrivaninha integrada", "Espelho incluso", "Aproveitamento máximo", "MDF 15mm"));

    // ── Mesas de Cabeceira ─────────────────────────────────────────────────────
    prod("Mesa de Cabeceira BRUNA 2 Gavetas", "Criado-mudo BRUNA com 2 gavetas e pés em madeira maciça. Branco ou nogueira.", "CAB-001", new BigDecimal("399.90"), 20, dormitorio, IMG_DORMITORIO,
        List.of("2 gavetas com corrediças", "Pés madeira maciça", "Tampo 25mm", "Branco ou nogueira", "MDF 15mm"));
    prod("Mesa de Cabeceira LUXO NEW", "Criado-mudo LUXO com 2 prateleiras abertas e porta. Design moderno.", "CAB-002", new BigDecimal("449.90"), 18, dormitorio, IMG_DORMITORIO,
        List.of("2 prateleiras abertas", "1 porta com dobradiça", "Design moderno", "MDF premium", "Fácil montagem"));
    prod("Mesa de Cabeceira MAVIE", "Criado-mudo MAVIE minimalista com nicho e gaveta. Quarto contemporâneo.", "CAB-003", new BigDecimal("499.90"), 16, dormitorio, IMG_DORMITORIO,
        List.of("Design minimalista", "Nicho + gaveta", "Pés metálicos", "MDF 15mm", "Várias cores"));
    prod("Criado-Mudo 2 Gavetas Par Branco/Nogueira", "Par de criados-mudos com 2 gavetas e 1 porta. Pés madeira maciça.", "CAB-004", new BigDecimal("599.90"), 15, dormitorio, IMG_DORMITORIO,
        List.of("Vendido em par", "2 gavetas", "1 porta com dobradiça", "Pés madeira maciça", "Branco ou nogueira"));

    // ── Cabeceiras ─────────────────────────────────────────────────────────────
    prod("Cabeceira SOFIA Estofada Casal", "Cabeceira estofada SOFIA 1,60m para cama casal. Tecido veludo.", "CABEC-001", new BigDecimal("699.90"), 12, dormitorio, IMG_DORMITORIO,
        List.of("Tecido veludo macio", "Estrutura MDF", "Para cama casal 1,40m–1,60m", "Altura 1,10m", "Fácil fixação"));
    prod("Cabeceira TIFFANY Ripada Casal", "Cabeceira ripada TIFFANY 1,60m em MDF. Ripas verticais decorativas.", "CABEC-002", new BigDecimal("649.90"), 13, dormitorio, IMG_DORMITORIO,
        List.of("Ripas verticais", "MDF 15mm", "Para cama casal", "Off white/freijó", "Design contemporâneo"));
    prod("Cabeceira ALEMANHA Painel MDF", "Cabeceira ALEMANHA painel 2,00m com nichos laterais. Funcional e moderna.", "CABEC-003", new BigDecimal("799.90"), 10, dormitorio, IMG_DORMITORIO,
        List.of("Painel 2,00m com nichos", "MDF 18mm premium", "Nichos decorativos", "Para cama casal", "Instalação parede"));
    prod("Cabeceira Casal Clássica MDF", "Cabeceira casal clássica em MDF com detalhes entalhados. Atemporal.", "CABEC-004", new BigDecimal("549.90"), 15, dormitorio, IMG_DORMITORIO,
        List.of("Design clássico", "MDF 15mm", "Acabamento liso", "Para cama casal", "Fácil montagem"));

    // ── Camas e Bases ──────────────────────────────────────────────────────────
    prod("Cama Casal VENEZA com Cabeceira MDF", "Cama casal VENEZA em MDF com cabeceira inclusa e estrado de madeira.", "CAMA-001", new BigDecimal("1099.90"), 9, dormitorio, IMG_DORMITORIO,
        List.of("Cabeceira inclusa", "Estrado madeira", "MDF 15mm", "Colchão 1,38x1,88m", "Pés em MDF"));
    prod("Cama Casal VERONA 1.38 Com Cabeceira Ripada", "Cama casal VERONA com cabeceira ripada inclusa em MDF.", "CAMA-002", new BigDecimal("1199.90"), 8, dormitorio, IMG_DORMITORIO,
        List.of("Cabeceira ripada", "Base MDF resistente", "Colchão 1,38m", "Estrado madeira", "Garantia"));
    prod("Cama Casal IRIS Design Clean", "Cama casal IRIS design clean com cabeceira slim e estrutura robusta.", "CAMA-003", new BigDecimal("999.90"), 10, dormitorio, IMG_DORMITORIO,
        List.of("Design clean", "Cabeceira slim", "MDF 15mm", "Para casal", "Fácil montagem"));
    prod("Cama Solteiro IRIS", "Cama solteiro IRIS com cabeceira moderna e estrutura MDF robusta.", "CAMA-004", new BigDecimal("699.90"), 14, dormitorio, IMG_DORMITORIO,
        List.of("Cabeceira incluída", "MDF resistente", "Colchão 0,88x1,88m", "Estrado madeira", "Design moderno"));
    prod("Cama Bibox NEW LUARA com Auxiliar", "Cama baú solteiro LUARA com base bipartida e 2 auxiliares.", "CAMA-005", new BigDecimal("1299.90"), 7, dormitorio, IMG_DORMITORIO,
        List.of("Base baú bipartida", "2 camas auxiliares", "Dobradiças resistentes", "MDF 15mm", "Colchão solteiro"));
    prod("Cama Casal PALMO Box com Cabeceira Estofada", "Cama casal box PALMO com base molas e cabeceira estofada.", "CAMA-006", new BigDecimal("1599.90"), 6, dormitorio, IMG_DORMITORIO,
        List.of("Base box com molas", "Cabeceira estofada", "Tecido removível", "Para casal", "Garantia 5 anos"));
    prod("Cama Juvenil DAKOTA Montessoriana", "Cama Montessoriana DAKOTA com laterais de proteção. MDF.", "CAMA-007", new BigDecimal("849.90"), 12, dormitorio, IMG_DORMITORIO,
        List.of("Laterais de proteção", "100% MDF", "Colchão 150x70cm", "Estrado reforçado", "Pode ser 2 camas"));
    prod("Mini Cama ANGEL com Auxiliar Infantil", "Mini cama infantil ANGEL com auxiliar dobrável. Branco ou rosé.", "CAMA-008", new BigDecimal("699.90"), 14, dormitorio, IMG_DORMITORIO,
        List.of("Auxiliar dobrável inclusa", "Pés madeira", "Colchão 150x70cm", "Branco ou rosé", "Fácil montagem"));
    prod("Base Box Casal Bipartida 1,38", "Base box bipartida para cama casal. Resistente e silenciosa.", "CAMA-009", new BigDecimal("799.90"), 11, dormitorio, IMG_DORMITORIO,
        List.of("Bipartida — fácil transporte", "Estrutura reforçada", "Tecido premium", "1,28m ou 1,38m", "Silenciosa"));

    // ── Colchões ───────────────────────────────────────────────────────────────
    prod("Colchão Casal Molas Ensacadas Premium 18cm", "Colchão casal molas pocket 1,38x1,88m. Zero efeito parceiro.", "COLCH-001", new BigDecimal("2199.90"), 8, dormitorio, IMG_DORMITORIO,
        List.of("Molas pocket", "Zero efeito parceiro", "18cm de altura", "Pillow top", "Garantia 5 anos"));
    prod("Colchão Solteiro Molas Ensacadas 18cm", "Colchão solteiro molas pocket 0,88x1,88m. Conforto diário.", "COLCH-002", new BigDecimal("1299.90"), 10, dormitorio, IMG_DORMITORIO,
        List.of("Molas pocket", "Alta densidade", "18cm", "Pillow top", "Garantia 3 anos"));
    prod("Colchão D45 Casal Ortopédico", "Colchão casal D45 ortopédico de espuma de alta densidade.", "COLCH-003", new BigDecimal("1499.90"), 9, dormitorio, IMG_DORMITORIO,
        List.of("Espuma D45 ortopédica", "Alta densidade", "Pillow top", "Antialérgico", "Garantia 3 anos"));
    prod("Colchão D33 Solteiro", "Colchão solteiro D33. Ótimo custo-benefício para uso diário.", "COLCH-004", new BigDecimal("699.90"), 15, dormitorio, IMG_DORMITORIO,
        List.of("Espuma D33", "Pillow top", "Leve", "Antialérgico", "Garantia 1 ano"));

    // ── Beliches e Infantil ────────────────────────────────────────────────────
    prod("Beliche Infantil JADE com Escada Lateral", "Beliche JADE 2 camas com escada e grade de segurança. MDF.", "INF-001", new BigDecimal("1299.90"), 8, dormitorio, IMG_DORMITORIO,
        List.of("Grade de segurança", "Escada lateral", "MDF 15mm", "Colchão solteiro", "Suporta 80kg/andar"));
    prod("Beliche MONTANA com Escrivaninha", "Beliche MONTANA com escrivaninha embaixo. Quarto pequeno.", "INF-002", new BigDecimal("1499.90"), 7, dormitorio, IMG_DORMITORIO,
        List.of("Escrivaninha integrada", "Grade de segurança", "3 degraus", "MDF resistente", "Colchão solteiro"));
    prod("Treliche TURBO 3 Andares", "Treliche TURBO 3 andares com grades em todos. Para 3 filhos.", "INF-003", new BigDecimal("2199.90"), 4, dormitorio, IMG_DORMITORIO,
        List.of("3 andares completos", "Grades em todos", "Escada lateral", "MDF reforçado", "Colchão 0,80x1,90m"));
    prod("Bicama SOFIA com Grade de Proteção", "Cama solteiro SOFIA com grade e auxiliar dobrável.", "INF-004", new BigDecimal("999.90"), 10, dormitorio, IMG_DORMITORIO,
        List.of("Auxiliar dobrável", "Grade de proteção", "MDF resistente", "Colchão 0,88m", "Fácil acesso"));

    // ══════════════════════════════════════════════════════════════════════════
    // COZINHA
    // ══════════════════════════════════════════════════════════════════════════

    prod("Cozinha Modulada SYRAH Amendoa/Chumbo 8 Módulos", "Cozinha modulada SYRAH em MDP com aéreo, balcão, paneleiro e torre quente.", "COZ-001", new BigDecimal("4299.90"), 4, cozinha, IMG_COZINHA, DIF_COZ);
    prod("Cozinha Modulada KARINE Angelin/Nude 10 Módulos", "Cozinha modulada KARINE 10 módulos com pés ABS e vidro Reflecta.", "COZ-002", new BigDecimal("4999.90"), 3, cozinha, IMG_COZINHA, DIF_COZ);
    prod("Cozinha Modulada CRIARE Freijó Ripado 3D", "Cozinha CRIARE com acabamento Ripado 3D exclusivo. Moderna.", "COZ-003", new BigDecimal("5499.90"), 3, cozinha, IMG_COZINHA, DIF_COZ);
    prod("Cozinha Modulada AMERICANA Completa", "Cozinha modulada AMERICANA 12 módulos. Múltiplas configurações.", "COZ-004", new BigDecimal("5299.90"), 3, cozinha, IMG_COZINHA, DIF_COZ);
    prod("Cozinha Modulada TEMPRANILLO Premium Ripado", "Cozinha TEMPRANILLO premium com detalhes ripados. Elegante.", "COZ-005", new BigDecimal("5799.90"), 2, cozinha, IMG_COZINHA, DIF_COZ);
    prod("Cozinha Modulada BRISA Compacta", "Cozinha BRISA 6 módulos para cozinhas menores. Moderna.", "COZ-006", new BigDecimal("3299.90"), 5, cozinha, IMG_COZINHA, DIF_COZ);
    prod("Cozinha ALBA 4 Portas Econômica", "Cozinha ALBA 4 portas em MDP. Prática para apartamentos compactos.", "COZ-007", new BigDecimal("1899.90"), 8, cozinha, IMG_COZINHA, DIF_COZ);
    prod("Cozinha CLARA Off White 6 Portas", "Cozinha CLARA off white 6 portas com aéreo e balcão.", "COZ-008", new BigDecimal("2499.90"), 6, cozinha, IMG_COZINHA, DIF_COZ);
    prod("Cozinha Completa BARCELONA Planejada", "Cozinha completa BARCELONA com todos os módulos essenciais.", "COZ-009", new BigDecimal("5999.90"), 2, cozinha, IMG_COZINHA, DIF_COZ);
    prod("Cozinha Completa ESTRELA com Torre Forno/Micro", "Cozinha ESTRELA completa com torre para forno e micro-ondas.", "COZ-010", new BigDecimal("4799.90"), 3, cozinha, IMG_COZINHA, DIF_COZ);
    prod("Cozinha Completa LAURA 6 Módulos", "Cozinha LAURA 6 módulos com balcão pia. Freijó/off white.", "COZ-011", new BigDecimal("3799.90"), 4, cozinha, IMG_COZINHA, DIF_COZ);
    prod("Cozinha Completa MILA 8 Módulos", "Cozinha MILA 8 módulos com torre paneleiro e Reflecta.", "COZ-012", new BigDecimal("4299.90"), 3, cozinha, IMG_COZINHA, DIF_COZ);
    prod("Cozinha CRISTAL com Vidro Reflecta", "Cozinha CRISTAL com portas em vidro Reflecta. Elegante.", "COZ-013", new BigDecimal("3499.90"), 4, cozinha, IMG_COZINHA, DIF_COZ);
    prod("Cozinha GIRASSOL Amarelo/Branco", "Cozinha GIRASSOL amarelo e branco. Alegra o ambiente.", "COZ-014", new BigDecimal("2799.90"), 6, cozinha, IMG_COZINHA, DIF_COZ);
    prod("Cozinha FIRENZE Italiana Ripado", "Cozinha FIRENZE estilo italiano com ripado. Sofisticada.", "COZ-015", new BigDecimal("4099.90"), 3, cozinha, IMG_COZINHA, DIF_COZ);
    prod("Cozinha ISABELLE Off White 8 Portas", "Cozinha ISABELLE off white 8 portas com puxadores bronze.", "COZ-016", new BigDecimal("3299.90"), 5, cozinha, IMG_COZINHA, DIF_COZ);
    prod("Cozinha NANDA Compacta 1.60m", "Cozinha NANDA 1,60m para apartamentos. Módulos essenciais.", "COZ-017", new BigDecimal("1999.90"), 8, cozinha, IMG_COZINHA, DIF_COZ);
    prod("Cozinha RUBI Vermelho/Branco", "Cozinha RUBI vermelho com branco. Vibrante e moderna.", "COZ-018", new BigDecimal("2599.90"), 6, cozinha, IMG_COZINHA, DIF_COZ);
    prod("Cozinha DORA Planejada 3m Completa", "Cozinha DORA 3 metros completa. A cozinha dos sonhos.", "COZ-019", new BigDecimal("5499.90"), 2, cozinha, IMG_COZINHA, DIF_COZ);
    // Kits
    prod("Kit Cozinha DUBAI 2.18 3 Peças", "Kit cozinha DUBAI 2,18m aéreo + balcão + torre quente.", "KIT-001", new BigDecimal("2199.90"), 6, cozinha, IMG_COZINHA, DIF_COZ);
    prod("Kit Cozinha SOLE 2.10 Aéreo + Balcão", "Kit cozinha SOLE 2,10m. Prático para reformas rápidas.", "KIT-002", new BigDecimal("1899.90"), 7, cozinha, IMG_COZINHA, DIF_COZ);
    prod("Kit Cozinha Compacta AMORA 1,98 3 Peças", "Kit cozinha AMORA 1,98m 3 peças. Compacto e moderno.", "KIT-003", new BigDecimal("1699.90"), 8, cozinha, IMG_COZINHA, DIF_COZ);
    prod("Kit Cozinha PITAYA 1,78 2 Peças", "Kit cozinha PITAYA 1,78m aéreo + balcão. Econômico.", "KIT-004", new BigDecimal("1299.90"), 10, cozinha, IMG_COZINHA, DIF_COZ);
    prod("Kit Cozinha SELECT 1,78 3 Peças", "Kit cozinha SELECT 1,78m com cuba inclusa. Pronto para usar.", "KIT-005", new BigDecimal("1499.90"), 9, cozinha, IMG_COZINHA, DIF_COZ);
    prod("Kit Cozinha DIAMANTE 1.63 com Cuba Inox", "Kit cozinha DIAMANTE 1,63m com cuba inox inclusa.", "KIT-006", new BigDecimal("1399.90"), 9, cozinha, IMG_COZINHA, DIF_COZ);
    prod("Kit Cozinha LA PAZ 1.60 2 Peças", "Kit cozinha LA PAZ 1,60m aéreo + balcão.", "KIT-007", new BigDecimal("1099.90"), 11, cozinha, IMG_COZINHA, DIF_COZ);
    prod("Kit Cozinha ALAMO 1,60 3 Peças", "Kit cozinha ALAMO 1,60m 3 peças. Robusto e durável.", "KIT-008", new BigDecimal("1199.90"), 10, cozinha, IMG_COZINHA, DIF_COZ);
    prod("Kit Cozinha HARMONIA 1,32 2 Peças", "Kit cozinha HARMONIA 1,32m para espaços compactos.", "KIT-009", new BigDecimal("999.90"), 12, cozinha, IMG_COZINHA, DIF_COZ);
    prod("Kit Cozinha JULY 1.37 3 Peças", "Kit cozinha JULY 1,37m com prateleiras reguláveis.", "KIT-010", new BigDecimal("1099.90"), 11, cozinha, IMG_COZINHA, DIF_COZ);
    prod("Kit Cozinha BERLIM 1.20 Aéreo + Balcão", "Kit cozinha BERLIM 1,20m estilo europeu.", "KIT-011", new BigDecimal("899.90"), 13, cozinha, IMG_COZINHA, DIF_COZ);
    prod("Kit Cozinha CORAL 8 Portas 1.20", "Kit cozinha CORAL 8 portas 1,20m econômico.", "KIT-012", new BigDecimal("799.90"), 14, cozinha, IMG_COZINHA, DIF_COZ);
    prod("Kit Cozinha CORAL 6 Portas 0.90 Compacto", "Kit cozinha CORAL 6 portas 0,90m ultra-compacto.", "KIT-013", new BigDecimal("649.90"), 16, cozinha, IMG_COZINHA, DIF_COZ);
    prod("Kit Cozinha NEW LIVIA 1.10 2 Peças", "Kit cozinha LIVIA 1,10m 2 peças renovado.", "KIT-014", new BigDecimal("849.90"), 13, cozinha, IMG_COZINHA, DIF_COZ);
    prod("Kit Cozinha BELA 4 Portas 0.60", "Mini kit cozinha BELA 0,60m. Para espaços muito reduzidos.", "KIT-015", new BigDecimal("499.90"), 18, cozinha, IMG_COZINHA, DIF_COZ);
    prod("Kit Cozinha DUDA 0.90 Aéreo + Balcão", "Kit cozinha DUDA 0,90m simples. 1 aéreo + 1 balcão.", "KIT-016", new BigDecimal("599.90"), 17, cozinha, IMG_COZINHA, DIF_COZ);
    prod("Kit Cozinha URUGUAI 0.91 2 Peças", "Kit cozinha URUGUAI 0,91m 2 peças econômico.", "KIT-017", new BigDecimal("649.90"), 16, cozinha, IMG_COZINHA, DIF_COZ);
    // Mesas cozinha / jantar
    prod("Conjunto Mesa JADE 1,36 e 6 Cadeiras", "Mesa JADE 1,36m + 6 cadeiras estofadas. Elegante e resistente.", "MESA-001", new BigDecimal("2299.90"), 5, cozinha, IMG_COZINHA,
        List.of("Mesa 1,36m MDF", "6 cadeiras estofadas", "Tampo 25mm", "Pés madeira maciça", "Família completa"));
    prod("Conjunto Mesa JADE 1,20 e 4 Cadeiras", "Mesa JADE 1,20m + 4 cadeiras. Compacto e elegante.", "MESA-002", new BigDecimal("1799.90"), 6, cozinha, IMG_COZINHA,
        List.of("Mesa 1,20m", "4 cadeiras", "Tampo MDF", "Pés madeira", "Design moderno"));
    prod("Conjunto Mesa JADE Redonda 0,75 e 4 Cadeiras", "Mesa redonda JADE 0,75m + 4 cadeiras. Cozinha compacta.", "MESA-003", new BigDecimal("1399.90"), 8, cozinha, IMG_COZINHA,
        List.of("Mesa redonda", "4 cadeiras", "Sem pontas", "Pés madeira", "Compacta"));
    prod("Conjunto Mesa GRANITO 1,20 Tampo Granito", "Mesa tampo granito real 1,20m com 6 cadeiras. Sofisticação.", "MESA-004", new BigDecimal("2799.90"), 4, cozinha, IMG_COZINHA,
        List.of("Tampo granito real", "Alta durabilidade", "Resistente manchas", "Pés ferro", "6 cadeiras"));
    prod("Conjunto Mesa DALLAS 1,60 6 Cadeiras CRISTAL", "Mesa DALLAS 1,60m amadeirada + 6 cadeiras CRISTAL.", "MESA-005", new BigDecimal("2999.90"), 4, cozinha, IMG_COZINHA,
        List.of("Mesa 1,60m", "6 cadeiras", "MDF premium", "Rústico moderno", "Pés madeira"));
    prod("Conjunto Mesa MADRI 1,70 6 Cadeiras GRECIA", "Mesa MADRI 1,70m + 6 cadeiras GRECIA para receber.", "MESA-006", new BigDecimal("3199.90"), 3, cozinha, IMG_COZINHA,
        List.of("Mesa 1,70m ampla", "6 cadeiras estofadas", "Design espanhol", "MDF 25mm", "Pés ferro"));
    prod("Mesa de Madeira Rústica Natural", "Mesa rústica em madeira maciça. Atemporal e resistente.", "MESA-007", new BigDecimal("1899.90"), 6, cozinha, IMG_COZINHA,
        List.of("Madeira maciça", "Acabamento natural", "Alta resistência", "Design rústico", "Fácil manutenção"));

    // ══════════════════════════════════════════════════════════════════════════
    // SALA
    // ══════════════════════════════════════════════════════════════════════════

    // Estofados
    prod("Estofado PARMA 2.90 com Porta USB", "Sofá PARMA 2,90m com entrada USB integrada. Moderno.", "EST-001", new BigDecimal("4299.90"), 3, sala, IMG_SALA, DIF_EST);
    prod("Estofado LIVERPOOL 2.40 3 Lugares + Chaise", "Sofá LIVERPOOL 2,40m 3 lugares com chaise. Veludo.", "EST-002", new BigDecimal("3499.90"), 4, sala, IMG_SALA, DIF_EST);
    prod("Estofado FENIX 2,40 3 Lugares", "Sofá FENIX 2,40m 3 lugares contemporâneo. Espuma D28.", "EST-003", new BigDecimal("2999.90"), 5, sala, IMG_SALA, DIF_EST);
    prod("Estofado PORTINARI 2,30 Retrátil Reclinável", "Sofá PORTINARI 2,30m retrátil e reclinável.", "EST-004", new BigDecimal("3299.90"), 4, sala, IMG_SALA, DIF_EST);
    prod("Estofado GABRIELA 2.50 3 Lugares", "Sofá GABRIELA 2,50m 3 lugares. Várias cores disponíveis.", "EST-005", new BigDecimal("2799.90"), 5, sala, IMG_SALA, DIF_EST);
    prod("Estofado CAIQUE 2.00 2 Lugares + Chaise", "Sofá CAIQUE 2,00m com chaise. Para sala pequena.", "EST-006", new BigDecimal("2499.90"), 5, sala, IMG_SALA, DIF_EST);
    prod("Estofado BARCELONA 2,10 3 Lugares", "Sofá BARCELONA 2,10m. Pés cromados, tecido premium.", "EST-007", new BigDecimal("2699.90"), 5, sala, IMG_SALA, DIF_EST);
    prod("Estofado STANLEY 2.10 com Cooler e Porta-Copos", "Sofá STANLEY com cooler e 4 porta-copos. Perfeito para receber.", "EST-008", new BigDecimal("3199.90"), 4, sala, IMG_SALA, DIF_EST);
    prod("Estofado DOUX 2,15 Sem Caixa Moderno", "Sofá DOUX 2,15m slim. Moderno e minimalista.", "EST-009", new BigDecimal("2599.90"), 5, sala, IMG_SALA, DIF_EST);
    prod("Estofado de Canto VENEZA em L Grande", "Sofá de canto VENEZA em L com 5 lugares.", "EST-010", new BigDecimal("4799.90"), 3, sala, IMG_SALA, DIF_EST);
    prod("Estofado TURQUIA Retrátil Reclinável", "Sofá TURQUIA retrátil e reclinável 3 lugares.", "EST-011", new BigDecimal("3599.90"), 4, sala, IMG_SALA, DIF_EST);
    prod("Estofado LIVING PALAZO 3 Módulos", "Sofá PALAZO 3 módulos independentes. Configure à vontade.", "EST-012", new BigDecimal("5299.90"), 2, sala, IMG_SALA, DIF_EST);
    prod("Poltrona Reclinável CLIO do Papai", "Poltrona recliner CLIO com apoio retrátil para pés.", "POLT-001", new BigDecimal("1299.90"), 8, sala, IMG_SALA,
        List.of("Reclinável manual", "Apoio retrátil pés", "Tecido suede", "Espuma D28", "Pés madeira"));
    prod("Poltrona e Namoradeira JADE 2 Peças", "Kit namoradeira JADE com 2 poltronas. Sala ou quarto.", "POLT-002", new BigDecimal("1799.90"), 6, sala, IMG_SALA,
        List.of("Kit 2 poltronas", "Veludo macio", "Pés madeira maciça", "Alta durabilidade", "Design moderno"));
    prod("Poltrona PARIS Decorativa", "Poltrona PARIS decorativa. Tecido floral ou liso.", "POLT-003", new BigDecimal("899.90"), 10, sala, IMG_SALA,
        List.of("Design decorativo", "Tecido premium", "Pés dourados ou cromados", "Leve e elegante", "Várias cores"));
    // Homes/Racks/Painéis
    prod("Home CANCÚN 2.40 Para TV 82pol LED", "Home CANCÚN 2,40m com LED e prateleiras iluminadas. TV 82pol.", "HOME-001", new BigDecimal("3199.90"), 4, sala, IMG_SALA, DIF_HOME);
    prod("Home JORDÃO 2.00 LED Ripado", "Home JORDÃO 2,00m com LED embutido e painel ripado. TV 75pol.", "HOME-002", new BigDecimal("2499.90"), 5, sala, IMG_SALA, DIF_HOME);
    prod("Home ATLANTA 2.01 Portas Ripadas 3D LED", "Home ATLANTA 2,01m portas ripadas 3D e LED. TV 65pol.", "HOME-003", new BigDecimal("2799.90"), 5, sala, IMG_SALA, DIF_HOME);
    prod("Home JURERÊ 1.8 Painel Ripado", "Home JURERÊ 1,82m com frisos decorativos e prateleira 25mm.", "HOME-004", new BigDecimal("2199.90"), 6, sala, IMG_SALA, DIF_HOME);
    prod("Home CURITIBA 1.79 Ripado Moderno", "Home CURITIBA 1,79m estilo contemporâneo com nichos.", "HOME-005", new BigDecimal("1999.90"), 7, sala, IMG_SALA, DIF_HOME);
    prod("Home Suspenso EGEU 1.79 Minimalista", "Home suspenso EGEU 1,79m. Sem pés, fixação na parede.", "HOME-006", new BigDecimal("1799.90"), 7, sala, IMG_SALA, DIF_HOME);
    prod("Home Suspenso LOA 2.17 com LED", "Home suspenso LOA 2,17m com LED e nichos amplos.", "HOME-007", new BigDecimal("2399.90"), 5, sala, IMG_SALA, DIF_HOME);
    prod("Home Suspenso SUPREME 1.80 Premium", "Home suspenso SUPREME 1,80m premium com ripado e LED.", "HOME-008", new BigDecimal("2199.90"), 6, sala, IMG_SALA, DIF_HOME);
    prod("Home TAURUS 1.60 Compacto", "Home TAURUS 1,60m para sala pequena.", "HOME-009", new BigDecimal("1499.90"), 8, sala, IMG_SALA, DIF_HOME);
    prod("Rack e Painel SIRIUS 1,60", "Conjunto rack + painel SIRIUS 1,60m. Gavetas e nichos.", "RACK-001", new BigDecimal("1499.90"), 8, sala, IMG_SALA, DIF_HOME);
    prod("Rack MIAMI Moderno", "Rack MIAMI moderno com nichos organizadores.", "RACK-002", new BigDecimal("899.90"), 12, sala, IMG_SALA, DIF_HOME);
    prod("Rack OURO PRETO 1.64 com Gavetas", "Rack OURO PRETO 1,64m 2 gavetas. Amadeirado.", "RACK-003", new BigDecimal("1099.90"), 10, sala, IMG_SALA, DIF_HOME);
    prod("Rack TITAN e Painel LEGACY 1.60 Premium", "Conjunto rack TITAN + painel LEGACY. Acabamento premium.", "RACK-004", new BigDecimal("1899.90"), 6, sala, IMG_SALA, DIF_HOME);
    prod("Rack TURIM 1.60 com Painel", "Rack TURIM 1,60m com painel integrado. Estilo italiano.", "RACK-005", new BigDecimal("1399.90"), 8, sala, IMG_SALA, DIF_HOME);
    prod("Painel ATLAS 1.63 Ripado Off White", "Painel ATLAS 1,63m com ripas verticais off white.", "PAIN-001", new BigDecimal("799.90"), 13, sala, IMG_SALA, DIF_HOME);
    prod("Painel BYTE 1.15 Minimalista", "Painel BYTE 1,15m minimalista para sala pequena.", "PAIN-002", new BigDecimal("449.90"), 16, sala, IMG_SALA, DIF_HOME);
    prod("Painel DAVOS 1.40 Clean", "Painel DAVOS 1,40m nicho central e prateleiras laterais.", "PAIN-003", new BigDecimal("599.90"), 14, sala, IMG_SALA, DIF_HOME);
    prod("Painel FOX NEW 1.20 Ripado", "Painel FOX 1,20m com ripas originais. Moderno.", "PAIN-004", new BigDecimal("499.90"), 15, sala, IMG_SALA, DIF_HOME);
    prod("Painel JERI 1.60 com Suporte TV", "Painel JERI 1,60m suporte giratório TV e nichos.", "PAIN-005", new BigDecimal("749.90"), 12, sala, IMG_SALA, DIF_HOME);
    prod("Painel MAXI 1.80 Amplo", "Painel MAXI 1,80m para sala grande. Múltiplos nichos.", "PAIN-006", new BigDecimal("899.90"), 10, sala, IMG_SALA, DIF_HOME);
    prod("Painel VERSALHES 1.80 Luxo", "Painel VERSALHES 1,80m luxo com entalhes decorativos.", "PAIN-007", new BigDecimal("1099.90"), 8, sala, IMG_SALA, DIF_HOME);
    // Mesa de centro
    prod("Mesa de Centro SLIM Pés Madeira Maciça", "Mesa SLIM MDF com tampo redondo e pés madeira maciça.", "MESA-C01", new BigDecimal("599.90"), 14, sala, IMG_SALA,
        List.of("Tampo redondo MDF", "Pés madeira maciça", "Sapatas protetoras", "Design slim", "Leve"));
    prod("Mesa de Centro PRISM Tampo Chanfrado", "Mesa PRISM tampo chanfrado 25mm e pés madeira maciça.", "MESA-C02", new BigDecimal("699.90"), 12, sala, IMG_SALA,
        List.of("Tampo chanfrado 25mm", "Cantos arredondados", "Pés madeira", "Sapatas protetoras", "MDF premium"));

    // ══════════════════════════════════════════════════════════════════════════
    // ESCRITÓRIO
    // ══════════════════════════════════════════════════════════════════════════

    prod("Escrivaninha Em L MAXTAURUS 1.50x1.20m", "Mesa em L MAXTAURUS com passagem de cabos e suporte CPU.", "ESC-001", new BigDecimal("1199.90"), 9, escritorio, IMG_ESCRITORIO, DIF_ESC);
    prod("Escrivaninha TAURUS 1.40m com Gaveta", "Mesa TAURUS 1,40m retangular com gaveta. MDF 18mm.", "ESC-002", new BigDecimal("799.90"), 12, escritorio, IMG_ESCRITORIO, DIF_ESC);
    prod("Escrivaninha MORADA com Nicho e Gaveta", "Mesa MORADA com nicho e gaveta. Home office compacto.", "ESC-003", new BigDecimal("699.90"), 13, escritorio, IMG_ESCRITORIO, DIF_ESC);
    prod("Escrivaninha OFFICE Compacta 1.20m", "Mesa OFFICE compacta 1,20m para espaços pequenos.", "ESC-004", new BigDecimal("549.90"), 15, escritorio, IMG_ESCRITORIO, DIF_ESC);
    prod("Escrivaninha 2 Gavetas Estudante", "Mesa simples 2 gavetas. Ideal para quarto de estudante.", "ESC-005", new BigDecimal("449.90"), 17, escritorio, IMG_ESCRITORIO, DIF_ESC);
    prod("Escrivaninha LION com Biblioteca Integrada", "Mesa LION com estante biblioteca integrada. Home office completo.", "ESC-006", new BigDecimal("1399.90"), 7, escritorio, IMG_ESCRITORIO, DIF_ESC);
    prod("Mesa de Computador TURIM 1.20m", "Mesa computador TURIM com nichos e suporte CPU.", "ESC-007", new BigDecimal("649.90"), 14, escritorio, IMG_ESCRITORIO, DIF_ESC);
    prod("Mesa de Computador VIENA 1.40m", "Mesa computador VIENA com gaveta e prateleira para monitor.", "ESC-008", new BigDecimal("749.90"), 12, escritorio, IMG_ESCRITORIO, DIF_ESC);
    prod("Estante Livreiro 5 Prateleiras 2.00m", "Livreiro 5 prateleiras ajustáveis 2,00m. Livros e decoração.", "ESC-009", new BigDecimal("899.90"), 11, escritorio, IMG_ESCRITORIO,
        List.of("5 prateleiras ajustáveis", "2,00m altura", "MDF 15mm", "Suporta 20kg/prateleira", "Fácil montagem"));

    // ══════════════════════════════════════════════════════════════════════════
    // LAVANDERIA
    // ══════════════════════════════════════════════════════════════════════════

    prod("Armário Multiuso JADE 4 Portas 4 Nichos", "Armário multiuso JADE 4 portas e 4 nichos. Versátil.", "MULT-001", new BigDecimal("749.90"), 13, lavanderia, IMG_LAVANDERIA, DIF_MULTI);
    prod("Armário Multiuso SOFIA 3 Portas com Gavetas", "Armário SOFIA 3 portas e gavetas internas.", "MULT-002", new BigDecimal("699.90"), 13, lavanderia, IMG_LAVANDERIA, DIF_MULTI);
    prod("Armário Multiuso GAEL Ripado Moderno", "Armário GAEL com detalhes ripados. Design moderno.", "MULT-003", new BigDecimal("799.90"), 11, lavanderia, IMG_LAVANDERIA, DIF_MULTI);
    prod("Armário Multiuso VIENA 6 Nichos Abertos", "Armário VIENA 6 nichos abertos. Design escandinavo.", "MULT-004", new BigDecimal("599.90"), 15, lavanderia, IMG_LAVANDERIA, DIF_MULTI);
    prod("Armário Multiuso SMART Compacto Econômico", "Armário SMART econômico e funcional.", "MULT-005", new BigDecimal("499.90"), 17, lavanderia, IMG_LAVANDERIA, DIF_MULTI);
    prod("Armário Multiuso MICHELE 5 Portas", "Armário MICHELE 5 portas. Muito espaço para organização.", "MULT-006", new BigDecimal("849.90"), 11, lavanderia, IMG_LAVANDERIA, DIF_MULTI);
    prod("Armário Multiuso BRIZ Suspenso", "Armário suspenso BRIZ. Fixação na parede, ganha espaço.", "MULT-007", new BigDecimal("549.90"), 15, lavanderia, IMG_LAVANDERIA, DIF_MULTI);
    prod("Armário Multiuso DUETO 2 Peças Combinadas", "Conjunto DUETO 2 peças combinadas. Flexível.", "MULT-008", new BigDecimal("999.90"), 9, lavanderia, IMG_LAVANDERIA, DIF_MULTI);
    prod("Armário Multiuso 4 Portas Econômico", "Armário 4 portas econômico. Quarto, sala ou escritório.", "MULT-009", new BigDecimal("449.90"), 18, lavanderia, IMG_LAVANDERIA, DIF_MULTI);
    prod("Sapateira com 4 Prateleiras Inclinadas", "Sapateira 4 prateleiras inclinadas. Comporta 12 pares.", "MULT-010", new BigDecimal("349.90"), 22, lavanderia, IMG_LAVANDERIA,
        List.of("4 prateleiras inclinadas", "12 pares de sapatos", "MDF 15mm", "Pés reguláveis", "Fácil montagem"));
    prod("Passadeira MIAMI para Lavanderia", "Passadeira MIAMI com suporte ferro e cesto de roupas.", "MULT-011", new BigDecimal("399.90"), 20, lavanderia, IMG_LAVANDERIA,
        List.of("Suporte para ferro", "Cesto incluso", "Dobrável", "MDF e ferro", "Resistente ao calor"));
    prod("Estante Livreiro Alta 5 Prateleiras Ajustáveis", "Estante alta 5 prateleiras ajustáveis 2m. Livros.", "MULT-012", new BigDecimal("799.90"), 12, lavanderia, IMG_LAVANDERIA,
        List.of("5 prateleiras ajustáveis", "2,00m", "MDF 15mm", "20kg/prateleira", "Livros e decoração"));

    // ══════════════════════════════════════════════════════════════════════════
    // BANHEIRO
    // ══════════════════════════════════════════════════════════════════════════

    prod("Conjunto Banheiro IBIZA Gabinete + Espelheira", "Conjunto banheiro IBIZA com gabinete sob pia e espelheira com nicho.", "BAN-001", new BigDecimal("899.90"), 10, banheiro, IMG_BANHEIRO,
        List.of("Gabinete sob pia", "Espelheira com nicho", "MDF resistente à umidade", "Pés reguláveis", "Fácil instalação"));
    prod("Conjunto Banheiro MUNIQUE Gabinete + Espelheira", "Conjunto banheiro MUNIQUE gabinete com 2 portas e espelheira ampla.", "BAN-002", new BigDecimal("1099.90"), 8, banheiro, IMG_BANHEIRO,
        List.of("Gabinete 2 portas", "Espelheira ampla", "MDF resistente", "Acabamento premium", "Pés reguláveis"));
    prod("Gabinete de Banheiro 2 Portas com Cuba", "Gabinete banheiro 2 portas com cuba embutida. Prático.", "BAN-003", new BigDecimal("699.90"), 12, banheiro, IMG_BANHEIRO,
        List.of("Cuba embutida", "2 portas com dobradiça", "MDF resistente umidade", "Pés reguláveis", "Fácil montagem"));
    prod("Espelheira de Banheiro com Nicho", "Espelheira banheiro com nicho organizador. Espelho temperado.", "BAN-004", new BigDecimal("399.90"), 18, banheiro, IMG_BANHEIRO,
        List.of("Espelho temperado", "Nicho interno", "MDF resistente", "Instalação parede", "Várias medidas"));

    System.out.println("[DevDataLoader] " + produtoRepository.count() + " produtos criados em 6 categorias.");
  }

  private Categoria cat(String nome, String descricao, MaterialType material, String imagemUrl) {
    return categoriaRepository.findByNomeIgnoreCaseAndMaterial(nome, material)
        .orElseGet(() -> categoriaRepository.save(
            Categoria.builder()
                .nome(nome)
                .descricao(descricao)
                .material(material)
                .imagemUrl(imagemUrl)
                .ativa(true)
                .build()));
  }

  private void prod(String nome, String descricao, String sku,
      BigDecimal preco, int estoque, Categoria categoria, String imagemUrl, List<String> diferenciais) {
    produtoRepository.save(Produto.builder()
        .nome(nome)
        .descricao(descricao)
        .sku(sku)
        .preco(preco)
        .estoque(estoque)
        .categoria(categoria)
        .imagemUrl(imagemUrl)
        .diferenciais(diferenciais)
        .ativa(true)
        .build());
  }
}
