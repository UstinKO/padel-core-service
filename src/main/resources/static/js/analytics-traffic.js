(function () {
    'use strict';

    var storageKey = 'padel.analytics.testUntil';
    var duration = 48 * 60 * 60 * 1000;
    var reasons = {
        visitor: ['public'],
        internal: ['staff_account', 'admin_page'],
        test: ['test_environment', 'test_event']
    };

    function testMode(url) {
        var choice = url.searchParams.get('analytics_test');
        var enabled = choice === 'on';
        try {
            if (choice === 'off') {
                window.localStorage.removeItem(storageKey);
                return false;
            }
            if (enabled) {
                window.localStorage.setItem(storageKey, String(Date.now() + duration));
                return true;
            }
            var until = Number(window.localStorage.getItem(storageKey));
            enabled = Number.isFinite(until) && until > Date.now() && until <= Date.now() + duration;
            if (!enabled) window.localStorage.removeItem(storageKey);
        } catch (error) {
            // Заблокированное хранилище не должно мешать странице и текущему явному выбору.
        }
        return enabled;
    }

    window.PadelAnalyticsTraffic = function (group, reason, url) {
        var manual = testMode(url);
        url.searchParams.delete('analytics_test');
        if (manual) {
            group = 'test';
            reason = 'manual_test';
        } else if (!Object.hasOwn(reasons, group) || !reasons[group].includes(reason)) {
            group = 'unclassified';
            reason = 'missing_context';
        }
        return {
            traffic_group: group,
            traffic_reason: reason,
            traffic_type: group === 'visitor' ? 'external' : group === 'unclassified' ? 'unclassified' : 'internal'
        };
    };
})();
