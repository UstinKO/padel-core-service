const iso = date => date.toISOString().slice(0, 10);
export const dateValue = value => new Date(`${value}T00:00:00Z`);
export function addDays(value, days) {
    const date = dateValue(value);
    date.setUTCDate(date.getUTCDate() + days);
    return iso(date);
}
export function shiftMonth(month, step) {
    const date = dateValue(`${month}-01`);
    date.setUTCMonth(date.getUTCMonth() + step);
    return iso(date).slice(0, 7);
}
export function monthDays(month) {
    const first = `${month}-01`;
    const offset = (dateValue(first).getUTCDay() + 6) % 7;
    const last = addDays(`${shiftMonth(month, 1)}-01`, -1);
    const length = Math.ceil((Number(last.slice(-2)) + offset) / 7) * 7;
    return Array.from({length}, (_, index) => addDays(first, index - offset));
}
export function eventsOnDay(events, day) {
    return events.filter(event => {
        let end = event.endDate || event.date;
        if (event.endTime === '00:00:00' && end > event.date) end = addDays(end, -1);
        return event.date <= day && day <= end;
    }).sort((a, b) => {
        const aTime = a.date < day ? '' : (a.time || '99:99');
        const bTime = b.date < day ? '' : (b.time || '99:99');
        return aTime.localeCompare(bTime) || a.id - b.id;
    });
}
