package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class TournamentNotificationService {

    private final EmailService emailService;
    private final ClubRepository clubRepository;

    public void sendPartnerInvitationEmail(String partnerEmail, String partnerName,
                                           String mainPlayerName, TournamentDto tournament,
                                           RegistrationStatus status, int position, Locale locale) {
        try {
            String dateStr = tournament.getFechaInicio().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            String timeStr = tournament.getHoraInicio().format(DateTimeFormatter.ofPattern("HH:mm"));
            String clubName = getClubName(tournament.getClubId());

            // ТОЖЕ отправляем tournament-confirmation.html для партнера
            emailService.sendTournamentConfirmationEmail(
                    partnerEmail,
                    partnerName,
                    tournament.getNombre(),
                    dateStr,
                    timeStr,
                    clubName,
                    locale
            );

            log.info("Tournament confirmation email sent to partner: {}", partnerEmail);

        } catch (Exception e) {
            log.error("Error sending tournament confirmation to partner: {}", e.getMessage());
        }
    }

    public void sendNewPartnerInvitation(String email, String partnerName, String mainPlayerName,
                                         TournamentDto tournament, String completionUrl, int expiryHours, Locale locale) {
        try {
            // Получаем данные о турнире
            String dateStr = tournament.getFechaInicio().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            String timeStr = tournament.getHoraInicio().format(DateTimeFormatter.ofPattern("HH:mm"));
            String clubName = getClubName(tournament.getClubId());

            emailService.sendNewPartnerInvitationEmail(
                    email,
                    partnerName,
                    mainPlayerName,
                    tournament.getNombre(),
                    dateStr,        // ← передаем дату
                    timeStr,        // ← передаем время
                    clubName,       // ← передаем название клуба
                    completionUrl,
                    expiryHours,
                    locale
            );
        } catch (Exception e) {
            log.error("Error sending new partner invitation email: {}", e.getMessage());
        }
    }

    public void sendConfirmationToMainPlayer(String mainPlayerEmail, String mainPlayerName,
                                             String partnerFullName, TournamentDto tournament,
                                             RegistrationStatus status, int position, Locale locale) {
        try {
            String dateStr = tournament.getFechaInicio().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            String timeStr = tournament.getHoraInicio().format(DateTimeFormatter.ofPattern("HH:mm"));
            String clubName = getClubName(tournament.getClubId());

            // ВСЕГДА отправляем tournament-confirmation.html для основного игрока
            emailService.sendTournamentConfirmationEmail(
                    mainPlayerEmail,
                    mainPlayerName,
                    tournament.getNombre(),
                    dateStr,
                    timeStr,
                    clubName,
                    locale
            );

            log.info("Tournament confirmation email sent to main player: {}", mainPlayerEmail);

        } catch (Exception e) {
            log.error("Error sending tournament confirmation to main player: {}", e.getMessage());
        }
    }

    public void sendMainPlayerNotification(String mainPlayerEmail, String mainPlayerName,
                                           String partnerName, TournamentDto tournament,
                                           RegistrationStatus status, int position, Locale locale) {
        try {
            String dateStr = tournament.getFechaInicio().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            String timeStr = tournament.getHoraInicio().format(DateTimeFormatter.ofPattern("HH:mm"));
            String clubName = getClubName(tournament.getClubId());

            if (status == RegistrationStatus.CONFIRMED) {
                emailService.sendRegistrationConfirmationEmail(
                        mainPlayerEmail,
                        mainPlayerName,
                        tournament.getNombre(),
                        dateStr,
                        timeStr,
                        clubName,
                        locale
                );
            } else {
                emailService.sendWaitlistNotificationEmail(
                        mainPlayerEmail,
                        mainPlayerName,
                        tournament.getNombre(),
                        dateStr,
                        timeStr,
                        clubName,
                        position,
                        locale
                );
            }
        } catch (Exception e) {
            log.error("Error sending notification to main player: {}", e.getMessage());
        }
    }

    public void sendPairConfirmationEmails(TournamentRegistration mainReg, TournamentRegistration partnerReg) {
        try {
            Tournament tournament = mainReg.getTournament();
            String dateStr = tournament.getFechaInicio().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            String timeStr = tournament.getHoraInicio().format(DateTimeFormatter.ofPattern("HH:mm"));
            String clubName = getClubName(tournament.getClubId());

            // Основному игроку
            emailService.sendTournamentConfirmationEmail(
                    mainReg.getPlayer().getEmail(),
                    mainReg.getPlayer().getNombre(),
                    tournament.getNombre(),
                    dateStr,
                    timeStr,
                    clubName,
                    mainReg.getPlayer().getLocale()
            );

            // Партнеру
            emailService.sendTournamentConfirmationEmail(
                    partnerReg.getPlayer().getEmail(),
                    partnerReg.getPlayer().getNombre(),
                    tournament.getNombre(),
                    dateStr,
                    timeStr,
                    clubName,
                    partnerReg.getPlayer().getLocale()
            );

            log.info("Pair confirmation emails sent to both players");
        } catch (Exception e) {
            log.error("Error sending pair confirmation emails: {}", e.getMessage());
        }
    }

    private String getClubName(Long clubId) {
        return clubRepository.findById(clubId)
                .map(Club::getNombre)
                .orElse("Club Desconocido");
    }
}