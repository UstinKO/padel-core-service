package com.padle.core.padelcoreservice.controller.view;

import com.padle.core.padelcoreservice.dto.PaymentManagementViewDto;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.enums.PaymentMethod;
import com.padle.core.padelcoreservice.model.enums.PaymentStatus;
import com.padle.core.padelcoreservice.service.PaymentService;
import com.padle.core.padelcoreservice.service.TournamentService;
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
                               @ModelAttribute PaymentFormWrapper formWrapper,
                               @AuthenticationPrincipal Owner owner,
                               RedirectAttributes redirectAttributes) {
        log.info("Saving payment data for tournament: {}", tournamentId);

        try {
            List<PaymentManagementViewDto> updates = new ArrayList<>();

            for (int i = 0; i < formWrapper.getRegistrationIds().size(); i++) {
                PaymentManagementViewDto dto = new PaymentManagementViewDto();

                Long registrationId = formWrapper.getRegistrationIds().get(i);
                dto.setRegistrationId(registrationId);

                // paymentId
                if (formWrapper.getPaymentIds() != null && i < formWrapper.getPaymentIds().size()) {
                    String paymentIdStr = formWrapper.getPaymentIds().get(i);
                    if (paymentIdStr != null && !paymentIdStr.isBlank()) {
                        try {
                            dto.setPaymentId(Long.parseLong(paymentIdStr));
                        } catch (NumberFormatException ignored) {}
                    }
                }

                // hasPayment
                if (formWrapper.getHasPayments() != null && i < formWrapper.getHasPayments().size()) {
                    dto.setHasPayment(Boolean.parseBoolean(formWrapper.getHasPayments().get(i)));
                }

                // isPartnerRow
                if (formWrapper.getPartnerRows() != null && i < formWrapper.getPartnerRows().size()) {
                    dto.setPartnerRow(Boolean.parseBoolean(formWrapper.getPartnerRows().get(i)));
                }

                // Посещение
                dto.setAttended(formWrapper.getAttended() != null &&
                        formWrapper.getAttended().contains(registrationId));

                // Сумма
                if (formWrapper.getAmounts() != null && i < formWrapper.getAmounts().size()) {
                    String amountStr = formWrapper.getAmounts().get(i);
                    if (amountStr != null && !amountStr.trim().isEmpty()) {
                        try {
                            dto.setAmount(new BigDecimal(amountStr));
                        } catch (NumberFormatException e) {
                            log.warn("Invalid amount at index {}: {}", i, amountStr);
                        }
                    }
                }

                // Валюта
                if (formWrapper.getCurrencies() != null && i < formWrapper.getCurrencies().size()) {
                    dto.setCurrency(formWrapper.getCurrencies().get(i));
                }

                // Статус платежа
                if (formWrapper.getPaymentStatuses() != null && i < formWrapper.getPaymentStatuses().size()) {
                    String status = formWrapper.getPaymentStatuses().get(i);
                    if (status != null && !status.isEmpty()) {
                        dto.setPaymentStatus(PaymentStatus.valueOf(status));
                    }
                }

                // Метод платежа
                if (formWrapper.getPaymentMethods() != null && i < formWrapper.getPaymentMethods().size()) {
                    String method = formWrapper.getPaymentMethods().get(i);
                    if (method != null && !method.isEmpty()) {
                        dto.setPaymentMethod(PaymentMethod.valueOf(method));
                    }
                }

                // ID транзакции
                if (formWrapper.getTransactionIds() != null && i < formWrapper.getTransactionIds().size()) {
                    dto.setTransactionId(formWrapper.getTransactionIds().get(i));
                }

                // Заметки
                if (formWrapper.getNotes() != null && i < formWrapper.getNotes().size()) {
                    dto.setNotes(formWrapper.getNotes().get(i));
                }

                updates.add(dto);
                log.debug("Payment update [{}]: regId={}, paymentId={}, hasPayment={}, partnerRow={}, amount={}",
                        i, dto.getRegistrationId(), dto.getPaymentId(),
                        dto.isHasPayment(), dto.isPartnerRow(), dto.getAmount());
            }

            paymentService.savePaymentManagementData(tournamentId, updates, owner.getId());
            redirectAttributes.addFlashAttribute("successMessage", "Datos de pago guardados correctamente");

        } catch (Exception e) {
            log.error("Error saving payment data", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error al guardar: " + e.getMessage());
        }

        return "redirect:/admin/tournaments/" + tournamentId;
    }

    public static class PaymentFormWrapper {
        private List<Long> registrationIds;
        private List<Long> attended;
        private List<String> amounts;
        private List<String> currencies;
        private List<String> paymentStatuses;
        private List<String> paymentMethods;
        private List<String> transactionIds;
        private List<String> notes;
        private List<String> paymentIds;
        private List<String> hasPayments;
        private List<String> partnerRows;   // ← ДОБАВЛЕНО

        public List<Long> getRegistrationIds() { return registrationIds; }
        public void setRegistrationIds(List<Long> v) { this.registrationIds = v; }

        public List<Long> getAttended() { return attended; }
        public void setAttended(List<Long> v) { this.attended = v; }

        public List<String> getAmounts() { return amounts; }
        public void setAmounts(List<String> v) { this.amounts = v; }

        public List<String> getCurrencies() { return currencies; }
        public void setCurrencies(List<String> v) { this.currencies = v; }

        public List<String> getPaymentStatuses() { return paymentStatuses; }
        public void setPaymentStatuses(List<String> v) { this.paymentStatuses = v; }

        public List<String> getPaymentMethods() { return paymentMethods; }
        public void setPaymentMethods(List<String> v) { this.paymentMethods = v; }

        public List<String> getTransactionIds() { return transactionIds; }
        public void setTransactionIds(List<String> v) { this.transactionIds = v; }

        public List<String> getNotes() { return notes; }
        public void setNotes(List<String> v) { this.notes = v; }

        public List<String> getPaymentIds() { return paymentIds; }
        public void setPaymentIds(List<String> v) { this.paymentIds = v; }

        public List<String> getHasPayments() { return hasPayments; }
        public void setHasPayments(List<String> v) { this.hasPayments = v; }

        public List<String> getPartnerRows() { return partnerRows; }
        public void setPartnerRows(List<String> v) { this.partnerRows = v; }
    }
}