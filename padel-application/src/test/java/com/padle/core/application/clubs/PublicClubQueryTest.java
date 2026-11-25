package com.padle.core.application.clubs;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PublicClubQueryTest {
    @Test
    void preservesEmptyReadAndAbsenceWithoutInventingWebErrors() {
        var query = new PublicClubQuery(port(List.of()));
        assertThat(query.clubs()).isEmpty();
        assertThat(query.club(0L)).isEmpty();
        assertThat(query.club(-1L)).isEmpty();
    }

    @Test
    void preservesReadModelAndNullablePublicFields() {
        var club = new PublicClub(1L, "Клуб", null, null, null, null, null, null);
        var query = new PublicClubQuery(port(List.of(club)));
        assertThat(query.clubs()).containsExactly(club);
        assertThat(query.club(1L)).contains(club);
        assertThat(query.club(2L)).isEmpty();
    }

    private ClubReadPort port(List<PublicClub> clubs) {
        return new ClubReadPort() {
            @Override
            public List<PublicClub> findActiveOrderedByNameAndId() {
                return clubs;
            }

            @Override
            public Optional<PublicClub> findActiveById(Long id) {
                return clubs.stream().filter(club -> club.id().equals(id)).findFirst();
            }
        };
    }
}
