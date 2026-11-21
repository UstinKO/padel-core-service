package com.padle.core.padelcoreservice.controller.view.americano;

import com.padle.core.padelcoreservice.dto.americano.AmericanoMatchDto;
import com.padle.core.padelcoreservice.dto.americano.AmericanoRoundDto;
import com.padle.core.padelcoreservice.dto.americano.TeamAmericanoConfigDto;
import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.service.americano.TeamAmericanoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import com.padle.core.padelcoreservice.model.enums.RoundRobinScoringMode;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * То же самое, что {@code TeamPlayoffGuestPartnerDisplayTest}, но для формата
 * Team Americano (round-robin) — команда с гостевым (незарегистрированным) вторым игроком
 * не должна терять партнёра в отображении матчей публичной страницы турнира.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.mail.username=test@example.com",
        "spring.mail.password=test-password",
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "recaptcha.site-key=test-site-key",
        "recaptcha.secret-key=test-secret-key",
})
@Transactional
class TeamAmericanoGuestPartnerDisplayTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TeamAmericanoViewController controller;
    @Autowired
    private TeamAmericanoService teamAmericanoService;
    @Autowired
    private TournamentRepository tournamentRepository;
    @Autowired
    private ClubRepository clubRepository;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private TournamentRegistrationRepository registrationRepository;

    @Test
    void viewTournament_showsGuestPartnerName_whenSecondPlayerHasNoAccount() {
        Long tournamentId = createTournament();

        PlayerPadel guestTeamCaptain = createPlayer(tournamentId, "Registered", "Captain");
        registerPairWithGuestPartner(tournamentId, guestTeamCaptain, "Invitado", "SinCuenta", "+5491100000000");

        PlayerPadel opponent1 = createPlayer(tournamentId, "Opponent", "One");
        PlayerPadel opponent2 = createPlayer(tournamentId, "Opponent", "Two");
        registerFullyRegisteredPair(tournamentId, opponent1, opponent2);

        TeamAmericanoConfigDto config = new TeamAmericanoConfigDto();
        config.setCourts(1);
        config.setPointsPerMatch(24);
        teamAmericanoService.initialize(tournamentId, config);

        Model model = new ExtendedModelMap();
        controller.viewTournament(tournamentId, model, null);

        @SuppressWarnings("unchecked")
        List<AmericanoRoundDto> rounds = (List<AmericanoRoundDto>) model.getAttribute("rounds");
        assertThat(rounds).isNotEmpty();

        AmericanoMatchDto match = rounds.get(0).getMatches().get(0);

        boolean team1IsGuestTeam = "Registered Captain".equals(match.getTeam1Player1Name());
        String guestSideName = team1IsGuestTeam ? match.getTeam1Player2Name() : match.getTeam2Player2Name();
        Long guestSideId = team1IsGuestTeam ? match.getTeam1Player2Id() : match.getTeam2Player2Id();

        assertThat(guestSideName)
                .as("гостевой партнёр должен быть виден, даже без аккаунта на сайте")
                .isEqualTo("Invitado SinCuenta");
        assertThat(guestSideId).isNull();

        String registeredSideP1 = team1IsGuestTeam ? match.getTeam2Player1Name() : match.getTeam1Player1Name();
        String registeredSideP2 = team1IsGuestTeam ? match.getTeam2Player2Name() : match.getTeam1Player2Name();
        assertThat(registeredSideP1).isEqualTo("Opponent One");
        assertThat(registeredSideP2).isEqualTo("Opponent Two");
    }

    @ParameterizedTest
    @CsvSource({
            "5,2,5,ru,SETS,В этом раунде отдыхает:",
            "5,2,5,es,SETS,Descansa en esta ronda:",
            "5,2,5,en,POINTS,Resting this round:",
            "4,2,0,ru,SETS,В этом раунде отдыхает:",
            "5,1,30,ru,SETS,В этом раунде отдыхает:",
            "2,1,0,en,POINTS,Resting this round:"
    })
    void publicRoundsShowEveryRestingPairWithoutChangingAfterResults(
            int pairs, int courts, int expectedResting, String language, String mode, String label) throws Exception {
        Long tournamentId = createTournament();
        PlayerPadel captain = createPlayer(tournamentId, "Registered", "Captain");
        registerPairWithGuestPartner(tournamentId, captain, "Invitado", "SinCuenta", "+5491100000000");
        for (int i = 1; i < pairs; i++) {
            registerFullyRegisteredPair(tournamentId,
                    createPlayer(tournamentId, "Pair" + i, "First"),
                    createPlayer(tournamentId, "Pair" + i, "Second"));
        }
        var config = new TeamAmericanoConfigDto();
        config.setCourts(courts);
        config.setPointsPerMatch(24);
        config.setScoringMode(RoundRobinScoringMode.valueOf(mode));
        teamAmericanoService.initialize(tournamentId, config);
        String before = publicHtml(tournamentId, language);
        var restingBefore = restingTeams(before);
        assertThat(restingBefore).hasSize(expectedResting);
        assertThat(before).contains("Invitado SinCuenta").doesNotContain("data-rr-score");
        if (expectedResting > 0) {
            assertThat(before).contains(label);
            assertThat(restingBefore.stream().distinct().count()).isEqualTo(pairs);
        } else {
            assertThat(before).doesNotContain(label);
        }
        Model model = new ExtendedModelMap();
        controller.viewTournament(tournamentId, model, null);
        @SuppressWarnings("unchecked")
        var rounds = (List<AmericanoRoundDto>) model.getAttribute("rounds");
        int firstScore = mode.equals("SETS") ? 6 : 14;
        int secondScore = mode.equals("SETS") ? 3 : 10;
        teamAmericanoService.submitMatchResult(rounds.get(0).getMatches().get(0).getId(), firstScore, secondScore);
        assertThat(restingTeams(publicHtml(tournamentId, language)))
                .containsExactlyInAnyOrderElementsOf(restingBefore);
        rounds.stream().flatMap(round -> round.getMatches().stream())
                .forEach(match -> teamAmericanoService.submitMatchResult(match.getId(), firstScore, secondScore));
        String completed = publicHtml(tournamentId, language);
        var roundTags = Pattern.compile("<details\\b[^>]*>").matcher(completed).results()
                .map(result -> result.group()).filter(tag -> tag.contains("rr-round")).toList();
        assertThat(roundTags).hasSize(rounds.size()).allSatisfy(tag -> assertThat(tag).contains("open"));
        assertThat(completed).contains("rr-public-pairing");
        if (mode.equals("SETS")) {
            var header = Pattern.compile("<thead>(.*?)</thead>", Pattern.DOTALL).matcher(completed);
            assertThat(header.find()).isTrue();
            assertThat(Pattern.compile("<th\\b").matcher(header.group(1)).results().count()).isEqualTo(4);
        }
    }

    private String publicHtml(Long tournamentId, String language) throws Exception {
        return mockMvc.perform(get("/tournaments/team-americano/" + tournamentId).param("lang", language))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private List<String> restingTeams(String html) {
        return Pattern.compile("data-rr-rest-team=\"(\\d+)\"").matcher(html).results()
                .map(result -> result.group(1)).toList();
    }

    private Long createTournament() {
        String suffix = "LFPT360TA-" + UUID.randomUUID();

        Club club = clubRepository.save(Club.builder()
                .nombre("Club " + suffix)
                .isActive(true)
                .build());

        Tournament tournament = tournamentRepository.save(Tournament.builder()
                .clubId(club.getId())
                .nombre("Torneo " + suffix)
                .fechaInicio(LocalDate.now().plusDays(7))
                .horaInicio(LocalTime.of(10, 0))
                .generoFormato(GenderFormat.MIXTO)
                .categoriaNivel(Nivel.C7)
                .tipo(TournamentType.AMERICANO)
                .modalidad(Modalidad.DOBLES)
                .cupoMax(8)
                .precio(BigDecimal.ZERO)
                .estado(TournamentStatus.CERRADO)
                .contactoOrganizador("test@example.com")
                .isActive(true)
                .build());

        return tournament.getId();
    }

    private PlayerPadel createPlayer(Long tournamentId, String nombre, String apellido) {
        String emailSuffix = tournamentId + "-" + UUID.randomUUID();
        return playerRepository.save(PlayerPadel.builder()
                .nombre(nombre)
                .apellido(apellido)
                .email("lfpt360ta-" + emailSuffix + "@example.com")
                .passwordHash("$2b$12$test")
                .activo(true)
                .emailConfirmado(true)
                .oauth2User(false)
                .preferredLocale("es")
                .nivelJugador(Nivel.C9)
                .build());
    }

    /** Главный игрок зарегистрирован, партнёр — гостевой (не зарегистрирован на сайте). */
    private void registerPairWithGuestPartner(Long tournamentId, PlayerPadel captain,
                                               String partnerFirstName, String partnerLastName, String partnerPhone) {
        Tournament tournament = tournamentRepository.findById(tournamentId).orElseThrow();
        registrationRepository.save(TournamentRegistration.builder()
                .tournament(tournament)
                .player(captain)
                .registrationDate(LocalDateTime.now())
                .status(RegistrationStatus.CONFIRMED)
                .isDoubleRegistration(true)
                .mainPlayerId(captain.getId())
                .partnerFirstName(partnerFirstName)
                .partnerLastName(partnerLastName)
                .partnerPhone(partnerPhone)
                .build());
    }

    /** Оба игрока пары зарегистрированы на сайте — две строки регистрации с общим mainPlayerId. */
    private void registerFullyRegisteredPair(Long tournamentId, PlayerPadel main, PlayerPadel partner) {
        Tournament tournament = tournamentRepository.findById(tournamentId).orElseThrow();
        registrationRepository.save(TournamentRegistration.builder()
                .tournament(tournament)
                .player(main)
                .registrationDate(LocalDateTime.now())
                .status(RegistrationStatus.CONFIRMED)
                .isDoubleRegistration(true)
                .mainPlayerId(main.getId())
                .partner(partner)
                .build());
        registrationRepository.save(TournamentRegistration.builder()
                .tournament(tournament)
                .player(partner)
                .registrationDate(LocalDateTime.now())
                .status(RegistrationStatus.CONFIRMED)
                .isDoubleRegistration(true)
                .mainPlayerId(main.getId())
                .build());
    }
}
