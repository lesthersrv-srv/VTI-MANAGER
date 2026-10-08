const CACHE='encasa-mobile-v1';
const ROOT=new URL('./',self.location.href).href;
const ASSETS=['./','./index.html','./manifest.webmanifest','./icon-192.png','./icon-512.png'].map(p=>new URL(p,ROOT).href);
self.addEventListener('install',e=>e.waitUntil(caches.open(CACHE).then(c=>c.addAll(ASSETS)).then(()=>self.skipWaiting())));
self.addEventListener('activate',e=>e.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>k.startsWith('encasa-mobile-')&&k!==CACHE).map(k=>caches.delete(k)))).then(()=>self.clients.claim())));
self.addEventListener('fetch',e=>{if(e.request.method!=='GET'||!e.request.url.startsWith(ROOT))return;if(e.request.mode==='navigate'){e.respondWith(fetch(e.request).then(async r=>{if(r.ok){const c=await caches.open(CACHE);await c.put(ROOT,r.clone())}return r}).catch(()=>caches.match(ROOT)));return}if(ASSETS.includes(e.request.url))e.respondWith(caches.match(e.request).then(r=>r||fetch(e.request)))});
