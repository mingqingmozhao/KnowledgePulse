<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { getSharedNote } from '@/api/share'
import { useAuthStore } from '@/stores/auth'
import type { Note } from '@/types'
import { escapeHtml, formatDateTime } from '@/utils/format'
import { buildPublicShareUrl } from '@/utils/publicUrl'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const password = ref('')
const shareInput = ref('')
const note = ref<Note | null>(null)
const errorMessage = ref('')
const requiresPassword = ref(false)
const requiresLogin = ref(false)

const token = computed(() => {
  const raw = route.params.token
  return typeof raw === 'string' ? raw.trim() : ''
})
const hasToken = computed(() => Boolean(token.value))
const fallbackHtml = computed(() => `<pre>${escapeHtml(note.value?.content || '')}</pre>`)
const currentShareLink = computed(() => (hasToken.value ? buildPublicShareUrl(token.value) : ''))

function extractShareToken(value: string) {
  const trimmed = value.trim().replace(/^<|>$/g, '')

  if (!trimmed) {
    return ''
  }

  try {
    const url = new URL(trimmed, window.location.origin)
    const segments = url.pathname.split('/').filter(Boolean)
    const shareIndex = segments.findIndex((segment) => segment.toLowerCase() === 'share')

    if (shareIndex >= 0) {
      return decodeURIComponent(segments[shareIndex + 1] || '')
    }

    return decodeURIComponent(segments.at(-1) || '')
  } catch {
    return trimmed.split(/[/?#]/).filter(Boolean).at(-1) || ''
  }
}

function resetSharedContent() {
  note.value = null
  errorMessage.value = ''
  requiresPassword.value = false
  requiresLogin.value = false
  loading.value = false
}

async function loadSharedContent() {
  if (!token.value) {
    resetSharedContent()
    return
  }

  loading.value = true
  errorMessage.value = ''
  requiresPassword.value = false
  requiresLogin.value = false

  try {
    note.value = await getSharedNote(token.value, password.value || undefined)
  } catch (error) {
    const message = error instanceof Error ? error.message : '无法读取分享内容'

    note.value = null
    errorMessage.value = message
    requiresLogin.value = /login is required|authenticated/i.test(message)
    requiresPassword.value = /password/i.test(message) && !requiresLogin.value
  } finally {
    loading.value = false
  }
}

function openSharedLink() {
  const nextToken = extractShareToken(shareInput.value)

  if (!nextToken) {
    ElMessage.warning('请先粘贴分享链接或输入分享码')
    return
  }

  password.value = ''

  if (nextToken === token.value) {
    void loadSharedContent()
    return
  }

  void router.push(`/share/${encodeURIComponent(nextToken)}`)
}

async function submitPassword() {
  if (!password.value.trim()) {
    ElMessage.warning('请输入分享密码')
    return
  }

  await loadSharedContent()
}

function goToLogin() {
  void router.push({
    path: '/login',
    query: {
      redirect: `/share/${encodeURIComponent(token.value)}`
    }
  })
}

function goHome() {
  void router.push(authStore.isAuthenticated ? '/dashboard' : '/login')
}

watch(
  token,
  (value) => {
    password.value = ''

    if (!value) {
      shareInput.value = ''
      resetSharedContent()
      return
    }

    shareInput.value = buildPublicShareUrl(value)
    void loadSharedContent()
  },
  {
    immediate: true
  }
)
</script>

<template>
  <div class="share-page">
    <div class="share-page__container">
      <header class="share-page__hero panel">
        <div>
          <span class="section-kicker">Shared Note</span>
          <h1>{{ note?.title || '查看别人分享的笔记' }}</h1>
          <p>
            不用再去浏览器地址栏手动输入。把别人发来的完整分享链接、/share/xxx 路径或分享码粘贴到下面，就能在这里查看内容。
          </p>
        </div>
        <el-button plain @click="goHome">
          {{ authStore.isAuthenticated ? '回到工作台' : '去登录' }}
        </el-button>
      </header>

      <section class="share-page__opener panel">
        <div class="share-page__opener-copy">
          <span class="section-kicker">Open Share</span>
          <strong>粘贴分享链接或分享码</strong>
          <p>支持完整链接、局域网/ngrok 链接、/share/xxx 路径，也支持只输入最后那段分享码。</p>
        </div>

        <div class="share-page__input-row">
          <el-input
            v-model="shareInput"
            clearable
            placeholder="例如：http://localhost:5173/share/abc123 或 abc123"
            @keyup.enter="openSharedLink"
          />
          <el-button type="primary" @click="openSharedLink">查看分享</el-button>
        </div>

        <div v-if="hasToken" class="share-page__current-link">
          <span>当前分享</span>
          <code>{{ currentShareLink }}</code>
        </div>
      </section>

      <section v-if="!hasToken" class="share-page__content panel">
        <div class="share-page__empty-guide">
          <strong>等待打开一个分享</strong>
          <p>你可以从聊天窗口、邮件或系统通知里复制分享链接，然后直接粘贴到上面的输入框。</p>
        </div>
      </section>

      <section v-else-if="loading" class="share-page__content panel">
        <div class="empty-state">分享内容加载中...</div>
      </section>

      <section v-else-if="note" class="share-page__content panel">
        <div class="share-page__meta">
          <span>更新时间：{{ formatDateTime(note.updateTime) }}</span>
          <div class="share-page__tags">
            <el-tag v-for="tag in note.tags" :key="tag" effect="plain">#{{ tag }}</el-tag>
          </div>
        </div>

        <article class="markdown-preview share-page__article" v-html="note.htmlContent || fallbackHtml" />
      </section>

      <section v-else class="share-page__content panel">
        <div class="share-page__locked">
          <strong>{{ errorMessage || '该分享内容暂时不可访问。' }}</strong>

          <template v-if="requiresLogin">
            <p>这是一个“登录可见”分享。登录后系统会自动带你回到当前分享页继续查看。</p>
            <el-button type="primary" @click="goToLogin">
              {{ authStore.isAuthenticated ? '重新登录' : '前往登录' }}
            </el-button>
          </template>

          <template v-else>
            <p>如果这是密码保护的分享，请输入访问密码后继续查看。</p>

            <div class="share-page__password">
              <el-input
                v-model="password"
                type="password"
                show-password
                placeholder="请输入分享密码"
                @keyup.enter="submitPassword"
              />
              <el-button type="primary" @click="submitPassword">验证并查看</el-button>
            </div>

            <small v-if="requiresPassword" class="share-page__hint">
              当前链接需要密码验证。
            </small>
          </template>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.share-page {
  min-height: 100vh;
  padding: 24px;
}

.share-page__container {
  display: grid;
  width: min(980px, 100%);
  gap: 24px;
  margin: 0 auto;
}

.share-page__hero,
.share-page__opener,
.share-page__content {
  padding: 28px 30px;
}

.share-page__hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 22px;
  background:
    radial-gradient(circle at top right, rgba(197, 157, 88, 0.22), transparent 30%),
    linear-gradient(160deg, rgba(255, 252, 247, 0.96), rgba(241, 232, 219, 0.88));
}

.share-page__hero h1 {
  margin: 10px 0 0;
  font-family: var(--header-font);
  font-size: clamp(2rem, 3vw, 3rem);
}

.share-page__hero p,
.share-page__opener-copy p {
  margin: 12px 0 0;
  color: var(--text-soft);
  line-height: 1.8;
}

.share-page__opener {
  display: grid;
  gap: 18px;
  background:
    radial-gradient(circle at top left, rgba(54, 92, 75, 0.12), transparent 34%),
    rgba(255, 255, 255, 0.72);
}

.share-page__opener-copy {
  display: grid;
  gap: 4px;
}

.share-page__opener-copy strong {
  font-size: 1.15rem;
}

.share-page__input-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
}

.share-page__current-link {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  padding: 12px 14px;
  border: 1px solid rgba(54, 92, 75, 0.14);
  border-radius: 16px;
  background: rgba(54, 92, 75, 0.07);
  color: #365c4b;
}

.share-page__current-link span {
  flex: 0 0 auto;
  font-weight: 700;
}

.share-page__current-link code {
  overflow: hidden;
  min-width: 0;
  color: var(--text-main);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.share-page__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
  color: var(--text-soft);
}

.share-page__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.share-page__article {
  min-height: 320px;
}

.share-page__empty-guide,
.share-page__locked {
  display: grid;
  gap: 14px;
  text-align: center;
}

.share-page__empty-guide {
  min-height: 220px;
  place-content: center;
  border: 1px dashed rgba(141, 69, 41, 0.18);
  border-radius: 22px;
  background:
    radial-gradient(circle at center, rgba(184, 92, 56, 0.08), transparent 44%),
    rgba(255, 255, 255, 0.48);
}

.share-page__empty-guide p,
.share-page__locked p,
.share-page__hint {
  margin: 0;
  color: var(--text-soft);
  line-height: 1.8;
}

.share-page__password {
  display: flex;
  width: min(460px, 100%);
  gap: 12px;
  margin: 8px auto 0;
}

@media (max-width: 720px) {
  .share-page {
    padding: 16px;
  }

  .share-page__hero,
  .share-page__opener,
  .share-page__content {
    padding: 22px;
  }

  .share-page__hero,
  .share-page__meta,
  .share-page__password {
    flex-direction: column;
    align-items: stretch;
  }

  .share-page__input-row {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 420px) {
  .share-page {
    padding: 10px;
  }

  .share-page__container {
    gap: 16px;
  }

  .share-page__hero,
  .share-page__opener,
  .share-page__content {
    padding: 16px;
  }

  .share-page__hero h1 {
    font-size: 1.78rem;
    line-height: 1.16;
  }

  .share-page__hero p,
  .share-page__opener-copy p,
  .share-page__empty-guide p,
  .share-page__locked p,
  .share-page__hint {
    line-height: 1.65;
  }

  .share-page__article {
    min-height: 220px;
  }

  .share-page__password :deep(.el-button) {
    width: 100%;
  }
}
</style>
