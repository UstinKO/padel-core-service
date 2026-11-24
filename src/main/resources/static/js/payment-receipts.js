(function () {
    'use strict';

    function init() {
        const root = document.getElementById('payment-receipts');
        if (!root) return;
        const copy = Object.fromEntries(Array.from(root.querySelector('[data-receipts-copy]').content.children)
            .map(node => [node.dataset.key, node.textContent]));
        const list = root.querySelector('[data-receipts-list]');
        const status = root.querySelector('[data-receipts-status]');
        const filter = root.querySelector('[data-receipts-pending]');
        const reload = root.querySelector('[data-receipts-reload]');
        const paymentForm = document.getElementById('paymentForm');
        const initialPayments = paymentForm ? JSON.stringify(Array.from(new FormData(paymentForm))) : '';
        let receipts = [];
        let busy = false;
        let needsReload = false;

        function element(tag, text, className) {
            const node = document.createElement(tag);
            if (text !== undefined) node.textContent = text;
            if (className) node.className = className;
            return node;
        }

        function report(text, error = false) {
            status.textContent = text;
            status.classList.toggle('receipts-error', error);
        }

        function errorText(error) {
            if (error.status === 401 || error.status === 403) return copy.session;
            if (error.code === 'RECEIPT_PAYMENT_CONFLICT') return copy.conflict;
            if (error.code === 'RECEIPT_UNAVAILABLE') return copy.unavailable;
            return copy.failed;
        }

        async function request(path = '', body) {
            const response = await fetch(root.dataset.receiptsUrl + path, {
                method: body ? 'POST' : 'GET', credentials: 'same-origin', cache: 'no-store',
                headers: { Accept: 'application/json', ...(body ? { 'Content-Type': 'application/json' } : {}) },
                ...(body ? { body: JSON.stringify(body) } : {}),
            });
            if (!response.ok) {
                const problem = await response.json().catch(() => ({}));
                throw { status: response.status, code: problem.code };
            }
            return response.json();
        }

        function setBusy(value) {
            busy = value;
            root.querySelectorAll('button').forEach(button => { button.disabled = value || needsReload; });
            reload.disabled = false;
        }

        function lockPaymentForm() {
            needsReload = true;
            reload.hidden = false;
            if (paymentForm) Array.from(paymentForm.elements).forEach(field => { field.disabled = true; });
        }

        async function decide(receipt, action, body) {
            if (busy || needsReload) return;
            if (action === 'accept' && paymentForm && JSON.stringify(Array.from(new FormData(paymentForm))) !== initialPayments) {
                report(copy.dirty, true);
                return;
            }
            setBusy(true);
            if (action === 'accept') lockPaymentForm();
            try {
                const updated = await request('/' + encodeURIComponent(receipt.id) + '/' + action, body);
                receipts = receipts.map(item => item.id === updated.id ? updated : item);
                render();
                report(copy.saved);
                if (action === 'accept') window.location.reload();
            } catch (error) {
                report(errorText(error) + (needsReload ? ' ' + copy.reload : ''), true);
            } finally {
                setBusy(false);
            }
        }

        function field(form, labelText, control) {
            const label = element('label', labelText);
            label.append(control);
            form.append(label);
            return control;
        }

        function approval(receipt) {
            const form = element('form', undefined, 'receipt-review');
            const amount = field(form, copy.amount, element('input'));
            Object.assign(amount, { type: 'number', min: '0.01', max: '99999999.99', step: '0.01', required: true });
            amount.inputMode = 'decimal';
            const currency = field(form, copy.currency, element('select'));
            ['ARS', 'USD', 'USDT'].forEach(value => {
                const option = element('option', value); option.value = value; currency.append(option);
            });
            const method = field(form, copy.method, root.querySelector('[data-receipts-methods]').content.querySelector('select').cloneNode(true));
            method.required = true;
            const button = element('button', copy.accept, 'btn btn-primary');
            button.type = 'submit'; form.append(button);
            form.addEventListener('submit', event => {
                event.preventDefault();
                if (form.reportValidity()) decide(receipt, 'accept', { amount: amount.value, currency: currency.value, method: method.value });
            });
            return form;
        }

        function rejection(receipt) {
            const details = element('details', undefined, 'receipt-reject');
            details.append(element('summary', copy.reject));
            const form = element('form');
            const reason = field(form, copy.reason, element('textarea'));
            Object.assign(reason, { required: true, maxLength: 500, rows: 2 });
            const button = element('button', copy.reject, 'btn btn-secondary');
            button.type = 'submit'; form.append(button); details.append(form);
            form.addEventListener('submit', event => {
                event.preventDefault();
                if (form.reportValidity() && reason.value.trim()) decide(receipt, 'reject', { reason: reason.value.trim() });
            });
            return details;
        }

        function fileActions(receipt, card) {
            const actions = element('div', undefined, 'receipt-actions');
            const fileUrl = root.dataset.receiptsUrl + '/' + encodeURIComponent(receipt.id) + '/file';
            const download = element('a', copy.download);
            Object.assign(download, { href: fileUrl, target: '_blank', rel: 'noopener noreferrer' });
            actions.append(download);
            if (receipt.contentType === 'image/jpeg' || receipt.contentType === 'image/png') {
                const button = element('button', copy.view, 'btn btn-secondary btn-sm');
                button.type = 'button';
                button.addEventListener('click', () => {
                    if (card.querySelector('.receipt-preview')) return;
                    const image = element('img', undefined, 'receipt-preview');
                    image.alt = copy.title || copy.view;
                    image.src = fileUrl;
                    image.addEventListener('error', () => { image.remove(); report(copy.imageError, true); });
                    actions.after(image);
                });
                actions.prepend(button);
            }
            card.append(actions);
        }

        function card(receipt) {
            const article = element('article', undefined, 'receipt-card');
            article.id = 'receipt-' + receipt.id;
            const header = element('div', undefined, 'receipt-card-header');
            const title = element('div');
            title.append(element('h3', receipt.playerName));
            const when = element('time', new Intl.DateTimeFormat(document.documentElement.lang || 'es',
                { dateStyle: 'short', timeStyle: 'short' }).format(new Date(receipt.createdAt)));
            when.dateTime = receipt.createdAt; title.append(when);
            const state = receipt.state.toLowerCase();
            header.append(title, element('span', copy[state] || copy.pending, 'receipt-state ' + state));
            article.append(header);
            fileActions(receipt, article);
            if (receipt.rejectionReason) article.append(element('p', receipt.rejectionReason, 'receipt-reason'));
            if (receipt.state === 'PENDING' && receipt.currentCycle) {
                article.append(approval(receipt), rejection(receipt));
            } else if (!receipt.currentCycle) {
                article.append(element('p', copy.outdated, 'receipt-outdated'));
            }
            return article;
        }

        function rowLinks() {
            document.querySelectorAll('[data-receipt-registration]').forEach(link => {
                const own = receipts.filter(receipt => String(receipt.registrationId) === link.dataset.receiptRegistration);
                link.hidden = own.length === 0;
                link.textContent = copy.row + ': ' + own.length;
                if (own.length) link.href = '#receipt-' + own[0].id;
                link.onclick = () => { filter.checked = false; render(); };
            });
        }

        function render() {
            list.replaceChildren();
            const visible = receipts.filter(receipt => !filter.checked || receipt.state === 'PENDING');
            if (!visible.length) list.append(element('p', copy.empty));
            visible.forEach(receipt => list.append(card(receipt)));
            rowLinks();
        }

        async function load() {
            if (busy || needsReload) return;
            setBusy(true); report(copy.loading);
            try {
                receipts = await request(); render(); report('');
            } catch (error) {
                report(errorText(error), true);
            } finally { setBusy(false); }
        }

        if (paymentForm) paymentForm.addEventListener('submit', event => {
            if (needsReload) { event.preventDefault(); report(copy.reload, true); }
        });
        root.querySelector('[data-receipts-refresh]').addEventListener('click', load);
        reload.addEventListener('click', () => window.location.reload());
        filter.addEventListener('change', render);
        load();
    }

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
    else init();
})();
