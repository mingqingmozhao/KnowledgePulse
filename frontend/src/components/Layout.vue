<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import QuickSwitcher from '@/components/QuickSwitcher.vue'
import NotificationCenter from '@/components/NotificationCenter.vue'
import { useAuthStore } from '@/stores/auth'
import { useNoteWorkspaceStore } from '@/stores/noteWorkspace'
import { buildDraftNoteRoute, buildWorkspaceTabRoute } from '@/utils/noteWorkspace'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const noteWorkspaceStore = useNoteWorkspaceStore()

const navOpen = ref(false)
const quickSwitcherOpen = ref(false)
const moreNavOpen = ref(false)
const appStandalone = ref(false)
const pwaInstallPrompt = ref<BeforeInstallPromptEvent | null>(null)

type BeforeInstallPromptChoice = {
  outcome: 'accepted' | 'dismissed'
  platform: string
}

type BeforeInstallPromptEvent = Event & {
  prompt: () => Promise<void>
  userChoice: Promise<BeforeInstallPromptChoice>
}

const primaryNavItems = [
  { path: '/dashboard', label: '首页', caption: '从这里继续当前任务' },
  { path: '/folder', label: '文件与笔记', caption: '管理、收藏、回收站' },
  { path: '/search', label: '搜索', caption: '快速找到已有内容' },
  { path: '/share', label: '查看分享', caption: '输入分享链接或分享码' },
  { path: '/profile', label: '个人中心', caption: '资料、头像与账号设置' }
]

const secondaryNavItems = [
  { path: '/templates', label: '模板中心', caption: '常用结构与快速起草' },
  { path: '/attachments', label: '附件中心', caption: '图片、PDF、Word 管理' },
  { path: '/import', label: '导入中心', caption: '批量导入外部资料' },
  { path: '/graph', label: '知识图谱', caption: '查看笔记关系网络' }
]

const mobileNavItems = [
  { key: 'dashboard', path: '/dashboard', label: '首页', mark: '总' },
  { key: 'folder', path: '/folder', label: '笔记', mark: '记' },
  { key: 'new-note', path: '/note/new', label: '新建', mark: '+' },
  { key: 'search', path: '/search', label: '搜索', mark: '搜' },
  { key: 'share', path: '/share', label: '分享', mark: '享' },
  { key: 'profile', path: '/profile', label: '我的', mark: '我' }
]

const pageTitle = computed(() =>
  typeof route.meta.title === 'string' ? route.meta.title : '知识工作台'
)
const workspaceOpenCount = computed(() => noteWorkspaceStore.openCount)
const workspaceDirtyCount = computed(() => noteWorkspaceStore.dirtyCount)
const workspacePinnedCount = computed(() => noteWorkspaceStore.pinnedCount)
const activeWorkspaceTab = computed(() => noteWorkspaceStore.activeTab)
const quickSwitcherShortcut = computed(() =>
  typeof navigator !== 'undefined' && /Mac|iPhone|iPad|iPod/i.test(navigator.platform)
    ? 'Cmd K'
    : 'Ctrl K'
)
const showPwaInstallAction = computed(
  () => !appStandalone.value && (Boolean(pwaInstallPrompt.value) || isMobileBrowser())
)
const workspaceSummary = computed(() => {
  if (!workspaceOpenCount.value) {
    return '从任意页面打开笔记后，都会自动加入你的工作区标签。'
  }

  const dirtySummary = workspaceDirtyCount.value
    ? `其中 ${workspaceDirtyCount.value} 个还没保存。`
    : '当前内容都已经同步。'

  const pinnedSummary = workspacePinnedCount.value ? `另有 ${workspacePinnedCount.value} 个固定标签。` : ''

  return `当前已打开 ${workspaceOpenCount.value} 个编辑标签，${dirtySummary}${pinnedSummary}`
})
const secondaryNavActive = computed(() => secondaryNavItems.some((item) => isActive(item.path)))

watch(
  () => authStore.user?.id ?? authStore.user?.username ?? null,
  (ownerKey) => {
    noteWorkspaceStore.hydrateForUser(ownerKey)
  },
  {
    immediate: true
  }
)

watch(
  () => route.path,
  () => {
    if (secondaryNavActive.value) {
      moreNavOpen.value = true
    }
  },
  {
    immediate: true
  }
)

function isActive(path: string) {
  return route.path === path || route.path.startsWith(`${path}/`)
}

function isMobileNavActive(item: (typeof mobileNavItems)[number]) {
  if (item.key === 'new-note') {
    return route.path.startsWith('/note/')
  }

  return isActive(item.path)
}

function navigate(path: string) {
  navOpen.value = false
  void router.push(path)
}

function navigateMobile(item: (typeof mobileNavItems)[number]) {
  if (item.key === 'new-note') {
    createNote()
    return
  }

  navigate(item.path)
}

function createNote() {
  navOpen.value = false
  void router.push(buildDraftNoteRoute())
}

function continueWorkspace() {
  navOpen.value = false

  if (!activeWorkspaceTab.value) {
    void router.push('/folder')
    return
  }

  void router.push(buildWorkspaceTabRoute(activeWorkspaceTab.value))
}

function openQuickSwitcher() {
  navOpen.value = false
  quickSwitcherOpen.value = true
}

function handleGlobalQuickSwitcher(event: KeyboardEvent) {
  if (!(event.ctrlKey || event.metaKey)) {
    return
  }

  if (event.key.toLowerCase() !== 'k') {
    return
  }

  event.preventDefault()
  openQuickSwitcher()
}

function isStandaloneMode() {
  const standaloneNavigator = navigator as Navigator & {
    standalone?: boolean
  }

  return window.matchMedia('(display-mode: standalone)').matches || standaloneNavigator.standalone === true
}

function isMobileBrowser() {
  return /Android|iPhone|iPad|iPod/i.test(navigator.userAgent)
}

function refreshStandaloneMode() {
  appStandalone.value = isStandaloneMode()
}

function handleBeforeInstallPrompt(event: Event) {
  event.preventDefault()
  pwaInstallPrompt.value = event as BeforeInstallPromptEvent
}

function handleAppInstalled() {
  pwaInstallPrompt.value = null
  refreshStandaloneMode()
  ElMessage.success('App 已安装到设备')
}

async function installPwa() {
  if (appStandalone.value) {
    ElMessage.success('当前已经是 App 模式')
    return
  }

  if (pwaInstallPrompt.value) {
    const installPrompt = pwaInstallPrompt.value
    await installPrompt.prompt()
    const choice = await installPrompt.userChoice

    if (choice.outcome === 'accepted') {
      pwaInstallPrompt.value = null
    }

    return
  }

  await ElMessageBox.alert(
    'Android：用 Chrome 或 Edge 打开站点后，选择浏览器菜单里的“安装应用”。iPhone：用 Safari 打开站点后，点击分享按钮，再选择“添加到主屏幕”。',
    '安装到手机桌面',
    {
      confirmButtonText: '知道了'
    }
  )
}

async function logout() {
  authStore.logout()
  noteWorkspaceStore.clear()
  navOpen.value = false
  await router.push('/login')
}

onMounted(() => {
  refreshStandaloneMode()
  window.addEventListener('keydown', handleGlobalQuickSwitcher)
  window.addEventListener('beforeinstallprompt', handleBeforeInstallPrompt)
  window.addEventListener('appinstalled', handleAppInstalled)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleGlobalQuickSwitcher)
  window.removeEventListener('beforeinstallprompt', handleBeforeInstallPrompt)
  window.removeEventListener('appinstalled', handleAppInstalled)
})
</script>

<template>
  <div class="layout-shell">
    <div v-if="navOpen" class="layout-shell__mask" @click="navOpen = false" />

    <aside class="layout-shell__aside" :class="{ 'layout-shell__aside--open': navOpen }">
      <div class="layout-shell__brand">
        <span class="section-kicker">Knowledge Pulse</span>
        <h1>知识脉冲</h1>
        <p>把零散灵感整理成可搜索、可协作、可连接的个人知识网络。</p>
      </div>

      <div class="layout-shell__user panel">
        <strong>{{ authStore.displayName }}</strong>
        <span>{{ authStore.user?.role || '普通用户' }}</span>
        <small>{{ authStore.user?.email }}</small>
        <div class="layout-shell__user-actions">
          <el-button size="small" plain @click="navigate('/profile')">个人中心</el-button>
          <el-button size="small" plain type="danger" @click="logout">退出登录</el-button>
        </div>
      </div>

      <section class="layout-shell__workspace panel">
        <div class="layout-shell__workspace-copy">
          <span class="section-kicker">Current Workspace</span>
          <strong>{{ activeWorkspaceTab?.title || '还没有打开中的编辑标签' }}</strong>
          <p>{{ workspaceSummary }}</p>
        </div>

        <div class="layout-shell__workspace-metrics">
          <span class="layout-shell__workspace-pill">已打开 {{ workspaceOpenCount }}</span>
          <span
            class="layout-shell__workspace-pill"
            :class="{ 'layout-shell__workspace-pill--alert': workspaceDirtyCount > 0 }"
          >
            未保存 {{ workspaceDirtyCount }}
          </span>
          <span v-if="workspacePinnedCount" class="layout-shell__workspace-pill">已固定 {{ workspacePinnedCount }}</span>
        </div>

        <div class="layout-shell__workspace-actions">
          <el-button type="primary" plain @click="continueWorkspace">
            {{ activeWorkspaceTab ? '继续编辑' : '打开工作区' }}
          </el-button>
          <el-button plain @click="createNote">新建草稿</el-button>
          <el-button v-if="showPwaInstallAction" plain @click="installPwa">安装 App</el-button>
        </div>
      </section>

      <nav class="layout-shell__nav">
        <button
          v-for="item in primaryNavItems"
          :key="item.path"
          class="layout-shell__nav-item"
          :class="{ 'layout-shell__nav-item--active': isActive(item.path) }"
          @click="navigate(item.path)"
        >
          <strong>{{ item.label }}</strong>
          <span>{{ item.caption }}</span>
        </button>

        <button
          type="button"
          class="layout-shell__nav-item layout-shell__nav-more"
          :class="{ 'layout-shell__nav-item--active': secondaryNavActive }"
          @click="moreNavOpen = !moreNavOpen"
        >
          <span>
            <strong>更多工具</strong>
            <small>模板、附件、导入、图谱</small>
          </span>
          <b>{{ moreNavOpen ? '-' : '+' }}</b>
        </button>

        <div v-show="moreNavOpen" class="layout-shell__nav-secondary">
          <button
            v-for="item in secondaryNavItems"
            :key="item.path"
            class="layout-shell__nav-item layout-shell__nav-item--secondary"
            :class="{ 'layout-shell__nav-item--active': isActive(item.path) }"
            @click="navigate(item.path)"
          >
            <strong>{{ item.label }}</strong>
            <span>{{ item.caption }}</span>
          </button>
        </div>
      </nav>
    </aside>

    <div class="layout-shell__main">
      <header class="layout-shell__header panel">
        <div class="layout-shell__header-left">
          <button class="layout-shell__menu" @click="navOpen = true">菜单</button>
          <div>
            <span class="section-kicker">Workspace</span>
            <h2>{{ pageTitle }}</h2>
          </div>
        </div>

        <button type="button" class="layout-shell__switcher-trigger" @click="openQuickSwitcher">
          <div class="layout-shell__switcher-copy">
            <strong>快速切换、搜索或跳转</strong>
            <span>笔记、文件夹、页面、每日日记都能从这里直接打开</span>
          </div>
          <span class="layout-shell__switcher-shortcut">{{ quickSwitcherShortcut }}</span>
        </button>

        <div class="layout-shell__header-right">
          <div v-if="workspaceOpenCount" class="layout-shell__header-pills">
            <span class="layout-shell__header-pill">工作区 {{ workspaceOpenCount }}</span>
            <span
              class="layout-shell__header-pill"
              :class="{ 'layout-shell__header-pill--alert': workspaceDirtyCount > 0 }"
            >
              未保存 {{ workspaceDirtyCount }}
            </span>
            <span v-if="workspacePinnedCount" class="layout-shell__header-pill">已固定 {{ workspacePinnedCount }}</span>
          </div>

          <div class="layout-shell__header-actions">
            <NotificationCenter />
            <el-button plain @click="navigate('/profile')">个人中心</el-button>
            <el-button plain @click="logout">退出登录</el-button>
          </div>
        </div>
      </header>

      <main class="layout-shell__content">
        <router-view />
      </main>
    </div>

    <QuickSwitcher v-model:visible="quickSwitcherOpen" />

    <nav class="layout-shell__mobile-nav" aria-label="手机快捷导航">
      <button
        v-for="item in mobileNavItems"
        :key="item.key"
        type="button"
        class="layout-shell__mobile-nav-item"
        :class="{ 'layout-shell__mobile-nav-item--active': isMobileNavActive(item) }"
        @click="navigateMobile(item)"
      >
        <span class="layout-shell__mobile-nav-mark">{{ item.mark }}</span>
        <strong>{{ item.label }}</strong>
      </button>
    </nav>
  </div>
</template>

<style scoped>
.layout-shell {
  display: grid;
  grid-template-columns: 296px minmax(0, 1fr);
  min-height: 100vh;
}

.layout-shell__mask {
  position: fixed;
  inset: 0;
  z-index: 29;
  background: rgba(20, 24, 28, 0.28);
}

.layout-shell__aside {
  position: sticky;
  top: 0;
  display: grid;
  align-content: start;
  gap: 14px;
  height: 100vh;
  max-height: 100dvh;
  min-height: 100vh;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 20px 16px;
  background:
    radial-gradient(circle at top left, rgba(197, 157, 88, 0.16), transparent 42%),
    linear-gradient(180deg, rgba(255, 252, 247, 0.88), rgba(245, 236, 223, 0.76));
  border-right: 1px solid var(--line);
  backdrop-filter: blur(18px);
  scrollbar-gutter: stable;
  -webkit-overflow-scrolling: touch;
}

.layout-shell__aside::-webkit-scrollbar {
  width: 8px;
}

.layout-shell__aside::-webkit-scrollbar-track {
  background: transparent;
}

.layout-shell__aside::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: rgba(141, 69, 41, 0.18);
}

.layout-shell__aside::-webkit-scrollbar-thumb:hover {
  background: rgba(141, 69, 41, 0.28);
}

.layout-shell__brand h1 {
  margin: 6px 0 0;
  font-family: var(--header-font);
  font-size: 2rem;
}

.layout-shell__brand p {
  margin: 8px 0 0;
  color: var(--text-soft);
  line-height: 1.55;
}

.layout-shell__user,
.layout-shell__workspace {
  display: grid;
  gap: 8px;
  padding: 14px;
}

.layout-shell__user span,
.layout-shell__user small,
.layout-shell__workspace p {
  color: var(--text-soft);
}

.layout-shell__user-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-top: 4px;
}

.layout-shell__user-actions :deep(.el-button) {
  margin: 0;
}

.layout-shell__workspace-copy strong {
  display: block;
  margin-top: 6px;
}

.layout-shell__workspace-copy p {
  margin: 6px 0 0;
  line-height: 1.5;
}

.layout-shell__workspace-metrics,
.layout-shell__workspace-actions,
.layout-shell__header-pills,
.layout-shell__header-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.layout-shell__workspace-pill,
.layout-shell__header-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 30px;
  padding: 0 11px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.78);
  color: var(--text-soft);
  font-size: 0.84rem;
  border: 1px solid rgba(184, 92, 56, 0.12);
}

.layout-shell__workspace-pill--alert,
.layout-shell__header-pill--alert {
  color: #8d4529;
  background: rgba(184, 92, 56, 0.12);
  border-color: rgba(184, 92, 56, 0.2);
}

.layout-shell__nav {
  display: grid;
  gap: 7px;
}

.layout-shell__nav-item {
  display: grid;
  gap: 4px;
  padding: 12px 14px;
  border: 1px solid transparent;
  border-radius: 15px;
  background: rgba(255, 255, 255, 0.44);
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition: transform 0.2s ease, border-color 0.2s ease, background 0.2s ease;
}

.layout-shell__nav-item span {
  color: var(--text-soft);
  font-size: 0.88rem;
}

.layout-shell__nav-more {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.layout-shell__nav-more > span {
  display: grid;
  gap: 4px;
}

.layout-shell__nav-more small {
  color: var(--text-soft);
  font-size: 0.82rem;
}

.layout-shell__nav-more b {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  color: #8d4529;
  font-weight: 800;
}

.layout-shell__nav-secondary {
  display: grid;
  gap: 6px;
  padding: 6px 0 0 10px;
  border-left: 1px solid rgba(184, 92, 56, 0.12);
}

.layout-shell__nav-item--secondary {
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.32);
}

.layout-shell__nav-item:hover,
.layout-shell__nav-item--active {
  border-color: rgba(184, 92, 56, 0.22);
  background: rgba(184, 92, 56, 0.08);
  transform: translateX(4px);
}

.layout-shell__main {
  display: grid;
  align-content: start;
  gap: 16px;
  padding: 16px;
}

.layout-shell__header {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
}

.layout-shell__header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.layout-shell__header-left h2 {
  margin: 4px 0 0;
  font-family: var(--header-font);
  font-size: 1.35rem;
}

.layout-shell__menu {
  display: none;
  padding: 8px 13px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.56);
  cursor: pointer;
}

.layout-shell__switcher-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  width: 100%;
  padding: 8px 11px;
  border: 1px solid var(--line);
  border-radius: 18px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.76), rgba(255, 250, 244, 0.64)),
    rgba(255, 255, 255, 0.6);
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    background-color 0.2s ease,
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

.layout-shell__switcher-trigger:hover {
  transform: translateY(-1px);
  border-color: rgba(184, 92, 56, 0.22);
  box-shadow: 0 14px 28px rgba(141, 69, 41, 0.08);
}

.layout-shell__switcher-copy {
  display: grid;
  gap: 2px;
}

.layout-shell__switcher-copy strong {
  font-size: 0.98rem;
}

.layout-shell__switcher-copy span {
  color: var(--text-soft);
  font-size: 0.85rem;
}

.layout-shell__switcher-shortcut {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 64px;
  min-height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(184, 92, 56, 0.08);
  border: 1px solid rgba(184, 92, 56, 0.16);
  color: #8d4529;
  font-size: 0.82rem;
  white-space: nowrap;
}

.layout-shell__header-right {
  display: grid;
  justify-items: end;
  gap: 8px;
}

.layout-shell__content {
  min-width: 0;
}

.layout-shell__mobile-nav {
  display: none;
}

@media (max-width: 1180px) {
  .layout-shell {
    grid-template-columns: 1fr;
  }

  .layout-shell__aside {
    position: fixed;
    left: 0;
    top: 0;
    z-index: 30;
    width: min(300px, calc(100vw - 20px));
    height: 100dvh;
    min-height: 100dvh;
    padding-bottom: calc(20px + env(safe-area-inset-bottom));
    transform: translateX(-100%);
    transition: transform 0.24s ease;
  }

  .layout-shell__aside--open {
    transform: translateX(0);
  }

  .layout-shell__menu {
    display: inline-flex;
  }
}

@media (max-width: 900px) {
  .layout-shell__main {
    padding: 12px;
  }

  .layout-shell__aside {
    gap: 12px;
    padding: 16px 14px calc(18px + env(safe-area-inset-bottom));
  }

  .layout-shell__header {
    grid-template-columns: 1fr;
  }

  .layout-shell__switcher-trigger {
    align-items: flex-start;
  }

  .layout-shell__header-right {
    justify-items: stretch;
  }

  .layout-shell__header-pills,
  .layout-shell__header-actions {
    justify-content: flex-end;
  }
}

@media (max-width: 640px) {
  .layout-shell__main {
    gap: 10px;
    padding: 8px 8px calc(76px + env(safe-area-inset-bottom));
  }

  .layout-shell__aside {
    width: min(286px, calc(100vw - 10px));
    padding: 12px 10px calc(14px + env(safe-area-inset-bottom));
  }

  .layout-shell__brand h1 {
    font-size: 1.76rem;
  }

  .layout-shell__brand p,
  .layout-shell__workspace p {
    font-size: 0.92rem;
    line-height: 1.5;
  }

  .layout-shell__workspace,
  .layout-shell__user {
    padding: 12px;
  }

  .layout-shell__workspace-actions :deep(.el-button) {
    flex: 1 1 120px;
    min-width: 0;
  }

  .layout-shell__header {
    position: sticky;
    top: 6px;
    z-index: 20;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    padding: 8px 10px;
    border-radius: 18px;
  }

  .layout-shell__header-left {
    min-width: 0;
    flex: 1 1 auto;
    align-items: center;
    gap: 8px;
  }

  .layout-shell__header-left .section-kicker {
    display: none;
  }

  .layout-shell__header-left h2 {
    overflow: hidden;
    margin-top: 0;
    font-size: 1.04rem;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .layout-shell__menu {
    flex: 0 0 auto;
    padding: 8px 10px;
  }

  .layout-shell__switcher-trigger,
  .layout-shell__header-pills,
  .layout-shell__header-actions :deep(.el-button) {
    display: none;
  }

  .layout-shell__header-right {
    display: flex;
    flex: 0 0 auto;
    justify-items: end;
  }

  .layout-shell__header-actions {
    gap: 0;
  }

  .layout-shell__mobile-nav {
    position: fixed;
    left: 8px;
    right: 8px;
    bottom: calc(6px + env(safe-area-inset-bottom));
    z-index: 28;
    display: grid;
    grid-template-columns: repeat(6, minmax(0, 1fr));
    gap: 4px;
    padding: 6px;
    border: 1px solid rgba(141, 69, 41, 0.14);
    border-radius: 20px;
    background:
      linear-gradient(180deg, rgba(255, 252, 247, 0.96), rgba(246, 237, 226, 0.94)),
      rgba(255, 255, 255, 0.92);
    box-shadow: 0 18px 46px rgba(82, 55, 34, 0.2);
    backdrop-filter: blur(18px);
  }

  .layout-shell__mobile-nav-item {
    display: grid;
    place-items: center;
    gap: 2px;
    min-width: 0;
    padding: 5px 1px;
    border: 0;
    border-radius: 14px;
    background: transparent;
    color: var(--text-soft);
    cursor: pointer;
  }

  .layout-shell__mobile-nav-mark {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 24px;
    height: 24px;
    border-radius: 999px;
    background: rgba(184, 92, 56, 0.08);
    color: #8d4529;
    font-size: 0.72rem;
    font-weight: 800;
  }

  .layout-shell__mobile-nav-item strong {
    overflow: hidden;
    max-width: 100%;
    font-size: 0.66rem;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .layout-shell__mobile-nav-item--active {
    color: #8d4529;
    background: rgba(184, 92, 56, 0.1);
  }

  .layout-shell__mobile-nav-item--active .layout-shell__mobile-nav-mark {
    background: #8d4529;
    color: #fffaf1;
  }
}

@media (max-width: 420px) {
  .layout-shell__main {
    padding: 7px 7px calc(72px + env(safe-area-inset-bottom));
  }

  .layout-shell__header {
    padding: 8px 10px;
  }

  .layout-shell__header-pill {
    min-height: 30px;
    padding: 0 10px;
    font-size: 0.78rem;
  }

  .layout-shell__mobile-nav {
    left: 6px;
    right: 6px;
    bottom: calc(5px + env(safe-area-inset-bottom));
    border-radius: 18px;
  }
}
</style>
