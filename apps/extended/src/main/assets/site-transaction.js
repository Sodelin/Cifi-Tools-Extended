/* Site-side import operations. The app invokes these only for its verified origin.
 * This module never sends data, authorizes a request, seals a backup, or marks a
 * transaction durable. Those responsibilities belong to the native controller.
 */
(function(root){
'use strict';
function createSiteTransaction(env) {
  if(!env || typeof env.stores!=='function' || typeof env.project!=='function' || typeof env.uuid!=='function') throw Error('invalid environment');
  function get() {
    if(env.origin()!=='https://cifi-tools.com') throw Error('unexpected origin');
    const {hunter,gems}=env.stores();
    if(!hunter || !gems || !hunter.$state || !gems.$state) throw Error('stores unavailable');
    return {hunter,gems};
  }
  function copy(v) { return JSON.parse(JSON.stringify(v)); }
  function capture() {
    const {hunter,gems}=get();
    return {origin:'https://cifi-tools.com',hunter:copy(hunter.$state),gems:copy(gems.$state),
      hunterStorage:env.storage.getItem('hunter-data'),gemStorage:env.storage.getItem('gemPlanner_store')};
  }
  function verify(h,g,m) {
    for(const [key,value] of Object.entries(m.globalUpgrades)) {
      const i=key.indexOf('.'); if(i<1 || h?.upgrades?.[key.slice(0,i)]?.[key.slice(i+1)]!==value) throw Error('upgrade mismatch');
    }
    for(const [name,data] of Object.entries(m.perHunter)) {
      for(const [key,value] of Object.entries(data.hunterStats)) if(h?.hunterStats?.[name]?.[key]!==value) throw Error('hunter mismatch');
      const owned=(h?.hunterBuilds?.[name]||[]).filter(b=>b.cifiPhoneManaged==='game-save-v1');
      if(owned.length!==1 || owned[0].level!==data.level || !Object.entries(data.talents).every(([k,v])=>owned[0].talents?.[k]===v) || !Object.entries(data.attributes).every(([k,v])=>owned[0].attributes?.[k]===v)) throw Error('build mismatch');
    }
    for(const [name,data] of Object.entries(m.gems)) {
      const tree=g?.gemStates?.[name];
      if(!tree || tree.level!==data.level || JSON.stringify(tree.nodes)!==JSON.stringify(data.nodes) || !Object.entries(data.upgrades||{}).every(([k,v])=>tree.upgrades?.[k]===v)) throw Error('gem mismatch');
    }
  }
  function apply(before,mapped) {
    const {hunter,gems}=get();
    if(!before || before.origin!=='https://cifi-tools.com' || JSON.stringify(hunter.$state)!==JSON.stringify(before.hunter) || JSON.stringify(gems.$state)!==JSON.stringify(before.gems)) return {status:'refused-before-write'};
    // Project validates every source field and produces copies before mutation.
    const next=env.project({origin:before.origin,authenticated:true,hunter:hunter.$state,gems:gems.$state},mapped,env.uuid);
    try {
      hunter.$patch(next.hunter);gems.$patch(next.gems);hunter.$persist();gems.saveToStorage();
      const h=JSON.parse(env.storage.getItem('hunter-data')||'null');
      const g=JSON.parse(env.storage.getItem('gemPlanner_store')||'null');
      verify(hunter.$state,gems.$state,mapped);verify(h,g,mapped);
      // This does not prove the browser's asynchronous storage survived restart.
      return {status:'pending-restart-readback'};
    } catch(_) { return {status:'unknown'}; }
  }
  return {capture,apply};
}
if(typeof module!=='undefined')module.exports={createSiteTransaction};else root.CifiSiteTransaction={createSiteTransaction};
})(globalThis);
