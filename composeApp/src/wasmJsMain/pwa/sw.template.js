// Service Worker der Web-Version (wird beim Build aus sw.template.js erzeugt, siehe pwaServiceWorker in
// composeApp/build.gradle.kts). Lädt beim Installieren alle Dateien der App in einen Zwischenspeicher, damit sie
// offline startet. Jede neue Version hat eine eigene Kennung: Der Browser installiert sie im Hintergrund komplett und
// nutzt sie ab dem nächsten Öffnen. Anfragen an Supabase (andere Domain) laufen nie über diesen Speicher.
const VERSION = "__VERSION__";
const CACHE = "harzlytics-" + VERSION;
const ASSETS = [
  "./",
  __ASSETS__
];

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(ASSETS)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k.startsWith("harzlytics-") && k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (event) => {
  const request = event.request;
  if (request.method !== "GET" || new URL(request.url).origin !== self.location.origin) return;
  event.respondWith(
    caches.open(CACHE).then((cache) =>
      cache.match(request, { ignoreSearch: true }).then((cached) => {
        if (cached) return cached;
        if (request.mode === "navigate") return cache.match("./").then((page) => page || fetch(request));
        return fetch(request);
      })
    )
  );
});
