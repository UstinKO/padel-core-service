package com.padle.core.padelcoreservice.repository;

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
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LFPT-0679: {@code PlayerRepository.searchPartnerCandidates} — поиск партнёра по телефону
 * (в разных форматах записи) и по Telegram username (с/без '@', без учёта регистра), в
 * дополнение к уже существующему поиску по имени/фамилии/email.
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
class PlayerRepositorySearchPartnerCandidatesTest {

    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private TournamentRepository tournamentRepository;
    @Autowired
    private TournamentRegistrationRepository registrationRepository;
    @Autowired
    private ClubRepository clubRepository;

    // tournamentId, для которого заведомо нет регистраций — ветка "уже зарегистрирован
    // на этот турнир" в проверяемом запросе остаётся пустой и не влияет на результат.
    private static final Long NO_REGISTRATIONS_TOURNAMENT_ID = -1L;
    private static final Long EXCLUDE_NONE = -1L;

    @Test
    void buscaPorTelefono_normalizaFormatosDiferentes() {
        PlayerPadel player = playerRepository.saveAndFlush(buildPlayer("+5491122334455", null));

        List<PlayerPadel> porFormatoSinCodigoPais = search("01122334455");
        List<PlayerPadel> porFormatoSoloDigitos = search("91122334455");

        assertThat(porFormatoSinCodigoPais).extracting(PlayerPadel::getId).contains(player.getId());
        assertThat(porFormatoSoloDigitos).extracting(PlayerPadel::getId).contains(player.getId());
    }

    @Test
    void buscaPorTelefono_consultaCorta_noDevuelveATodosLosQueTienenTelefono() {
        PlayerPadel conTelefono = playerRepository.saveAndFlush(buildPlayer("+5491199998888", null));

        // Menos de 4 dígitos: la rama de teléfono no debe activarse (ver
        // PlayerService.extractPhoneDigits) — una consulta corta y ajena al nombre
        // no debe devolver a todo jugador que simplemente tenga un teléfono.
        List<PlayerPadel> resultado = search("88");

        assertThat(resultado).extracting(PlayerPadel::getId).doesNotContain(conTelefono.getId());
    }

    @Test
    void buscaPorTelegram_sinArrobaYSinDistincionDeMayusculas() {
        PlayerPadel player = playerRepository.saveAndFlush(
                buildPlayer(null, "@Ivan_Buscable_" + UUID.randomUUID().toString().substring(0, 8)));

        String username = player.getTelegramUsername();
        List<PlayerPadel> conArroba = search(username);
        List<PlayerPadel> sinArroba = search(username.substring(1));
        List<PlayerPadel> minusculas = search(username.toLowerCase());

        assertThat(conArroba).extracting(PlayerPadel::getId).contains(player.getId());
        assertThat(sinArroba).extracting(PlayerPadel::getId).contains(player.getId());
        assertThat(minusculas).extracting(PlayerPadel::getId).contains(player.getId());
    }

    @Test
    void buscaPorNombre_siguenFuncionandoComoAntes() {
        String apellidoUnico = "Buscable" + UUID.randomUUID().toString().substring(0, 8);
        PlayerPadel player = playerRepository.saveAndFlush(buildPlayerWithName("Pedro", apellidoUnico));

        assertThat(search(apellidoUnico)).extracting(PlayerPadel::getId).contains(player.getId());
    }

    @Test
    void excluyeAlPropioJugadorYAlYaRegistradoEnElTorneo() {
        String apellidoUnico = "Excluible" + UUID.randomUUID().toString().substring(0, 8);
        PlayerPadel buscador = playerRepository.saveAndFlush(buildPlayerWithName("Ana", apellidoUnico));
        PlayerPadel yaRegistrado = playerRepository.saveAndFlush(buildPlayerWithName("Luis", apellidoUnico));
        PlayerPadel candidatoValido = playerRepository.saveAndFlush(buildPlayerWithName("Marco", apellidoUnico));

        Club club = clubRepository.save(Club.builder()
                .nombre("Club " + apellidoUnico)
                .direccion("Av. Test 123")
                .isActive(true)
                .build());
        Tournament tournament = tournamentRepository.save(Tournament.builder()
                .clubId(club.getId())
                .nombre("Torneo " + apellidoUnico)
                .fechaInicio(LocalDateTime.now().plusDays(1).toLocalDate())
                .horaInicio(LocalDateTime.now().plusDays(1).toLocalTime())
                .generoFormato(GenderFormat.MIXTO)
                .categoriaNivel(Nivel.C7)
                .tipo(TournamentType.AMERICANO)
                .modalidad(Modalidad.INDIVIDUAL)
                .cupoMax(8)
                .precio(BigDecimal.ZERO)
                .estado(TournamentStatus.REGISTRO_ABIERTO)
                .contactoOrganizador("test@example.com")
                .isActive(true)
                .build());
        registrationRepository.save(TournamentRegistration.builder()
                .tournament(tournament)
                .player(yaRegistrado)
                .status(RegistrationStatus.CONFIRMED)
                .build());

        List<PlayerPadel> resultado = playerRepository.searchPartnerCandidates(
                apellidoUnico, tournament.getId(), buscador.getId(),
                extractPhoneDigitsForTest(apellidoUnico), apellidoUnico.replace("@", ""));

        assertThat(resultado).extracting(PlayerPadel::getId)
                .contains(candidatoValido.getId())
                .doesNotContain(buscador.getId(), yaRegistrado.getId());
    }

    private List<PlayerPadel> search(String query) {
        return playerRepository.searchPartnerCandidates(
                query, NO_REGISTRATIONS_TOURNAMENT_ID, EXCLUDE_NONE,
                extractPhoneDigitsForTest(query), query.replace("@", ""));
    }

    // Misma lógica que PlayerService.extractPhoneDigits — duplicada aquí a propósito para no
    // acoplar el test a la visibilidad de un método privado de otra capa.
    private static String extractPhoneDigitsForTest(String query) {
        String digits = query.replaceAll("\\D", "");
        if (digits.length() < 4) {
            return "";
        }
        return digits.length() > 10 ? digits.substring(digits.length() - 10) : digits;
    }

    private PlayerPadel buildPlayer(String telefono, String telegramUsername) {
        return PlayerPadel.builder()
                .nombre("Test")
                .apellido("Player")
                .email("search-" + UUID.randomUUID() + "@example.com")
                .passwordHash("$2a$10$fake")
                .nivelJugador(Nivel.SIN_ESPECIFICAR)
                .preferredLocale("es")
                .telefono(telefono)
                .telegramUsername(telegramUsername)
                .activo(true)
                .emailConfirmado(true)
                .build();
    }

    private PlayerPadel buildPlayerWithName(String nombre, String apellido) {
        return PlayerPadel.builder()
                .nombre(nombre)
                .apellido(apellido)
                .email("search-" + UUID.randomUUID() + "@example.com")
                .passwordHash("$2a$10$fake")
                .nivelJugador(Nivel.SIN_ESPECIFICAR)
                .preferredLocale("es")
                .activo(true)
                .emailConfirmado(true)
                .build();
    }
}
