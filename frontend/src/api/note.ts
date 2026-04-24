import type { Note, NoteRequest, NoteVersion } from '@/types'
import { del, get, post, postFile, put } from './axios'

export function getNotes(folderId?: number | null) {
  return get<Note[]>('/note/list', {
    params: folderId ? { folderId } : undefined
  })
}

export function getTrashNotes() {
  return get<Note[]>('/note/trash')
}

export function getFavoriteNotes() {
  return get<Note[]>('/note/favorites')
}

export function getOrCreateDailyNote(date?: string) {
  return get<Note>('/note/daily', {
    params: date ? { date } : undefined
  })
}

export function getDailyNoteCalendar(month: string) {
  return get<string[]>('/note/daily/calendar', {
    params: { month }
  })
}

export function getNoteById(noteId: number) {
  return get<Note>(`/note/${noteId}`)
}

export function createNote(payload: NoteRequest) {
  return post<Note>('/note', payload)
}

export function updateNote(noteId: number, payload: NoteRequest) {
  return put<Note>(`/note/${noteId}`, payload)
}

export function deleteNote(noteId: number) {
  return del<void>(`/note/${noteId}`)
}

export function favoriteNote(noteId: number) {
  return post<Note>(`/note/${noteId}/favorite`)
}

export function unfavoriteNote(noteId: number) {
  return del<Note>(`/note/${noteId}/favorite`)
}

export function restoreTrashedNote(noteId: number) {
  return post<Note>(`/note/${noteId}/trash/restore`)
}

export function permanentlyDeleteNote(noteId: number) {
  return del<void>(`/note/${noteId}/trash/permanent`)
}

export function exportNote(noteId: number, format: 'MARKDOWN' | 'WORD' | 'PDF') {
  return postFile(`/note/export/${noteId}`, undefined, {
    params: {
      format
    }
  })
}

export function getNoteVersions(noteId: number) {
  return get<NoteVersion[]>(`/note/${noteId}/versions`)
}

export function restoreNoteVersion(noteId: number, version: number) {
  return post<Note>(`/note/${noteId}/restore/${version}`)
}
