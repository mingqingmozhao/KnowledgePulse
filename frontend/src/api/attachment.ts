import type { AttachmentItem } from '@/types'
import { del, get, post } from './axios'

export interface AttachmentQuery {
  fileType?: string
  unusedOnly?: boolean
  keyword?: string
}

export function getAttachments(params?: AttachmentQuery) {
  return get<AttachmentItem[]>('/attachment', {
    params
  })
}

export function uploadAttachment(file: File) {
  const formData = new FormData()
  formData.append('file', file)

  return post<AttachmentItem>('/attachment/upload', formData, {
    timeout: 30000
  })
}

export function deleteAttachment(id: number) {
  return del<void>(`/attachment/${id}`)
}
