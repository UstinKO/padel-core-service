(() => {
    'use strict';
    const form = document.querySelector('[data-join-form]');
    if (!form) return;
    const button = form.querySelector('[data-join-submit]');
    const label = button.textContent;
    let submitted = false;
    form.addEventListener('submit', event => {
        if (submitted) { event.preventDefault(); return; }
        submitted = true;
        button.disabled = true;
        button.textContent = button.dataset.opening;
        form.setAttribute('aria-busy', 'true');
    });
    window.addEventListener('pageshow', () => {
        submitted = false;
        button.disabled = false;
        button.textContent = label;
        form.removeAttribute('aria-busy');
    });
})();
