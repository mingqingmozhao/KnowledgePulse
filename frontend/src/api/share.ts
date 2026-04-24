import type { Note, ShareRequest } from '@/types'
import { del, get, post } from './axios'

export function generateShareLink(noteId: number, payload: ShareRequest) {
  return post<string>(`/share/${noteId}`, payload)
}

export function getSharedNote(token: string, password?: string) {
  return get<Note>(`/share/public/${token}`, {
    params: password ? { password } : undefined
  })
}

export function revokeShare(noteId: number) {
  return del<void>(`/share/${noteId}`)
}
