package com.padle.core.application.clubs;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ClubAdminReadPort {
    List<ClubProfile> findAll();

    List<ClubProfile> findActive();

    Optional<ClubProfile> findProfileById(Long id);

    Optional<ClubProfile> findByName(String name);

    List<ClubProfile> findByCity(String city);

    List<ClubProfile> search(String term);

    List<ClubProfile> findAllOrderedByName();

    List<ClubProfile> findActiveOrderedByName();

    boolean existsByName(String name);

    boolean existsActiveById(Long id);

    Set<Long> findExistingIds(Set<Long> ids);
}
