import type { FolderNode, FolderRequest } from '@/types'
import { del, get, post, put } from './axios'

export function getFolderTree() {
  return get<FolderNode[]>('/folder/tree')
}

export function createFolder(payload: FolderRequest) {
  return post<FolderNode>('/folder', payload)
}

export function updateFolder(folderId: number, payload: FolderRequest) {
  return put<FolderNode>(`/folder/${folderId}`, payload)
}

export function deleteFolder(folderId: number) {
  return del<void>(`/folder/${folderId}`)
}
