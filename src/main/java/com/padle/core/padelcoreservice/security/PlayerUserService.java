package com.padle.core.padelcoreservice.security;

import com.padle.core.padelcoreservice.mapper.PlayerPrincipalMapper;
import com.padle.core.application.identity.PlayerProfiles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerUserService {

    private final PlayerProfiles players;

    @Transactional(readOnly = true)
    public PlayerPrincipal loadUserByUsername(String email) throws UsernameNotFoundException {
        log.debug("Loading player by email: {}", email);

        var login = players.findForLogin(email)
                .orElseThrow(() -> new UsernameNotFoundException("Player not found with email: " + email));

        if (!login.profile().activo()) {
            throw new UsernameNotFoundException("Player account is not active: " + email);
        }

        return PlayerPrincipalMapper.INSTANCE.toPrincipal(login.profile(), login.passwordHash());
    }
}
