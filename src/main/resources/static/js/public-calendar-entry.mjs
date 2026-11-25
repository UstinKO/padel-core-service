import {homeCalendarState, commonUrl} from './public-event-calendar-state.mjs';
const entry = document.getElementById('publicCalendarEntry');
const fields = {level:'nivelFilter',gender:'generoFilter',type:'tipoFilter',modality:'modalidadFilter'};
function updateEntry() {
    const filters = Object.fromEntries(Object.entries(fields).map(([key, id]) =>
        [key, document.getElementById(id)?.value || '']));
    entry.href = commonUrl(homeCalendarState(filters, document.documentElement.lang));
}
if (entry) {
    updateEntry();
    for (const id of Object.values(fields)) document.getElementById(id)?.addEventListener('change', updateEntry);
    entry.addEventListener('click', updateEntry);
}
