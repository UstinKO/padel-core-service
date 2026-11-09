package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.mapper.TournamentMapper;
import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.util.AfterCommit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TournamentNotificationService {

    private final EmailService emailService;
    private final ClubRepository clubRepository;
    private final TournamentMapper tournamentMapper;

    public void sendPairTermsNotice(PlayerPadel player, PairTermsService.Offer offer,
                                    Long clubId, RegistrationStatus status) {
        String email = player.getEmail();
        if (email == null || email.isBlank() || PlayerPadel.isGuestEmail(email)) return;
        String name = player.getNombre();
        Locale locale = player.getLocale();
        String club = getClubName(clubId);
        AfterCommit.run(() -> emailService.sendPairTermsEmail(email, name, club, offer,
                status == RegistrationStatus.WAITLIST, locale));
    }

    public void sendPartnerReplacedNotice(PlayerPadel player, PairReplacementNoticeService.Notice notice) {
        String email = player.getEmail();
        if (email == null || email.isBlank() || PlayerPadel.isGuestEmail(email)) return;
        Locale locale = player.getLocale();
        AfterCommit.run(() -> emailService.sendPartnerReplacedEmail(email, notice, locale));
    }

    public void sendRegistrationStatusEmail(PlayerPadel player, TournamentDto tournament,
                                            RegistrationStatus status, Integer waitlistPosition) {
        String email = player.getEmail();
        if (email == null || email.isBlank() || PlayerPadel.isGuestEmail(email)) return;
        if (status != RegistrationStatus.WAITLIST && status != RegistrationStatus.CONFIRMED) return;

        String name = player.getNombre();
        Locale locale = player.getLocale();
        String date = tournament.getFechaInicio().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String time = Optional.ofNullable(tournament.getHoraInicio())
                .map(value -> value.format(DateTimeFormatter.ofPattern("HH:mm"))).orElse("");
        String club = getClubName(tournament.getClubId());
        if (status == RegistrationStatus.WAITLIST) {
            int position = Objects.requireNonNullElse(waitlistPosition, 0);
            AfterCommit.run(() -> emailService.sendWaitlistNotificationEmail(email, name, tournament.getNombre(),
                    date, time, club, position, locale));
        } else {
            AfterCommit.run(() -> emailService.sendTournamentConfirmationEmail(email, name, tournament.getNombre(),
                    date, time, club, tournament.getCancellationDeadlineFormatted(), tournament.getTipo(), locale));
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
                        tournament.getCancellationDeadlineFormatted(),
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

    public void sendPairRegistrationEmails(TournamentRegistration mainReg, TournamentRegistration partnerReg) {
        if (mainReg.getStatus() != partnerReg.getStatus()) {
            log.warn("Статусы участников пары различаются, письма не отправлены: {}, {}", mainReg.getId(), partnerReg.getId());
            return;
        }
        TournamentDto tournament = tournamentMapper.toDto(mainReg.getTournament());
        sendRegistrationStatusEmail(mainReg.getPlayer(), tournament, mainReg.getStatus(), mainReg.getWaitlistPosition());
        sendRegistrationStatusEmail(partnerReg.getPlayer(), tournament, partnerReg.getStatus(), partnerReg.getWaitlistPosition());
    }

    private String getClubName(Long clubId) {
        return clubRepository.findById(clubId)
                .map(Club::getNombre)
                .orElse("Club Desconocido");
    }
}