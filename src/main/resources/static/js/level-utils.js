/** Общий фильтр уровней публичного каталога и кабинета. */
window.PadelLevels = Object.freeze({
    matches(eventLevel, selected) {
        const level = selected === 'Principiante' ? 'PRINCIPIANTES' : selected;
        return eventLevel === level || (/^[CD]\d+$/.test(level) && (eventLevel || '').split('_').includes(level));
    }
});
