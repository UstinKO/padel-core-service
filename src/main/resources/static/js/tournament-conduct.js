(function () {
    const attempts = new Map();
    const status = () => document.getElementById('conduct-save-status');

    function announce(state) {
        const node = status();
        if (node) node.textContent = node.dataset[state] || '';
    }

    window.conductFetch = async function (url, options) {
        const attempt = JSON.stringify([url, options.method, options.body]);
        const headers = new Headers(options.headers);
        if (!attempts.has(attempt)) attempts.set(attempt, crypto.randomUUID());
        headers.set('Idempotency-Key', attempts.get(attempt));
        announce('saving');
        try {
            const response = await fetch(url, { ...options, headers });
            if (response.redirected) throw new Error('Tournament save redirected');
            const isJson = response.headers.get('Content-Type')?.includes('json');
            const result = isJson ? await response.clone().json() : null;
            if (response.headers.has('X-Conduct-Replayed')) {
                announce('replayed');
            } else if (response.headers.has('X-Conduct-Stale')) {
                announce('stale');
            } else if (response.ok && result?.success !== false) {
                attempts.delete(attempt);
                announce('saved');
            } else {
                announce('error');
            }
            return response;
        } catch (error) {
            announce('error');
            throw error;
        }
    };

    document.addEventListener('submit', event => {
        const form = event.target;
        if (event.defaultPrevented || form.method !== 'post' || !status()) return;
        let key = form.elements.namedItem('conductRequestKey');
        if (!key) {
            key = document.createElement('input');
            key.type = 'hidden';
            key.name = 'conductRequestKey';
            key.value = crypto.randomUUID();
            form.appendChild(key);
        }
        announce('saving');
    });
})();
