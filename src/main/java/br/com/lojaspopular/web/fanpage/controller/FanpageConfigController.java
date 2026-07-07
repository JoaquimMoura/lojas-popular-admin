package br.com.lojaspopular.web.fanpage.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.lojaspopular.application.fanpage.FanpageConfigService;
import br.com.lojaspopular.web.fanpage.dto.FanpageConfigRequest;
import br.com.lojaspopular.web.fanpage.dto.FanpageConfigResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/fanpage")
@RequiredArgsConstructor
public class FanpageConfigController {

  private final FanpageConfigService service;

  @GetMapping
  public ResponseEntity<FanpageConfigResponse> get() {
    var config = service.getCurrent();
    return ResponseEntity.ok(FanpageConfigResponse.fromEntity(config));
  }

  @PreAuthorize("hasRole('ADMIN')")
  @PutMapping
  public ResponseEntity<FanpageConfigResponse> update(@Valid @RequestBody FanpageConfigRequest request) {
    var updated = service.update(request);
    return ResponseEntity.ok(FanpageConfigResponse.fromEntity(updated));
  }
}
