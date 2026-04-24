import type { Collaborator, CollaboratorRequest } from '@/types'
import { del, get, post, put } from './axios'

export function getCollaborators(noteId: number) {
  return get<Collaborator[]>(`/collaborator/${noteId}`)
}

export function addCollaborator(noteId: number, payload: CollaboratorRequest) {
  return post<void>(`/collaborator/${noteId}`, payload)
}

export function updateCollaboratorPermission(noteId: number, userId: number, permission: string) {
  return put<void>(`/collaborator/${noteId}/${userId}`, undefined, {
    params: { permission }
  })
}

export function removeCollaborator(noteId: number, userId: number) {
  return del<void>(`/collaborator/${noteId}/${userId}`)
}
