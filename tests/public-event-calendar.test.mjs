import test from 'node:test';
import assert from 'node:assert/strict';
import {readState, commonUrl, safeReturn, monthDays, shiftMonth, dayEvents, extraCount,
    visibleEvents, calendarQuery, homeCalendarState} from '../src/main/resources/static/js/public-event-calendar-state.mjs';
for (const [month, length] of [['2027-02',28],['2026-11',42],['2026-04',35]]) {
    test(`Сетка ${month}: ${length} дней`, () => assert.equal(monthDays(month).length,length));
}
test('Раздел открывает только календарь, прежний view=cards не создаёт второй каталог', () => {
    assert.equal(readState('').view,'calendar');
    assert.equal(readState('?view=cards').view,'calendar');
    assert.equal(readState('?view=calendar').view,'calendar');
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
test('Быстрые форматы: турниры не включают тренировки и Cancha Abierta', () => {
    const events=['KING_OF_COURT','AMERICANO_INDIVIDUAL','AMERICANO_PAIRS','PADEL_CLINIC','CANCHA_ABIERTA']
        .map((format,id)=>({id,format,date:'2026-12-05'}));
    assert.deepEqual(visibleEvents(events,new Map(),false,'','TOURNAMENTS').map(row=>row.id),[0,1,2]);
    assert.deepEqual(visibleEvents(events,new Map(),false,'','AMERICANO_ALL').map(row=>row.id),[1,2]);
    assert.deepEqual(visibleEvents(events,new Map(),false,'','PADEL_CLINIC').map(row=>row.id),[3]);
    assert.deepEqual(visibleEvents(events,new Map(),false,'','CANCHA_ABIERTA').map(row=>row.id),[4]);
    assert.equal(dayEvents(visibleEvents(events,new Map(),false,'','TOURNAMENTS'),'2026-12-05').length,3);
});
test('Группа турниров остаётся в общей ссылке, API получает только существующие форматы', () => {
    const state=readState('?format=TOURNAMENTS&month=2026-12&level=C7&status=CERRADO');
    assert.match(commonUrl(state),/format=TOURNAMENTS/);
    assert.equal(calendarQuery(state),'level=C7&month=2026-12');
    assert.equal(calendarQuery({...state,format:'PADEL_CLINIC'}),'level=C7&format=PADEL_CLINIC&month=2026-12');
});
for (const [type,modality,format] of [
    ['AMERICANO','','AMERICANO_ALL'],['AMERICANO','DOBLES','AMERICANO_PAIRS'],
    ['AMERICANO','INDIVIDUAL','AMERICANO_INDIVIDUAL'],['AMERICANO_TEAMS','','AMERICANO_PAIRS'],
    ['KING_OF_COURT','','KING_OF_COURT'],['PADEL_CLINIC','','PADEL_CLINIC'],
    ['CANCHA_ABIERTA','','CANCHA_ABIERTA'],['todos','','']]) {
    test(`Переход с главной переносит совместимые условия ${type}/${modality}`,()=>{
        const state=homeCalendarState({type,modality,level:'C7',gender:'MIXTO'},'ru');
        assert.equal(state.view,'calendar'); assert.equal(state.format,format);
        assert.equal(state.level,'C7'); assert.equal(state.gender,'MIXTO'); assert.equal(state.lang,'ru');
        assert.equal(state.month,'');
    });
}
test('Пустые фильтры главной не превращаются в ограничения календаря',()=>{
    const state=homeCalendarState({type:'todos',level:'todos',gender:'todos'},'es');
    for(const key of ['format','level','gender']) assert.equal(state[key],'');
});
