import test from 'node:test';
import assert from 'node:assert/strict';
import {demoRegistrationGateway as gateway} from '../src/services/registration.ts';
import {findEvent} from '../src/data/events.ts';
import {scenarioState,scenarios} from '../src/data/scenarios.ts';
const user=scenarioState('player').user;
const pairEvent=findEvent('pairs');
test('pair solo modes remain solo until explicitly completed',async()=>{const a=await gateway.register(pairEvent,user,[],{mode:'search'});const b=await gateway.register(pairEvent,user,[],{mode:'add-later'});assert.equal(a.status,'solo-search');assert.equal(a.allowContact,false);assert.equal(b.status,'solo-later');assert.ok(b.partnerDeadline);assert.equal(b.partner,undefined)});
test('full pair tournament accepts all three modes; incomplete entries do not claim a ready-pair position',async()=>{
 const full={...pairEvent,filled:20};
 for(const mode of ['search','add-later']) { const r=await gateway.register(full,user,[],{mode,allowContact:true}); assert.equal(r.status,'waiting'); assert.equal(r.partnerMode,mode); assert.equal(r.partner,undefined); assert.equal(r.position,undefined); assert.equal(r.allowContact,true); }
 const r=await gateway.register(full,user,[],{mode:'with-partner',partner:'Алексей Петров',partnerId:'alex'});assert.equal(r.status,'waiting');assert.equal(r.position,3);
});
test('promotion occupies the main roster before attendance acknowledgement and has no expiry',()=>{ const r=scenarioState('offer').registrations[0];assert.equal(r.status,'confirmed');assert.equal(r.participationStatus,'pending');assert.equal(r.promotedFromReserve,true);assert.equal(r.offerExpiresAt,undefined);assert.equal(r.position,undefined); });
test('after partner deadline add-later enters reserve without confirming a pair',async()=>{const r=await gateway.register({...pairEvent,partnerDeadlinePassed:true},user,[],{mode:'add-later'});assert.equal(r.status,'waiting');assert.equal(r.partner,undefined)});
test('cancelled entry allows a new registration and self-partner is rejected',async()=>{const e=findEvent('king');const r=await gateway.register(e,user,[{eventId:e.id,status:'cancelled',createdAt:''}]);assert.equal(r.status,'confirmed');await assert.rejects(gateway.register(pairEvent,user,[],{mode:'with-partner',partner:'Даниил Медведев',partnerId:user.id}),/себя/)});
test('preview scenario IDs are unique and protected examples have complete demo contact',()=>{assert.equal(new Set(scenarios.map(s=>s.id)).size,scenarios.length);for(const s of scenarios.filter(s=>s.path.startsWith('/book/'))){assert.ok(scenarioState(s.fixture).user.phone)};assert.equal(scenarioState('google').user.phone,undefined);assert.equal(scenarioState('guest').user,null)});
