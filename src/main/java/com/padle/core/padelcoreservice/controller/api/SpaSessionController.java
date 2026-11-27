package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.dto.SpaSessionResponse;
import com.padle.core.padelcoreservice.exception.UnsupportedSpaPrincipalException;
import com.padle.core.padelcoreservice.mapper.SpaSessionMapper;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.security.PlayerPrincipal;
import com.padle.core.application.identity.PlayerProfiles;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class SpaSessionController {

    private final SpaSessionMapper sessionMapper;
    private final PlayerProfiles players;

    @GetMapping("/session")
    public ResponseEntity<SpaSessionResponse> session(Authentication authentication) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(sessionResponse(authentication));
    }

    public SpaSessionResponse sessionResponse(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return SpaSessionResponse.anonymous();
        }

        var roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .toList();
        var principal = authentication.getPrincipal();
        if (principal instanceof PlayerPrincipal player) {
            return players.findById(player.getId())
                    .map(profile -> sessionMapper.fromPlayer(profile, roles))
                    .orElseGet(() -> sessionMapper.fromPrincipal(player, roles));
        }
        if (principal instanceof Owner owner) {
            return sessionMapper.fromOwner(owner, roles);
        }
        if ("anonymousUser".equals(principal)) {
            return SpaSessionResponse.anonymous();
        }
        throw new UnsupportedSpaPrincipalException();
    }
}
