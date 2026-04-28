export interface ApiResult<T> {
  code: number
  message: string
  data: T
  timestamp?: number
}

export interface User {
  id: number
  username: string
  email: string
  avatar?: string | null
  nickname?: string | null
  role?: string | null
  createTime?: string | null
}

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: User
}

export interface DashboardResponse {
  totalNotes: number
  totalFolders: number
  totalTags: number
  tagDistribution: Record<string, number>
  editHeatmap: Record<string, number>
}

export interface Note {
  id: number
  title: string
  content: string
  htmlContent: string
  tags: string[]
  folderId: number | null
  folderName?: string | null
  ownerUserId?: number | null
  ownerUsername?: string | null
  ownerNickname?: string | null
  ownerAvatar?: string | null
  currentUserPermission?: string | null
  currentUserCanManage?: boolean | null
  isPublic: number
  dailyNoteDate?: string | null
  dailyNote?: boolean | null
  favorited?: boolean | null
  favoriteTime?: string | null
  deleted?: number
  deletedTime?: string | null
  createTime?: string | null
  updateTime?: string | null
}

export interface NoteTemplate {
  id: number
  userId?: number | null
  name: string
  description?: string | null
  content: string
  htmlContent: string
  tags: string[]
  category?: string | null
  system?: boolean | null
  createTime?: string | null
  updateTime?: string | null
}

export interface FolderNode {
  id: number
  name: string
  parentId: number | null
  children: FolderNode[]
  notes: Note[]
  createTime?: string | null
}

export interface InspirationResponse {
  date: string
  recommendedTags: string[]
  relatedNotes: Note[]
  recommendations: InspirationMatch[]
  inspirationPrompts: string[]
  matchSummary: string
  inspirationQuote: string
}

export interface InspirationMatch {
  noteId: number
  title: string
  matchedTags: string[]
  score: number
  reason: string
  updateTime?: string | null
}

export interface SearchResult {
  id: number
  title: string
  snippet: string
  tags: string
  updateTime?: string | null
}

export interface GraphNode {
  id: number
  name: string
  type: string
}

export interface GraphLink {
  source: number
  target: number
  relationType: string
}

export interface GraphData {
  nodes: GraphNode[]
  links: GraphLink[]
}

export type ImportMode = 'MARKDOWN_FOLDER' | 'OBSIDIAN_VAULT' | 'BATCH_MARKDOWN'

export interface ImportedNoteItem {
  id: number
  title: string
  path: string
  folderId: number
  tags: string[]
}

export interface ImportResponse {
  rootFolderId: number
  rootFolderName: string
  mode: ImportMode | string
  totalFiles: number
  importedNotes: number
  createdFolders: number
  skippedFiles: number
  tags: string[]
  warnings: string[]
  notes: ImportedNoteItem[]
  attachments?: AttachmentItem[]
}

export interface NoteVersion {
  id: number
  version: number
  contentSnapshot: string
  createTime?: string | null
}

export type NotificationType =
  | 'SHARE_CREATED'
  | 'SHARE_REVOKED'
  | 'COLLABORATION_INVITE'
  | 'COLLABORATION_REMOVED'
  | 'PERMISSION_CHANGED'
  | 'COMMENT'
  | string

export interface NotificationItem {
  id: number
  recipientUserId: number
  actorUserId?: number | null
  actorUsername?: string | null
  actorNickname?: string | null
  actorAvatar?: string | null
  type: NotificationType
  title: string
  content?: string | null
  noteId?: number | null
  noteTitle?: string | null
  targetUrl?: string | null
  read: boolean
  createTime?: string | null
  readTime?: string | null
}

export interface NoteComment {
  id: number
  noteId: number
  userId: number
  username?: string | null
  nickname?: string | null
  avatar?: string | null
  content: string
  canDelete?: boolean | null
  createTime?: string | null
  updateTime?: string | null
}

export type AttachmentFileType = 'IMAGE' | 'PDF' | 'WORD' | string

export interface AttachmentItem {
  id: number
  userId: number
  originalName: string
  fileName?: string | null
  fileType: AttachmentFileType
  contentType: string
  fileSize: number
  fileUrl: string
  referenceCount: number
  used: boolean
  createTime?: string | null
  updateTime?: string | null
}

export interface Collaborator {
  id?: number
  noteId?: number
  userId: number
  permission: 'READ' | 'EDIT' | 'OWNER' | string
  createTime?: string | null
  username?: string | null
  email?: string | null
  nickname?: string
  avatar?: string | null
  active?: boolean
  typing?: boolean
  editingRegion?: string | null
}

export interface TagBucket {
  label: string
  count: number
}

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
  email: string
  nickname?: string
}

export interface UpdateProfileRequest {
  nickname?: string
  avatar?: string
  email?: string
  currentPassword?: string
  newPassword?: string
}

export interface FolderRequest {
  name: string
  parentId?: number | null
}

export interface NoteRequest {
  title: string
  content?: string
  htmlContent?: string
  tags?: string[]
  folderId?: number | null
  attachmentIds?: number[]
}

export interface NoteTemplateRequest {
  name: string
  description?: string
  content?: string
  htmlContent?: string
  tags?: string[]
  category?: string
}

export interface RelationRequest {
  sourceNoteId: number
  targetNoteId: number
  relationType: string
}

export interface ShareRequest {
  isPublic: 0 | 1 | 2 | number
  password?: string
}

export interface CollaboratorRequest {
  userId: number
  permission: 'READ' | 'EDIT' | 'OWNER' | string
}

export interface CommentRequest {
  content: string
}
