package com.padle.core.padelcoreservice.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@Slf4j
public class CompositeUserDetailsService implements UserDetailsService {

    private final PlayerUserService playerUserService;
    private final OwnerUserService ownerUserService;

    public CompositeUserDetailsService(PlayerUserService playerUserService,
                                       OwnerUserService ownerUserService) {
        this.playerUserService = playerUserService;
        this.ownerUserService = ownerUserService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.info("Attempting to load user: {}", username);

        try {
            UserDetails owner = ownerUserService.loadUserByUsername(username);
            log.info("Owner found: {}", username);
            return owner;
        } catch (UsernameNotFoundException e) {
            log.debug("Owner not found: {}, trying player", username);
        }

        try {
            UserDetails player = playerUserService.loadUserByUsername(username);
            log.info("Player found: {}", username);
            return player;
        } catch (UsernameNotFoundException e) {
            log.error("User not found: {}", username);
            throw new UsernameNotFoundException("Usuario no encontrado con email: " + username);
        }
    }
}