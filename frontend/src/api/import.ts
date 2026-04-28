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

export interface ImportDocumentPayload {
  files: ImportableFile[]
  rootFolderName?: string
  targetFolderId?: number | null
  keepAttachments?: boolean
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

export function importDocumentsAsNotes(payload: ImportDocumentPayload) {
  const formData = new FormData()

  payload.files.forEach((file) => {
    const relativePath = file.webkitRelativePath || file.name
    formData.append('files', file, relativePath)
    formData.append('paths', relativePath)
  })

  if (payload.rootFolderName?.trim()) {
    formData.append('rootFolderName', payload.rootFolderName.trim())
  }

  if (payload.targetFolderId) {
    formData.append('targetFolderId', String(payload.targetFolderId))
  }

  formData.append('keepAttachments', String(payload.keepAttachments ?? true))

  return post<ImportResponse>('/import/documents', formData, {
    timeout: 90000
  })
}
