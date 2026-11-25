const TournamentAvailability = (() => {
    const labelKeys = {
        available: 'card.spots.available',
        limited: 'card.spots.limited',
        full: 'card.spots.completos'
    };

    function getStatus(tournament) {
        const capacity = tournament.cupoMax || 0;
        const registered = tournament.inscritosActuales || 0;
        if (capacity <= 0) return 'available';

        const fillRatio = registered / capacity;
        if (fillRatio >= 1) return 'full';
        if (fillRatio >= 0.7) return 'limited';
        return 'available';
    }

    return { getStatus, labelKey: status => labelKeys[status] };
})();
