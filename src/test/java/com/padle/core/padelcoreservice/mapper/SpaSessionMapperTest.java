package com.padle.core.padelcoreservice.mapper;

import com.padle.core.padelcoreservice.dto.SpaSessionResponse;
import com.padle.core.padelcoreservice.dto.SpaSessionUserType;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpaSessionMapperTest {

    private final SpaSessionMapper mapper = Mappers.getMapper(SpaSessionMapper.class);

    @Test
    void playerMappingPreservesStringsAndQualifiesOnlyLocale() {
        var player = PlayerPadel.builder()
                .id(41L)
                .nombre("Ana")
                .apellido("Ruiz")
                .email("ana@example.com")
                .telefono("+5491112345678")
                .preferredLocale("de")
                .build();

        assertThat(mapper.fromPlayer(player, List.of("ROLE_PLAYER")))
                .isEqualTo(new SpaSessionResponse(
                        true,
                        SpaSessionUserType.PLAYER,
                        41L,
                        "Ana Ruiz",
                        "ana@example.com",
                        List.of("ROLE_PLAYER"),
                        null,
                        true,
                        null
                ));
    }

    @Test
    void ownerMappingPreservesExactContract() {
        var owner = Owner.builder()
                .id(52L)
                .firstName("Club")
                .lastName("Admin")
                .email("club@example.com")
                .role(OwnerRole.CLUB_ADMIN)
                .clubId(73L)
                .build();

        assertThat(mapper.fromOwner(owner, List.of("ROLE_CLUB_ADMIN")))
                .isEqualTo(new SpaSessionResponse(
                        true,
                        SpaSessionUserType.OWNER,
                        52L,
                        "Club Admin",
                        "club@example.com",
                        List.of("ROLE_CLUB_ADMIN"),
                        73L,
                        null,
                        null
                ));
    }
}
