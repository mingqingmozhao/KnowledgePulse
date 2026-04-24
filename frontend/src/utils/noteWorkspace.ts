import type { RouteLocationRaw } from 'vue-router'

export const NOTE_DRAFT_QUERY_KEY = 'draft'
export const NOTE_TEMPLATE_QUERY_KEY = 'template'

type WorkspaceRouteTarget = {
  key: string
  noteId: number | null
  routeKind: 'draft' | 'note'
}

export function createDraftTabKey() {
  return `draft-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`
}

export function buildDraftNoteRoute(
  draftKey = createDraftTabKey(),
  templateId?: number | null
): RouteLocationRaw {
  const query: Record<string, string> = {
    [NOTE_DRAFT_QUERY_KEY]: draftKey
  }

  if (templateId) {
    query[NOTE_TEMPLATE_QUERY_KEY] = String(templateId)
  }

  return {
    path: '/note/new',
    query
  }
}

export function buildNoteEditRoute(noteId: number): RouteLocationRaw {
  return `/note/${noteId}/edit`
}

export function buildWorkspaceTabRoute(tab: WorkspaceRouteTarget): RouteLocationRaw {
  if (tab.routeKind === 'note' && tab.noteId) {
    return buildNoteEditRoute(tab.noteId)
  }

  return buildDraftNoteRoute(tab.key)
}
