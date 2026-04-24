<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const formModel = reactive({
  username: '',
  password: ''
})

const rules: FormRules = {
  username: [
    {
      required: true,
      message: '请输入用户名',
      trigger: 'blur'
    }
  ],
  password: [
    {
      required: true,
      message: '请输入密码',
      trigger: 'blur'
    },
    {
      min: 6,
      message: '密码至少 6 位',
      trigger: 'blur'
    }
  ]
}

async function submitLogin() {
  if (!formRef.value) {
    return
  }

  const valid = await formRef.value.validate().catch(() => false)

  if (!valid) {
    return
  }

  loading.value = true

  try {
    await authStore.login({
      username: formModel.username,
      password: formModel.password
    })

    ElMessage.success('欢迎回来，工作台已准备好')

    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    await router.push(redirect)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-shell">
    <section class="auth-shell__intro panel">
      <span class="section-kicker">Knowledge Pulse</span>
      <h1>把知识沉淀成可持续增长的第二大脑。</h1>
      <p>
        知脉把笔记、标签、知识图谱、实时协作和分享权限放在同一个工作台里，让日常记录真正变成可检索、可延展、可协同的知识资产。
      </p>

      <div class="auth-shell__highlights">
        <article>
          <strong>富文本与 Markdown 混合创作</strong>
          <span>Vditor 全屏编辑，保留结构化表达和所见即所得体验。</span>
        </article>
        <article>
          <strong>实时协作与输入状态提示</strong>
          <span>多人共同编辑同一篇笔记时，能看到在线协作者和同步状态。</span>
        </article>
        <article>
          <strong>知识图谱与标签驱动发现</strong>
          <span>通过关联关系和标签分布，把碎片想法连成清晰脉络。</span>
        </article>
      </div>
    </section>

    <section class="auth-shell__form panel">
      <div class="auth-shell__form-header">
        <span class="section-kicker">Login</span>
        <h2>登录知脉</h2>
        <p>继续进入你的知识工作台。</p>
      </div>

      <el-form ref="formRef" :model="formModel" :rules="rules" label-position="top" class="auth-form">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="formModel.username" placeholder="请输入用户名" />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input v-model="formModel.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>

        <el-button type="primary" :loading="loading" class="auth-form__submit" @click="submitLogin">
          登录并进入工作台
        </el-button>
      </el-form>

      <div class="auth-shell__footer">
        <span>还没有账号？</span>
        <router-link to="/register">立即注册</router-link>
      </div>
    </section>
  </div>
</template>

<style scoped>
.auth-shell {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(420px, 0.8fr);
  gap: 24px;
  min-height: 100vh;
  padding: 24px;
}

.auth-shell__intro,
.auth-shell__form {
  padding: 34px;
}

.auth-shell__intro {
  display: grid;
  align-content: start;
  gap: 22px;
  background:
    radial-gradient(circle at top right, rgba(197, 157, 88, 0.26), transparent 30%),
    linear-gradient(160deg, rgba(255, 251, 246, 0.96), rgba(240, 231, 217, 0.88));
}

.auth-shell__intro h1 {
  margin: 0;
  font-family: var(--header-font);
  font-size: clamp(2.6rem, 4vw, 4.2rem);
  line-height: 1.04;
}

.auth-shell__intro p {
  margin: 0;
  max-width: 720px;
  color: var(--text-soft);
  font-size: 1.04rem;
  line-height: 1.85;
}

.auth-shell__highlights {
  display: grid;
  gap: 16px;
  margin-top: 8px;
}

.auth-shell__highlights article {
  display: grid;
  gap: 8px;
  padding: 18px 20px;
  border: 1px solid rgba(184, 92, 56, 0.14);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.54);
}

.auth-shell__highlights span {
  color: var(--text-soft);
  line-height: 1.7;
}

.auth-shell__form {
  display: grid;
  align-content: center;
}

.auth-shell__form-header h2 {
  margin: 10px 0 0;
  font-family: var(--header-font);
  font-size: 2.2rem;
}

.auth-shell__form-header p {
  margin: 12px 0 0;
  color: var(--text-soft);
}

.auth-form {
  margin-top: 26px;
}

.auth-form__submit {
  width: 100%;
  margin-top: 8px;
}

.auth-shell__footer {
  display: flex;
  gap: 10px;
  margin-top: 20px;
  color: var(--text-soft);
}

.auth-shell__footer a {
  color: var(--accent-strong);
  font-weight: 700;
}

@media (max-width: 1080px) {
  .auth-shell {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .auth-shell {
    padding: 16px;
  }

  .auth-shell__intro,
  .auth-shell__form {
    padding: 24px;
  }
}

@media (max-width: 420px) {
  .auth-shell {
    padding: 10px;
  }

  .auth-shell__intro,
  .auth-shell__form {
    padding: 18px;
  }

  .auth-shell__intro h1 {
    font-size: 2rem;
  }

  .auth-shell__intro p {
    font-size: 0.95rem;
  }

  .auth-shell__form-header h2 {
    font-size: 1.75rem;
  }

  .auth-shell__footer {
    flex-wrap: wrap;
  }
}
</style>
