package com.padle.core.padelcoreservice.controller.view;

import com.padle.core.padelcoreservice.dto.PaymentManagementViewDto;
import com.padle.core.padelcoreservice.dto.PaymentUpdateDto;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.enums.PaymentMethod;
import com.padle.core.padelcoreservice.model.enums.PaymentStatus;
import com.padle.core.padelcoreservice.service.PaymentService;
import com.padle.core.padelcoreservice.service.TournamentService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

@Slf4j
@Controller
@RequestMapping("/admin/tournaments")
@RequiredArgsConstructor
public class PaymentManagementController {

    private final TournamentService tournamentService;
    private final PaymentService paymentService;

    @GetMapping("/{tournamentId}/payments")
    public String paymentManagementPage(@PathVariable Long tournamentId, Model model) {
        log.info("Opening payment management page for tournament: {}", tournamentId);

        var tournament = tournamentService.getTournamentDtoById(tournamentId)
                .orElseThrow(() -> new RuntimeException("Tournament not found"));

        List<PaymentManagementViewDto> players = paymentService.getPaymentManagementData(tournamentId);

        model.addAttribute("tournament", tournament);
        model.addAttribute("players", players);
        model.addAttribute("paymentMethods", PaymentMethod.values());
        model.addAttribute("paymentStatuses", PaymentStatus.values());

        return "admin/tournaments/payments";
    }

    @PostMapping("/{tournamentId}/payments/save")
    public String savePayments(@PathVariable Long tournamentId,
                               @ModelAttribute PaymentUpdateForm form,
                               @AuthenticationPrincipal Owner owner,
                               RedirectAttributes redirectAttributes) {
        log.info("Saving payment data for tournament: {}", tournamentId);

        try {
            List<PaymentUpdateDto> updates = parsePaymentUpdates(form.getPayments());
            paymentService.savePaymentManagementData(tournamentId, updates, owner.getId());
            redirectAttributes.addFlashAttribute("successMessage", "Datos de pago guardados correctamente");
        } catch (Exception e) {
            log.error("Error saving payment data", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error al guardar: " + e.getMessage());
        }

        return "redirect:/admin/tournaments/" + tournamentId + "/payments";
    }

    // Маппинг формы (строки с "сырыми" строковыми значениями — безопасно биндятся
    // Spring MVC даже когда поле пустое) в типизированный PaymentUpdateDto.
    private List<PaymentUpdateDto> parsePaymentUpdates(List<PaymentRowForm> rows) {
        return IntStream.range(0, rows.size())
                .mapToObj(i -> toPaymentUpdateDto(rows.get(i), i))
                .toList();
    }

    private PaymentUpdateDto toPaymentUpdateDto(PaymentRowForm row, int index) {
        PaymentUpdateDto dto = new PaymentUpdateDto();
        dto.setRegistrationId(row.getRegistrationId());
        dto.setPaymentId(parseLongOrLog(row.getPaymentId(), index, "paymentId"));
        dto.setHasPayment(row.isHasPayment());
        dto.setPartnerRow(row.isPartnerRow());
        dto.setAttended(row.isAttended());
        dto.setParticipationConfirmed(row.isParticipationConfirmed());
        dto.setAmount(parseAmountOrLog(row.getAmount(), index));
        dto.setCurrency(row.getCurrency());
        dto.setPaymentStatus(parseEnumOrNull(PaymentStatus.class, row.getPaymentStatus()));
        dto.setPaymentMethod(parseEnumOrNull(PaymentMethod.class, row.getPaymentMethod()));
        dto.setTransactionId(row.getTransactionId());
        dto.setNotes(row.getNotes());
        return dto;
    }

    private Long parseLongOrLog(String value, int index, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid {} at index {}: {}", fieldName, index, value);
            return null;
        }
    }

    private BigDecimal parseAmountOrLog(String value, int index) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid amount at index {}: {}", index, value);
            return null;
        }
    }

    private static <E extends Enum<E>> E parseEnumOrNull(Class<E> type, String value) {
        return (value == null || value.isBlank()) ? null : Enum.valueOf(type, value);
    }

    /**
     * Обёртка верхнего уровня для @ModelAttribute — Spring MVC биндит вложенный
     * список через индексированные имена полей формы (payments[i].xxx), заменяя
     * прежний паттерн из 11+ параллельных List-полей (issue #130).
     */
    @Data
    public static class PaymentUpdateForm {
        private List<PaymentRowForm> payments = new ArrayList<>();
    }

    @Data
    public static class PaymentRowForm {
        private Long registrationId;
        private String paymentId;
        private boolean hasPayment;
        private boolean partnerRow;
        private boolean attended;
        private boolean participationConfirmed;
        private String amount;
        private String currency;
        private String paymentStatus;
        private String paymentMethod;
        private String transactionId;
        private String notes;
    }
}