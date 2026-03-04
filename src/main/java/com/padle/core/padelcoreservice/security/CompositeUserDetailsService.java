package com.padle.core.padelcoreservice.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@Primary
@RequiredArgsConstructor
@Slf4j
public class CompositeUserDetailsService implements UserDetailsService {

    private final PlayerUserService playerUserService; // Изменили имя
    private final OwnerUserService ownerUserService;   // Изменили имя

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.info("Attempting to load user: {}", username);

        // Сначала пытаемся найти владельца
        try {
            UserDetails owner = ownerUserService.loadUserByUsername(username);
            log.info("Owner found: {}", username);
            return owner;
        } catch (UsernameNotFoundException e) {
            log.debug("Owner not found: {}, trying player", username);
        }

        // Если владелец не найден, ищем игрока
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