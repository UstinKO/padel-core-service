import test from 'node:test';
import assert from 'node:assert/strict';
import {readState, commonUrl, safeReturn, monthDays, shiftMonth, dayEvents, extraCount,
    visibleEvents, cardOrder} from '../src/main/resources/static/js/public-event-calendar-state.mjs';
for (const [month, length] of [['2027-02',28],['2026-11',42],['2026-04',35]]) {
    test(`Сетка ${month}: ${length} дней`, () => assert.equal(monthDays(month).length,length));
}
test('Первый визит — карточки, явная ссылка перекрывает запомненный вид', () => {
    assert.equal(readState('').view,'cards');
    assert.equal(readState('', 'calendar').view,'calendar');
    assert.equal(readState('?view=cards','calendar').view,'cards');
});
test('Общие фильтры и выбранный день сохраняются в ссылке без личных данных', () => {
    const state = readState('?view=calendar&month=2026-12&day=2026-12-05&level=C7&format=AMERICANO_PAIRS&clubId=12&organizerId=42&gender=MIXTO&lang=es&myOnly=true&playerId=8');
    const url=commonUrl({...state,myOnly:true,playerId:8});
    assert.deepEqual(readState(new URL(url,'https://example.test').search),state);
    assert.doesNotMatch(url,/myOnly|playerId/);
});
test('Некорректные даты и идентификаторы не попадают в запрос', () => {
    const state=readState('?month=2026-99&day=2026-02-30&clubId=-1&organizerId=0&format=%3Cscript%3E');
    for (const key of ['month','day','clubId','organizerId','format']) assert.equal(state[key],'');
});
test('Возврат ограничен маршрутом турниров того же сайта и белым списком параметров', () => {
    assert.equal(safeReturn('https://evil.test/torneos','https://one.test'),'');
    assert.equal(safeReturn('/admin?view=calendar','https://one.test'),'');
    assert.equal(safeReturn('/torneos?view=calendar&myOnly=1&playerId=99&level=TODOS','https://one.test'),'/torneos?view=calendar&level=TODOS');
});
test('Границы года и дата события не зависят от часового пояса устройства', () => {
    assert.equal(shiftMonth('2026-12',1),'2027-01');
    assert.equal(shiftMonth('2027-01',-1),'2026-12');
    const saved=process.env.TZ;
    for(const zone of ['Asia/Tokyo','America/Los_Angeles','America/Argentina/Buenos_Aires']) {
        process.env.TZ=zone;
        assert.equal(monthDays('2027-02')[0],'2027-02-01');
        assert.equal(dayEvents([{id:1,date:'2026-12-05',time:'23:30:00'}],'2026-12-05').length,1);
    }
    process.env.TZ=saved;
});
test('0/1/3/5 и 65 мероприятий используют полный список, одинаковые старты не схлопываются', () => {
    for (const count of [0,1,3,5,65]) {
        const events=Array.from({length:count},(_,i)=>({id:count-i,date:'2026-12-05',time:'14:00:00'}));
        const rows=dayEvents(events,'2026-12-05');
        assert.equal(rows.length,count); assert.equal(extraCount(rows),Math.max(0,count-3));
        assert.deepEqual(rows.map(row=>row.id),Array.from({length:count},(_,i)=>i+1));
    }
});
test('Личные статусы и статус события одинаково ограничивают сетку, счётчик и список дня', () => {
    const events=[{id:1,date:'2026-12-05',status:'REGISTRO_ABIERTO'},{id:2,date:'2026-12-05',status:'REGISTRO_ABIERTO'},{id:3,date:'2026-12-05',status:'CERRADO'}];
    const own=new Map([[1,'WAITLIST'],[3,'SOLO_SEARCH']]);
    assert.deepEqual(visibleEvents(events,own,true,'REGISTRO_ABIERTO').map(row=>row.id),[1]);
    assert.equal(dayEvents(visibleEvents(events,own,true,''),'2026-12-05').length,2);
    assert.equal(visibleEvents(events,new Map(),true,'').length,0);
});
test('Карточки: ближайшие впереди, прошлые доступны в конце', () => {
    const events=[{id:1,date:'2026-11-20'},{id:2,date:'2026-12-10'},{id:3,date:'2026-11-25'},{id:4,date:'2026-11-21'}];
    assert.deepEqual(cardOrder(events,'2026-11-24').map(row=>row.id),[3,2,4,1]);
});
