package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.service.TelegramPipelineService;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Внутренний эндпоинт, который опрашивает scripts/telegram-relay/relay.py перед каждым fire.
 * Защищён общим секретом в заголовке (не пользовательской авторизацией — это не запрос пользователя,
 * а сервер-сервер вызов с того же хоста), см. .claude/GIT_WORKFLOW.md §8.
 */
@RestController
@RequestMapping("/api/internal/telegram-pipeline")
@Slf4j
public class TelegramPipelineStatusController {

    private final TelegramPipelineService telegramPipelineService;
    private final String sharedSecret;

    public TelegramPipelineStatusController(TelegramPipelineService telegramPipelineService,
                                             @Value("${pipeline.shared-secret:}") String sharedSecret) {
        this.telegramPipelineService = telegramPipelineService;
        this.sharedSecret = sharedSecret;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Boolean>> status(
            @RequestHeader(value = "X-Internal-Secret", required = false) String providedSecret) {
        if (sharedSecret.isEmpty() || !sharedSecret.equals(providedSecret)) {
            log.warn("Telegram pipeline status check: неверный или отсутствующий X-Internal-Secret");
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(Map.of("paused", telegramPipelineService.isPaused()));
    }
}
