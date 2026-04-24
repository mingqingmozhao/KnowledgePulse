import type { AttachmentItem } from '@/types'

const API_CONTEXT_PATH = '/api/v1'

export const SUPPORTED_ATTACHMENT_TYPES = new Set([
  'image/png',
  'image/jpeg',
  'image/webp',
  'image/gif',
  'application/pdf',
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
])

export const SUPPORTED_ATTACHMENT_EXTENSIONS = new Set(['png', 'jpg', 'jpeg', 'webp', 'gif', 'pdf', 'doc', 'docx'])

export const MAX_ATTACHMENT_BYTES = 25 * 1024 * 1024

export function formatFileSize(value?: number | null): string {
  if (!value || value <= 0) {
    return '0 KB'
  }

  if (value < 1024 * 1024) {
    return `${Math.max(1, Math.round(value / 1024))} KB`
  }

  return `${(value / 1024 / 1024).toFixed(1)} MB`
}

export function attachmentTypeLabel(type?: string | null): string {
  if (type === 'IMAGE') {
    return '图片'
  }

  if (type === 'PDF') {
    return 'PDF'
  }

  if (type === 'WORD') {
    return 'Word'
  }

  return '附件'
}

export function attachmentIcon(type?: string | null): string {
  if (type === 'IMAGE') {
    return '图'
  }

  if (type === 'PDF') {
    return 'PDF'
  }

  if (type === 'WORD') {
    return 'W'
  }

  return '附'
}

export function resolveAttachmentUrlValue(fileUrl?: string | null): string {
  if (!fileUrl) {
    return ''
  }

  if (/^(https?:|blob:|data:)/i.test(fileUrl)) {
    return fileUrl
  }

  if (fileUrl.startsWith('/api/')) {
    return fileUrl
  }

  if (fileUrl.startsWith('/media/')) {
    return `${API_CONTEXT_PATH}${fileUrl}`
  }

  return fileUrl
}

export function resolveAttachmentUrl(attachment: AttachmentItem): string {
  return resolveAttachmentUrlValue(attachment.fileUrl)
}

export function attachmentUrl(attachment: AttachmentItem): string {
  const fileUrl = resolveAttachmentUrl(attachment)
  const separator = fileUrl.includes('?') ? '&' : '?'
  return `${fileUrl}${separator}attachmentId=${attachment.id}`
}

export function attachmentMarkdown(attachment: AttachmentItem): string {
  const safeName = attachment.originalName.replace(/[[\]]/g, '')
  const url = attachmentUrl(attachment)

  if (attachment.fileType === 'IMAGE') {
    return `![${safeName}](${url})`
  }

  return `[${safeName}](${url})`
}

export function extractAttachmentIds(...values: Array<string | null | undefined>): number[] {
  const ids = new Set<number>()
  const pattern = /(?:attachmentId=|data-attachment-id=["']?)(\d+)/g

  values.forEach((value) => {
    if (!value) {
      return
    }

    for (const match of value.matchAll(pattern)) {
      const parsed = Number(match[1])

      if (Number.isFinite(parsed) && parsed > 0) {
        ids.add(parsed)
      }
    }
  })

  return [...ids]
}

export function normalizeAttachmentLinks(value?: string | null): string {
  if (!value) {
    return ''
  }

  return value.replace(/(^|[\s("'=])\/media\/attachments\//g, `$1${API_CONTEXT_PATH}/media/attachments/`)
}

export function isSupportedAttachmentFile(file: File): boolean {
  const contentType = file.type.toLowerCase()
  const extension = file.name.split('.').pop()?.toLowerCase() || ''
  return SUPPORTED_ATTACHMENT_TYPES.has(contentType) || SUPPORTED_ATTACHMENT_EXTENSIONS.has(extension)
}
