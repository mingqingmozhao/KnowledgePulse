<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { Collaborator } from '@/types'
import { resolveAvatarSrc } from '@/utils/avatar'
import { initials } from '@/utils/format'

const props = defineProps<{
  collaborators: Collaborator[]
}>()

type StripState = 'expanded' | 'minimized' | 'hidden'
type StripPosition = {
  x: number
  y: number
}

const STORAGE_KEY = 'knowledgepulse.collaborator-strip-state'
const POSITION_STORAGE_KEY = 'knowledgepulse.collaborator-strip-position'
const savedState = window.localStorage.getItem(STORAGE_KEY) as StripState | null
const stripState = ref<StripState>(savedState === 'expanded' || savedState === 'hidden' ? savedState : 'minimized')
const stripRef = ref<HTMLElement | null>(null)
const position = ref<StripPosition | null>(readSavedPosition())
let dragStart: {
  pointerX: number
  pointerY: number
  startX: number
  startY: number
} | null = null
let hasDragged = false
let suppressNextClick = false

const sortedCollaborators = computed(() =>
  [...props.collaborators].sort((left, right) => {
    const leftWeight = Number(Boolean(left.typing || left.active))
    const rightWeight = Number(Boolean(right.typing || right.active))
    return rightWeight - leftWeight
  })
)

const activeCount = computed(() => sortedCollaborators.value.filter((member) => member.typing || member.active).length)
const visibleAvatars = computed(() => sortedCollaborators.value.slice(0, 4))
const floatingStyle = computed(() => {
  if (!position.value) {
    return {}
  }

  return {
    left: `${position.value.x}px`,
    top: `${position.value.y}px`,
    right: 'auto',
    bottom: 'auto'
  }
})
const compactStatus = computed(() => {
  const typingMembers = sortedCollaborators.value.filter((member) => member.typing)

  if (typingMembers.length) {
    const firstMember = typingMembers[0]
    const region = firstMember.editingRegion || '正文'
    return typingMembers.length === 1 ? `${displayName(firstMember)} 正在编辑 ${region}` : `${typingMembers.length} 人正在编辑`
  }

  if (activeCount.value) {
    return `${activeCount.value} 人在线`
  }

  return '点击查看协作状态'
})

watch(stripState, (value) => {
  window.localStorage.setItem(STORAGE_KEY, value)
})

watch(position, (value) => {
  if (!value) {
    return
  }

  window.localStorage.setItem(POSITION_STORAGE_KEY, JSON.stringify(value))
})

function displayName(member: Collaborator): string {
  return member.nickname || member.username || member.email || `成员 ${member.userId}`
}

function statusText(member: Collaborator): string {
  if (member.typing) {
    return member.editingRegion ? `正在编辑：${member.editingRegion}` : '正在输入'
  }

  if (member.active) {
    return '在线'
  }

  if (member.permission === 'OWNER') {
    return '拥有者'
  }

  return member.permission
}

function expandStrip() {
  stripState.value = 'expanded'
}

function minimizeStrip() {
  stripState.value = 'minimized'
}

function hideStrip() {
  stripState.value = 'hidden'
}

function readSavedPosition(): StripPosition | null {
  try {
    const parsed = JSON.parse(window.localStorage.getItem(POSITION_STORAGE_KEY) || 'null') as Partial<StripPosition> | null
    const x = parsed?.x
    const y = parsed?.y

    if (
      typeof x === 'number' &&
      typeof y === 'number' &&
      Number.isFinite(x) &&
      Number.isFinite(y) &&
      x >= 0 &&
      y >= 0
    ) {
      return {
        x,
        y
      }
    }
  } catch {
    return null
  }

  return null
}

function clampPosition(nextX: number, nextY: number): StripPosition {
  const element = stripRef.value
  const rect = element?.getBoundingClientRect()
  const width = rect?.width ?? 300
  const height = rect?.height ?? 64
  const margin = 12
  const maxX = Math.max(margin, window.innerWidth - width - margin)
  const maxY = Math.max(margin, window.innerHeight - height - margin)

  return {
    x: Math.min(Math.max(nextX, margin), maxX),
    y: Math.min(Math.max(nextY, margin), maxY)
  }
}

function ensurePosition() {
  if (position.value) {
    position.value = clampPosition(position.value.x, position.value.y)
    return position.value
  }

  const rect = stripRef.value?.getBoundingClientRect()

  if (rect) {
    position.value = clampPosition(rect.left, rect.top)
    return position.value
  }

  position.value = clampPosition(window.innerWidth - 324, window.innerHeight - 88)
  return position.value
}

function startDrag(event: PointerEvent) {
  if (event.button !== 0) {
    return
  }

  const target = event.target as HTMLElement | null
  if (target?.closest('[data-no-drag="true"]')) {
    return
  }

  const currentPosition = ensurePosition()
  dragStart = {
    pointerX: event.clientX,
    pointerY: event.clientY,
    startX: currentPosition.x,
    startY: currentPosition.y
  }
  hasDragged = false
  document.addEventListener('pointermove', handlePointerMove)
  document.addEventListener('pointerup', stopDrag, { once: true })
}

function handlePointerMove(event: PointerEvent) {
  if (!dragStart) {
    return
  }

  const deltaX = event.clientX - dragStart.pointerX
  const deltaY = event.clientY - dragStart.pointerY

  if (Math.abs(deltaX) + Math.abs(deltaY) > 4) {
    hasDragged = true
  }

  position.value = clampPosition(dragStart.startX + deltaX, dragStart.startY + deltaY)
}

function stopDrag() {
  if (hasDragged) {
    suppressNextClick = true
    window.setTimeout(() => {
      suppressNextClick = false
    }, 0)
  }

  dragStart = null
  document.removeEventListener('pointermove', handlePointerMove)
}

function handleMiniClick() {
  if (suppressNextClick) {
    return
  }

  expandStrip()
}

function handleLauncherClick() {
  if (suppressNextClick) {
    return
  }

  minimizeStrip()
}

function handleViewportResize() {
  if (!position.value) {
    return
  }

  position.value = clampPosition(position.value.x, position.value.y)
}

onMounted(() => {
  window.addEventListener('resize', handleViewportResize)
  window.visualViewport?.addEventListener('resize', handleViewportResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleViewportResize)
  window.visualViewport?.removeEventListener('resize', handleViewportResize)
  document.removeEventListener('pointermove', handlePointerMove)
})
</script>

<template>
  <button
    v-if="sortedCollaborators.length && stripState === 'hidden'"
    ref="stripRef"
    type="button"
    class="collaborator-strip-launcher"
    :style="floatingStyle"
    @pointerdown="startDrag"
    @click="handleLauncherClick"
  >
    协作者 {{ sortedCollaborators.length }}
  </button>

  <button
    v-else-if="sortedCollaborators.length && stripState === 'minimized'"
    ref="stripRef"
    type="button"
    class="collaborator-strip-mini"
    :style="floatingStyle"
    @pointerdown="startDrag"
    @click="handleMiniClick"
  >
    <span class="collaborator-strip-mini__avatars">
      <el-avatar
        v-for="member in visibleAvatars"
        :key="member.userId"
        :size="30"
        class="collaborator-strip__avatar collaborator-strip-mini__avatar"
        :src="resolveAvatarSrc(member.avatar)"
      >
        {{ initials(displayName(member)) }}
      </el-avatar>
    </span>
    <span class="collaborator-strip-mini__copy">
      <strong>{{ sortedCollaborators.length }} 位协作者</strong>
      <small>{{ compactStatus }}</small>
    </span>
    <span class="collaborator-strip-mini__action">展开</span>
  </button>

  <div v-else-if="sortedCollaborators.length" ref="stripRef" class="collaborator-strip panel" :style="floatingStyle">
    <div class="collaborator-strip__header" @pointerdown="startDrag">
      <div class="collaborator-strip__title">
        <span class="section-kicker">Realtime</span>
        <strong>{{ sortedCollaborators.length }} 位协作者</strong>
      </div>

      <div class="collaborator-strip__controls" data-no-drag="true">
        <button type="button" @click="minimizeStrip">最小化</button>
        <button type="button" @click="hideStrip">隐藏</button>
      </div>
    </div>

    <div class="collaborator-strip__list">
      <div v-for="member in sortedCollaborators" :key="member.userId" class="collaborator-strip__item">
        <el-avatar :size="42" class="collaborator-strip__avatar" :src="resolveAvatarSrc(member.avatar)">
          {{ initials(displayName(member)) }}
        </el-avatar>

        <div class="collaborator-strip__meta">
          <strong>{{ displayName(member) }}</strong>
          <span>{{ statusText(member) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.collaborator-strip {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 40;
  width: min(360px, calc(100vw - 32px));
  padding: 18px;
  background: rgba(255, 250, 241, 0.94);
  border-color: rgba(184, 92, 56, 0.18);
  box-shadow: 0 18px 44px rgba(54, 92, 75, 0.14);
}

.collaborator-strip__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  cursor: grab;
  user-select: none;
  touch-action: none;
}

.collaborator-strip__header:active {
  cursor: grabbing;
}

.collaborator-strip__title {
  display: grid;
  gap: 4px;
}

.collaborator-strip__controls {
  display: flex;
  align-items: center;
  gap: 8px;
}

.collaborator-strip__controls button,
.collaborator-strip-launcher,
.collaborator-strip-mini {
  border: 1px solid rgba(184, 92, 56, 0.16);
  color: var(--text-main);
  background: rgba(255, 255, 255, 0.72);
  cursor: pointer;
  font: inherit;
  transition: transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease, background-color 0.18s ease;
}

.collaborator-strip__controls button {
  min-height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  color: var(--text-soft);
  font-size: 0.78rem;
}

.collaborator-strip__controls button:hover,
.collaborator-strip-launcher:hover,
.collaborator-strip-mini:hover {
  transform: translateY(-1px);
  border-color: rgba(141, 69, 41, 0.28);
  box-shadow: 0 10px 22px rgba(141, 69, 41, 0.1);
}

.collaborator-strip__list {
  display: grid;
  gap: 12px;
  margin-top: 14px;
  max-height: min(320px, 42vh);
  overflow: auto;
  padding-right: 2px;
}

.collaborator-strip__item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.collaborator-strip__avatar {
  color: var(--paper-strong);
  background: linear-gradient(135deg, var(--moss), var(--accent));
}

.collaborator-strip__meta {
  display: grid;
  gap: 2px;
}

.collaborator-strip__meta span {
  color: var(--text-soft);
  font-size: 0.84rem;
}

.collaborator-strip-mini {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 40;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  width: min(300px, calc(100vw - 32px));
  min-height: 58px;
  padding: 10px 12px;
  border-radius: 999px;
  background:
    radial-gradient(circle at top left, rgba(54, 92, 75, 0.14), transparent 38%),
    rgba(255, 250, 241, 0.94);
  box-shadow: 0 14px 34px rgba(54, 92, 75, 0.12);
  text-align: left;
  user-select: none;
  touch-action: none;
}

.collaborator-strip-mini__avatars {
  display: flex;
  align-items: center;
  min-width: 36px;
}

.collaborator-strip-mini__avatar {
  margin-left: -8px;
  border: 2px solid rgba(255, 250, 241, 0.96);
}

.collaborator-strip-mini__avatar:first-child {
  margin-left: 0;
}

.collaborator-strip-mini__copy {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.collaborator-strip-mini__copy strong,
.collaborator-strip-mini__copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.collaborator-strip-mini__copy strong {
  color: var(--text-main);
  font-size: 0.9rem;
}

.collaborator-strip-mini__copy small {
  color: var(--text-soft);
  font-size: 0.76rem;
}

.collaborator-strip-mini__action {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(54, 92, 75, 0.1);
  color: #365c4b;
  font-size: 0.78rem;
  white-space: nowrap;
}

.collaborator-strip-launcher {
  position: fixed;
  right: 18px;
  bottom: 18px;
  z-index: 40;
  min-height: 38px;
  padding: 0 13px;
  border-radius: 999px;
  background: rgba(255, 250, 241, 0.9);
  color: var(--text-soft);
  box-shadow: 0 10px 24px rgba(54, 92, 75, 0.1);
  font-size: 0.82rem;
  user-select: none;
  touch-action: none;
}

@media (max-width: 900px) {
  .collaborator-strip {
    left: 16px;
    right: 16px;
    bottom: 16px;
    width: auto;
  }

  .collaborator-strip-mini {
    left: 16px;
    right: 16px;
    bottom: 16px;
    width: auto;
  }

  .collaborator-strip-launcher {
    right: 16px;
    bottom: 16px;
  }

  .collaborator-strip__header {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (max-width: 640px) {
  .collaborator-strip,
  .collaborator-strip-mini {
    bottom: calc(88px + env(safe-area-inset-bottom));
  }

  .collaborator-strip-launcher {
    bottom: calc(88px + env(safe-area-inset-bottom));
  }
}

@media (max-width: 520px) {
  .collaborator-strip,
  .collaborator-strip-mini {
    left: 10px !important;
    right: 10px !important;
    top: auto !important;
    bottom: calc(88px + env(safe-area-inset-bottom)) !important;
    width: auto;
    max-width: calc(100vw - 20px);
  }

  .collaborator-strip {
    padding: 14px;
  }

  .collaborator-strip__controls {
    width: 100%;
    flex-wrap: wrap;
  }

  .collaborator-strip__controls button {
    flex: 1 1 88px;
  }

  .collaborator-strip__list {
    max-height: min(260px, 40dvh);
  }

  .collaborator-strip-mini {
    grid-template-columns: auto minmax(0, 1fr);
    padding: 10px 12px;
  }

  .collaborator-strip-mini__action {
    display: none;
  }

  .collaborator-strip-launcher {
    right: 10px !important;
    bottom: calc(88px + env(safe-area-inset-bottom)) !important;
    top: auto !important;
    left: auto !important;
  }
}
</style>
