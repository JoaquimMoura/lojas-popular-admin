package br.com.lojaspopular.web.admin;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import br.com.lojaspopular.application.realtime.RealtimeBus;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/stream")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class RealtimeController {

	private final RealtimeBus realtimeBus;

	@GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter subscribe() {
		// 30 minutos
		return realtimeBus.subscribe(30 * 60 * 1000L);
	}
}
