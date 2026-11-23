package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.PlayingSideDtos;
import com.padle.core.padelcoreservice.util.SecurityUtils;
import com.padle.core.padelcoreservice.exception.AccountException;
import com.padle.core.padelcoreservice.mapper.PlayingSideMapper;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PlayingSideService {
    private final PlayerRepository players;
    private final PlayingSideMapper mapper;

    public PlayingSideDtos.State visit(Authentication authentication) {
        var player = locked(authentication);
        Instant now = Instant.now();
        boolean ask = player.getPlayingSide() == null && (player.getPlayingSidePromptAfter() == null
                || !player.getPlayingSidePromptAfter().isAfter(now));
        // Отмечаем сам показ: обновление страницы и второй браузер не открывают ещё один опрос.
        if (ask) player.setPlayingSidePromptAfter(now.plus(Duration.ofHours(24)));
        return mapper.state(player, ask);
    }

    public PlayingSideDtos.State answer(Authentication authentication, PlayingSideDtos.Answer answer) {
        var player = locked(authentication);
        if (answer.playingSide() == null || (answer.nivelJugador() != null && !Nivel.isPlayerLevel(answer.nivelJugador()))) {
            throw new AccountException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_FIELD", "playingSide");
        }
        player.setPlayingSide(answer.playingSide());
        if (answer.nivelJugador() != null) player.setNivelJugador(answer.nivelJugador());
        player.setPlayingSidePromptAfter(null);
        return mapper.state(player, false);
    }

    public void defer(Authentication authentication) {
        var player = locked(authentication);
        if (player.getPlayingSide() == null) player.setPlayingSidePromptAfter(Instant.now().plus(Duration.ofHours(24)));
    }

    private PlayerPadel locked(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated())
            throw new AccountException(HttpStatus.UNAUTHORIZED, "SESSION_REQUIRED");
        var player = SecurityUtils.extractPlayer(authentication.getPrincipal());
        if (player == null) throw new AccountException(HttpStatus.FORBIDDEN, "PLAYER_REQUIRED");
        return players.lockPlayers(List.of(player.getId())).stream().filter(PlayerPadel::isActivo).findFirst()
                .orElseThrow(() -> new AccountException(HttpStatus.UNAUTHORIZED, "SESSION_REQUIRED"));
    }
}
