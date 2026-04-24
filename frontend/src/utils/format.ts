import type { FolderNode, Note, TagBucket } from '@/types'

export function formatDateTime(value?: string | null): string {
  if (!value) {
    return '刚刚'
  }

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return value
  }

  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date)
}

export function formatDateOnly(value?: string | null): string {
  if (!value) {
    return '暂无'
  }

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return value
  }

  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).format(date)
}

export function relativeTime(value?: string | null): string {
  if (!value) {
    return '刚刚'
  }

  const date = new Date(value)
  const diff = Date.now() - date.getTime()

  if (Number.isNaN(diff)) {
    return value
  }

  const minute = 60 * 1000
  const hour = 60 * minute
  const day = 24 * hour

  if (diff < minute) {
    return '刚刚'
  }

  if (diff < hour) {
    return `${Math.floor(diff / minute)} 分钟前`
  }

  if (diff < day) {
    return `${Math.floor(diff / hour)} 小时前`
  }

  if (diff < day * 7) {
    return `${Math.floor(diff / day)} 天前`
  }

  return formatDateOnly(value)
}

export function sanitizeFilename(value: string): string {
  const sanitized = value.replace(/[<>:"/\\|?*\u0000-\u001F]/g, '-').trim()
  return sanitized || 'knowledgepulse-note'
}

export function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

export function downloadTextFile(filename: string, content: string, mimeType: string): void {
  const blob = new Blob([content], { type: mimeType })
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')

  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

export function htmlToWordDocument(title: string, html: string): string {
  return `<!DOCTYPE html>
<html>
  <head>
    <meta charset="utf-8" />
    <title>${escapeHtml(title)}</title>
    <style>
      body {
        font-family: "Microsoft YaHei", sans-serif;
        padding: 32px;
        line-height: 1.8;
        color: #1f2933;
      }

      img {
        max-width: 100%;
      }

      pre {
        white-space: pre-wrap;
      }
    </style>
  </head>
  <body>
    <h1>${escapeHtml(title)}</h1>
    ${html}
  </body>
</html>`
}

export function initials(name: string): string {
  const trimmed = name.trim()

  if (!trimmed) {
    return 'KP'
  }

  return trimmed.replace(/\s+/g, '').slice(0, 2).toUpperCase()
}

export function buildTagBuckets(notes: Note[]): TagBucket[] {
  const tagMap = new Map<string, number>()

  notes.forEach((note) => {
    note.tags.forEach((tag) => {
      const normalized = tag.trim()

      if (!normalized) {
        return
      }

      tagMap.set(normalized, (tagMap.get(normalized) ?? 0) + 1)
    })
  })

  return [...tagMap.entries()]
    .map(([label, count]) => ({ label, count }))
    .sort((left, right) => right.count - left.count || left.label.localeCompare(right.label))
}

export function flattenFolders(folders: FolderNode[]): FolderNode[] {
  return folders.flatMap((folder) => [folder, ...flattenFolders(folder.children)])
}

export function findFolderById(folders: FolderNode[], folderId: number): FolderNode | null {
  for (const folder of folders) {
    if (folder.id === folderId) {
      return folder
    }

    const nested = findFolderById(folder.children, folderId)

    if (nested) {
      return nested
    }
  }

  return null
}

export function findFolderName(folders: FolderNode[], folderId: number): string | null {
  return findFolderById(folders, folderId)?.name ?? null
}

export function normalizeTags(rawValue: string): string[] {
  const seen = new Set<string>()

  return rawValue
    .split(/[，,]/)
    .map((tag) => tag.trim())
    .filter((tag) => {
      if (!tag || seen.has(tag)) {
        return false
      }

      seen.add(tag)
      return true
    })
}
