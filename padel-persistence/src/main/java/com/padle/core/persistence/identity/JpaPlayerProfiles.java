package com.padle.core.persistence.identity;

import com.padle.core.application.identity.PlayerContacts;
import com.padle.core.application.identity.PlayerIdentity;
import com.padle.core.application.identity.PlayerProfiles;
import com.padle.core.application.tournament.Nivel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JpaPlayerProfiles implements PlayerProfiles, PlayerContacts {

    private final PlayerRepository players;
    private final PlayerProfileMapper mapper;

    @Override
    public Optional<Profile> findById(Long id) {
        return players.findById(id).map(mapper::toProfile);
    }

    @Override
    public Optional<Login> findForLogin(String email) {
        return players.findByEmail(email).map(player -> new Login(mapper.toProfile(player), player.getPasswordHash()));
    }

    @Override
    public boolean existsActiveByIdentity(PlayerIdentity identity) {
        return players.existsByIdAndEmailAndActivoTrue(identity.id(), identity.email());
    }

    @Override
    public boolean existsByEmail(String email) {
        return players.existsByEmail(email);
    }

    @Override
    public boolean existsByTelegramUsername(String username) {
        return players.existsByTelegramUsernameIgnoreCase(username);
    }

    @Override
    @Transactional
    public Profile update(Long id, ProfileUpdate update) {
        var player = players.getReferenceById(id);
        mapper.update(update, player);
        return mapper.toProfile(player);
    }

    @Override
    @Transactional
    public boolean updateActiveLevel(Long id, Nivel nivel, LocalDateTime updatedAt) {
        return players.actualizarNivelActivo(id, nivel, updatedAt) == 1;
    }

    @Override
    public Optional<String> claimPhoneKey(String phone) {
        return players.claimPhoneKey(phone);
    }

    @Override
    @Transactional
    public void acquireContactLock(String key) {
        players.acquireContactLock(key);
    }

    @Override
    public List<Long> findActiveIdsByClaimPhoneKey(String key) {
        return players.findClaimPhoneMatches(key).stream().map(PlayerPadel::getId).toList();
    }

    @Override
    public boolean existsByPhone(String phone) {
        return players.existsByTelefono(phone);
    }
}
