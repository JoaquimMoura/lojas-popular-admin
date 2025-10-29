package br.com.lojaspopular.application.realtime;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class RealtimeBus {

  private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

  public SseEmitter subscribe(long timeoutMillis) {
    SseEmitter emitter = new SseEmitter(timeoutMillis);
    emitters.add(emitter);
    emitter.onCompletion(() -> emitters.remove(emitter));
    emitter.onTimeout(() -> emitters.remove(emitter));
    return emitter;
  }

  public void publish(String eventName, Object payload) {
    emitters.forEach(em -> {
      try {
        em.send(SseEmitter.event().name(eventName).data(payload));
      } catch (IOException e) {
        em.complete();
        emitters.remove(em);
      }
    });
  }
}
