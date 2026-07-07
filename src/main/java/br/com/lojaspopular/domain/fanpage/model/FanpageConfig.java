package br.com.lojaspopular.domain.fanpage.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "fanpage_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FanpageConfig {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(length = 160)
  private String heroTitle;

  @Column(length = 160)
  private String heroSubtitle;

  @Column(length = 500)
  private String heroDescription;

  private String heroBannerUrl;

  @Column(length = 100)
  private String heroPrimaryLabel;

  @Column(length = 255)
  private String heroPrimaryMessage;

  @Column(length = 100)
  private String heroSecondaryLabel;

  @Column(length = 255)
  private String heroSecondaryUrl;

  @Builder.Default
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "fanpage_benefits", joinColumns = @JoinColumn(name = "fanpage_id"))
  @OrderColumn(name = "ordem")
  private List<FanpageBenefit> benefits = new ArrayList<>();

  @Builder.Default
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "fanpage_collections", joinColumns = @JoinColumn(name = "fanpage_id"))
  @OrderColumn(name = "ordem")
  private List<FanpageCollectionBlock> collections = new ArrayList<>();

  @Column(length = 160)
  private String offersTitle;

  @Column(length = 255)
  private String offersDescription;

  @Column(length = 160)
  private String combosTitle;

  @Column(length = 255)
  private String combosDescription;

  @Column(length = 160)
  private String ctaTitle;

  @Column(length = 255)
  private String ctaDescription;

  @Builder.Default
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "fanpage_cta_highlights", joinColumns = @JoinColumn(name = "fanpage_id"))
  @OrderColumn(name = "ordem")
  private List<String> ctaHighlights = new ArrayList<>();

  private LocalDateTime updatedAt;

  @PrePersist
  @PreUpdate
  void onSave() {
    updatedAt = LocalDateTime.now();
  }
}
