<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useNotificationStore } from '@/stores/notification'
import type { NotificationItem } from '@/types'
import { resolveAvatarSrc } from '@/utils/avatar'
import { initials, relativeTime } from '@/utils/format'

type ReadFilter = 'all' | 'unread'
type TypeFilter = 'all' | 'collaboration' | 'comment' | 'share' | 'permission'

interface NotificationGroup {
  key: string
  label: string
  items: NotificationItem[]
}

const router = useRouter()
const notificationStore = useNotificationStore()

const drawerVisible = ref(false)
const readFilter = ref<ReadFilter>('all')
const typeFilter = ref<TypeFilter>('all')

const readFilterOptions = [
  { label: '全部', value: 'all' },
  { label: '未读', value: 'unread' }
]

const unreadBadgeValue = computed(() =>
  notificationStore.unreadCount > 99 ? '99+' : notificationStore.unreadCount || ''
)

const visibleNotifications = computed(() => {
  if (typeFilter.value === 'all') {
    return notificationStore.notifications
  }

  return notificationStore.notifications.filter((item) => notificationTone(item.type) === typeFilter.value)
})

const typeFilterOptions = computed(() => {
  const countByTone = new Map<TypeFilter, number>([
    ['collaboration', 0],
    ['comment', 0],
    ['share', 0],
    ['permission', 0]
  ])

  notificationStore.notifications.forEach((item) => {
    const tone = notificationTone(item.type)
    countByTone.set(tone, (countByTone.get(tone) ?? 0) + 1)
  })

  return [
    { value: 'all' as const, label: '全部', count: notificationStore.notifications.length },
    { value: 'collaboration' as const, label: '协作', count: countByTone.get('collaboration') ?? 0 },
    { value: 'comment' as const, label: '评论', count: countByTone.get('comment') ?? 0 },
    { value: 'share' as const, label: '分享', count: countByTone.get('share') ?? 0 },
    { value: 'permission' as const, label: '权限', count: countByTone.get('permission') ?? 0 }
  ]
})

const groupedNotifications = computed<NotificationGroup[]>(() => {
  const groups = new Map<string, NotificationGroup>()

  visibleNotifications.value.forEach((item) => {
    const group = resolveGroup(item.createTime)

    if (!groups.has(group.key)) {
      groups.set(group.key, {
        ...group,
        items: []
      })
    }

    groups.get(group.key)?.items.push(item)
  })

  return [...groups.values()]
})

const unreadInCurrentList = computed(() =>
  notificationStore.notifications.filter((item) => !item.read).length
)

const inboxSummary = computed(() => {
  if (!notificationStore.notifications.length) {
    return '分享、协作邀请、评论和权限变更都会集中到这里，重要动态不会再散落在各个页面。'
  }

  if (notificationStore.unreadCount) {
    return `还有 ${notificationStore.unreadCount} 条未读。你可以先筛选类型，也可以只标记已读，不会被迫跳转。`
  }

  return '当前没有未读提醒。需要回看协作记录时，可以按类型快速过滤。'
})

function actorName(item: NotificationItem) {
  return item.actorNickname?.trim() || item.actorUsername?.trim() || '系统提醒'
}

function notificationTone(type: string): Exclude<TypeFilter, 'all'> {
  if (type.includes('COMMENT')) {
    return 'comment'
  }

  if (type.includes('PERMISSION') || type.includes('REMOVED')) {
    return 'permission'
  }

  if (type.includes('SHARE')) {
    return 'share'
  }

  return 'collaboration'
}

function notificationIcon(type: string) {
  const tone = notificationTone(type)

  if (tone === 'comment') {
    return '评'
  }

  if (tone === 'share') {
    return '享'
  }

  if (tone === 'permission') {
    return '权'
  }

  return '协'
}

function notificationTypeLabel(type: string) {
  if (type === 'COMMENT') {
    return '评论'
  }

  if (type === 'SHARE_CREATED') {
    return '分享已开启'
  }

  if (type === 'SHARE_REVOKED') {
    return '分享已关闭'
  }

  if (type === 'PERMISSION_CHANGED') {
    return '权限变更'
  }

  if (type === 'COLLABORATION_REMOVED') {
    return '协作移除'
  }

  if (type === 'COLLABORATION_INVITE') {
    return '协作邀请'
  }

  return '系统提醒'
}

function notificationActionLabel(item: NotificationItem) {
  if (!item.targetUrl) {
    return '无跳转'
  }

  if (item.type === 'COMMENT') {
    return '查看评论'
  }

  if (item.type.includes('SHARE')) {
    return '查看分享'
  }

  if (item.type.includes('PERMISSION')) {
    return '查看权限'
  }

  if (item.type.includes('REMOVED')) {
    return '查看文件'
  }

  return '打开笔记'
}

function notificationContext(item: NotificationItem) {
  const segments = [notificationTypeLabel(item.type), actorName(item)]

  if (item.noteTitle) {
    segments.push(`《${item.noteTitle}》`)
  }

  return segments.join(' / ')
}

function resolveGroup(value?: string | null) {
  if (!value) {
    return {
      key: 'unknown',
      label: '稍早'
    }
  }

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return {
      key: 'unknown',
      label: '稍早'
    }
  }

  const today = new Date()
  today.setHours(0, 0, 0, 0)

  const yesterday = new Date(today)
  yesterday.setDate(today.getDate() - 1)

  const week = new Date(today)
  week.setDate(today.getDate() - 7)

  if (date >= today) {
    return {
      key: 'today',
      label: '今天'
    }
  }

  if (date >= yesterday) {
    return {
      key: 'yesterday',
      label: '昨天'
    }
  }

  if (date >= week) {
    return {
      key: 'week',
      label: '最近 7 天'
    }
  }

  return {
    key: 'older',
    label: '更早'
  }
}

async function refreshInbox() {
  try {
    await notificationStore.refresh()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '刷新通知失败')
  }
}

async function openDrawer() {
  drawerVisible.value = true
  await refreshInbox()
}

async function changeReadFilter(value: string | number) {
  readFilter.value = value === 'unread' ? 'unread' : 'all'

  try {
    await notificationStore.loadNotifications(readFilter.value === 'unread')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '筛选通知失败')
  }
}

function changeTypeFilter(value: TypeFilter) {
  typeFilter.value = value
}

async function markAllRead() {
  try {
    await notificationStore.markAllRead()
    ElMessage.success('已全部标记为已读')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  }
}

async function markReadOnly(item: NotificationItem) {
  if (item.read) {
    return
  }

  try {
    await notificationStore.markRead(item.id)
    ElMessage.success('已标记为已读')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  }
}

async function openNotification(item: NotificationItem) {
  try {
    if (!item.read) {
      await notificationStore.markRead(item.id)
    }

    if (!item.targetUrl) {
      ElMessage.info('这条提醒没有可跳转的位置')
      return
    }

    drawerVisible.value = false
    await router.push(item.targetUrl)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '无法打开通知')
  }
}

function handleNotificationKeydown(event: KeyboardEvent, item: NotificationItem) {
  if (event.key !== 'Enter' && event.key !== ' ') {
    return
  }

  event.preventDefault()
  void openNotification(item)
}

onMounted(() => {
  void notificationStore.loadUnreadCount()
  notificationStore.startPolling()
})

onBeforeUnmount(() => {
  notificationStore.stopPolling()
})
</script>

<template>
  <div class="notification-center">
    <button type="button" class="notification-center__trigger" @click="openDrawer">
      <span class="notification-center__trigger-icon">N</span>
      <span class="notification-center__trigger-copy">
        <strong>通知</strong>
        <small>{{ notificationStore.unreadCount ? `${notificationStore.unreadCount} 条未读` : '无未读' }}</small>
      </span>
      <span v-if="unreadBadgeValue" class="notification-center__badge">{{ unreadBadgeValue }}</span>
    </button>

    <el-drawer
      v-model="drawerVisible"
      class="notification-center__drawer"
      modal-class="notification-center__overlay"
      size="min(640px, calc(100vw - 18px))"
      direction="rtl"
      append-to-body
      lock-scroll
      :with-header="false"
      destroy-on-close
    >
      <section class="notification-center__panel">
        <header class="notification-center__hero">
          <div class="notification-center__hero-main">
            <span class="section-kicker">Inbox</span>
            <h2>通知中心</h2>
            <p>{{ inboxSummary }}</p>
          </div>

          <div class="notification-center__hero-side">
            <div class="notification-center__hero-stat">
              <strong>{{ notificationStore.unreadCount }}</strong>
              <span>未读</span>
            </div>
            <button type="button" class="notification-center__close" @click="drawerVisible = false">关闭</button>
          </div>
        </header>

        <div class="notification-center__toolbar">
          <el-segmented
            :model-value="readFilter"
            :options="readFilterOptions"
            @update:model-value="changeReadFilter"
          />

          <div class="notification-center__toolbar-actions">
            <el-button plain size="small" :loading="notificationStore.loading" @click="refreshInbox">
              刷新
            </el-button>
            <el-button plain size="small" :disabled="!unreadInCurrentList" @click="markAllRead">
              当前列表全部已读
            </el-button>
          </div>
        </div>

        <div class="notification-center__type-filters" aria-label="通知类型筛选">
          <button
            v-for="option in typeFilterOptions"
            :key="option.value"
            type="button"
            class="notification-center__type-filter"
            :class="{ 'notification-center__type-filter--active': typeFilter === option.value }"
            @click="changeTypeFilter(option.value)"
          >
            <span>{{ option.label }}</span>
            <strong>{{ option.count }}</strong>
          </button>
        </div>

        <div v-if="notificationStore.loading" class="notification-center__loading">
          <div v-for="index in 4" :key="index" class="notification-center__skeleton" />
        </div>

        <div v-else-if="groupedNotifications.length" class="notification-center__groups">
          <section v-for="group in groupedNotifications" :key="group.key" class="notification-center__group">
            <div class="notification-center__group-head">
              <strong>{{ group.label }}</strong>
              <span>{{ group.items.length }} 条</span>
            </div>

            <article
              v-for="item in group.items"
              :key="item.id"
              class="notification-center__item"
              :class="[
                `notification-center__item--${notificationTone(item.type)}`,
                { 'notification-center__item--unread': !item.read }
              ]"
              tabindex="0"
              role="button"
              @click="openNotification(item)"
              @keydown="handleNotificationKeydown($event, item)"
            >
              <div class="notification-center__item-icon" aria-hidden="true">
                {{ notificationIcon(item.type) }}
              </div>

              <div class="notification-center__item-body">
                <div class="notification-center__item-head">
                  <div>
                    <span class="notification-center__item-type">{{ notificationTypeLabel(item.type) }}</span>
                    <strong>{{ item.title }}</strong>
                  </div>
                  <span class="notification-center__time">{{ relativeTime(item.createTime) }}</span>
                </div>

                <p>{{ item.content || '有一条新的协作动态等待查看。' }}</p>

                <div class="notification-center__item-meta">
                  <el-avatar :size="24" class="notification-center__avatar" :src="resolveAvatarSrc(item.actorAvatar)">
                    {{ initials(actorName(item)) }}
                  </el-avatar>
                  <span>{{ notificationContext(item) }}</span>
                </div>
              </div>

              <div class="notification-center__item-actions" @click.stop>
                <span v-if="!item.read" class="notification-center__unread-dot">未读</span>
                <el-button v-if="!item.read" plain size="small" @click="markReadOnly(item)">
                  只标已读
                </el-button>
                <el-button type="primary" plain size="small" :disabled="!item.targetUrl" @click="openNotification(item)">
                  {{ notificationActionLabel(item) }}
                </el-button>
              </div>
            </article>
          </section>
        </div>

        <div v-else class="notification-center__empty">
          <strong>{{ notificationStore.notifications.length ? '这个筛选下没有提醒' : '暂时没有提醒' }}</strong>
          <span>
            {{
              notificationStore.notifications.length
                ? '可以切换到“全部”或换一个类型继续查看。'
                : '当有人邀请你协作、评论笔记、调整权限或开启分享时，这里会第一时间显示。'
            }}
          </span>
        </div>
      </section>
    </el-drawer>
  </div>
</template>

<style scoped>
.notification-center {
  position: relative;
}

.notification-center__trigger {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-height: 42px;
  padding: 7px 12px 7px 8px;
  border: 1px solid rgba(54, 92, 75, 0.16);
  border-radius: 999px;
  background:
    radial-gradient(circle at top left, rgba(54, 92, 75, 0.12), transparent 42%),
    rgba(255, 255, 255, 0.74);
  color: var(--text-main);
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}

.notification-center__trigger:hover {
  transform: translateY(-1px);
  border-color: rgba(54, 92, 75, 0.28);
  box-shadow: 0 12px 24px rgba(54, 92, 75, 0.08);
}

.notification-center__trigger-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: linear-gradient(135deg, #365c4b, #8d6a22);
  color: #fff8ed;
  font-size: 0.84rem;
  font-weight: 700;
}

.notification-center__trigger-copy {
  display: grid;
  gap: 1px;
  text-align: left;
}

.notification-center__trigger-copy strong {
  font-size: 0.88rem;
  line-height: 1.1;
}

.notification-center__trigger-copy small {
  color: var(--text-soft);
  font-size: 0.72rem;
}

.notification-center__badge {
  position: absolute;
  right: -5px;
  top: -6px;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 999px;
  background: #b85c38;
  color: #fff;
  font-size: 0.72rem;
  line-height: 20px;
  text-align: center;
  box-shadow: 0 0 0 3px rgba(255, 252, 247, 0.95);
}

:global(.notification-center__overlay) {
  position: fixed;
  inset: 0;
  background: rgba(36, 48, 38, 0.2);
  backdrop-filter: blur(3px);
}

:global(.notification-center__overlay .notification-center__drawer.el-drawer) {
  top: 14px;
  right: 14px;
  bottom: 14px;
  height: calc(100vh - 28px);
  border-radius: 30px;
  overflow: hidden;
  box-shadow: 0 30px 80px rgba(36, 48, 38, 0.22);
}

.notification-center__drawer :deep(.el-drawer__body) {
  height: 100%;
  padding: 0;
  background:
    radial-gradient(circle at top right, rgba(54, 92, 75, 0.13), transparent 28%),
    linear-gradient(180deg, #fffaf2, #f2eadc);
}

.notification-center__panel {
  display: grid;
  align-content: start;
  gap: 16px;
  min-height: 100%;
  max-height: 100%;
  overflow: auto;
  padding: 20px;
}

.notification-center__hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 18px;
  padding: 22px;
  border: 1px solid rgba(54, 92, 75, 0.14);
  border-radius: 30px;
  background:
    radial-gradient(circle at top right, rgba(197, 157, 88, 0.24), transparent 34%),
    linear-gradient(135deg, rgba(255, 255, 255, 0.94), rgba(243, 249, 244, 0.84));
  box-shadow: 0 20px 50px rgba(54, 92, 75, 0.08);
}

.notification-center__hero-main {
  min-width: 0;
}

.notification-center__hero h2 {
  margin: 8px 0 0;
  font-family: var(--header-font);
  font-size: 1.85rem;
}

.notification-center__hero p {
  max-width: 440px;
  margin: 10px 0 0;
  color: var(--text-soft);
  line-height: 1.75;
}

.notification-center__hero-side {
  display: grid;
  align-content: space-between;
  justify-items: end;
  gap: 12px;
}

.notification-center__hero-stat {
  display: grid;
  place-items: center;
  min-width: 92px;
  min-height: 86px;
  border: 1px solid rgba(54, 92, 75, 0.12);
  border-radius: 24px;
  background: rgba(54, 92, 75, 0.1);
  color: #365c4b;
}

.notification-center__hero-stat strong {
  font-size: 2rem;
  line-height: 1;
}

.notification-center__hero-stat span {
  color: rgba(54, 92, 75, 0.78);
  font-size: 0.82rem;
}

.notification-center__close {
  padding: 8px 12px;
  border: 1px solid rgba(54, 92, 75, 0.14);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.62);
  color: var(--text-soft);
  cursor: pointer;
}

.notification-center__toolbar,
.notification-center__toolbar-actions,
.notification-center__type-filters,
.notification-center__group-head,
.notification-center__item-head,
.notification-center__item-meta,
.notification-center__item-actions {
  display: flex;
  align-items: center;
}

.notification-center__toolbar {
  position: sticky;
  top: 0;
  z-index: 2;
  justify-content: space-between;
  gap: 12px;
  padding: 10px;
  border: 1px solid rgba(93, 113, 92, 0.1);
  border-radius: 22px;
  background: rgba(255, 250, 242, 0.82);
  backdrop-filter: blur(14px);
}

.notification-center__toolbar-actions {
  gap: 8px;
  flex-wrap: wrap;
}

.notification-center__type-filters {
  gap: 10px;
  overflow-x: auto;
  padding-bottom: 2px;
}

.notification-center__type-filter {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 38px;
  padding: 0 12px;
  border: 1px solid rgba(93, 113, 92, 0.12);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.58);
  color: var(--text-soft);
  white-space: nowrap;
  cursor: pointer;
  transition: transform 0.18s ease, border-color 0.18s ease, background 0.18s ease;
}

.notification-center__type-filter:hover,
.notification-center__type-filter--active {
  transform: translateY(-1px);
  border-color: rgba(54, 92, 75, 0.24);
  background: rgba(54, 92, 75, 0.1);
  color: #365c4b;
}

.notification-center__type-filter strong {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 22px;
  padding: 0 7px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  color: inherit;
  font-size: 0.78rem;
}

.notification-center__groups,
.notification-center__group,
.notification-center__loading {
  display: grid;
  gap: 14px;
}

.notification-center__group-head {
  justify-content: space-between;
  gap: 12px;
  color: var(--text-soft);
  font-size: 0.86rem;
}

.notification-center__group-head strong {
  color: #365c4b;
}

.notification-center__item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 14px;
  padding: 16px;
  border: 1px solid rgba(93, 113, 92, 0.12);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.7);
  cursor: pointer;
  outline: none;
  transition: transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}

.notification-center__item:hover,
.notification-center__item:focus-visible {
  transform: translateY(-1px);
  border-color: rgba(54, 92, 75, 0.24);
  box-shadow: 0 14px 28px rgba(54, 92, 75, 0.1);
}

.notification-center__item--unread {
  background:
    linear-gradient(90deg, rgba(54, 92, 75, 0.13), transparent 32%),
    rgba(255, 255, 255, 0.9);
}

.notification-center__item--share {
  border-color: rgba(197, 157, 88, 0.24);
}

.notification-center__item--permission {
  border-color: rgba(184, 92, 56, 0.22);
}

.notification-center__item--comment {
  border-color: rgba(54, 92, 75, 0.24);
}

.notification-center__item-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 18px;
  background: linear-gradient(135deg, #365c4b, #b85c38);
  color: #fff8ed;
  font-weight: 700;
  box-shadow: 0 10px 20px rgba(54, 92, 75, 0.12);
}

.notification-center__item--share .notification-center__item-icon {
  background: linear-gradient(135deg, #8d6a22, #c59d58);
}

.notification-center__item--permission .notification-center__item-icon {
  background: linear-gradient(135deg, #8d4529, #b85c38);
}

.notification-center__item-body {
  min-width: 0;
  display: grid;
  gap: 9px;
}

.notification-center__item-head {
  justify-content: space-between;
  gap: 12px;
}

.notification-center__item-head > div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.notification-center__item-head strong {
  color: var(--text-main);
  font-size: 1rem;
}

.notification-center__item-type {
  color: #365c4b;
  font-size: 0.76rem;
  font-weight: 700;
  letter-spacing: 0.06em;
}

.notification-center__time {
  color: var(--text-soft);
  font-size: 0.8rem;
  white-space: nowrap;
}

.notification-center__item-body p {
  margin: 0;
  color: var(--text-main);
  line-height: 1.7;
}

.notification-center__item-meta {
  gap: 8px;
  min-width: 0;
  color: var(--text-soft);
  font-size: 0.82rem;
}

.notification-center__item-meta span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notification-center__avatar {
  flex: 0 0 auto;
  color: #fff8ed;
  background: linear-gradient(135deg, #365c4b, #b85c38);
}

.notification-center__item-actions {
  grid-column: 2;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

.notification-center__unread-dot {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 8px;
  border-radius: 999px;
  background: rgba(184, 92, 56, 0.12);
  color: #8d4529;
  font-size: 0.76rem;
}

.notification-center__empty,
.notification-center__skeleton {
  border: 1px dashed rgba(93, 113, 92, 0.24);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.56);
}

.notification-center__empty {
  display: grid;
  gap: 8px;
  justify-items: center;
  padding: 46px 22px;
  color: var(--text-soft);
  text-align: center;
  line-height: 1.75;
}

.notification-center__empty strong {
  color: var(--text-main);
}

.notification-center__skeleton {
  height: 112px;
  overflow: hidden;
  border-style: solid;
  background:
    linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.86), transparent),
    rgba(255, 255, 255, 0.54);
  background-size: 220% 100%;
  animation: notification-skeleton 1.3s ease-in-out infinite;
}

@keyframes notification-skeleton {
  from {
    background-position: 120% 0;
  }

  to {
    background-position: -120% 0;
  }
}

@media (max-width: 720px) {
  :global(.notification-center__overlay .notification-center__drawer.el-drawer) {
    top: 0;
    right: 0;
    bottom: 0;
    width: 100vw !important;
    height: 100dvh;
    border-radius: 0;
  }

  .notification-center__trigger-copy {
    display: none;
  }

  .notification-center__panel {
    padding: 14px;
  }

  .notification-center__hero,
  .notification-center__toolbar,
  .notification-center__item-head {
    grid-template-columns: 1fr;
  }

  .notification-center__toolbar,
  .notification-center__item-head {
    align-items: flex-start;
  }

  .notification-center__hero-side {
    grid-template-columns: 1fr auto;
    align-items: center;
    justify-items: stretch;
  }

  .notification-center__hero-stat {
    min-height: 72px;
  }

  .notification-center__item {
    grid-template-columns: 1fr;
  }

  .notification-center__item-icon {
    width: 40px;
    height: 40px;
  }

  .notification-center__item-actions {
    grid-column: 1;
    justify-content: flex-start;
  }
}

@media (max-width: 480px) {
  .notification-center__trigger {
    min-height: 40px;
    padding: 0 10px;
  }

  .notification-center__panel {
    gap: 12px;
    padding: 10px;
  }

  .notification-center__hero {
    gap: 14px;
    padding: 16px;
    border-radius: 22px;
  }

  .notification-center__hero h2 {
    font-size: 1.45rem;
  }

  .notification-center__hero p {
    font-size: 0.92rem;
    line-height: 1.65;
  }

  .notification-center__hero-side {
    width: 100%;
  }

  .notification-center__hero-stat {
    min-height: 62px;
  }

  .notification-center__toolbar {
    padding: 8px;
    border-radius: 18px;
  }

  .notification-center__toolbar-actions,
  .notification-center__toolbar-actions :deep(.el-button) {
    width: 100%;
  }

  .notification-center__item {
    gap: 12px;
    padding: 14px;
    border-radius: 18px;
  }

  .notification-center__time {
    white-space: normal;
  }

  .notification-center__item-actions :deep(.el-button) {
    flex: 1 1 120px;
    min-width: 0;
  }
}
</style>
