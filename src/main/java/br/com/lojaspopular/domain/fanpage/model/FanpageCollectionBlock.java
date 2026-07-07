package br.com.lojaspopular.domain.fanpage.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FanpageCollectionBlock {

  @Column(name = "collection_name", length = 120)
  private String name;

  @Column(name = "collection_description", length = 255)
  private String description;

  @Column(name = "collection_image_url")
  private String imageUrl;
}
