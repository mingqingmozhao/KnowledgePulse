import type { SearchResult } from '@/types'
import { get } from './axios'

export function searchNotes(keyword: string) {
  return get<SearchResult[]>('/search', {
    params: { keyword }
  })
}

export function searchNotesByTag(tagName: string) {
  return get<SearchResult[]>('/search/tag', {
    params: { tagName }
  })
}
