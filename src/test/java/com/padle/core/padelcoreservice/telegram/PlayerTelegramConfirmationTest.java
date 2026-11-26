package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.dto.ParticipationDtos.Review;
import com.padle.core.padelcoreservice.exception.AccountException;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.enums.ParticipationStatus;
import com.padle.core.padelcoreservice.repository.ParticipationRequestRepository;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.service.TournamentParticipationService;
import com.padle.core.padelcoreservice.telegram.TelegramModels.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerTelegramConfirmationTest {
    private final UUID requestId = UUID.randomUUID();
    private final long start = 1_900_000_000L;
    private final PlayerTelegramApi api = mock(PlayerTelegramApi.class);
    private final PlayerTelegramStore store = mock(PlayerTelegramStore.class);
    private final PlayerRepository players = mock(PlayerRepository.class);
    private final ParticipationRequestRepository requests = mock(ParticipationRequestRepository.class);
    private final TournamentParticipationService participation = mock(TournamentParticipationService.class);
    private final PlayerTelegramAccess access = new PlayerTelegramAccess(PlayerTelegramPilotTest.properties(42L, 10L));
    private PlayerTelegramMessages messages;
    private PlayerPadel player;
    private PlayerTelegramPoller poller;

    @BeforeEach void setup() {
        var source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding("UTF-8");
        messages = new PlayerTelegramMessages(source);
        player = PlayerPadel.builder().id(42L).activo(true).preferredLocale("ru").build();
        when(api.available()).thenReturn(true);
        when(api.send(anyLong(), anyString(), anyList())).thenReturn(new Result("SENT", 456L));
        when(store.byUser(123L)).thenReturn(Optional.of(new Connection(42L, 123L, 123L, true)));
        when(players.findById(42L)).thenReturn(Optional.of(player));
        when(requests.findTournamentId(requestId)).thenReturn(Optional.of(10L));
        when(participation.confirmTelegram(requestId, player, start)).thenReturn(review(ParticipationStatus.CONFIRMED, false));
        var claimed = new HashSet<String>();
        when(store.claim(anyString(), eq(10L), isNull(), eq(123L)))
                .thenAnswer(call -> claimed.add(call.getArgument(0)));
        poller = poller();
    }

    private PlayerTelegramPoller poller() {
        var delivery = new PlayerTelegramDelivery(store, api, access);
        var receipts = mock(PlayerTelegramReceipts.class);
        var confirmation = new PlayerTelegramConfirmation(delivery, api, messages,
                mock(TournamentRegistrationRepository.class), receipts);
        ReflectionTestUtils.setField(confirmation, "baseUrl", "https://padel.example");
        return new PlayerTelegramPoller(api, store, mock(PlayerTelegramLinks.class), players, messages,
                delivery, participation, mock(PlayerTelegramSummary.class), access, requests, confirmation, receipts,
                mock(PlayerTelegramOnboarding.class));
    }

    private Review review(ParticipationStatus status, boolean outdated) {
        return new Review(requestId, 10L, "Fixture event", "Fixture player", status,
                false, false, false, false, outdated);
    }

    private Update callback(String callbackId, long userId, long expectedStart) {
        var message = new Message(8L, new User(999L), new Chat(userId, "private"), "Fixture request");
        return new Update(15L, null, new Callback(callbackId, new User(userId), message,
                "c:" + requestId + ":" + Long.toString(expectedStart, 36)));
    }

    @Test void confirmsWithPersistentMessageAndOnlyTwoSiteLinksInOriginal() {
        poller.handle(callback("first", 123L, start));
        verify(participation).confirmTelegram(requestId, player, start);
        verify(api).send(123L, "Участие в «Fixture event» подтверждено. Отлично, до встречи на корте!", List.of());
        verify(api).editKeyboard(123L, 8L, List.of(List.of(
                Button.link("Открыть мероприятие", "https://padel.example/torneo/10"),
                Button.link("Не смогу", "https://padel.example/players/dashboard"))));
        verify(api).acknowledge("first", messages.text("confirmed", Locale.forLanguageTag("ru")));
    }

    @Test void anotherCallbackAndRestartRepairKeyboardWithoutDuplicatingTheReceipt() {
        poller.handle(callback("first", 123L, start));
        poller().handle(callback("second", 123L, start));
        verify(api, times(1)).send(anyLong(), anyString(), anyList());
        verify(api, times(2)).editKeyboard(eq(123L), eq(8L), anyList());
        verify(store, times(2)).claim("confirmed:" + requestId + ":" + start + ":42", 10L, null, 123L);
    }

    @Test void uncertainReceiptDoesNotRepeatOrPreventKeyboardUpdate() {
        when(api.send(anyLong(), anyString(), anyList())).thenReturn(new Result("UNKNOWN", null));
        poller.handle(callback("first", 123L, start));
        poller().handle(callback("second", 123L, start));
        verify(api, times(1)).send(anyLong(), anyString(), anyList());
        verify(api, times(2)).editKeyboard(eq(123L), eq(8L), anyList());
        verify(store).finish("confirmed:" + requestId + ":" + start + ":42", new Result("UNKNOWN", null));
    }

    @Test void staleCycleOrUnconfirmedReviewCannotProduceSuccessFeedback() {
        when(participation.confirmTelegram(requestId, player, start))
                .thenReturn(review(ParticipationStatus.CONFIRMED, true), review(ParticipationStatus.PENDING, false));
        poller.handle(callback("stale", 123L, start));
        poller.handle(callback("pending", 123L, start));
        verify(api, never()).send(anyLong(), anyString(), anyList());
        verify(api, never()).editKeyboard(anyLong(), anyLong(), anyList());
    }

    @Test void rescheduledOrRejectedCallbackCannotProduceSuccessFeedback() {
        when(participation.confirmTelegram(requestId, player, start - 60))
                .thenThrow(new AccountException(HttpStatus.CONFLICT, "PARTICIPATION_RESPONSE_UNAVAILABLE"));
        poller.handle(callback("rescheduled", 123L, start - 60));
        verify(api).acknowledge("rescheduled", messages.text("unavailable", player.getLocale()));
        verify(api, never()).send(anyLong(), anyString(), anyList());
        verify(api, never()).editKeyboard(anyLong(), anyLong(), anyList());
    }

    @Test void otherPlayerOrEventCannotChangeParticipationOrMessages() {
        when(store.byUser(789L)).thenReturn(Optional.of(new Connection(99L, 789L, 789L, true)));
        when(players.findById(99L)).thenReturn(Optional.of(PlayerPadel.builder().id(99L).activo(true).build()));
        poller.handle(callback("stranger", 789L, start));
        when(requests.findTournamentId(requestId)).thenReturn(Optional.of(11L));
        poller.handle(callback("other-event", 123L, start));
        verifyNoInteractions(participation);
        verify(api, never()).send(anyLong(), anyString(), anyList());
        verify(api, never()).editKeyboard(anyLong(), anyLong(), anyList());
    }

    @ParameterizedTest
    @CsvSource({"es,Participación confirmada en Fixture event. Genial,No puedo",
                "ru,Участие в «Fixture event» подтверждено. Отлично,Не смогу",
                "en,Your participation in Fixture event is confirmed. Great,Cannot attend"})
    void successAndCancellationLabelFollowThePlayerLocale(String language, String prefix, String cancel) {
        player.setPreferredLocale(language);
        poller.handle(callback("localized", 123L, start));
        verify(api).send(eq(123L), startsWith(prefix), eq(List.of()));
        assertThat(messages.text("cancel", player.getLocale())).isEqualTo(cancel);
    }
}
