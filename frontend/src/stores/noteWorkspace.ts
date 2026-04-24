import { defineStore } from 'pinia'
import type { Note } from '@/types'

export type NoteWorkspaceRouteKind = 'draft' | 'note'

export interface NoteWorkspaceSnapshot {
  id: number | null
  title: string
  content: string
  htmlContent: string
  tagsText: string
  folderId: number | null
  dailyNoteDate: string
  dailyNote: boolean
  ownerUserId: number | null
  ownerUsername: string
  ownerNickname: string
  ownerAvatar: string
  currentUserPermission: string
  currentUserCanManage: boolean
  isPublic: number
  favorited: boolean
  shareMode: 0 | 1 | 2
  shareLink: string
  lastSavedAt: string
  dirty: boolean
}

export interface NoteWorkspaceTab {
  key: string
  routeKind: NoteWorkspaceRouteKind
  noteId: number | null
  title: string
  dirty: boolean
  pinned: boolean
  closable: boolean
  snapshot: NoteWorkspaceSnapshot | null
  createdAt: number
  updatedAt: number
}

interface NoteWorkspaceState {
  tabs: NoteWorkspaceTab[]
  activeKey: string | null
  restoredDraftKeys: string[]
  storageOwnerKey: string
}

type PersistedWorkspaceTab = Omit<NoteWorkspaceTab, 'closable'> & {
  closable?: boolean
}

type PersistedWorkspaceState = {
  tabs: PersistedWorkspaceTab[]
  activeKey: string | null
}

const LEGACY_NOTE_WORKSPACE_STORAGE_KEY = 'knowledgepulse.note-workspace.v2'
const NOTE_WORKSPACE_STORAGE_KEY_PREFIX = `${LEGACY_NOTE_WORKSPACE_STORAGE_KEY}.`
const DEFAULT_WORKSPACE_OWNER_KEY = 'guest'

function canUseWorkspaceStorage() {
  return typeof window !== 'undefined' && typeof window.localStorage !== 'undefined'
}

function normalizeStorageOwnerKey(ownerKey?: string | number | null) {
  const raw = String(ownerKey ?? DEFAULT_WORKSPACE_OWNER_KEY).trim()
  return (raw || DEFAULT_WORKSPACE_OWNER_KEY).replace(/[^\w.-]/g, '_')
}

function getWorkspaceStorageKey(ownerKey: string) {
  return `${NOTE_WORKSPACE_STORAGE_KEY_PREFIX}${ownerKey}`
}

function normalizeTitle(title: string | null | undefined, fallback: string) {
  const normalized = title?.trim()
  return normalized || fallback
}

function getFallbackTitle(routeKind: NoteWorkspaceRouteKind, noteId?: number | null) {
  if (routeKind === 'draft') {
    return '未命名草稿'
  }

  return noteId ? `笔记 ${noteId}` : '笔记'
}

function buildNoteTabKey(noteId: number) {
  return `note-${noteId}`
}

function normalizeSnapshot(snapshot: NoteWorkspaceSnapshot | null | undefined) {
  if (!snapshot || typeof snapshot !== 'object') {
    return null
  }

  return {
    id: typeof snapshot.id === 'number' ? snapshot.id : null,
    title: typeof snapshot.title === 'string' ? snapshot.title : '',
    content: typeof snapshot.content === 'string' ? snapshot.content : '',
    htmlContent: typeof snapshot.htmlContent === 'string' ? snapshot.htmlContent : '',
    tagsText: typeof snapshot.tagsText === 'string' ? snapshot.tagsText : '',
    folderId: typeof snapshot.folderId === 'number' ? snapshot.folderId : null,
    dailyNoteDate: typeof snapshot.dailyNoteDate === 'string' ? snapshot.dailyNoteDate : '',
    dailyNote: Boolean(snapshot.dailyNote),
    ownerUserId: typeof snapshot.ownerUserId === 'number' ? snapshot.ownerUserId : null,
    ownerUsername: typeof snapshot.ownerUsername === 'string' ? snapshot.ownerUsername : '',
    ownerNickname: typeof snapshot.ownerNickname === 'string' ? snapshot.ownerNickname : '',
    ownerAvatar: typeof snapshot.ownerAvatar === 'string' ? snapshot.ownerAvatar : '',
    currentUserPermission:
      typeof snapshot.currentUserPermission === 'string' ? snapshot.currentUserPermission : '',
    currentUserCanManage: Boolean(snapshot.currentUserCanManage),
    isPublic: typeof snapshot.isPublic === 'number' ? snapshot.isPublic : 0,
    favorited: Boolean(snapshot.favorited),
    shareMode:
      snapshot.shareMode === 1 || snapshot.shareMode === 2 ? snapshot.shareMode : 0,
    shareLink: typeof snapshot.shareLink === 'string' ? snapshot.shareLink : '',
    lastSavedAt: typeof snapshot.lastSavedAt === 'string' ? snapshot.lastSavedAt : '',
    dirty: Boolean(snapshot.dirty)
  } satisfies NoteWorkspaceSnapshot
}

function normalizeTab(tab: PersistedWorkspaceTab): NoteWorkspaceTab | null {
  if (!tab || typeof tab !== 'object' || typeof tab.key !== 'string' || !tab.key.trim()) {
    return null
  }

  const routeKind: NoteWorkspaceRouteKind = tab.routeKind === 'note' ? 'note' : 'draft'
  const noteId = routeKind === 'note' && typeof tab.noteId === 'number' ? tab.noteId : null
  const snapshot = normalizeSnapshot(tab.snapshot)

  if (routeKind === 'draft' && !snapshot) {
    return null
  }

  return {
    key: tab.key,
    routeKind,
    noteId,
    title: normalizeTitle(tab.title, getFallbackTitle(routeKind, noteId)),
    dirty: Boolean(tab.dirty || snapshot?.dirty),
    pinned: Boolean(tab.pinned),
    closable: tab.closable !== false,
    snapshot,
    createdAt: typeof tab.createdAt === 'number' ? tab.createdAt : Date.now(),
    updatedAt: typeof tab.updatedAt === 'number' ? tab.updatedAt : Date.now()
  }
}

function sortWorkspaceTabs(tabs: NoteWorkspaceTab[]) {
  const pinnedTabs = tabs.filter((tab) => tab.pinned)
  const normalTabs = tabs.filter((tab) => !tab.pinned)
  return [...pinnedTabs, ...normalTabs]
}

function createEmptyWorkspaceState(ownerKey: string): NoteWorkspaceState {
  return {
    tabs: [],
    activeKey: null,
    restoredDraftKeys: [],
    storageOwnerKey: ownerKey
  }
}

function readWorkspaceState(ownerKey?: string | number | null): NoteWorkspaceState {
  const storageOwnerKey = normalizeStorageOwnerKey(ownerKey)

  if (!canUseWorkspaceStorage()) {
    return createEmptyWorkspaceState(storageOwnerKey)
  }

  try {
    const storageKey = getWorkspaceStorageKey(storageOwnerKey)
    let raw = window.localStorage.getItem(storageKey)
    const shouldMigrateLegacyState =
      !raw &&
      storageOwnerKey !== DEFAULT_WORKSPACE_OWNER_KEY &&
      Boolean(window.localStorage.getItem(LEGACY_NOTE_WORKSPACE_STORAGE_KEY))

    if (shouldMigrateLegacyState) {
      raw = window.localStorage.getItem(LEGACY_NOTE_WORKSPACE_STORAGE_KEY)
    }

    if (!raw) {
      return createEmptyWorkspaceState(storageOwnerKey)
    }

    const parsed = JSON.parse(raw) as Partial<PersistedWorkspaceState>
    const tabs = Array.isArray(parsed.tabs)
      ? sortWorkspaceTabs(parsed.tabs.map(normalizeTab).filter((tab): tab is NoteWorkspaceTab => Boolean(tab)))
      : []
    const activeKey =
      typeof parsed.activeKey === 'string' && tabs.some((tab) => tab.key === parsed.activeKey)
        ? parsed.activeKey
        : tabs[0]?.key ?? null

    const nextState = {
      tabs,
      activeKey,
      restoredDraftKeys: tabs
        .filter((tab) => Boolean(tab.snapshot?.dirty))
        .map((tab) => tab.key),
      storageOwnerKey
    }

    if (shouldMigrateLegacyState) {
      window.localStorage.setItem(storageKey, JSON.stringify({
        activeKey: nextState.activeKey,
        tabs: nextState.tabs
      }))
      window.localStorage.removeItem(LEGACY_NOTE_WORKSPACE_STORAGE_KEY)
    }

    return nextState
  } catch {
    if (canUseWorkspaceStorage()) {
      window.localStorage.removeItem(getWorkspaceStorageKey(storageOwnerKey))
    }

    return createEmptyWorkspaceState(storageOwnerKey)
  }
}

export const useNoteWorkspaceStore = defineStore('note-workspace', {
  state: (): NoteWorkspaceState => readWorkspaceState(),
  getters: {
    activeTab: (state) => state.tabs.find((tab) => tab.key === state.activeKey) ?? null,
    openCount: (state) => state.tabs.length,
    dirtyCount: (state) => state.tabs.filter((tab) => tab.dirty).length,
    cleanCount: (state) => state.tabs.filter((tab) => !tab.dirty).length,
    pinnedCount: (state) => state.tabs.filter((tab) => tab.pinned).length,
    latestDraftTab: (state) => {
      const activeDraft = state.tabs.find(
        (tab) => tab.key === state.activeKey && tab.routeKind === 'draft'
      )

      if (activeDraft) {
        return activeDraft
      }

      return (
        [...state.tabs]
          .filter((tab) => tab.routeKind === 'draft')
          .sort((left, right) => right.updatedAt - left.updatedAt)[0] ?? null
      )
    }
  },
  actions: {
    persistState() {
      if (!canUseWorkspaceStorage()) {
        return
      }

      const payload: PersistedWorkspaceState = {
        activeKey: this.activeKey,
        tabs: this.tabs.map((tab) => ({
          ...tab,
          snapshot: tab.routeKind === 'draft' || tab.snapshot?.dirty ? tab.snapshot : null
        }))
      }

      window.localStorage.setItem(getWorkspaceStorageKey(this.storageOwnerKey), JSON.stringify(payload))
    },
    hydrateForUser(ownerKey?: string | number | null) {
      const nextOwnerKey = normalizeStorageOwnerKey(ownerKey)

      if (this.storageOwnerKey === nextOwnerKey) {
        return
      }

      const nextState = readWorkspaceState(nextOwnerKey)
      this.tabs = nextState.tabs
      this.activeKey = nextState.activeKey
      this.restoredDraftKeys = nextState.restoredDraftKeys
      this.storageOwnerKey = nextState.storageOwnerKey
    },
    reflowTabs() {
      this.tabs = sortWorkspaceTabs(this.tabs)
    },
    pruneRestoredDraftKeys() {
      const currentKeys = new Set(this.tabs.map((tab) => tab.key))
      this.restoredDraftKeys = this.restoredDraftKeys.filter((key) => currentKeys.has(key))
    },
    shouldShowRestoreNotice(key: string) {
      return this.restoredDraftKeys.includes(key)
    },
    consumeRestoreNotice(key: string) {
      this.restoredDraftKeys = this.restoredDraftKeys.filter((item) => item !== key)
    },
    openDraftTab(key: string, title?: string | null) {
      const existingTab = this.tabs.find((tab) => tab.key === key)

      if (existingTab) {
        existingTab.updatedAt = Date.now()
        existingTab.title = normalizeTitle(title, existingTab.title || getFallbackTitle('draft'))
        this.activeKey = existingTab.key
        this.persistState()
        return existingTab
      }

      const nextTab: NoteWorkspaceTab = {
        key,
        routeKind: 'draft',
        noteId: null,
        title: normalizeTitle(title, getFallbackTitle('draft')),
        dirty: false,
        pinned: false,
        closable: true,
        snapshot: null,
        createdAt: Date.now(),
        updatedAt: Date.now()
      }

      this.tabs.push(nextTab)
      this.reflowTabs()
      this.activeKey = nextTab.key
      this.persistState()
      return nextTab
    },
    openNoteTab(noteId: number, title?: string | null) {
      const key = buildNoteTabKey(noteId)
      const existingTab = this.tabs.find((tab) => tab.key === key)

      if (existingTab) {
        existingTab.updatedAt = Date.now()
        existingTab.title = normalizeTitle(title, existingTab.title || getFallbackTitle('note', noteId))
        existingTab.noteId = noteId
        existingTab.routeKind = 'note'
        this.activeKey = existingTab.key
        this.persistState()
        return existingTab
      }

      const nextTab: NoteWorkspaceTab = {
        key,
        routeKind: 'note',
        noteId,
        title: normalizeTitle(title, getFallbackTitle('note', noteId)),
        dirty: false,
        pinned: false,
        closable: true,
        snapshot: null,
        createdAt: Date.now(),
        updatedAt: Date.now()
      }

      this.tabs.push(nextTab)
      this.reflowTabs()
      this.activeKey = nextTab.key
      this.persistState()
      return nextTab
    },
    activateTab(key: string) {
      if (!this.tabs.some((tab) => tab.key === key)) {
        return null
      }

      this.activeKey = key
      this.persistState()
      return this.tabs.find((tab) => tab.key === key) ?? null
    },
    renameTab(key: string, title: string | null | undefined) {
      const targetTab = this.tabs.find((tab) => tab.key === key)

      if (!targetTab) {
        return
      }

      targetTab.title = normalizeTitle(title, getFallbackTitle(targetTab.routeKind, targetTab.noteId))
      targetTab.updatedAt = Date.now()
      this.persistState()
    },
    saveSnapshot(key: string, snapshot: NoteWorkspaceSnapshot) {
      const targetTab = this.tabs.find((tab) => tab.key === key)

      if (!targetTab) {
        return
      }

      targetTab.snapshot = snapshot
      targetTab.dirty = snapshot.dirty
      targetTab.updatedAt = Date.now()

      if (snapshot.id) {
        targetTab.noteId = snapshot.id
      }

      if (!snapshot.dirty) {
        this.consumeRestoreNotice(key)
      }

      this.persistState()
    },
    touchTab(
      key: string,
      payload: Partial<Pick<NoteWorkspaceTab, 'title' | 'dirty' | 'pinned'>>
    ) {
      const targetTab = this.tabs.find((tab) => tab.key === key)

      if (!targetTab) {
        return
      }

      if (typeof payload.title === 'string') {
        targetTab.title = payload.title
      }

      if (typeof payload.dirty === 'boolean') {
        targetTab.dirty = payload.dirty
      }

      if (typeof payload.pinned === 'boolean') {
        targetTab.pinned = payload.pinned
        this.reflowTabs()
      }

      targetTab.updatedAt = Date.now()
      this.persistState()
    },
    togglePinTab(key: string) {
      const targetTab = this.tabs.find((tab) => tab.key === key)

      if (!targetTab) {
        return false
      }

      targetTab.pinned = !targetTab.pinned
      targetTab.updatedAt = Date.now()
      this.reflowTabs()
      this.persistState()
      return targetTab.pinned
    },
    promoteDraftTab(draftKey: string, note: Note, snapshot: NoteWorkspaceSnapshot | null) {
      const nextKey = buildNoteTabKey(note.id)
      let sourceIndex = this.tabs.findIndex((tab) => tab.key === draftKey)
      const duplicateIndex = this.tabs.findIndex((tab) => tab.key === nextKey)

      if (duplicateIndex !== -1 && duplicateIndex !== sourceIndex) {
        this.tabs.splice(duplicateIndex, 1)

        if (sourceIndex > duplicateIndex) {
          sourceIndex -= 1
        }
      }

      const nextTab: NoteWorkspaceTab = {
        key: nextKey,
        routeKind: 'note',
        noteId: note.id,
        title: normalizeTitle(note.title, getFallbackTitle('note', note.id)),
        dirty: Boolean(snapshot?.dirty),
        pinned: this.tabs[sourceIndex]?.pinned ?? false,
        closable: true,
        snapshot,
        createdAt: Date.now(),
        updatedAt: Date.now()
      }

      if (sourceIndex === -1) {
        this.tabs.push(nextTab)
      } else {
        const sourceTab = this.tabs[sourceIndex]
        this.tabs.splice(sourceIndex, 1, {
          ...sourceTab,
          ...nextTab,
          createdAt: sourceTab.createdAt
        })
      }

      this.reflowTabs()
      this.activeKey = nextKey
      this.consumeRestoreNotice(draftKey)
      this.persistState()
      return nextKey
    },
    syncNote(note: Note, snapshot?: NoteWorkspaceSnapshot | null) {
      const key = buildNoteTabKey(note.id)
      const targetTab = this.tabs.find((tab) => tab.key === key)

      if (!targetTab) {
        return
      }

      targetTab.noteId = note.id
      targetTab.routeKind = 'note'
      targetTab.title = normalizeTitle(note.title, getFallbackTitle('note', note.id))

      if (snapshot) {
        targetTab.snapshot = snapshot
        targetTab.dirty = snapshot.dirty
      }

      targetTab.updatedAt = Date.now()
      this.persistState()
    },
    closeTab(key: string) {
      const targetIndex = this.tabs.findIndex((tab) => tab.key === key)

      if (targetIndex === -1) {
        return this.activeKey
      }

      const nextActiveKey =
        this.tabs[targetIndex + 1]?.key ?? this.tabs[targetIndex - 1]?.key ?? null

      this.tabs.splice(targetIndex, 1)

      if (this.activeKey === key) {
        this.activeKey = nextActiveKey
      }

      this.consumeRestoreNotice(key)
      this.persistState()
      return this.activeKey
    },
    closeTabsToRight(key: string) {
      const targetIndex = this.tabs.findIndex((tab) => tab.key === key)

      if (targetIndex === -1) {
        return 0
      }

      const nextTabs = this.tabs.filter((tab, index) => index <= targetIndex || tab.pinned)
      const closedCount = this.tabs.length - nextTabs.length
      this.tabs = nextTabs
      this.pruneRestoredDraftKeys()

      if (!this.tabs.some((tab) => tab.key === this.activeKey)) {
        this.activeKey = key
      }

      this.persistState()
      return closedCount
    },
    closeOtherTabs(key: string) {
      if (!this.tabs.some((tab) => tab.key === key)) {
        return this.activeKey
      }

      this.tabs = this.tabs.filter((tab) => tab.key === key || tab.pinned)
      this.pruneRestoredDraftKeys()

      if (!this.tabs.some((tab) => tab.key === this.activeKey)) {
        this.activeKey = key
      }

      this.persistState()
      return this.activeKey
    },
    closeCleanTabs(preserveKey?: string | null) {
      const nextPreserveKey = preserveKey ?? this.activeKey

      this.tabs = this.tabs.filter(
        (tab) => tab.key === nextPreserveKey || tab.dirty || tab.pinned
      )
      this.pruneRestoredDraftKeys()

      if (!this.tabs.some((tab) => tab.key === this.activeKey)) {
        this.activeKey =
          (nextPreserveKey && this.tabs.some((tab) => tab.key === nextPreserveKey)
            ? nextPreserveKey
            : null) ??
          this.tabs[0]?.key ??
          null
      }

      this.persistState()
      return this.activeKey
    },
    removeNoteTab(noteId: number) {
      return this.closeTab(buildNoteTabKey(noteId))
    },
    clear() {
      this.tabs = []
      this.activeKey = null
      this.restoredDraftKeys = []

      if (canUseWorkspaceStorage()) {
        window.localStorage.removeItem(getWorkspaceStorageKey(this.storageOwnerKey))
      }
    }
  }
})
