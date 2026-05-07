function normalizeBaseUrl(value?: string) {
  const trimmed = value?.trim()

  if (!trimmed) {
    return ''
  }

  return trimmed.replace(/\/+$/, '')
}

function configuredPublicAppUrl() {
  return normalizeBaseUrl(import.meta.env.PUBLIC_APP_URL || import.meta.env.VITE_PUBLIC_APP_URL)
}

function currentAppOrigin() {
  if (typeof window === 'undefined') {
    return ''
  }

  return window.location.origin
}

export function publicAppBaseUrl() {
  return configuredPublicAppUrl() || currentAppOrigin()
}

export function buildPublicAppUrl(pathOrUrl: string) {
  const value = pathOrUrl.trim()

  if (!value) {
    return publicAppBaseUrl()
  }

  if (/^https?:\/\//i.test(value)) {
    return value
  }

  const baseUrl = publicAppBaseUrl()
  const normalizedPath = value.startsWith('/') ? value : `/${value}`
  return `${baseUrl}${normalizedPath}`
}

export function buildPublicShareUrl(token: string) {
  return buildPublicAppUrl(`/share/${encodeURIComponent(token)}`)
}
