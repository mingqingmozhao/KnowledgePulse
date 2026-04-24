import type { CommentRequest, NoteComment } from '@/types'
import { del, get, post } from './axios'

export function getNoteComments(noteId: number) {
  return get<NoteComment[]>(`/comment/${noteId}`)
}

export function createNoteComment(noteId: number, payload: CommentRequest) {
  return post<NoteComment>(`/comment/${noteId}`, payload)
}

export function deleteNoteComment(noteId: number, commentId: number) {
  return del<void>(`/comment/${noteId}/${commentId}`)
}
