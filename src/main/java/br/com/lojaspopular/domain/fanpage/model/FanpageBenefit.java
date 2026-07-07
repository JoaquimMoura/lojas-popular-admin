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
public class FanpageBenefit {

  @Column(name = "benefit_title", length = 120)
  private String title;

  @Column(name = "benefit_description", length = 255)
  private String description;
}
