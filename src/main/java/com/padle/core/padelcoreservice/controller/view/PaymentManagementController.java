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

        var tournament = tournamentService.getTournamentById(tournamentId)
                .orElseThrow(() -> new RuntimeException("Tournament not found"));

        List<PaymentManagementViewDto> players = paymentService.getPaymentManagementData(tournamentId);

        model.addAttribute("tournament", tournament);
        model.addAttribute("players", players);
        model.addAttribute("paymentMethods", PaymentMethod.values());
        model.addAttribute("paymentStatuses", PaymentStatus.values());

        // ИСПРАВЛЕНО: меняем путь с "admin/payments/management" на "admin/tournaments/payments"
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

                // Посещение
                dto.setAttended(formWrapper.getAttended() != null &&
                        formWrapper.getAttended().contains(registrationId));

                // Платежные данные
                if (formWrapper.getAmounts() != null && i < formWrapper.getAmounts().size()) {
                    String amountStr = formWrapper.getAmounts().get(i);
                    if (amountStr != null && !amountStr.trim().isEmpty()) {
                        dto.setAmount(new BigDecimal(amountStr));
                    }
                }

                if (formWrapper.getCurrencies() != null && i < formWrapper.getCurrencies().size()) {
                    dto.setCurrency(formWrapper.getCurrencies().get(i));
                }

                if (formWrapper.getPaymentStatuses() != null && i < formWrapper.getPaymentStatuses().size()) {
                    String status = formWrapper.getPaymentStatuses().get(i);
                    if (status != null && !status.isEmpty()) {
                        dto.setPaymentStatus(PaymentStatus.valueOf(status));
                    }
                }

                if (formWrapper.getPaymentMethods() != null && i < formWrapper.getPaymentMethods().size()) {
                    String method = formWrapper.getPaymentMethods().get(i);
                    if (method != null && !method.isEmpty()) {
                        dto.setPaymentMethod(PaymentMethod.valueOf(method));
                    }
                }

                if (formWrapper.getTransactionIds() != null && i < formWrapper.getTransactionIds().size()) {
                    dto.setTransactionId(formWrapper.getTransactionIds().get(i));
                }

                if (formWrapper.getNotes() != null && i < formWrapper.getNotes().size()) {
                    dto.setNotes(formWrapper.getNotes().get(i));
                }

                updates.add(dto);
            }

            paymentService.savePaymentManagementData(tournamentId, updates, owner.getId());

            redirectAttributes.addFlashAttribute("successMessage",
                    "Datos de pago guardados correctamente");

        } catch (Exception e) {
            log.error("Error saving payment data", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Error al guardar: " + e.getMessage());
        }

        return "redirect:/admin/tournaments/" + tournamentId;
    }

    // Вспомогательный класс для формы
    public static class PaymentFormWrapper {
        private List<Long> registrationIds;
        private List<Long> attended;
        private List<String> amounts;
        private List<String> currencies;
        private List<String> paymentStatuses;
        private List<String> paymentMethods;
        private List<String> transactionIds;
        private List<String> notes;

        // Геттеры и сеттеры
        public List<Long> getRegistrationIds() { return registrationIds; }
        public void setRegistrationIds(List<Long> registrationIds) { this.registrationIds = registrationIds; }

        public List<Long> getAttended() { return attended; }
        public void setAttended(List<Long> attended) { this.attended = attended; }

        public List<String> getAmounts() { return amounts; }
        public void setAmounts(List<String> amounts) { this.amounts = amounts; }

        public List<String> getCurrencies() { return currencies; }
        public void setCurrencies(List<String> currencies) { this.currencies = currencies; }

        public List<String> getPaymentStatuses() { return paymentStatuses; }
        public void setPaymentStatuses(List<String> paymentStatuses) { this.paymentStatuses = paymentStatuses; }

        public List<String> getPaymentMethods() { return paymentMethods; }
        public void setPaymentMethods(List<String> paymentMethods) { this.paymentMethods = paymentMethods; }

        public List<String> getTransactionIds() { return transactionIds; }
        public void setTransactionIds(List<String> transactionIds) { this.transactionIds = transactionIds; }

        public List<String> getNotes() { return notes; }
        public void setNotes(List<String> notes) { this.notes = notes; }
    }
}