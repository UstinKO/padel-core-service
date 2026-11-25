package com.padle.core.application.clubs;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class PublicClubQuery {
    private final ClubReadPort clubs;

    public PublicClubQuery(ClubReadPort clubs) {
        this.clubs = clubs;
    }

    public List<PublicClub> clubs() {
        return clubs.findActiveOrderedByNameAndId();
    }

    public Optional<PublicClub> club(Long id) {
        return clubs.findActiveById(id);
    }
}
