import type { NoteTemplate, NoteTemplateRequest } from '@/types'
import { del, get, post, put } from './axios'

export function getTemplates() {
  return get<NoteTemplate[]>('/template/list')
}

export function getTemplateById(templateId: number) {
  return get<NoteTemplate>(`/template/${templateId}`)
}

export function createTemplate(payload: NoteTemplateRequest) {
  return post<NoteTemplate>('/template', payload)
}

export function updateTemplate(templateId: number, payload: NoteTemplateRequest) {
  return put<NoteTemplate>(`/template/${templateId}`, payload)
}

export function deleteTemplate(templateId: number) {
  return del<void>(`/template/${templateId}`)
}
