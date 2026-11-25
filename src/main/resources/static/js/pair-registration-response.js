window.PairRegistrationResponse = {
    async read(response) {
        return response.json().catch(() => ({}));
    },

    message(response, data) {
        if (response.status === 401 || response.status === 403 || response.redirected) {
            return t('pair.registration.sign_in');
        }
        if (response.status === 409) {
            return t('pair_terms.retry');
        }
        const message = data.code && data.message ? data.message : t('pair.registration.failed');
        return data.requestId ? message + ' (' + t('pair.registration.request_id') + ' ' + data.requestId + ')' : message;
    }
};
