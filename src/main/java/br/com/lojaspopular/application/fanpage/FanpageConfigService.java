package br.com.lojaspopular.application.fanpage;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.lojaspopular.domain.fanpage.model.FanpageBenefit;
import br.com.lojaspopular.domain.fanpage.model.FanpageCollectionBlock;
import br.com.lojaspopular.domain.fanpage.model.FanpageConfig;
import br.com.lojaspopular.domain.fanpage.repository.FanpageConfigRepository;
import br.com.lojaspopular.web.fanpage.dto.FanpageConfigRequest;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FanpageConfigService {

        private final FanpageConfigRepository repository;

        @Transactional
        public FanpageConfig getCurrent() {
                return repository.findFirstByOrderByIdAsc().orElseGet(this::createDefault);
        }

        @Transactional
        public FanpageConfig update(FanpageConfigRequest request) {
                FanpageConfig config = repository.findFirstByOrderByIdAsc().orElseGet(this::createDefault);

                config.setHeroTitle(request.heroTitle());
                config.setHeroSubtitle(request.heroSubtitle());
                config.setHeroDescription(request.heroDescription());
                config.setHeroBannerUrl(request.heroBannerUrl());
                config.setHeroPrimaryLabel(request.heroPrimaryLabel());
                config.setHeroPrimaryMessage(request.heroPrimaryMessage());
                config.setHeroSecondaryLabel(request.heroSecondaryLabel());
                config.setHeroSecondaryUrl(request.heroSecondaryUrl());

                config.getBenefits().clear();
                if (request.benefits() != null) {
                        request.benefits().stream()
                                        .filter(dto -> dto != null && dto.title() != null && !dto.title().isBlank())
                                        .map(dto -> FanpageBenefit.builder()
                                                        .title(dto.title())
                                                        .description(dto.description())
                                                        .build())
                                        .forEach(config.getBenefits()::add);
                }

                config.getCollections().clear();
                if (request.collections() != null) {
                        request.collections().stream()
                                        .filter(dto -> dto != null && dto.name() != null && !dto.name().isBlank())
                                        .map(dto -> FanpageCollectionBlock.builder()
                                                        .name(dto.name())
                                                        .description(dto.description())
                                                        .imageUrl(dto.imageUrl())
                                                        .build())
                                        .forEach(config.getCollections()::add);
                }

                config.setOffersTitle(request.offersTitle());
                config.setOffersDescription(request.offersDescription());
                config.setCombosTitle(request.combosTitle());
                config.setCombosDescription(request.combosDescription());
                config.setCtaTitle(request.ctaTitle());
                config.setCtaDescription(request.ctaDescription());

                config.getCtaHighlights().clear();
                if (request.ctaHighlights() != null) {
                        request.ctaHighlights().stream()
                                        .filter(line -> line != null && !line.isBlank())
                                        .forEach(config.getCtaHighlights()::add);
                }

                return repository.save(config);
        }

        private FanpageConfig createDefault() {
                FanpageConfig config = FanpageConfig.builder()
                                .heroTitle("Popular Móveis — móveis planejados com preço popular")
                                .heroSubtitle("Sua casa renovada sem complicacoes")
                                .heroDescription(
                                                "Kits completos de sala, cozinha, quarto e escritorio com condicoes especiais. Entregamos e montamos em tempo recorde para voce usar no mesmo dia.")
                                .heroPrimaryLabel("Quero ser atendido agora")
                                .heroPrimaryMessage("Olá! Vi as ofertas na fanpage e quero montar meu ambiente.")
                                .heroSecondaryLabel("Ver toda a coleção")
                                .heroSecondaryUrl("/loja")
                                .offersTitle("Ofertas imperdíveis da semana")
                                .offersDescription(
                                                "Conjuntos selecionados para renovar sua casa com descontos exclusivos da fanpage.")
                                .combosTitle("Combos planejados")
                                .combosDescription(
                                                "Kits completos com armrios, mesas, cadeiras e acessorios que cabem no seu espaco e no seu bolso.")
                                .ctaTitle("Atendimento personalizado")
                                .ctaDescription(
                                                "Conte para a nossa equipe como e o seu comodo e receba um projeto com os móveis perfeitos para o seu espaco.")
                                .build();

                List<FanpageBenefit> benefits = List.of(
                                FanpageBenefit.builder().title("Montagem rapida")
                                                .description("Equipe propria e agenda flexivel.")
                                                .build(),
                                FanpageBenefit.builder().title("Entrega expressa")
                                                .description("Despacho em ate 48h na capital.")
                                                .build(),
                                FanpageBenefit.builder().title("Pagamento facilitado")
                                                .description("Parcelamento em ate 12x sem juros.")
                                                .build());
                config.getBenefits().addAll(benefits);

                List<FanpageCollectionBlock> collections = List.of(
                                FanpageCollectionBlock.builder()
                                                .name("Salas Planejadas")
                                                .description("Paineis, racks e sofas que deixam o ambiente completo.")
                                                .imageUrl(
                                                                "https://images.unsplash.com/photo-1484100356142-db6ab6244067?auto=format&fit=crop&w=1200&q=80")
                                                .build(),
                                FanpageCollectionBlock.builder()
                                                .name("Cozinhas Compactas")
                                                .description("Kits completos com armrios, balcoes e torres.")
                                                .imageUrl(
                                                                "https://images.unsplash.com/photo-1588853431081-02531f0526eb?auto=format&fit=crop&w=1200&q=80")
                                                .build(),
                                FanpageCollectionBlock.builder()
                                                .name("Quartos Aconchegantes")
                                                .description("Guarda-roupas, camas box e cabeceras combinando.")
                                                .imageUrl(
                                                                "https://images.unsplash.com/photo-1505692794403-55b39b05e08c?auto=format&fit=crop&w=1200&q=80")
                                                .build());
                config.getCollections().addAll(collections);

                config.getCtaHighlights().addAll(new ArrayList<>(List.of(
                                "Plantão de segunda a sexta-feira das 8h às 19hs;",
                                "Simulacao de pagamento em tempo real com as melhores condicoes.")));

                return repository.save(config);
        }
}
