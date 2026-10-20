package com.padle.core.padelcoreservice.dto;

// LFPT-0668: превью для страницы подтверждения отмены резервной заявки по ссылке из email
// (GET /reserva/accion/cancelar) — показать игроку, что именно он отменяет, до реального POST.
public record ReserveCancelPreviewDto(
        String playerName,
        String tournamentName,
        String tournamentDate,
        String clubName
) {
}
