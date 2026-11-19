package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.exception.ContactValidationException;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerContactServiceTest {
    @ParameterizedTest
    @CsvSource({"011 15-2345-6789,AR,+5491123456789", "8 (912) 345-67-89,RU,+79123456789", "+7 (912) 345-67-89,AR,+79123456789", "+44 20 7946 0018,AR,+442079460018"})
    void normalizesPhone(String input, String country, String expected) {
        assertThat(PlayerContactService.normalize(input, country, "valid_user").telefono()).isEqualTo(expected);
        assertThat(PlayerContactService.normalize(input, country, "valid_user").telegramUsername()).isEqualTo("@valid_user");
    }

    @ParameterizedTest
    @CsvSource({"'',AR,PHONE_REQUIRED", "123,AR,PHONE_TOO_SHORT", "+549112345678901234567890,AR,PHONE_TOO_LONG", "hola,AR,PHONE_INVALID", "+79123456789,ZZ,PHONE_COUNTRY_INVALID"})
    void specificErrors(String phone, String country, String code) {
        assertThatThrownBy(() -> PlayerContactService.normalize(phone, country, "valid_user"))
                .isInstanceOfSatisfying(ContactValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(code);
                    assertThat(e.field()).isEqualTo("telefono");
                });
    }

    @Test
    void telegramOptionalAndInternationalClientCompatible() {
        assertThat(PlayerContactService.normalize("+79123456789", null, null).telegramUsername()).isEmpty();
        assertThat(PlayerPadel.builder().telegramUsername("@valid_user").build().hasValidContact()).isFalse();
        assertThat(PlayerPadel.builder().telefono("legacy phone").build().hasValidContact()).isTrue();
    }

    @Test
    void noMutationOnInvalidOrDuplicateTelegram() {
        var players = mock(PlayerRepository.class);
        var service = mock(PlayerService.class);
        var contacts = new PlayerContactService(players, service);
        var player = PlayerPadel.builder().id(42L).telefono("original").telegramUsername("@old_user").build();
        when(players.findById(42L)).thenReturn(Optional.of(player));
        when(service.existsByTelegramUsername("@taken_user")).thenReturn(true);
        assertThatThrownBy(() -> contacts.update(42L, "+79123456789", "RU", "bad!"))
                .isInstanceOf(ContactValidationException.class);
        assertThatThrownBy(() -> contacts.update(42L, "+79123456789", "RU", "taken_user"))
                .isInstanceOf(ContactValidationException.class);
        assertThat(player.getTelefono()).isEqualTo("original");
        assertThat(player.getTelegramUsername()).isEqualTo("@old_user");
        verify(service, never()).save(any());
    }
}
