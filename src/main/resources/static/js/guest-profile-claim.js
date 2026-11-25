(() => {
    'use strict';

    const $ = (parent, selector) => parent.querySelector(selector);
    const nodes = (parent, selector) => Array.from(parent.querySelectorAll(selector));

    async function request(endpoint, body, fallback) {
        const options = { headers: { Accept: 'application/json' }, cache: 'no-store' };
        if (body !== undefined) {
            options.method = 'POST';
            options.headers['Content-Type'] = 'application/json';
            options.body = JSON.stringify(body);
        }
        const response = await fetch(endpoint, options).catch(() => { throw new Error(fallback); });
        const result = await response.json().catch(() => null);
        if (!response.ok || result === null) throw new Error(result?.detail || fallback);
        return result;
    }

    function texts(section) {
        const labels = $(section, '[data-claim-labels]');
        const values = Object.fromEntries(nodes(labels, '[data-label]').map(node => [node.dataset.label, node.textContent]));
        const formatter = new Intl.DateTimeFormat(labels.dataset.locale, { dateStyle: 'medium', timeStyle: 'short' });
        return {
            get: (key, valuesToInsert = {}) => (values[key] || values.error).replace(/\{(\w+)\}/g,
                (fragment, name) => valuesToInsert[name] ?? fragment),
            date: value => formatter.format(new Date(value))
        };
    }

    function actionButton(label, action) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'btn btn-outline';
        button.textContent = label;
        button.addEventListener('click', action);
        return button;
    }

    function line(tag, value) {
        const node = document.createElement(tag);
        node.textContent = value;
        return node;
    }

    async function run(control, status, action, fallback) {
        if (control.disabled) return;
        control.disabled = true;
        status.textContent = '';
        try {
            await action();
        } catch (error) {
            status.textContent = error.message || fallback;
        } finally {
            control.disabled = false;
        }
    }

    function setupPlayer(section) {
        const t = texts(section);
        const form = $(section, '[data-claim-request]');
        const status = $(form, '[data-claim-status]');
        const phone = form.elements.phone;
        const list = $(section, '[data-claim-player-list]');
        const template = $(section, '[data-claim-player-card]');

        function card(receipt) {
            const item = template.content.firstElementChild.cloneNode(true);
            item.dataset.reference = receipt.reference;
            const title = t.get('request', receipt);
            $(item, '[data-claim-number]').textContent = title;
            $(item, '[data-claim-state]').textContent = t.get(receipt.status);
            const time = $(item, 'time');
            time.dateTime = receipt.createdAt;
            time.textContent = t.date(receipt.createdAt);
            const help = { PENDING: 'pendingHelp', NEEDS_DETAILS: 'detailsHelp', MERGED: 'mergedHelp', REJECTED: 'rejectedHelp' };
            $(item, '[data-claim-guidance]').textContent = t.get(help[receipt.status]);
            if (receipt.status === 'MERGED' || receipt.status === 'REJECTED') {
                $(item, '[data-claim-contact]').hidden = true;
                return item;
            }
            const message = t.get('message', receipt);
            const area = $(item, '[data-claim-message]');
            area.value = message;
            const link = $(item, '[data-claim-whatsapp]');
            const support = $(item, '[data-claim-support]');
            support.textContent = t.get('supportUnavailable');
            if (receipt.whatsappUrl) {
                const url = new URL(receipt.whatsappUrl);
                // Открытие чата — обычная ссылка, состояние заявки при этом не меняется.
                url.searchParams.set('text', message);
                link.href = url.href;
                link.hidden = false;
                support.textContent = t.get('support', { phone: '+' + url.pathname.slice(1) });
            }
            $(item, '[data-claim-copy]').addEventListener('click', async () => {
                const hint = $(item, '[data-claim-copy-status]');
                try {
                    await navigator.clipboard.writeText(message);
                    hint.textContent = t.get('copied');
                } catch {
                    $(item, 'details').open = true;
                    area.focus();
                    area.select();
                    hint.textContent = t.get('copyFailed');
                }
            });
            return item;
        }

        async function load() {
            const incoming = await request(form.dataset.endpoint, undefined, t.get('error'));
            list.replaceChildren(...incoming.map(card));
        }

        if (phone.value || location.hash === '#guest-profile-claim') section.open = true;
        const shortcut = document.querySelector('[data-open-guest-claim]');
        shortcut?.addEventListener('click', () => {
            section.open = true;
            phone.focus();
        });
        section.addEventListener('toggle', () => {
            if (section.open) load().catch(error => { status.textContent = error.message; });
        });
        form.addEventListener('submit', event => {
            event.preventDefault();
            const control = $(form, '[type="submit"]');
            run(control, status, async () => {
                const receipt = await request(form.dataset.endpoint, { phone: phone.value }, t.get('error'));
                await load();
                status.textContent = t.get('request', receipt) + ' · ' + t.get(receipt.status);
                nodes(list, '[data-reference]').find(item => item.dataset.reference === receipt.reference)
                    ?.scrollIntoView({ block: 'nearest' });
            }, t.get('error'));
        });
        if (section.open) load().catch(error => { status.textContent = error.message; });
    }

    function setupReview(section, t, onChange) {
        const detail = $(section, '[data-claim-detail]');
        const result = $(detail, '[data-claim-result]');
        const form = $(detail, '[data-claim-merge]');
        const decisions = $(detail, '[data-claim-decisions]');
        const check = $(form, '[data-claim-replace]');
        const sender = $(form, '[data-claim-sender]');
        let current;
        const mergeButton = $(form, '[type="submit"]');
        const fields = $(form, '[data-claim-resolution-fields]');
        const contact = form.elements.contactChoice;

        const syncMethod = () => {
            sender.hidden = form.elements.method.value !== 'WHATSAPP';
            form.elements.sender.required = !sender.hidden;
        };
        function syncAction() {
            if (!current) return;
            const { review, context } = current;
            const forbidden = !review.guest || review.conflicts.length > 0 || review.status === 'REJECTED';
            const terminal = ['MERGED', 'REJECTED'].includes(review.status);
            const manual = !context.reference;
            const replace = review.differences.includes('PHONE_DIFFERS')
                && (!manual || contact.value === 'USE_GUEST_PHONE');
            check.hidden = !replace;
            form.elements.replacePhone.required = replace;
            syncMethod();
            let reason = '';
            if (forbidden) reason = t.get(review.conflicts[0] || 'invalidReview');
            else if (manual && !contact.value) reason = t.get('chooseContact');
            else if (review.differences.includes('PLAYING_SIDE_DIFFERS') && !form.elements.sideChoice.value) reason = t.get('sideHelp');
            else if (!form.elements.method.value) reason = t.get('chooseMethod');
            else if (replace && !form.elements.replacePhone.checked) reason = t.get('replaceHelp');
            else if (!form.elements.verified.checked) reason = t.get('verifyHelp');
            else if (form.elements.method.value === 'WHATSAPP' && !form.elements.sender.value.trim()) reason = t.get('senderHelp');
            fields.disabled = forbidden || terminal;
            mergeButton.disabled = Boolean(reason) || terminal;
            $(detail, '[data-claim-next-step]').textContent = terminal ? '' : reason;
            const state = terminal ? (review.status === 'MERGED' ? 'COMPLETED' : 'FORBIDDEN')
                : forbidden ? 'FORBIDDEN' : reason ? 'NEEDS_RESOLUTION' : 'AVAILABLE';
            $(detail, '[data-claim-readiness]').textContent = t.get(state);
            $(detail, '[data-claim-outcome]').hidden = !review.guest || terminal;
            if (!review.guest) return;
            $(detail, '[data-claim-keep-account]').textContent = t.get('keepAccountHelp', {
                account: review.account.id, level: review.account.level || '—'
            });
            $(detail, '[data-claim-contact-outcome]').textContent = !manual || contact.value === 'USE_GUEST_PHONE'
                ? t.get('useGuestPhoneHelp', { phone: review.guest.phone || '—' })
                : contact.value === 'KEEP_ACCOUNT' || terminal
                    ? t.get('keepContactHelp', { phone: review.account.phone || '—', telegram: review.account.telegram || '—' })
                    : t.get('contactUndecided');
        }
        form.addEventListener('change', syncAction);
        form.addEventListener('input', syncAction);

        function display(review, context, focus = true) {
            current = { review, context };
            detail.hidden = false;
            const heading = $(detail, '[data-claim-title]');
            heading.textContent = review.number ? t.get('request', review) : t.get('manualReviewTitle');
            $(detail, '[data-claim-state]').textContent = context.reference ? t.get(review.status) : '';
            $(detail, '[data-claim-phone]').textContent = review.submittedPhone
                ? t.get('submittedPhone', { phone: review.submittedPhone }) : '';
            for (const side of ['guest', 'account']) {
                const profile = review[side];
                const data = $(detail, `[data-claim-profile="${side}"]`);
                data.hidden = !profile;
                $(data.parentElement, '[data-claim-no-profile]').hidden = Boolean(profile);
                nodes(data, '[data-field]').forEach(field => {
                    const name = field.dataset.field;
                    if (name === 'playingSide') field.textContent = t.get(profile?.playingSide || 'sideUnknown');
                    else if (name === 'profileType') field.textContent = t.get(profile?.guest ? 'profileGuest' : 'profileAccount');
                    else if (name === 'profileState') field.textContent = t.get(profile?.active ? 'profileActive' : 'profileInactive');
                    else field.textContent = profile?.[name] ?? '—';
                });
            }
            const notes = [...new Set([...review.conflicts, ...review.differences, review.reviewReason].filter(Boolean))]
                .filter(note => review.status !== 'MERGED' || !['PLAYING_SIDE_DIFFERS', 'PHONE_DIFFERS', 'GUEST_PHONE_INVALID', 'GUEST_PHONE_MISSING', 'GUEST_PHONE_AMBIGUOUS'].includes(note));
            $(detail, '[data-claim-conflicts]').replaceChildren(...notes.map(note => {
                if (note.startsWith('TOURNAMENT:')) return line('li', t.get('TOURNAMENT', { id: note.split(':')[1] }));
                return line('li', t.get(note));
            }));
            $(detail, '[data-claim-direction]').textContent = review.guest
                ? t.get('direction', { guest: review.guest.id, account: review.account.id }) : '';
            const terminal = ['MERGED', 'REJECTED'].includes(review.status);
            form.hidden = terminal;
            fields.hidden = !review.guest;
            decisions.hidden = terminal || !context.reference;
            form.reset();
            const manual = !context.reference;
            const invalidPhone = review.differences.some(note => ['GUEST_PHONE_INVALID', 'GUEST_PHONE_MISSING'].includes(note));
            const ambiguousPhone = review.differences.includes('GUEST_PHONE_AMBIGUOUS');
            $(form, '[data-claim-contact-choice]').hidden = !manual;
            contact.required = manual;
            contact.querySelector('[value="USE_GUEST_PHONE"]').disabled = invalidPhone || ambiguousPhone;
            contact.value = !invalidPhone && !ambiguousPhone && !review.differences.includes('PHONE_DIFFERS') ? 'USE_GUEST_PHONE' : '';
            form.elements.method.querySelector('[value="WHATSAPP"]').disabled = invalidPhone;
            form.elements.method.value = invalidPhone ? '' : 'WHATSAPP';
            const sideConflict = review.differences.includes('PLAYING_SIDE_DIFFERS');
            form.querySelector('[data-claim-side]').hidden = !sideConflict;
            form.elements.sideChoice.required = sideConflict;
            syncAction();
            if (focus) {
                heading.focus({ preventScroll: true });
                heading.scrollIntoView({ block: 'start' });
            }
        }

        async function refresh(context, focus = true) {
            const review = await request(context.url, undefined, t.get('error'));
            display(review, context, focus);
        }

        form.addEventListener('submit', async event => {
            event.preventDefault();
            const context = current?.context;
            const review = current?.review;
            if (!context || !review?.guest || form.hidden || mergeButton.disabled || !form.reportValidity()) return;
            await run(mergeButton, result, async () => {
                const data = {
                    guestId: review.guest.id, verified: form.elements.verified.checked,
                    verificationMethod: form.elements.method.value,
                    whatsappSenderPhone: form.elements.sender.value || null,
                    replacePhone: form.elements.replacePhone.checked, sideChoice: form.elements.sideChoice.value || null
                };
                if (!context.reference) {
                    data.accountId = review.account.id;
                    data.contactChoice = contact.value;
                }
                fields.disabled = true;
                try {
                    await request(context.submit, data, t.get('error'));
                    await refresh(context);
                    result.textContent = t.get('mergedHelp');
                } catch (error) {
                    await refresh(context, false).catch(() => {});
                    if (current?.review.status === 'MERGED') {
                        result.textContent = t.get('mergedHelp');
                        return;
                    }
                    throw error;
                } finally {
                    await onChange();
                }
            }, t.get('error'));
            syncAction();
        });

        decisions.addEventListener('click', event => {
            const button = event.target.closest('[data-decision]');
            const context = current?.context;
            if (!button || !context?.reference || decisions.hidden) return;
            run(button, result, async () => {
                const review = await request(context.url + '/decision', { decision: button.dataset.decision }, t.get('error'));
                display(review, context);
                await onChange();
            }, t.get('error'));
        });
        return {
            open: async context => {
                current = undefined;
                detail.hidden = true;
                result.textContent = '';
                await refresh(context);
            },
            close: () => { current = undefined; detail.hidden = true; }
        };
    }

    function setupQueue(section) {
        const t = texts(section);
        const endpoint = section.dataset.endpoint;
        const form = $(section, '[data-claim-queue-search]');
        const list = $(section, '[data-claim-queue-list]');
        const status = $(section, '[data-claim-status]');
        const prev = $(section, '[data-claim-prev]');
        const next = $(section, '[data-claim-next]');
        let page = 0;
        let loadId = 0;
        const review = setupReview(section, t, () => load().catch(error => { status.textContent = error.message; }));

        async function load() {
            const id = ++loadId;
            const params = new URLSearchParams({ search: form.elements.search.value, page });
            if (form.elements.status.value) params.set('status', form.elements.status.value);
            const data = await request(endpoint + '?' + params, undefined, t.get('error'));
            if (id !== loadId) return;
            $(section, '[data-claim-count]').textContent = t.get('waiting', { count: data.pendingCount });
            list.replaceChildren(...data.items.map(item => {
                const card = document.createElement('article');
                card.className = 'claim-card';
                card.append(line('h3', t.get('request', item)), line('p', item.accountName + ' · #' + item.accountId),
                    line('p', (item.phone || '—') + ' · ' + t.date(item.createdAt)), line('p', t.get(item.status)));
                card.append(actionButton(t.get('review'), event => run(event.currentTarget, status, async () => {
                    const url = endpoint + '/' + encodeURIComponent(item.reference);
                    await review.open({ url, submit: url + '/approve', reference: item.reference });
                }, t.get('error'))));
                return card;
            }));
            status.textContent = data.items.length ? '' : t.get('empty');
            prev.hidden = page === 0;
            next.hidden = !data.hasMore;
        }

        function changePage(value) {
            page = value;
            review.close();
            load().catch(error => { status.textContent = error.message; });
        }
        form.addEventListener('submit', event => { event.preventDefault(); changePage(0); });
        prev.addEventListener('click', () => changePage(page - 1));
        next.addEventListener('click', () => changePage(page + 1));
        load().catch(error => { status.textContent = error.message; });
    }

    function setupManual(section) {
        const t = texts(section);
        const endpoint = section.dataset.endpoint;
        const control = $(section, '[data-claim-manual-review]');
        const status = $(section, '[data-claim-status]');
        const review = setupReview(section, t, async () => {});
        const chosen = {};

        for (const picker of nodes(section, '[data-claim-picker]')) {
            const side = picker.dataset.claimPicker;
            const form = $(picker, 'form');
            const feedback = $(picker, '[data-claim-choice]');
            const options = $(picker, '[data-claim-options]');
            form.addEventListener('submit', event => {
                event.preventDefault();
                delete chosen[side];
                control.disabled = true;
                review.close();
                options.replaceChildren();
                const search = form.elements.search.value.trim();
                if (!search) { feedback.textContent = t.get('searchHint'); return; }
                run($(form, '[type="submit"]'), feedback, async () => {
                    const params = new URLSearchParams({ search, guest: side === 'guest' });
                    const list = await request(endpoint + '/profiles?' + params, undefined, t.get('error'));
                    if (!list.length) feedback.textContent = t.get('empty');
                    options.replaceChildren(...list.map(player => actionButton(player.name + ' · #' + player.id, () => {
                        chosen[side] = player;
                        feedback.textContent = t.get('pick', player);
                        options.replaceChildren();
                        control.disabled = !(chosen.guest && chosen.account);
                        if (!control.disabled) control.focus();
                    })));
                }, t.get('error'));
            });
        }
        control.addEventListener('click', () => run(control, status, async () => {
            const params = new URLSearchParams({ guestId: chosen.guest.id, accountId: chosen.account.id });
            await review.open({ url: endpoint + '/manual?' + params, submit: endpoint + '/manual' });
        }, t.get('error')));
    }

    const player = document.querySelector('[data-guest-claim="request"]');
    const queue = document.querySelector('[data-guest-claim="review"]');
    const manual = document.querySelector('[data-guest-claim="manual"]');
    if (player) setupPlayer(player);
    if (queue) setupQueue(queue);
    if (manual) setupManual(manual);

    const activate = document.querySelector('[data-claim-activate]');
    activate?.addEventListener('submit', event => {
        event.preventDefault();
        run($(activate, '[type="submit"]'), $(activate, '[data-claim-status]'), async () => {
            await request(activate.dataset.endpoint, Object.fromEntries(new FormData(activate)), activate.dataset.error);
            $(activate, '[data-claim-status]').textContent = activate.dataset.success;
        }, activate.dataset.error);
    });
})();
