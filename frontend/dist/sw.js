const CACHE_VERSION = 'knowledgepulse-shell-v1'
const APP_SHELL = [
  '/',
  '/index.html',
  '/manifest.webmanifest',
  '/pwa/icon-192.png',
  '/pwa/icon-512.png',
  '/pwa/maskable-512.png',
  '/pwa/apple-touch-icon.png'
]

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(CACHE_VERSION)
      .then((cache) => cache.addAll(APP_SHELL))
      .then(() => self.skipWaiting())
  )
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((cacheNames) =>
        Promise.all(cacheNames.filter((cacheName) => cacheName !== CACHE_VERSION).map((cacheName) => caches.delete(cacheName)))
      )
      .then(() => self.clients.claim())
  )
})

self.addEventListener('fetch', (event) => {
  const { request } = event

  if (request.method !== 'GET') {
    return
  }

  const requestUrl = new URL(request.url)

  if (requestUrl.origin !== self.location.origin || requestUrl.pathname.startsWith('/api/')) {
    return
  }

  if (request.mode === 'navigate') {
    event.respondWith(
      fetch(request)
        .then((response) => {
          const clonedResponse = response.clone()
          caches.open(CACHE_VERSION).then((cache) => cache.put('/index.html', clonedResponse))
          return response
        })
        .catch(() => caches.match('/index.html'))
    )
    return
  }

  if (
    requestUrl.pathname.startsWith('/assets/') ||
    requestUrl.pathname.startsWith('/pwa/') ||
    requestUrl.pathname.startsWith('/vditor/') ||
    requestUrl.pathname === '/manifest.webmanifest'
  ) {
    event.respondWith(
      caches.match(request).then((cachedResponse) => {
        if (cachedResponse) {
          return cachedResponse
        }

        return fetch(request).then((response) => {
          if (response.ok) {
            const clonedResponse = response.clone()
            caches.open(CACHE_VERSION).then((cache) => cache.put(request, clonedResponse))
          }

          return response
        })
      })
    )
  }
})
