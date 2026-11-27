package com.padle.core.application.clubs;

import java.util.List;
import java.util.Optional;

/** Возвращает только активные клубы, упорядочивая список по имени и затем по ID. */
public interface ClubReadPort {
    List<PublicClub> findActiveOrderedByNameAndId();

    Optional<PublicClub> findActiveById(Long id);

    long countActive();
}
