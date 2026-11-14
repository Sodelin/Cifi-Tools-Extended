/* Pure, partial mapping into CIFI Tools native state. No I/O. */
(function (root) {
'use strict';
const CATEGORIES = new Set(['relics','inscryptions','diamondcards','shardmilestones','researches','ultima','diamondspecials','cms','gadgets','loopmods','iap','mats_exchange']);
const HUNTERS = ['borge','ozzy','knox'];
function clone(v) {
  return JSON.parse(JSON.stringify(v, (_key,value)=>{
    if ((typeof value === 'number' && (!Number.isFinite(value) || Object.is(value,-0))) ||
        value === undefined || typeof value === 'bigint' || typeof value === 'function' || typeof value === 'symbol')
      throw Error('Destination cannot be cloned without losing values');
    return value;
  }));
}
// This catches unsafe integers presented to any projection entry point. It
// cannot recover source digits already rounded or establish fractional exactness.
function nonnegative(n) { return typeof n === 'number' && Number.isFinite(n) && n >= 0 && (!Number.isInteger(n) || Number.isSafeInteger(n)); }
function objectMap(v) { return !!v && typeof v === 'object' && !Array.isArray(v); }
function safeKey(key) { return typeof key === 'string' && key !== '__proto__' && key !== 'constructor' && key !== 'prototype'; }
function values(obj) {
  if (!obj || typeof obj !== 'object' || Array.isArray(obj)) throw Error('Expected a numeric field map');
  if (!Object.keys(obj).every(safeKey)) throw Error('Unsafe mapped key');
  if (!Object.values(obj).every(nonnegative)) throw Error('Invalid mapped number');
}
function projectCore(snapshot, mapped, uuid, selectedHunters) {
  if (!snapshot?.authenticated || snapshot.origin !== 'https://cifi-tools.com') throw Error('Wrong or unauthenticated origin');
  if (typeof uuid !== 'function') throw Error('Missing unique ID provider');
  const hunter=clone(snapshot.hunter), gems=clone(snapshot.gems);
  if (!objectMap(hunter) || !objectMap(gems) || !objectMap(hunter.upgrades) || !objectMap(hunter.hunterStats) || !objectMap(hunter.hunterBuilds) || !objectMap(gems.gemStates)) throw Error('Unexpected native store shape');
  if (!mapped || !objectMap(mapped.perHunter) || !objectMap(mapped.gems)) throw Error('Unexpected mapped store shape');
  values(mapped.globalUpgrades);
  for (const [key,val] of Object.entries(mapped.globalUpgrades)) {
    const dot=key.indexOf('.'), category=key.slice(0,dot), id=key.slice(dot+1);
    if (dot<1 || !id || !safeKey(id) || !CATEGORIES.has(category) || !objectMap(hunter.upgrades[category])) throw Error('Unsupported upgrade');
    hunter.upgrades[category][id]=val;
  }
  for (const name of selectedHunters) {
    const source=mapped.perHunter[name];
    if (!source || !nonnegative(source.level)) throw Error('Hunter level unavailable');
    values(source.hunterStats); values(source.talents); values(source.attributes);
    if (Object.keys(source.hunterStats).length) {
      if (Object.hasOwn(hunter.hunterStats,name)) {
        const existing=hunter.hunterStats[name];
        if (!existing || typeof existing !== 'object' || Array.isArray(existing)) throw Error('Invalid existing hunter stats');
      }
      hunter.hunterStats[name] = {...(hunter.hunterStats[name] || {}), ...source.hunterStats};
    }
    const builds = Object.hasOwn(hunter.hunterBuilds,name) ? hunter.hunterBuilds[name] : [];
    if (!Array.isArray(builds)) throw Error('Unexpected builds shape');
    if (!builds.every(objectMap)) throw Error('Unexpected build entry');
    const owned=builds.filter(b=>b.cifiPhoneManaged === 'game-save-v1');
    if (owned.length > 1) throw Error('Multiple managed builds; refusing ambiguity');
    if (owned.length) {
      const b=owned[0];
      for (const key of ['talents','attributes']) if (Object.hasOwn(b,key) && !objectMap(b[key])) throw Error('Invalid existing build fields');
      Object.assign(b,{level:source.level,talents:{...(b.talents||{}),...source.talents},attributes:{...(b.attributes||{}),...source.attributes}});
    } else {
      builds.push({id:uuid(),name:name[0].toUpperCase()+name.slice(1)+' Build (Phone Scan)',level:source.level,talents:{...source.talents},attributes:{...source.attributes},categoryId:'active',overrides:{},cifiPhoneManaged:'game-save-v1'});
    }
    hunter.hunterBuilds[name]=builds;
  }
  for (const [name, incoming] of Object.entries(mapped.gems)) {
    if (!safeKey(name)) throw Error('Unsafe gem key');
    if (!incoming || typeof incoming !== 'object' || Array.isArray(incoming) || Object.keys(incoming).some(k=>!['level','nodes','upgrades'].includes(k))) throw Error('Unsupported gem field');
    if (!nonnegative(incoming.level) || !Array.isArray(incoming.nodes) || incoming.nodes.length !== 6 || !incoming.nodes.every(x=>typeof x==='boolean')) throw Error('Invalid gem projection');
    if (Object.hasOwn(incoming,'upgrades')) values(incoming.upgrades);
    const before=Object.hasOwn(gems.gemStates,name) ? gems.gemStates[name] : {upgrades:{}};
    if (!objectMap(before)) throw Error('Invalid existing gem state');
    if (Object.hasOwn(before,'upgrades') && !objectMap(before.upgrades)) throw Error('Invalid existing gem upgrades');
    gems.gemStates[name] = {...before,...incoming,upgrades:{...(before.upgrades||{}),...(incoming.upgrades||{})}};
  }
  return {hunter,gems};
}
function project(snapshot, mapped, uuid) { return projectCore(snapshot, mapped, uuid, HUNTERS); }
// Experimental, source-only preview. Selection is explicit; it is not a
// completeness claim, an account check, or permission to apply the result.
function projectPartial(snapshot, mapped, uuid, selection) {
  if (!objectMap(selection) || !objectMap(mapped)) throw Error('Invalid partial selection');
  for (const k of ['global','hunters','gems']) {
    const a=selection[k];
    if (!Array.isArray(a) || new Set(a).size!==a.length || !a.every(s=>safeKey(s) && s.length>0)) throw Error('Invalid partial selection');
  }
  if (Object.keys(selection).some(k=>!['global','hunters','gems'].includes(k)) || selection.hunters.some(h=>!HUNTERS.includes(h))) throw Error('Invalid partial selection');
  const picked={globalUpgrades:Object.create(null),perHunter:Object.create(null),gems:Object.create(null)};
  const selected={global:[...selection.global],hunters:[...selection.hunters],gems:[...selection.gems]};
  const unavailable=[];
  for (const [group,keys] of [['globalUpgrades',selection.global],['perHunter',selection.hunters],['gems',selection.gems]]) {
    for (const key of keys) {
      if (!objectMap(mapped[group]) || !Object.hasOwn(mapped[group],key)) unavailable.push({group,key,reason:'missing'});
      else picked[group][key]=mapped[group][key];
    }
  }
  const omittedHunters=HUNTERS.filter(h=>!selection.hunters.includes(h));
  if (unavailable.length) return {status:'unavailable',selected,unavailable,omittedHunters,completeness:'partial'};
  const result=projectCore(snapshot,picked,uuid,selection.hunters);
  return {status:'preview',result,selected,unavailable,omittedHunters,completeness:'partial'};
}
if(typeof module !== 'undefined') module.exports={project,projectPartial};
else root.CifiPhoneProjection={project};
})(globalThis);
