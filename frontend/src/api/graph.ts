import type { GraphData, RelationRequest } from '@/types'
import { del, get, post } from './axios'

export function getGraphData(noteId: number) {
  return get<GraphData>(`/graph/${noteId}`)
}

export function getGlobalGraph() {
  return get<GraphData>('/graph/global')
}

export function addRelation(payload: RelationRequest) {
  return post<void>('/graph/relation', payload)
}

export function deleteRelation(relationId: number) {
  return del<void>(`/graph/relation/${relationId}`)
}
