import type { NotificationItem } from '@/types'
import { get, post } from './axios'

export function getNotifications(params?: { limit?: number; unreadOnly?: boolean }) {
  return get<NotificationItem[]>('/notification', {
    params
  })
}

export function getUnreadNotificationCount() {
  return get<{ count: number }>('/notification/unread-count')
}

export function markNotificationRead(id: number) {
  return post<void>(`/notification/${id}/read`)
}

export function markAllNotificationsRead() {
  return post<void>('/notification/read-all')
}
