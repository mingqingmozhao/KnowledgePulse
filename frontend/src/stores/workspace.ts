import { defineStore } from 'pinia'
import { getDashboard } from '@/api/dashboard'
import { createFolder, deleteFolder, getFolderTree, updateFolder } from '@/api/folder'
import { getDailyInspiration } from '@/api/inspiration'
import {
  createNote,
  deleteNote,
  favoriteNote as favoriteNoteApi,
  getDailyNoteCalendar,
  getFavoriteNotes,
  getOrCreateDailyNote,
  getNotes,
  getTrashNotes,
  permanentlyDeleteNote as permanentlyDeleteNoteApi,
  restoreTrashedNote,
  unfavoriteNote as unfavoriteNoteApi
} from '@/api/note'
import { searchNotes, searchNotesByTag } from '@/api/search'
import {
  createTemplate as createTemplateApi,
  deleteTemplate as deleteTemplateApi,
  getTemplates,
  updateTemplate as updateTemplateApi
} from '@/api/template'
import type {
  DashboardResponse,
  FolderNode,
  FolderRequest,
  InspirationResponse,
  Note,
  NoteTemplate,
  NoteTemplateRequest,
  SearchResult
} from '@/types'
import { buildTagBuckets, flattenFolders } from '@/utils/format'

function sortNotesByUpdateTime(notes: Note[]): Note[] {
  return [...notes].sort((left, right) => {
    const leftTime = new Date(left.updateTime ?? left.deletedTime ?? 0).getTime()
    const rightTime = new Date(right.updateTime ?? right.deletedTime ?? 0).getTime()
    return rightTime - leftTime
  })
}

function sortFavoriteNotes(notes: Note[]): Note[] {
  return [...notes].sort((left, right) => {
    const leftTime = new Date(left.favoriteTime ?? left.updateTime ?? 0).getTime()
    const rightTime = new Date(right.favoriteTime ?? right.updateTime ?? 0).getTime()
    return rightTime - leftTime
  })
}

function normalizeNote(note: Note): Note {
  return {
    ...note,
    content: note.content ?? '',
    htmlContent: note.htmlContent ?? '',
    tags: Array.isArray(note.tags) ? note.tags : [],
    folderId: note.folderId ?? null,
    folderName: note.folderName ?? null,
    ownerUserId: note.ownerUserId ?? null,
    ownerUsername: note.ownerUsername ?? null,
    ownerNickname: note.ownerNickname ?? null,
    ownerAvatar: note.ownerAvatar ?? null,
    currentUserPermission: note.currentUserPermission ?? null,
    currentUserCanManage: note.currentUserCanManage ?? false,
    isPublic: note.isPublic ?? 0,
    dailyNoteDate: note.dailyNoteDate ?? null,
    dailyNote: note.dailyNote ?? Boolean(note.dailyNoteDate),
    favorited: note.favorited ?? false,
    favoriteTime: note.favoriteTime ?? null,
    deleted: note.deleted ?? 0,
    deletedTime: note.deletedTime ?? null,
    createTime: note.createTime ?? null,
    updateTime: note.updateTime ?? null
  }
}

function normalizeFolder(folder: FolderNode): FolderNode {
  return {
    ...folder,
    parentId: folder.parentId ?? null,
    children: (folder.children ?? []).map(normalizeFolder),
    notes: (folder.notes ?? []).map(normalizeNote),
    createTime: folder.createTime ?? null
  }
}

function normalizeTemplate(template: NoteTemplate): NoteTemplate {
  return {
    ...template,
    userId: template.userId ?? null,
    description: template.description ?? '',
    content: template.content ?? '',
    htmlContent: template.htmlContent ?? '',
    tags: Array.isArray(template.tags) ? template.tags : [],
    category: template.category ?? '通用',
    system: template.system ?? false,
    createTime: template.createTime ?? null,
    updateTime: template.updateTime ?? null
  }
}

function addDailyNoteDateToCache(cache: Record<string, string[]>, dailyNoteDate?: string | null) {
  if (!dailyNoteDate) {
    return cache
  }

  const monthKey = dailyNoteDate.slice(0, 7)
  const nextDates = new Set(cache[monthKey] ?? [])
  nextDates.add(dailyNoteDate)

  return {
    ...cache,
    [monthKey]: [...nextDates].sort()
  }
}

function removeDailyNoteDateFromCache(cache: Record<string, string[]>, dailyNoteDate?: string | null) {
  if (!dailyNoteDate) {
    return cache
  }

  const monthKey = dailyNoteDate.slice(0, 7)
  const currentDates = cache[monthKey]

  if (!currentDates?.length || !currentDates.includes(dailyNoteDate)) {
    return cache
  }

  return {
    ...cache,
    [monthKey]: currentDates.filter((item) => item !== dailyNoteDate)
  }
}

interface WorkspaceState {
  folders: FolderNode[]
  notes: Note[]
  favoriteNotes: Note[]
  trashNotes: Note[]
  templates: NoteTemplate[]
  dashboard: DashboardResponse | null
  inspiration: InspirationResponse | null
  dailyNoteCalendarCache: Record<string, string[]>
  activeFolderId: number | null
  searchKeyword: string
  searchResults: SearchResult[]
  explorerLoading: boolean
  dashboardLoading: boolean
  dailyNoteLoading: boolean
  templateLoading: boolean
  searchLoading: boolean
}

export const useWorkspaceStore = defineStore('workspace', {
  state: (): WorkspaceState => ({
    folders: [],
    notes: [],
    favoriteNotes: [],
    trashNotes: [],
    templates: [],
    dashboard: null,
    inspiration: null,
    dailyNoteCalendarCache: {},
    activeFolderId: null,
    searchKeyword: '',
    searchResults: [],
    explorerLoading: false,
    dashboardLoading: false,
    dailyNoteLoading: false,
    templateLoading: false,
    searchLoading: false
  }),
  getters: {
    visibleNotes: (state) =>
      state.activeFolderId
        ? state.notes.filter((note) => note.folderId === state.activeFolderId)
        : state.notes,
    visibleTrashNotes: (state) =>
      state.activeFolderId
        ? state.trashNotes.filter((note) => note.folderId === state.activeFolderId)
        : state.trashNotes,
    tagBuckets: (state) => buildTagBuckets(state.notes),
    folderOptions: (state) =>
      flattenFolders(state.folders).map((folder) => ({
        label: folder.name,
        value: folder.id
      }))
  },
  actions: {
    async loadFolders() {
      const folders = await getFolderTree()
      this.folders = folders.map(normalizeFolder)
    },
    async loadNotes() {
      const notes = await getNotes()
      this.notes = sortNotesByUpdateTime(notes.map(normalizeNote))
    },
    async loadFavoriteNotes() {
      const notes = await getFavoriteNotes()
      this.favoriteNotes = sortFavoriteNotes(notes.map(normalizeNote))
    },
    async loadTrashNotes() {
      const notes = await getTrashNotes()
      this.trashNotes = sortNotesByUpdateTime(notes.map(normalizeNote))
    },
    async loadTemplates() {
      this.templateLoading = true

      try {
        const templates = await getTemplates()
        this.templates = templates.map(normalizeTemplate)
        return this.templates
      } finally {
        this.templateLoading = false
      }
    },
    async loadExplorer() {
      this.explorerLoading = true

      try {
        await Promise.all([this.loadFolders(), this.loadNotes(), this.loadFavoriteNotes(), this.loadTrashNotes()])
      } finally {
        this.explorerLoading = false
      }
    },
    async loadDashboard() {
      this.dashboardLoading = true

      try {
        const [dashboard, inspiration] = await Promise.all([getDashboard(), getDailyInspiration()])
        this.dashboard = dashboard
        this.inspiration = inspiration
      } finally {
        this.dashboardLoading = false
      }
    },
    async loadDailyNoteCalendar(month: string, force = false) {
      const normalizedMonth = month.trim()

      if (!normalizedMonth) {
        return []
      }

      if (!force && this.dailyNoteCalendarCache[normalizedMonth]) {
        return this.dailyNoteCalendarCache[normalizedMonth]
      }

      this.dailyNoteLoading = true

      try {
        const dates = await getDailyNoteCalendar(normalizedMonth)
        this.dailyNoteCalendarCache = {
          ...this.dailyNoteCalendarCache,
          [normalizedMonth]: dates
        }
        return dates
      } finally {
        this.dailyNoteLoading = false
      }
    },
    selectFolder(folderId: number | null) {
      this.activeFolderId = folderId
    },
    async createFolder(payload: FolderRequest) {
      await createFolder(payload)
      await this.loadFolders()
    },
    async renameFolder(folderId: number, payload: FolderRequest) {
      await updateFolder(folderId, payload)
      await this.loadFolders()
    },
    async removeFolder(folderId: number) {
      await deleteFolder(folderId)

      if (this.activeFolderId === folderId) {
        this.activeFolderId = null
      }

      await Promise.all([this.loadFolders(), this.loadNotes(), this.loadTrashNotes()])
    },
    async createQuickNote(folderId?: number | null) {
      const note = await createNote({
        title: '未命名笔记',
        content: '# 新笔记\n\n从这里开始整理你的思路。',
        htmlContent: '',
        tags: [],
        folderId: folderId ?? this.activeFolderId ?? null
      })

      this.syncNote(note)
      return note
    },
    async createTemplate(payload: NoteTemplateRequest) {
      const template = normalizeTemplate(await createTemplateApi(payload))
      this.templates = [template, ...this.templates]
      return template
    },
    async updateTemplate(templateId: number, payload: NoteTemplateRequest) {
      const template = normalizeTemplate(await updateTemplateApi(templateId, payload))
      this.templates = this.templates.map((item) => (item.id === template.id ? template : item))
      return template
    },
    async deleteTemplate(templateId: number) {
      await deleteTemplateApi(templateId)
      this.templates = this.templates.filter((item) => item.id !== templateId)
    },
    async openDailyNote(date?: string) {
      const note = normalizeNote(await getOrCreateDailyNote(date))
      this.syncNote(note)
      return note
    },
    async removeNote(noteId: number) {
      const existingNote =
        this.notes.find((item) => item.id === noteId) ??
        this.favoriteNotes.find((item) => item.id === noteId) ??
        this.trashNotes.find((item) => item.id === noteId) ??
        null

      await deleteNote(noteId)
      this.dailyNoteCalendarCache = removeDailyNoteDateFromCache(
        this.dailyNoteCalendarCache,
        existingNote?.dailyNoteDate
      )
      await Promise.all([this.loadNotes(), this.loadFavoriteNotes(), this.loadTrashNotes()])
    },
    async favoriteNote(noteId: number) {
      const note = await favoriteNoteApi(noteId)
      this.syncNote(note)
      return note
    },
    async unfavoriteNote(noteId: number) {
      const note = await unfavoriteNoteApi(noteId)
      this.syncNote(note)
      return note
    },
    async toggleFavorite(noteId: number, favorited: boolean) {
      return favorited ? this.favoriteNote(noteId) : this.unfavoriteNote(noteId)
    },
    async restoreNoteFromTrash(noteId: number) {
      const note = await restoreTrashedNote(noteId)
      this.syncNote(note)
      this.trashNotes = this.trashNotes.filter((item) => item.id !== noteId)
      return note
    },
    async permanentlyDeleteNote(noteId: number) {
      const existingNote =
        this.notes.find((item) => item.id === noteId) ??
        this.favoriteNotes.find((item) => item.id === noteId) ??
        this.trashNotes.find((item) => item.id === noteId) ??
        null

      await permanentlyDeleteNoteApi(noteId)
      this.dailyNoteCalendarCache = removeDailyNoteDateFromCache(
        this.dailyNoteCalendarCache,
        existingNote?.dailyNoteDate
      )
      this.favoriteNotes = this.favoriteNotes.filter((item) => item.id !== noteId)
      this.trashNotes = this.trashNotes.filter((item) => item.id !== noteId)
    },
    syncNote(note: Note) {
      const normalized = normalizeNote(note)
      const previousNote =
        this.notes.find((item) => item.id === normalized.id) ??
        this.favoriteNotes.find((item) => item.id === normalized.id) ??
        this.trashNotes.find((item) => item.id === normalized.id) ??
        null

      this.notes = this.notes.filter((item) => item.id !== normalized.id)
      this.favoriteNotes = this.favoriteNotes.filter((item) => item.id !== normalized.id)
      this.trashNotes = this.trashNotes.filter((item) => item.id !== normalized.id)

      if (previousNote?.dailyNoteDate && previousNote.dailyNoteDate !== normalized.dailyNoteDate) {
        this.dailyNoteCalendarCache = removeDailyNoteDateFromCache(
          this.dailyNoteCalendarCache,
          previousNote.dailyNoteDate
        )
      }

      if (normalized.deleted === 1) {
        this.dailyNoteCalendarCache = removeDailyNoteDateFromCache(
          this.dailyNoteCalendarCache,
          normalized.dailyNoteDate
        )
        this.trashNotes = sortNotesByUpdateTime([normalized, ...this.trashNotes])
        return
      }

      this.dailyNoteCalendarCache = addDailyNoteDateToCache(
        this.dailyNoteCalendarCache,
        normalized.dailyNoteDate
      )

      this.notes = sortNotesByUpdateTime([normalized, ...this.notes])

      if (normalized.favorited) {
        this.favoriteNotes = sortFavoriteNotes([normalized, ...this.favoriteNotes])
      }
    },
    clearSearch() {
      this.searchKeyword = ''
      this.searchResults = []
    },
    async runSearch(keyword: string) {
      const normalized = keyword.trim()

      if (!normalized) {
        this.clearSearch()
        return []
      }

      this.searchLoading = true

      try {
        this.searchKeyword = normalized
        this.searchResults = await searchNotes(normalized)
        return this.searchResults
      } finally {
        this.searchLoading = false
      }
    },
    async runTagSearch(tagName: string) {
      const normalized = tagName.trim()

      if (!normalized) {
        this.clearSearch()
        return []
      }

      this.searchLoading = true

      try {
        this.searchKeyword = normalized
        this.searchResults = await searchNotesByTag(normalized)
        return this.searchResults
      } finally {
        this.searchLoading = false
      }
    }
  }
})
