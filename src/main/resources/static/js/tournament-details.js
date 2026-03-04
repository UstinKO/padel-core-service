// src/main/resources/static/js/tournament-details.js

document.addEventListener('DOMContentLoaded', function() {
    'use strict';

    // Кнопки регистрации
    const registerBtn = document.querySelector('.btn-register');
    const cancelBtn = document.querySelector('.btn-cancel');

    if (registerBtn) {
        registerBtn.addEventListener('click', handleRegistration);
    }

    if (cancelBtn) {
        cancelBtn.addEventListener('click', handleCancellation);
    }

    async function handleRegistration(event) {
        const button = event.currentTarget;
        const tournamentId = button.dataset.tournamentId;
        const tournamentName = button.dataset.tournamentName;

        if (!confirm(`¿Deseas inscribirte en el torneo "${tournamentName}"?`)) {
            return;
        }

        button.disabled = true;
        button.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Procesando...';

        try {
            const response = await fetch(`/players/tournaments/${tournamentId}/register`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                }
            });

            const data = await response.json();

            if (data.success) {
                alert(data.message);
                window.location.reload();
            } else {
                alert('Error: ' + data.message);
                button.disabled = false;
                button.innerHTML = tournament.inscritosActuales >= tournament.cupoMax ?
                    '<i class="fas fa-clock"></i> Apuntarme a lista de espera' :
                    '<i class="fas fa-check-circle"></i> Inscribirme';
            }
        } catch (error) {
            console.error('Error registering:', error);
            alert('Error al procesar la solicitud');
            button.disabled = false;
            button.innerHTML = tournament.inscritosActuales >= tournament.cupoMax ?
                '<i class="fas fa-clock"></i> Apuntarme a lista de espera' :
                '<i class="fas fa-check-circle"></i> Inscribirme';
        }
    }

    async function handleCancellation(event) {
        const button = event.currentTarget;
        const tournamentId = button.dataset.tournamentId;
        const tournamentName = button.dataset.tournamentName;

        const reason = prompt('¿Por qué cancelas tu inscripción? (opcional)');

        if (!confirm(`¿Estás seguro de cancelar tu inscripción en "${tournamentName}"?`)) {
            return;
        }

        button.disabled = true;
        button.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Procesando...';

        try {
            const url = `/players/tournaments/${tournamentId}/cancel` +
                (reason ? `?reason=${encodeURIComponent(reason)}` : '');

            const response = await fetch(url, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                }
            });

            const data = await response.json();

            if (data.success) {
                alert(data.message);
                window.location.reload();
            } else {
                alert('Error: ' + data.message);
                button.disabled = false;
                button.innerHTML = '<i class="fas fa-times-circle"></i> Cancelar inscripción';
            }
        } catch (error) {
            console.error('Error cancelling:', error);
            alert('Error al procesar la solicitud');
            button.disabled = false;
            button.innerHTML = '<i class="fas fa-times-circle"></i> Cancelar inscripción';
        }
    }
});

// Функции для шаринга
function shareOnWhatsApp() {
    const url = encodeURIComponent(window.location.href);
    const text = encodeURIComponent(document.querySelector('h1').textContent);
    window.open(`https://wa.me/?text=${text}%20${url}`, '_blank');
}

function shareOnFacebook() {
    const url = encodeURIComponent(window.location.href);
    window.open(`https://www.facebook.com/sharer/sharer.php?u=${url}`, '_blank');
}

function shareOnTwitter() {
    const url = encodeURIComponent(window.location.href);
    const text = encodeURIComponent(document.querySelector('h1').textContent);
    window.open(`https://twitter.com/intent/tweet?text=${text}&url=${url}`, '_blank');
}

function shareByEmail() {
    const subject = encodeURIComponent(document.querySelector('h1').textContent);
    const body = encodeURIComponent(`Mira este torneo de padel: ${window.location.href}`);
    window.location.href = `mailto:?subject=${subject}&body=${body}`;
}