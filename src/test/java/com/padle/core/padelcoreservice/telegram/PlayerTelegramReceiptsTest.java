package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.exception.AccountException;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.receipts.*;
import com.padle.core.padelcoreservice.receipts.ReceiptModels.*;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.telegram.TelegramModels.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.support.ResourceBundleMessageSource;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerTelegramReceiptsTest {
    private final ReceiptRegistrations registrations = mock(ReceiptRegistrations.class);
    private final ReceiptStore receipts = mock(ReceiptStore.class);
    private final ReceiptIntake intake = mock(ReceiptIntake.class);
    private final PlayerTelegramStore connections = mock(PlayerTelegramStore.class);
    private final PlayerRepository players = mock(PlayerRepository.class);
    private final PlayerTelegramFiles files = mock(PlayerTelegramFiles.class);
    private final PlayerTelegramApi api = mock(PlayerTelegramApi.class);
    private final PlayerTelegramDelivery delivery = mock(PlayerTelegramDelivery.class);
    private final PlayerPadel player = PlayerPadel.builder().id(42L).nombre("Учебный").apellido("Игрок")
            .email("fixture@example.test").activo(true).preferredLocale("ru").build();
    private final UUID uploadId = UUID.randomUUID();
    private PlayerTelegramReceipts handler;
    @BeforeEach void setup() {
        var source = new ResourceBundleMessageSource(); source.setBasename("i18n/messages"); source.setDefaultEncoding("UTF-8");
        handler = new PlayerTelegramReceipts(registrations,receipts,intake,connections,players,files,api,delivery,
                new PlayerTelegramMessages(source));
        when(registrations.enabled()).thenReturn(true);
        when(connections.byUser(123)).thenReturn(Optional.of(new Connection(42,123,123,true)));
        when(connections.connection(42)).thenReturn(Optional.of(new Connection(42,123,123,true)));
        when(players.findById(42L)).thenReturn(Optional.of(player));
    }
    private TournamentRegistration row(long id, String name) {
        return TournamentRegistration.builder().id(id).player(player).cancellationVersion(0L)
                .tournament(Tournament.builder().id(id+10).nombre(name).fechaInicio(LocalDate.of(2030,1,1)).horaInicio(LocalTime.NOON).build()).build();
    }
    private Callback choose(long registration) {
        return new Callback("fixture-callback",new User(123),new Message(5,new User(99),new Chat(123,"private"),"choose"),
                "q:"+uploadId.toString().replace("-","")+":"+Long.toString(registration,36)+":0");
    }
    @Test void evenAnUnsolicitedFileWithOneEventRequiresAnExplicitChoiceBeforeDownload() {
        var row = row(100,"Событие А");
        when(registrations.choices(42)).thenReturn(List.of(row));
        when(intake.receive(7,42,123,"fixture-file")).thenReturn(new Upload(uploadId,42,123,"fixture-file",null,false));
        handler.message(7,new Message(1,new User(123),new Chat(123,"private"),null,List.of(),
                new Document("fixture-file","application/pdf",100L)));
        verifyNoInteractions(files);
        verify(intake,never()).attach(any(),anyLong(),anyLong(),anyLong(),anyLong(),any());
        @SuppressWarnings("unchecked") ArgumentCaptor<List<List<Button>>> buttons = ArgumentCaptor.forClass(List.class);
        verify(delivery).sendOnce(eq("receipt-choice:7"),isNull(),isNull(),eq(123L),anyString(),buttons.capture());
        assertThat(buttons.getValue()).singleElement().satisfies(rowButtons ->
                assertThat(rowButtons.get(0).callbackData()).isEqualTo(choose(100).data()));
    }
    @Test void aForeignOrExpiredUploadCannotTriggerAFileDownload() {
        when(receipts.upload(uploadId,42,123,false)).thenThrow(ReceiptRegistrations.unavailable());
        handler.callback(choose(100));
        verifyNoInteractions(files,intake);
        verify(delivery).sendOnce(startsWith("receipt-error:"),isNull(),isNull(),eq(123L),anyString(),eq(List.of()));
    }
    @Test void aSuccessfulChoiceAttachesToTheChosenCycleAndRemovesItsSelectionButtons() {
        var row = row(100,"Событие А");
        var receiptId = UUID.randomUUID();
        when(receipts.upload(uploadId,42,123,false)).thenReturn(new Upload(uploadId,42,123,"fixture-file",null,false));
        when(registrations.require(100,42,0)).thenReturn(row);
        var file = new ReceiptModels.File("application/pdf",new byte[]{1});
        when(files.download("fixture-file")).thenReturn(file);
        when(intake.attach(uploadId,42,123,100,0,file)).thenReturn(new Receipt(receiptId,100,0,42,"Игрок","Событие А",
                "application/pdf",State.PENDING,OffsetDateTime.now(),null,null,null,true));
        handler.callback(choose(100));
        verify(intake).attach(uploadId,42,123,100,0,file);
        verify(delivery).sendOnce(eq("receipt-received:"+uploadId),isNull(),isNull(),eq(123L),contains("на проверку"),eq(List.of()));
        verify(api).editKeyboard(123,5,List.of());
    }
    @Test void unsupportedDocumentsAreExplainedWithoutPersistingOrDownloadingThem() {
        handler.message(8,new Message(1,new User(123),new Chat(123,"private"),null,List.of(),
                new Document("fixture-file","text/html",100L)));
        verifyNoInteractions(files,intake);
        verify(delivery).sendOnce(eq("receipt-invalid:8"),isNull(),isNull(),eq(123L),contains("JPEG/PNG"),eq(List.of()));
    }
    @Test void stoppedConnectionsAndDisabledFeatureDoNotReceiveOrDownloadReceipts() {
        when(connections.byUser(123)).thenReturn(Optional.empty());
        handler.callback(choose(100));
        verifyNoInteractions(files,intake,delivery);
    }
}
