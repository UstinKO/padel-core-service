package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.PaymentUpdateDto;
import com.padle.core.padelcoreservice.exception.AccountException;
import com.padle.core.padelcoreservice.model.Payment;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.PaymentMethod;
import com.padle.core.padelcoreservice.model.enums.PaymentStatus;
import com.padle.core.padelcoreservice.receipts.ReceiptModels.Accept;
import com.padle.core.padelcoreservice.repository.PaymentRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentServiceReceiptTest {
    private final PaymentRepository repository = mock(PaymentRepository.class);
    private final PaymentService service = spy(new PaymentService(repository,
            mock(TournamentRegistrationRepository.class),null,null,null));
    private final TournamentRegistration row = TournamentRegistration.builder().id(100L)
            .tournament(Tournament.builder().id(10L).build()).attended(true).participationConfirmed(true).build();
    private final Accept decision = new Accept(new BigDecimal("1000.00"), "ARS", PaymentMethod.BANK_TRANSFER);
    private Payment payment(PaymentStatus state) {
        return Payment.builder().id(200L).registration(row).amount(new BigDecimal("1000.00"))
                .currency("ARS").status(state).paymentMethod(PaymentMethod.BANK_TRANSFER).createdAt(LocalDateTime.now()).build();
    }
    @Test void anAlreadyPaidMatchingPaymentIsLinkedWithoutAnotherLedgerWrite() {
        when(repository.findByRegistrationId(100L)).thenReturn(List.of(payment(PaymentStatus.PAID)));
        assertThat(service.confirmReceiptPayment(row,decision,1)).isEqualTo(200);
        verify(service,never()).savePaymentManagementData(anyLong(),anyList(),anyLong());
        verify(repository,never()).save(any());
    }
    @Test void conflictingPaidRefundedAndHistoricalPaymentsRequireManualReconciliation() {
        var paid = payment(PaymentStatus.PAID); paid.setAmount(new BigDecimal("2000"));
        when(repository.findByRegistrationId(100L)).thenReturn(List.of(paid));
        assertThatThrownBy(() -> service.confirmReceiptPayment(row,decision,1)).isInstanceOf(AccountException.class);
        when(repository.findByRegistrationId(100L)).thenReturn(List.of(payment(PaymentStatus.REFUNDED)));
        assertThatThrownBy(() -> service.confirmReceiptPayment(row,decision,1)).isInstanceOf(AccountException.class);
        var historical = payment(PaymentStatus.PENDING); historical.setCreatedAt(LocalDateTime.now().minusDays(2));
        row.setCancellationDate(LocalDateTime.now().minusDays(1));
        when(repository.findByRegistrationId(100L)).thenReturn(List.of(historical));
        assertThatThrownBy(() -> service.confirmReceiptPayment(row,decision,1)).hasMessage("RECEIPT_PAYMENT_CONFLICT");
    }
    @Test void aNewPaymentUsesExistingManualSaveWithoutResettingAttendanceOrParticipation() {
        when(repository.findByRegistrationId(100L)).thenReturn(List.of(),List.of(payment(PaymentStatus.PAID)));
        doNothing().when(service).savePaymentManagementData(anyLong(),anyList(),anyLong());
        assertThat(service.confirmReceiptPayment(row,decision,1)).isEqualTo(200);
        @SuppressWarnings("unchecked") ArgumentCaptor<List<PaymentUpdateDto>> updates = ArgumentCaptor.forClass(List.class);
        verify(service).savePaymentManagementData(eq(10L),updates.capture(),eq(1L));
        assertThat(updates.getValue()).singleElement().satisfies(dto -> {
            assertThat(dto.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(dto.getCurrency()).isEqualTo("ARS");
            assertThat(dto.isAttended()).isTrue();
            assertThat(dto.isParticipationConfirmed()).isTrue();
        });
    }
}
