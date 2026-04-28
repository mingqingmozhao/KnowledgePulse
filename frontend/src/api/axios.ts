import axios, {
  AxiosHeaders,
  type AxiosError,
  type InternalAxiosRequestConfig,
  type AxiosRequestConfig,
  type AxiosResponseHeaders,
  type RawAxiosResponseHeaders
} from 'axios'
import type { ApiResult } from '@/types'

export const TOKEN_KEY = 'knowledgepulse.access-token'

const service = axios.create({
  baseURL: '/api/v1',
  timeout: 15000
})

const fileService = axios.create({
  baseURL: '/api/v1',
  timeout: 30000
})

export interface FileDownloadResponse {
  blob: Blob
  fileName: string | null
  contentType: string | null
}

export function getAccessToken(): string {
  return localStorage.getItem(TOKEN_KEY) ?? ''
}

export function setAccessToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearAccessToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

function isPublicShareRequest(url?: string): boolean {
  return typeof url === 'string' && url.includes('/share/public/')
}

function shouldClearToken(config?: AxiosRequestConfig): boolean {
  return !isPublicShareRequest(config?.url)
}

function applyAuthorizationHeader(config: InternalAxiosRequestConfig) {
  const token = getAccessToken()

  if (token) {
    const headers = AxiosHeaders.from(config.headers)
    headers.set('Authorization', `Bearer ${token}`)
    config.headers = headers
  }

  return config
}

function getHeaderValue(
  headers: AxiosResponseHeaders | RawAxiosResponseHeaders | AxiosHeaders | undefined,
  key: string
): string | undefined {
  if (!headers) {
    return undefined
  }

  if (headers instanceof AxiosHeaders) {
    const headerValue = headers.get(key)

    if (Array.isArray(headerValue)) {
      return headerValue.join('; ')
    }

    if (typeof headerValue === 'string') {
      return headerValue
    }

    if (typeof headerValue === 'number' || typeof headerValue === 'boolean') {
      return String(headerValue)
    }

    return undefined
  }

  const normalizedKey = key.toLowerCase()
  const headerValue = headers[normalizedKey] ?? headers[key]

  if (Array.isArray(headerValue)) {
    return headerValue.join('; ')
  }

  if (typeof headerValue === 'string') {
    return headerValue
  }

  if (typeof headerValue === 'number' || typeof headerValue === 'boolean') {
    return String(headerValue)
  }

  return undefined
}

function hasCjkText(value: string): boolean {
  return /[\u3400-\u9fff\uf900-\ufaff]/.test(value)
}

function decodeRfc2047Filename(value: string): string | null {
  const match = value.match(/^=\?UTF-8\?([BQ])\?(.+)\?=$/i)

  if (!match) {
    return null
  }

  try {
    const mode = match[1].toUpperCase()
    const encodedValue = match[2]

    if (mode === 'B') {
      const binary = window.atob(encodedValue)
      const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0))
      return new TextDecoder('utf-8').decode(bytes)
    }

    const percentEncoded = encodedValue.replace(/_/g, ' ').replace(/=([0-9A-F]{2})/gi, '%$1')
    return decodeURIComponent(percentEncoded)
  } catch {
    return null
  }
}

function decodeMojibakeFilename(value: string): string | null {
  if (hasCjkText(value)) {
    return null
  }

  try {
    const bytes = Uint8Array.from(Array.from(value), (char) => char.charCodeAt(0) & 0xff)
    const decoded = new TextDecoder('utf-8', { fatal: true }).decode(bytes)
    return hasCjkText(decoded) ? decoded : null
  } catch {
    return null
  }
}

function decodeFilename(value: string): string {
  const normalizedValue = value.trim().replace(/^"|"$/g, '')
  const rfc2047Value = decodeRfc2047Filename(normalizedValue)

  if (rfc2047Value) {
    return rfc2047Value
  }

  try {
    const decodedValue = decodeURIComponent(normalizedValue)
    return decodeMojibakeFilename(decodedValue) ?? decodedValue
  } catch {
    return decodeMojibakeFilename(normalizedValue) ?? normalizedValue
  }
}

function extractFilename(contentDisposition?: string): string | null {
  if (!contentDisposition) {
    return null
  }

  const utf8Match = contentDisposition.match(/filename\*\s*=\s*UTF-8''([^;]+)/i)

  if (utf8Match?.[1]) {
    return decodeFilename(utf8Match[1].trim().replace(/^"|"$/g, ''))
  }

  const basicMatch = contentDisposition.match(/filename\s*=\s*("?)([^";]+)\1/i)

  if (basicMatch?.[2]) {
    return decodeFilename(basicMatch[2].trim())
  }

  return null
}

async function extractBlobErrorMessage(error: AxiosError<ApiResult<never>>): Promise<string> {
  const fallbackMessage = error.message || '网络异常，请稍后重试'
  const responseData = error.response?.data

  if (!(responseData instanceof Blob)) {
    return error.response?.data?.message || fallbackMessage
  }

  try {
    const text = await responseData.text()

    if (!text) {
      return fallbackMessage
    }

    const parsed = JSON.parse(text) as Partial<ApiResult<never>>
    return parsed.message || fallbackMessage
  } catch {
    return fallbackMessage
  }
}

service.interceptors.request.use((config) => applyAuthorizationHeader(config))
fileService.interceptors.request.use((config) => applyAuthorizationHeader(config))

service.interceptors.response.use(
  (response) => {
    const payload = response.data as ApiResult<unknown>

    if (payload && typeof payload.code === 'number') {
      if (payload.code === 200) {
        return payload.data
      }

      if (payload.code === 401 && shouldClearToken(response.config)) {
        clearAccessToken()
      }

      return Promise.reject(new Error(payload.message || '请求失败'))
    }

    return response.data
  },
  (error: AxiosError<ApiResult<never>>) => {
    const message = error.response?.data?.message || error.message || '网络异常，请稍后重试'

    if (error.response?.status === 401 && shouldClearToken(error.config)) {
      clearAccessToken()
    }

    return Promise.reject(new Error(message))
  }
)

export function get<T>(url: string, config?: AxiosRequestConfig) {
  return service.get<unknown, T>(url, config)
}

export function post<T>(url: string, data?: unknown, config?: AxiosRequestConfig) {
  return service.post<unknown, T>(url, data, config)
}

export function put<T>(url: string, data?: unknown, config?: AxiosRequestConfig) {
  return service.put<unknown, T>(url, data, config)
}

export function del<T>(url: string, config?: AxiosRequestConfig) {
  return service.delete<unknown, T>(url, config)
}

export async function postFile(url: string, data?: unknown, config?: AxiosRequestConfig) {
  try {
    const response = await fileService.post<Blob>(url, data, {
      ...config,
      responseType: 'blob'
    })

    return {
      blob: response.data,
      fileName: extractFilename(getHeaderValue(response.headers, 'content-disposition')),
      contentType: getHeaderValue(response.headers, 'content-type') ?? response.data.type ?? null
    } satisfies FileDownloadResponse
  } catch (error) {
    if (axios.isAxiosError(error)) {
      const message = await extractBlobErrorMessage(error)

      if (error.response?.status === 401 && shouldClearToken(error.config)) {
        clearAccessToken()
      }

      return Promise.reject(new Error(message))
    }

    return Promise.reject(error)
  }
}
