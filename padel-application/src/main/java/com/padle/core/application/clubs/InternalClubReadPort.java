package com.padle.core.application.clubs;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** Читает данные клуба для событий, регистрации и уведомлений независимо от его активности. */
public interface InternalClubReadPort {
    Optional<ClubDetails> findById(Long id);

    Map<Long, ClubDetails> findByIds(Collection<Long> ids);
}
