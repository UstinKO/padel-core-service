(() => {
    'use strict';

    async function request(id, path, token) {
        const response = await fetch('/api/tournaments/double/' + Number(id) + '/' + path, token == null ? {
            cache: 'no-store'
        } : {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ token })
        });
        if (response.status === 204) return null;
        if (!response.ok) throw new Error(t('pair_terms.retry'));
        return response.json();
    }

    const locale = document.documentElement.lang || 'es';
    const money = (value, currency) => currency === 'USDT'
        ? new Intl.NumberFormat(locale).format(value) + ' USDT'
        : new Intl.NumberFormat(locale, { style: 'currency', currency }).format(value);
    const date = value => new Intl.DateTimeFormat(locale, {
        dateStyle: 'medium', timeStyle: 'short', timeZone: 'America/Argentina/Buenos_Aires'
    }).format(new Date(value));
    const line = (key, value) => t('pair_terms.' + key) + ': ' + value;

    function termsLines(terms) {
        return [
            terms.eventName, date(terms.startsAt), terms.category,
            line('player_price', money(terms.pricePerPlayer, terms.currency)),
            line('pair_price', money(terms.pairPrice, terms.currency)),
            { heading: t('pair_terms.payment_section') },
            line('free_until', date(terms.freeUntil)),
            line('late_obligation', money(terms.lateObligation, terms.currency)),
            t('pair_terms.replacement'),
            { heading: t('pair_terms.arrival_section') },
            t('pair_terms.arrival')
        ].filter(Boolean);
    }

    function cancellationLines(quote) {
        if (quote.legacy) return [t('pair_terms.cancel_description'), t('pair_terms.legacy')];
        return [
            line('free_until', date(quote.freeUntil)),
            line('obligation', money(quote.obligation, quote.currency)),
            line('paid', money(quote.alreadyPaid, quote.currency)),
            line('due', money(quote.remainingDue, quote.currency)),
            line('refund', money(quote.refundable, quote.currency)),
            quote.remainingDue > 0 ? quote.paymentInstructions || quote.organizerContact || t('pair_terms.contact') : null
        ].filter(Boolean);
    }

    function dialog(title, lines, action, regulation, choices = []) {
        return new Promise(resolve => {
            const modal = document.createElement('dialog');
            modal.style.cssText = 'max-width:min(34rem,90vw);padding:1.5rem;border:0;border-radius:12px;';
            const heading = document.createElement('h2');
            heading.textContent = title;
            modal.setAttribute('aria-label', title);
            modal.append(heading);
            lines.forEach(value => {
                const element = document.createElement(typeof value === 'string' ? 'p' : 'h3');
                if (typeof value === 'string') {
                    element.textContent = value;
                } else {
                    element.textContent = value.heading;
                    element.style.cssText = 'font-size:1rem;margin:1rem 0 .5rem;padding-top:.75rem;border-top:1px solid #d9e0dc;';
                }
                modal.append(element);
            });
            if (/^https?:\/\//i.test(regulation || '')) {
                const link = document.createElement('a');
                link.href = regulation;
                link.target = '_blank';
                link.rel = 'noopener noreferrer';
                link.textContent = t('pair_terms.regulation');
                modal.append(link);
            }
            const controls = document.createElement('form');
            controls.method = 'dialog';
            controls.style.cssText = 'display:flex;flex-wrap:wrap;gap:.75rem;justify-content:flex-end;margin-top:1rem;';
            const button = (label, value, style) => {
                const el = document.createElement('button');
                el.type = 'submit';
                el.value = value;
                el.className = style;
                el.textContent = label;
                controls.append(el);
            };
            if (action || choices.length) button(t('pair_terms.back'), 'back', 'btn btn-outline');
            for (const [value, label] of choices) button(label, value, 'btn btn-outline');
            if (!choices.length) button(action || t('pair_terms.done'), 'confirm', 'btn btn-primary');
            modal.append(controls);
            modal.addEventListener('close', () => {
                const confirmed = modal.returnValue === 'confirm';
                modal.remove();
                resolve(choices.length ? modal.returnValue : confirmed);
            }, { once: true });
            document.body.append(modal);
            modal.showModal();
        });
    }

    const exitCopy = key => t('pair_exit.' + key);
    const replacementCopy = key => t('pair_replace.' + key);
    const noticeUrl = '/api/players/me/pair-replacement-notices';

    function button(label, className = 'btn btn-outline') {
        const control = document.createElement('button');
        control.type = 'button';
        control.className = className;
        control.textContent = label;
        return control;
    }

    function paragraph(value) {
        const element = document.createElement('p');
        element.textContent = value;
        return element;
    }

    function postJson(url, body) {
        return fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });
    }

    function openReplacementForm(id, { title, lines, success, submit }) {
        return new Promise(resolve => {
            const modal = document.createElement('dialog');
            modal.style.cssText = 'width:min(30rem,90vw);max-height:90vh;padding:1.5rem;border:0;border-radius:12px;';
            const heading = document.createElement('h2');
            heading.textContent = title;
            modal.setAttribute('aria-label', title);
            const form = document.createElement('form');
            const fields = document.createElement('fieldset');
            fields.style.cssText = 'display:grid;gap:.65rem;border:0;padding:0;margin:0;min-width:0;';
            const selection = paragraph('');
            const change = button(replacementCopy('change'));
            const selectedView = document.createElement('div');
            selectedView.append(selection, change);
            selectedView.hidden = true;
            const error = paragraph('');
            error.setAttribute('role', 'alert');
            const confirm = button(replacementCopy('confirm'), 'btn btn-primary');
            confirm.type = 'submit';
            const back = button(t('pair_terms.back'));
            const controls = document.createElement('div');
            controls.style.cssText = 'display:flex;flex-wrap:wrap;gap:.75rem;justify-content:flex-end;';
            controls.append(back, confirm);
            const inputs = {};
            const suggestions = [];
            let selected = null;

            function candidate() {
                if (selected) {
                    return { nombre: selected.nombre, apellido: selected.apellido, existingUserId: selected.id };
                }
                const nombre = inputs.nombre.value.trim();
                const apellido = inputs.apellido.value.trim();
                const telefono = inputs.telefono.value.trim();
                if (!nombre || !apellido || !/^[+]?[0-9 ()-]{8,19}$/.test(telefono)
                        || telefono.replace(/[^0-9]/g, '').length < 8) return null;
                return { nombre, apellido, telefono };
            }

            for (const [key, copy] of Object.entries({ nombre: 'first', apellido: 'last', telefono: 'phone' })) {
                const phone = key === 'telefono';
                const label = document.createElement('label');
                const input = document.createElement('input');
                input.type = phone ? 'tel' : 'text';
                input.className = 'form-control';
                input.style.cssText = 'display:block;width:100%;';
                input.maxLength = phone ? 20 : 100;
                input.autocomplete = 'off';
                inputs[key] = input;
                label.append(replacementCopy(copy), input);
                const results = document.createElement('div');
                suggestions.push(results);
                const field = document.createElement('div');
                field.append(label, results);
                fields.append(field);

                let timer;
                input.addEventListener('input', () => {
                    clearTimeout(timer);
                    results.replaceChildren();
                    confirm.disabled = !candidate();
                    const query = input.value.trim();
                    if (phone ? query.replace(/[^0-9]/g, '').length < 4 : query.length < 2) return;

                    timer = setTimeout(async () => {
                        try {
                            const url = '/api/players/search?tournamentId=' + id + '&query=' + encodeURIComponent(query);
                            const response = await fetch(url, { cache: 'no-store' });
                            if (!response.ok) throw new Error();
                            const players = await response.json();
                            if (!modal.open || selected || input.value.trim() !== query) return;
                            results.replaceChildren(...players.map(player => {
                                const suggestion = button(player.nombreCompleto, 'btn btn-outline btn-small');
                                suggestion.addEventListener('click', () => {
                                    selected = player;
                                    selection.textContent = player.nombreCompleto;
                                    fields.style.display = 'none';
                                    suggestions.forEach(list => list.replaceChildren());
                                    selectedView.hidden = false;
                                    confirm.disabled = false;
                                    confirm.focus();
                                });
                                return suggestion;
                            }));
                        } catch {
                            if (modal.open && !selected && input.value.trim() === query) {
                                results.replaceChildren(paragraph(replacementCopy('search_failed')));
                            }
                        }
                    }, 350);
                });
            }

            confirm.disabled = true;
            change.addEventListener('click', () => {
                selected = null;
                selectedView.hidden = true;
                fields.style.display = 'grid';
                confirm.disabled = !candidate();
                inputs.nombre.focus();
            });
            back.addEventListener('click', () => modal.close());
            modal.addEventListener('cancel', event => {
                if (back.disabled) event.preventDefault();
            });
            form.addEventListener('submit', async event => {
                event.preventDefault();
                const replacement = candidate();
                if (back.disabled || !replacement) return;
                confirm.disabled = back.disabled = change.disabled = fields.disabled = true;
                error.textContent = '';
                try {
                    await submit(replacement);
                    modal.close('replaced');
                    await dialog(title, [success]);
                    resolve(true);
                } catch {
                    error.textContent = replacementCopy('failed');
                    confirm.disabled = back.disabled = change.disabled = fields.disabled = false;
                }
            });
            form.append(fields, selectedView, error, controls);
            modal.append(heading, ...lines.map(paragraph), form);
            modal.addEventListener('close', () => {
                modal.remove();
                if (modal.returnValue !== 'replaced') resolve(false);
            }, { once: true });
            document.body.append(modal);
            modal.showModal();
        });
    }

    async function bindPartnerReplacement(control, tournamentId, showMissingPartner = false) {
        const id = Number(tournamentId);
        try {
            const response = await fetch('/players/tournaments/' + id + '/my-registration', { cache: 'no-store' });
            if (!response.ok) return;
            const registration = await response.json();
            const base = '/api/tournaments/double/' + id + '/';
            let form;
            if (registration.canReplacePartner && registration.partnerId) {
                const current = [registration.partnerFirstName, registration.partnerLastName].filter(Boolean).join(' ');
                form = {
                    title: replacementCopy('title'),
                    lines: [line('partner', current), replacementCopy('help')],
                    success: replacementCopy('success'),
                    async submit(replacement) {
                        const result = await postJson(base + 'replace-partner', { currentPartnerId: registration.partnerId, replacement });
                        if (!result.ok) throw new Error();
                    }
                };
            } else if (showMissingPartner && registration.replacementNeeded) {
                const retained = exitCopy(registration.status === 'WAITLIST' ? 'retained_reserve' : 'retained');
                form = {
                    title: exitCopy('add'),
                    lines: [retained, replacementCopy('help')],
                    success: replacementCopy('success'),
                    async submit(replacement) {
                        const result = await postJson(base + 'add-partner', replacement);
                        if (!result.ok || !(await result.json()).success) throw new Error();
                    }
                };
                const status = paragraph(exitCopy('missing') + ' ' + retained);
                status.className = 'tournament-modalidad';
                control.closest('.torneo-card-body')?.querySelector('.torneo-info')?.before(status);
            }
            if (!form) return;
            control.textContent = form.title;
            control.addEventListener('click', async () => {
                if (await openReplacementForm(id, form)) window.location.reload();
            });
            control.style.removeProperty('display');
        } catch {
            // A registration that could not be verified must not expose this action.
        }
    }

    async function reviewCurrent(id) {
        const current = await request(id, 'my-pair-terms');
        if (!current.terms) {
            await dialog(t('pair_terms.title'), [t('pair_terms.legacy')]);
            return;
        }
        if (await dialog(t('pair_terms.title'), termsLines(current.terms),
            current.canAccept ? t('pair_terms.accept') : null, current.terms.regulationUrl) && current.canAccept) {
            await request(id, 'my-pair-terms', current.terms.token);
            await dialog(t('pair_terms.title'), [t('pair_terms.accepted')]);
        }
    }

    async function withdrawSelf(id, quote, inReserve) {
        const choices = [];
        if (quote.canLeave) choices.push(['confirm', exitCopy('confirm_self')]);
        if (quote.canReplace) choices.push(['replace', exitCopy('replace')]);
        const help = exitCopy(inReserve ? 'leave_reserve' : 'leave_help');
        while (true) {
            const action = await dialog(exitCopy('self'), [help], null, null, choices);
            if (action === 'confirm') {
                const result = await postJson('/players/tournaments/' + id + '/cancel-double?option=self', {});
                if (!result.ok || !(await result.json()).success) throw new Error(t('pair_terms.retry'));
                await dialog(exitCopy(inReserve ? 'left_reserve' : 'left'), []);
                return true;
            }
            if (action !== 'replace') return false;
            const replaced = await openReplacementForm(id, {
                title: exitCopy('replace_title'),
                lines: [exitCopy('replace_help')],
                success: exitCopy('replaced'),
                async submit(candidate) {
                    const response = await postJson('/api/tournaments/double/' + id + '/replace-self', candidate);
                    if (!response.ok) throw new Error();
                }
            });
            if (replaced) return true;
        }
    }

    async function cancel(id, quote, teamFlow = false) {
        const title = teamFlow ? exitCopy('full') : t('pair_terms.cancel');
        const confirm = teamFlow ? exitCopy('confirm_full') : title;
        const lines = cancellationLines(quote);
        if (teamFlow && !quote.legacy) lines.unshift(t('pair_terms.cancel_description'));
        if (!await dialog(title, lines, confirm)) return null;
        const result = await request(id, 'pair-cancellation', quote.token);
        await dialog(t('pair_terms.cancelled'), cancellationLines(result));
        return { success: true };
    }

    async function showReplacementNotices(container) {
        try {
            const response = await fetch(noticeUrl, { cache: 'no-store' });
            if (!response.ok) return;
            for (const notice of await response.json()) {
                const card = document.createElement('article');
                card.className = 'dashboard-card';
                card.style.padding = '1rem';
                const heading = document.createElement('h3');
                heading.textContent = replacementCopy('notice') + ' · ' + notice.tournamentName;
                const names = { '{booker}': notice.bookerName, '{replacement}': notice.replacementName };
                const body = replacementCopy('notice_text').replace(/\{booker\}|\{replacement\}/g, key => names[key]);
                const dismiss = button(replacementCopy('dismiss'));
                dismiss.addEventListener('click', async () => {
                    dismiss.disabled = true;
                    try {
                        const result = await postJson(noticeUrl + '/' + notice.id + '/dismiss', {});
                        if (result.ok) card.remove();
                    } catch {
                        // Keep the notice visible so the player can retry.
                    } finally {
                        dismiss.disabled = false;
                    }
                });
                card.append(heading, paragraph(body), dismiss);
                container.append(card);
            }
        } catch {
            // The rest of the dashboard remains available if notices cannot be loaded.
        }
    }

    document.addEventListener('DOMContentLoaded', () => {
        for (const control of document.querySelectorAll('[data-replace-partner]')) {
            bindPartnerReplacement(control, control.dataset.tournamentId);
        }
        const notices = document.getElementById('pairReplacementNotices');
        if (notices) showReplacementNotices(notices);
    });

    window.PairTerms = {
        bindPartnerReplacement,
        async reviewBooking(id, partnerName) {
            const offer = await request(id, 'pair-terms');
            if (!offer) return {};
            const heading = t('details.mode.register_pair');
            const accepted = await dialog(heading,
                [line('partner', partnerName), ...termsLines(offer)], heading, offer.regulationUrl);
            return accepted ? { pairTermsToken: offer.token } : null;
        },
        reviewCurrent,
        async withdraw(tournamentId) {
            const id = Number(tournamentId);
            const response = await fetch('/players/tournaments/' + id + '/my-registration', { cache: 'no-store' });
            if (!response.ok) throw new Error(t('pair_terms.retry'));
            const registration = await response.json();
            let quote = await request(id, 'pair-cancellation');
            while (true) {
                const choices = [];
                if (quote.canLeave || quote.canReplace) choices.push(['self', exitCopy('self')]);
                choices.push(['full', exitCopy('full')], ['terms', t('pair_terms.title')]);
                const action = await dialog(exitCopy('choose'), [], null, null, choices);
                if (action === 'self') {
                    if (await withdrawSelf(id, quote, registration.status === 'WAITLIST')) return true;
                } else if (action === 'full') {
                    if (await cancel(id, quote, true)) return true;
                } else if (action === 'terms') {
                    await reviewCurrent(id);
                    quote = await request(id, 'pair-cancellation');
                } else {
                    return false;
                }
            }
        },
        async cancel(id) {
            return cancel(id, await request(id, 'pair-cancellation'));
        }
    };
})();
