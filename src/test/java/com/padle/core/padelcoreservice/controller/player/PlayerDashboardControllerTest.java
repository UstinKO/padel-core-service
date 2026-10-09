package com.padle.core.padelcoreservice.controller.player;

import com.padle.core.padelcoreservice.exception.InvalidStateException;
import com.padle.core.padelcoreservice.exception.ResourceNotFoundException;
import com.padle.core.padelcoreservice.exception.TournamentRegistrationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// LFPT-0518: классификация серьёзности Telegram-алерта на регистрации — бизнес-исключение
// TournamentService vs непредвиденная ошибка. Чистая функция, без Spring-контекста.
class PlayerDashboardControllerTest {

    @Test
    void isBusinessRegistrationError_excepcionesDeNegocioConocidas_sonErrorDeNegocio() {
        assertThat(PlayerDashboardController.isBusinessRegistrationError(
                new TournamentRegistrationException("Ya estás registrado en este torneo"))).isTrue();
        assertThat(PlayerDashboardController.isBusinessRegistrationError(
                new InvalidStateException("Tournament is not active"))).isTrue();
        assertThat(PlayerDashboardController.isBusinessRegistrationError(
                new ResourceNotFoundException("Registro no encontrado"))).isTrue();
    }

    @Test
    void isBusinessRegistrationError_excepcionImprevista_noEsErrorDeNegocio() {
        assertThat(PlayerDashboardController.isBusinessRegistrationError(new NullPointerException())).isFalse();
        assertThat(PlayerDashboardController.isBusinessRegistrationError(new RuntimeException("boom"))).isFalse();
    }
}
