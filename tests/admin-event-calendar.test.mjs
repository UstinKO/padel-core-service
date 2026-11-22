import test from 'node:test';
import assert from 'node:assert/strict';
import {monthDays,shiftMonth,eventsOnDay} from '../src/main/resources/static/js/admin-event-calendar-dates.mjs';

for (const [month,length] of [['2026-02',28],['2040-02',29],['2026-04',30],['2026-12',31]]) {
    test(`Полный месяц ${month}, с понедельника`, () => {
        const days = monthDays(month);
        assert.equal(new Date(`${days[0]}T00:00:00Z`).getUTCDay(),1);
        assert.equal(days.length % 7,0);
        assert.equal(days.filter(day=>day.startsWith(month)).length,length);
        assert.equal(new Set(days).size,days.length);
    });
}
test('Декабрь и январь сохраняют правильный год',()=>{
    assert.equal(shiftMonth('2026-12',1),'2027-01');
    assert.equal(shiftMonth('2027-01',-1),'2026-12');
});
test('События по местной дате не зависят от зоны устройства',()=>{
    const original = process.env.TZ;
    for (const zone of ['Asia/Tokyo','America/Los_Angeles','America/Argentina/Buenos_Aires']) {
        process.env.TZ = zone;
        assert.equal(monthDays('2040-02').filter(day=>day.startsWith('2040-02')).length,29);
        const rows = eventsOnDay([{id:1,date:'2026-11-22',time:'23:30:00',timeZone:'America/Argentina/Buenos_Aires'}],'2026-11-22');
        assert.equal(rows.length,1);
    }
    process.env.TZ = original;
});
test('Все одновременные события присутствуют и сортируются по времени и ID',()=>{
    const rows=[{id:3,date:'2026-11-22',time:'15:00:00'},{id:2,date:'2026-11-22',time:'14:00:00'},{id:1,date:'2026-11-22',time:'14:00:00'}];
    assert.deepEqual(eventsOnDay(rows,'2026-11-22').map(row=>row.id),[1,2,3]);
});
test('Многодневное событие присутствует до известного конца, полночь исключительна',()=>{
    const rows=[{id:1,date:'2026-11-30',time:'23:30:00',endDate:'2026-12-01',endTime:'01:30:00'}];
    assert.equal(eventsOnDay(rows,'2026-12-01').length,1);
    assert.equal(eventsOnDay(rows,'2026-12-02').length,0);
    rows[0].endTime='00:00:00';
    assert.equal(eventsOnDay(rows,'2026-12-01').length,0);
});
test('Неизвестное окончание не добавляет следующий день',()=>{
    const rows=[{id:1,date:'2026-11-22',time:'23:30:00'}];
    assert.equal(eventsOnDay(rows,'2026-11-23').length,0);
});
