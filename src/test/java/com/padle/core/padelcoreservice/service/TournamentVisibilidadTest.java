package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.model.enums.TournamentVisibility;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LFPT-491: Tournament.visibilidad — закрытые мероприятия (SOLO_POR_ENLACE) не попадают
 * в публичные списки/расписание/поиск, но остаются полностью доступны по прямой ссылке
 * (по id) и видны в админке независимо от режима.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.mail.username=test@example.com",
        "spring.mail.password=test-password",
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "recaptcha.site-key=test-site-key",
        "recaptcha.secret-key=test-secret-key",
})
@Transactional
class TournamentVisibilidadTest {

    @Autowired
    private TournamentService tournamentService;
    @Autowired
    private ClubRepository clubRepository;

    @Test
    void createTournament_sinVisibilidadEnDto_persistePublico() {
        Long clubId = createClub();
        TournamentDto dto = buildDto(clubId, TournamentType.AMERICANO);
        dto.setVisibilidad(null);

        TournamentDto created = tournamentService.createTournament(dto, 1L);

        assertThat(created.getVisibilidad()).isEqualTo(TournamentVisibility.PUBLICO);
    }

    @Test
    void createTournament_conSoloPorEnlace_persisteSoloPorEnlace() {
        Long clubId = createClub();
        TournamentDto dto = buildDto(clubId, TournamentType.KING_OF_COURT);
        dto.setVisibilidad(TournamentVisibility.SOLO_POR_ENLACE);

        TournamentDto created = tournamentService.createTournament(dto, 1L);

        assertThat(created.getVisibilidad()).isEqualTo(TournamentVisibility.SOLO_POR_ENLACE);
    }

    @Test
    void updateTournament_cambiaVisibilidadDePublicoASoloPorEnlaceYVuelta() {
        Long clubId = createClub();
        TournamentDto created = tournamentService.createTournament(
                buildDto(clubId, TournamentType.AMERICANO_TEAMS), 1L);
        assertThat(created.getVisibilidad()).isEqualTo(TournamentVisibility.PUBLICO);

        Owner superAdmin = Owner.builder().id(1L).role(OwnerRole.SUPER_ADMIN).build();

        TournamentDto toUnlistedDto = buildDto(clubId, TournamentType.AMERICANO_TEAMS);
        toUnlistedDto.setVisibilidad(TournamentVisibility.SOLO_POR_ENLACE);
        TournamentDto unlisted = tournamentService
                .updateTournament(created.getId(), toUnlistedDto, superAdmin)
                .orElseThrow();
        assertThat(unlisted.getVisibilidad()).isEqualTo(TournamentVisibility.SOLO_POR_ENLACE);

        TournamentDto toPublicDto = buildDto(clubId, TournamentType.AMERICANO_TEAMS);
        toPublicDto.setVisibilidad(TournamentVisibility.PUBLICO);
        TournamentDto backToPublic = tournamentService
                .updateTournament(created.getId(), toPublicDto, superAdmin)
                .orElseThrow();
        assertThat(backToPublic.getVisibilidad()).isEqualTo(TournamentVisibility.PUBLICO);
    }

    @Test
    void listasPublicas_excluyenSoloPorEnlace_peroIncluyenPublico() {
        Long clubId = createClub();

        TournamentDto publico = tournamentService.createTournament(
                buildDto(clubId, TournamentType.AMERICANO), 1L);

        TournamentDto soloEnlaceDto = buildDto(clubId, TournamentType.AMERICANO);
        soloEnlaceDto.setVisibilidad(TournamentVisibility.SOLO_POR_ENLACE);
        TournamentDto soloEnlace = tournamentService.createTournament(soloEnlaceDto, 1L);

        assertThat(idsOf(tournamentService.getAllPublicTournaments()))
                .contains(publico.getId())
                .doesNotContain(soloEnlace.getId());

        assertThat(idsOf(tournamentService.getPublicTournamentsByClub(clubId)))
                .contains(publico.getId())
                .doesNotContain(soloEnlace.getId());

        assertThat(idsOf(tournamentService.getUpcomingTournaments()))
                .contains(publico.getId())
                .doesNotContain(soloEnlace.getId());

        assertThat(idsOf(tournamentService.getAllActiveTournaments()))
                .contains(publico.getId())
                .doesNotContain(soloEnlace.getId());

        assertThat(idsOf(tournamentService.getActiveTournamentsForHome()))
                .contains(publico.getId())
                .doesNotContain(soloEnlace.getId());

        assertThat(idsOf(tournamentService.getVisibleTournamentsForPlayer()))
                .contains(publico.getId())
                .doesNotContain(soloEnlace.getId());

        assertThat(idsOf(tournamentService.getTournamentsByStatus(publico.getEstado())))
                .contains(publico.getId())
                .doesNotContain(soloEnlace.getId());

        assertThat(idsOf(tournamentService.searchTournaments(clubId, null, null, null, null)))
                .contains(publico.getId())
                .doesNotContain(soloEnlace.getId());
    }

    @Test
    void listaAdmin_getAllTournaments_incluyeAmbasVisibilidades() {
        Long clubId = createClub();
        TournamentDto publico = tournamentService.createTournament(
                buildDto(clubId, TournamentType.AMERICANO), 1L);

        TournamentDto soloEnlaceDto = buildDto(clubId, TournamentType.AMERICANO);
        soloEnlaceDto.setVisibilidad(TournamentVisibility.SOLO_POR_ENLACE);
        TournamentDto soloEnlace = tournamentService.createTournament(soloEnlaceDto, 1L);

        assertThat(idsOf(tournamentService.getAllTournaments()))
                .contains(publico.getId(), soloEnlace.getId());
        assertThat(idsOf(tournamentService.getTournamentsByClub(clubId)))
                .contains(publico.getId(), soloEnlace.getId());
    }

    @Test
    void accesoDirectoPorId_funcionaIgualParaSoloPorEnlace() {
        Long clubId = createClub();
        TournamentDto soloEnlaceDto = buildDto(clubId, TournamentType.AMERICANO);
        soloEnlaceDto.setVisibilidad(TournamentVisibility.SOLO_POR_ENLACE);
        TournamentDto soloEnlace = tournamentService.createTournament(soloEnlaceDto, 1L);

        assertThat(tournamentService.getActiveTournamentById(soloEnlace.getId())).isPresent();
        assertThat(tournamentService.getTournamentDtoById(soloEnlace.getId())).isPresent();
    }

    private List<Long> idsOf(List<TournamentDto> dtos) {
        return dtos.stream().map(TournamentDto::getId).toList();
    }

    private Long createClub() {
        String suffix = "LFPT491-" + UUID.randomUUID();
        return clubRepository.save(Club.builder().nombre("Club " + suffix).isActive(true).build()).getId();
    }

    private TournamentDto buildDto(Long clubId, TournamentType tipo) {
        return TournamentDto.builder()
                .clubId(clubId)
                .fechaInicio(LocalDate.now().plusDays(7))
                .horaInicio(LocalTime.of(10, 0))
                .generoFormato(GenderFormat.MASCULINO)
                .categoriaNivel(Nivel.C6.name())
                .tipo(tipo)
                .modalidad(Modalidad.INDIVIDUAL)
                .cupoMax(16)
                .precio(BigDecimal.TEN)
                .moneda("ARS")
                .contactoOrganizador("test@example.com")
                .build();
    }
}
