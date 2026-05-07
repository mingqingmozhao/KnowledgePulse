export function registerAppServiceWorker() {
  if (!import.meta.env.PROD || !('serviceWorker' in navigator) || isCapacitorRuntime()) {
    return
  }

  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch((error: unknown) => {
      console.warn('[PWA] Service worker registration failed', error)
    })
  })
}

function isCapacitorRuntime() {
  const capacitorWindow = window as Window & {
    Capacitor?: unknown
  }

  return Boolean(capacitorWindow.Capacitor) || window.location.protocol === 'capacitor:'
}
