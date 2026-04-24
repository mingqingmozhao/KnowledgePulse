<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { getSharedNote } from '@/api/share'
import { useAuthStore } from '@/stores/auth'
import type { Note } from '@/types'
import { escapeHtml, formatDateTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const password = ref('')
const note = ref<Note | null>(null)
const errorMessage = ref('')
const requiresPassword = ref(false)
const requiresLogin = ref(false)

const token = computed(() => String(route.params.token))
const fallbackHtml = computed(() => `<pre>${escapeHtml(note.value?.content || '')}</pre>`)

async function loadSharedContent() {
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

onMounted(() => {
  void loadSharedContent()
})

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
      redirect: `/share/${token.value}`
    }
  })
}
</script>

<template>
  <div class="share-page">
    <div class="share-page__container">
      <header class="share-page__hero panel">
        <span class="section-kicker">Shared Note</span>
        <h1>{{ note?.title || '共享知识卡片' }}</h1>
        <p>这里用于查看公开分享、密码保护分享，以及登录后可见的知识内容。</p>
      </header>

      <section v-if="loading" class="share-page__content panel">
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
            <p>这是一个“登录可见”分享链接。登录后会自动带你回到当前页面继续查看。</p>
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
  width: min(980px, 100%);
  margin: 0 auto;
  display: grid;
  gap: 24px;
}

.share-page__hero,
.share-page__content {
  padding: 28px 30px;
}

.share-page__hero {
  background:
    radial-gradient(circle at top right, rgba(197, 157, 88, 0.22), transparent 30%),
    linear-gradient(160deg, rgba(255, 252, 247, 0.96), rgba(241, 232, 219, 0.88));
}

.share-page__hero h1 {
  margin: 10px 0 0;
  font-family: var(--header-font);
  font-size: clamp(2rem, 3vw, 3rem);
}

.share-page__hero p {
  margin: 12px 0 0;
  color: var(--text-soft);
  line-height: 1.8;
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

.share-page__locked {
  display: grid;
  gap: 14px;
  text-align: center;
}

.share-page__locked p,
.share-page__hint {
  margin: 0;
  color: var(--text-soft);
  line-height: 1.8;
}

.share-page__password {
  display: flex;
  gap: 12px;
  width: min(460px, 100%);
  margin: 8px auto 0;
}

@media (max-width: 720px) {
  .share-page {
    padding: 16px;
  }

  .share-page__hero,
  .share-page__content {
    padding: 22px;
  }

  .share-page__meta,
  .share-page__password {
    flex-direction: column;
    align-items: stretch;
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
  .share-page__content {
    padding: 16px;
  }

  .share-page__hero h1 {
    font-size: 1.78rem;
    line-height: 1.16;
  }

  .share-page__hero p,
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
