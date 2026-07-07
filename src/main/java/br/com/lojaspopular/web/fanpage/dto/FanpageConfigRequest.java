package br.com.lojaspopular.web.fanpage.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record FanpageConfigRequest(
    @NotBlank String heroTitle,
    String heroSubtitle,
    String heroDescription,
    String heroBannerUrl,
    @NotBlank String heroPrimaryLabel,
    String heroPrimaryMessage,
    String heroSecondaryLabel,
    String heroSecondaryUrl,
    List<@Valid FanpageBenefitDto> benefits,
    List<@Valid FanpageCollectionDto> collections,
    String offersTitle,
    String offersDescription,
    String combosTitle,
    String combosDescription,
    String ctaTitle,
    String ctaDescription,
    List<String> ctaHighlights) {

  public record FanpageBenefitDto(
      @NotBlank String title,
      String description) {
  }

  public record FanpageCollectionDto(
      @NotBlank String name,
      String description,
      String imageUrl) {
  }
}
