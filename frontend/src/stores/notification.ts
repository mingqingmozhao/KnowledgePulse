import { defineStore } from 'pinia'
import {
  getNotifications,
  getUnreadNotificationCount,
  markAllNotificationsRead,
  markNotificationRead
} from '@/api/notification'
import type { NotificationItem } from '@/types'

let pollingTimer: number | null = null

interface NotificationState {
  notifications: NotificationItem[]
  unreadCount: number
  loading: boolean
  unreadOnly: boolean
}

export const useNotificationStore = defineStore('notification', {
  state: (): NotificationState => ({
    notifications: [],
    unreadCount: 0,
    loading: false,
    unreadOnly: false
  }),
  getters: {
    latestUnread: (state) => state.notifications.find((item) => !item.read) ?? null
  },
  actions: {
    async loadNotifications(unreadOnly?: boolean) {
      const nextUnreadOnly = unreadOnly ?? this.unreadOnly
      this.loading = true
      this.unreadOnly = nextUnreadOnly

      try {
        this.notifications = await getNotifications({
          limit: 50,
          unreadOnly: nextUnreadOnly
        })
      } finally {
        this.loading = false
      }
    },
    async loadUnreadCount() {
      const result = await getUnreadNotificationCount()
      this.unreadCount = result.count ?? 0
    },
    async refresh() {
      await Promise.all([this.loadNotifications(this.unreadOnly), this.loadUnreadCount()])
    },
    async markRead(id: number) {
      await markNotificationRead(id)
      const target = this.notifications.find((item) => item.id === id)

      if (target && !target.read) {
        target.read = true
        this.unreadCount = Math.max(0, this.unreadCount - 1)
      }
    },
    async markAllRead() {
      await markAllNotificationsRead()
      this.notifications = this.notifications.map((item) => ({
        ...item,
        read: true
      }))
      this.unreadCount = 0
    },
    startPolling() {
      if (pollingTimer !== null) {
        return
      }

      pollingTimer = window.setInterval(() => {
        void this.loadUnreadCount()
      }, 45000)
    },
    stopPolling() {
      if (pollingTimer === null) {
        return
      }

      window.clearInterval(pollingTimer)
      pollingTimer = null
    }
  }
})
