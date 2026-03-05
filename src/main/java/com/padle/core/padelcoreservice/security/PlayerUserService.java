package com.padle.core.padelcoreservice.security;

import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerUserService { // Убрали implements UserDetailsService

    private final PlayerRepository playerRepository;

    @Transactional(readOnly = true)
    public PlayerPadel loadUserByUsername(String email) throws UsernameNotFoundException {
        log.debug("Loading player by email: {}", email);

        PlayerPadel player = playerRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Player not found with email: " + email));

        if (!player.isActivo()) {
            throw new UsernameNotFoundException("Player account is not active: " + email);
        }

        return player;
    }
}