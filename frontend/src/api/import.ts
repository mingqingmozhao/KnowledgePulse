import type { ImportMode, ImportResponse } from '@/types'
import { post } from './axios'

export type ImportableFile = File & {
  webkitRelativePath?: string
}

export interface ImportMarkdownPayload {
  files: ImportableFile[]
  mode: ImportMode
  rootFolderName?: string
  targetFolderId?: number | null
}

export function importMarkdownFiles(payload: ImportMarkdownPayload) {
  const formData = new FormData()

  payload.files.forEach((file) => {
    const relativePath = file.webkitRelativePath || file.name
    formData.append('files', file, relativePath)
    formData.append('paths', relativePath)
  })

  formData.append('mode', payload.mode)

  if (payload.rootFolderName?.trim()) {
    formData.append('rootFolderName', payload.rootFolderName.trim())
  }

  if (payload.targetFolderId) {
    formData.append('targetFolderId', String(payload.targetFolderId))
  }

  return post<ImportResponse>('/import/markdown', formData, {
    timeout: 60000
  })
}
