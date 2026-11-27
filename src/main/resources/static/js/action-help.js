(() => {
    'use strict';
    let active;
    let pinned = false;
    let closeTimer;
    let suppressFocus = false;
    let lastPointer = {x: 0, y: 0};
    let dismissedPointer;
    let id = 0;
    const panel = document.createElement('div');
    panel.className = 'action-help-popover';
    panel.hidden = true;
    panel.id = 'action-help-popover';
    panel.setAttribute('role', 'dialog');
    panel.setAttribute('aria-modal', 'false');
    panel.setAttribute('aria-labelledby', 'action-help-title');
    panel.setAttribute('aria-describedby', 'action-help-text');
    panel.setAttribute('popover', 'manual');
    const header = document.createElement('div');
    header.className = 'action-help-popover__header';
    const title = document.createElement('h3');
    title.id = 'action-help-title';
    const close = document.createElement('button');
    close.type = 'button';
    close.textContent = '×';
    const text = document.createElement('p');
    text.id = 'action-help-text';
    header.append(title, close);
    panel.append(header, text);
    document.body.append(panel);

    function position() {
        if (!active || panel.hidden) return;
        const anchor = active.getBoundingClientRect();
        const view = window.visualViewport;
        const width = view?.width || document.documentElement.clientWidth;
        const height = view?.height || window.innerHeight;
        panel.style.maxHeight = `${Math.max(44, height - 24)}px`;
        const box = panel.getBoundingClientRect();
        const leftEdge = view?.offsetLeft || 0;
        const topEdge = view?.offsetTop || 0;
        const left = Math.max(leftEdge + 12, Math.min(anchor.right - box.width, leftEdge + width - box.width - 12));
        const below = anchor.bottom + 9;
        const top = below + box.height <= topEdge + height - 12 ? below : anchor.top - box.height - 9;
        panel.style.left = `${left}px`;
        panel.style.top = `${Math.max(topEdge + 12, Math.min(top, topEdge + height - box.height - 12))}px`;
    }

    function hide(returnFocus = false) {
        clearTimeout(closeTimer);
        if (!active) return;
        const previous = active;
        if (returnFocus) dismissedPointer = {...lastPointer};
        previous.setAttribute('aria-expanded', 'false');
        previous.removeAttribute('aria-controls');
        active = undefined;
        pinned = false;
        if (panel.hidePopover && panel.matches(':popover-open')) panel.hidePopover();
        panel.hidden = true;
        if (returnFocus && previous.isConnected) {
            suppressFocus = true;
            previous.focus({preventScroll: true});
            suppressFocus = false;
        }
    }

    function show(trigger, pin = false) {
        clearTimeout(closeTimer);
        dismissedPointer = undefined;
        if (active !== trigger) {
            hide();
            active = trigger;
            const container = trigger.closest('dialog[open]') || document.body;
            if (panel.parentElement !== container) container.append(panel);
            if (!trigger.id) trigger.id = `action-help-trigger-${++id}`;
            title.textContent = trigger.dataset.helpTitle;
            text.textContent = trigger.dataset.helpText;
            close.setAttribute('aria-label', trigger.dataset.helpClose);
        }
        pinned = pinned || pin;
        trigger.setAttribute('aria-expanded', 'true');
        trigger.setAttribute('aria-controls', panel.id);
        panel.hidden = false;
        if (panel.showPopover && !panel.matches(':popover-open')) panel.showPopover();
        position();
        if (pin) close.focus({preventScroll: true});
    }

    document.addEventListener('pointerover', event => {
        if (event.pointerType !== 'mouse') return;
        // Исчезнувшее окно может вызвать pointerover под неподвижным указателем.
        if (dismissedPointer && event.clientX === dismissedPointer.x && event.clientY === dismissedPointer.y) return;
        lastPointer = {x: event.clientX, y: event.clientY};
        const trigger = event.target.closest('[data-action-help]');
        if (trigger) show(trigger);
        if (panel.contains(event.target)) clearTimeout(closeTimer);
    });
    document.addEventListener('pointermove', event => {
        if (event.pointerType !== 'mouse') return;
        lastPointer = {x: event.clientX, y: event.clientY};
        if (!dismissedPointer || (event.clientX === dismissedPointer.x && event.clientY === dismissedPointer.y)) return;
        dismissedPointer = undefined;
        const trigger = event.target.closest('[data-action-help]');
        if (trigger) show(trigger);
    });
    document.addEventListener('pointerout', event => {
        if (!active || pinned || event.pointerType !== 'mouse') return;
        const next = event.relatedTarget;
        if (next && (active.contains(next) || panel.contains(next))) return;
        closeTimer = setTimeout(() => hide(), 220);
    });
    document.addEventListener('click', event => {
        const trigger = event.target.closest('[data-action-help]');
        if (trigger) {
            event.preventDefault();
            event.stopPropagation();
            if (active === trigger && pinned) hide(true);
            else show(trigger, true);
        } else if (!panel.contains(event.target)) hide();
    }, true);
    document.addEventListener('focusin', event => {
        if (suppressFocus) return;
        const trigger = event.target.closest('[data-action-help]');
        if (trigger) show(trigger);
        else if (!panel.contains(event.target)) hide();
    });
    document.addEventListener('keydown', event => {
        if (active && event.key === 'Escape') {
            event.preventDefault();
            event.stopImmediatePropagation();
            hide(true);
        }
    }, true);
    close.addEventListener('click', () => hide(true));
    window.addEventListener('resize', position);
    document.addEventListener('scroll', position, true);
    window.visualViewport?.addEventListener('resize', position);
    window.visualViewport?.addEventListener('scroll', position);
    new MutationObserver(() => {
        if (active && !active.isConnected) hide();
    }).observe(document.body, {childList: true, subtree: true});

    function formHelp() {
        const type = document.getElementById('tipo');
        const mode = document.getElementById('modalidad');
        if (!type || !mode) return;
        const paired = type.value === 'AMERICANO_TEAMS' || (type.value === 'AMERICANO' && mode.value === 'DOBLES');
        const individual = type.value === 'KING_OF_COURT' || (type.value === 'AMERICANO' && mode.value !== 'DOBLES');
        document.querySelectorAll('[data-event-help]').forEach(element => { element.hidden = !paired && !individual; });
        document.querySelectorAll('[data-individual-help]').forEach(element => { element.hidden = !individual; });
        document.querySelectorAll('[data-pair-help]').forEach(element => { element.hidden = !paired && !(element.hasAttribute('data-pair-help-chooser') && !type.value); });
        if (active && active.closest('[hidden]')) hide();
    }
    document.addEventListener('change', formHelp);
    formHelp();
})();
