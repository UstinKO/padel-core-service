/** Общий фильтр уровней публичного каталога и кабинета. */
window.PadelLevels = Object.freeze({
    eventName(value) {
        return String(value ?? '').replace(/(^|[^\p{L}\p{N}_])TODOS(?![\p{L}\p{N}_])/gu,
            (_, prefix) => prefix + t('enum.nivel.todos'));
    },
    matches(eventLevel, selected) {
        const level = selected === 'Principiante' ? 'PRINCIPIANTES' : selected;
        return eventLevel === level || (/^[CD]\d+$/.test(level) && (eventLevel || '').split('_').includes(level));
    }
});
