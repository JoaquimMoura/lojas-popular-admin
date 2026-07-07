package br.com.lojaspopular.web.fanpage.dto;

import java.time.LocalDateTime;
import java.util.List;

import br.com.lojaspopular.domain.fanpage.model.FanpageBenefit;
import br.com.lojaspopular.domain.fanpage.model.FanpageCollectionBlock;
import br.com.lojaspopular.domain.fanpage.model.FanpageConfig;

public record FanpageConfigResponse(
    Long id,
    String heroTitle,
    String heroSubtitle,
    String heroDescription,
    String heroBannerUrl,
    String heroPrimaryLabel,
    String heroPrimaryMessage,
    String heroSecondaryLabel,
    String heroSecondaryUrl,
    List<FanpageBenefitResponse> benefits,
    List<FanpageCollectionResponse> collections,
    String offersTitle,
    String offersDescription,
    String combosTitle,
    String combosDescription,
    String ctaTitle,
    String ctaDescription,
    List<String> ctaHighlights,
    LocalDateTime updatedAt) {

  public static FanpageConfigResponse fromEntity(FanpageConfig entity) {
    return new FanpageConfigResponse(
        entity.getId(),
        entity.getHeroTitle(),
        entity.getHeroSubtitle(),
        entity.getHeroDescription(),
        entity.getHeroBannerUrl(),
        entity.getHeroPrimaryLabel(),
        entity.getHeroPrimaryMessage(),
        entity.getHeroSecondaryLabel(),
        entity.getHeroSecondaryUrl(),
        entity.getBenefits().stream().map(FanpageBenefitResponse::from).toList(),
        entity.getCollections().stream().map(FanpageCollectionResponse::from).toList(),
        entity.getOffersTitle(),
        entity.getOffersDescription(),
        entity.getCombosTitle(),
        entity.getCombosDescription(),
        entity.getCtaTitle(),
        entity.getCtaDescription(),
        entity.getCtaHighlights(),
        entity.getUpdatedAt());
  }

  public record FanpageBenefitResponse(
      String title,
      String description) {
    private static FanpageBenefitResponse from(FanpageBenefit benefit) {
      return new FanpageBenefitResponse(benefit.getTitle(), benefit.getDescription());
    }
  }

  public record FanpageCollectionResponse(
      String name,
      String description,
      String imageUrl) {
    private static FanpageCollectionResponse from(FanpageCollectionBlock block) {
      return new FanpageCollectionResponse(block.getName(), block.getDescription(), block.getImageUrl());
    }
  }
}
