<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const formModel = reactive({
  username: '',
  email: '',
  nickname: '',
  password: '',
  confirmPassword: ''
})

function confirmPasswordValidator(_rule: unknown, value: string, callback: (error?: Error) => void) {
  if (!value) {
    callback(new Error('请再次输入密码'))
    return
  }

  if (value !== formModel.password) {
    callback(new Error('两次输入的密码不一致'))
    return
  }

  callback()
}

const rules: FormRules = {
  username: [
    {
      required: true,
      message: '请输入用户名',
      trigger: 'blur'
    },
    {
      min: 3,
      max: 50,
      message: '用户名长度应为 3-50 个字符',
      trigger: 'blur'
    }
  ],
  email: [
    {
      required: true,
      message: '请输入邮箱',
      trigger: 'blur'
    },
    {
      type: 'email',
      message: '请输入正确的邮箱格式',
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
  ],
  confirmPassword: [
    {
      validator: confirmPasswordValidator,
      trigger: 'blur'
    }
  ]
}

async function submitRegister() {
  if (!formRef.value) {
    return
  }

  const valid = await formRef.value.validate().catch(() => false)

  if (!valid) {
    return
  }

  loading.value = true

  try {
    await authStore.register({
      username: formModel.username,
      email: formModel.email,
      nickname: formModel.nickname,
      password: formModel.password
    })

    ElMessage.success('注册成功，已自动登录')
    await router.push('/dashboard')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '注册失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="register-shell">
    <section class="register-shell__card panel">
      <div class="register-shell__header">
        <span class="section-kicker">Create Account</span>
        <h1>创建你的知脉空间</h1>
        <p>完成注册后，就可以开始建立文件夹、写笔记、生成知识图谱并邀请协作者。</p>
      </div>

      <el-form ref="formRef" :model="formModel" :rules="rules" label-position="top" class="register-form">
        <div class="register-form__grid">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="formModel.username" placeholder="3-50 位用户名" />
          </el-form-item>

          <el-form-item label="昵称" prop="nickname">
            <el-input v-model="formModel.nickname" placeholder="展示给协作者的名称" />
          </el-form-item>
        </div>

        <el-form-item label="邮箱" prop="email">
          <el-input v-model="formModel.email" placeholder="用于账号联系和通知" />
        </el-form-item>

        <div class="register-form__grid">
          <el-form-item label="密码" prop="password">
            <el-input v-model="formModel.password" type="password" show-password placeholder="至少 6 位" />
          </el-form-item>

          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input
              v-model="formModel.confirmPassword"
              type="password"
              show-password
              placeholder="再次确认密码"
            />
          </el-form-item>
        </div>

        <el-button type="primary" :loading="loading" class="register-form__submit" @click="submitRegister">
          注册并进入知脉
        </el-button>
      </el-form>

      <div class="register-shell__footer">
        <span>已经有账号？</span>
        <router-link to="/login">返回登录</router-link>
      </div>
    </section>
  </div>
</template>

<style scoped>
.register-shell {
  display: grid;
  place-items: center;
  min-height: 100vh;
  padding: 24px;
}

.register-shell__card {
  width: min(860px, 100%);
  padding: 36px;
  background:
    radial-gradient(circle at top left, rgba(197, 157, 88, 0.18), transparent 28%),
    linear-gradient(160deg, rgba(255, 252, 247, 0.96), rgba(242, 233, 220, 0.9));
}

.register-shell__header h1 {
  margin: 10px 0 0;
  font-family: var(--header-font);
  font-size: clamp(2.2rem, 3vw, 3.4rem);
}

.register-shell__header p {
  margin: 12px 0 0;
  color: var(--text-soft);
  line-height: 1.8;
}

.register-form {
  margin-top: 28px;
}

.register-form__grid {
  display: grid;
  gap: 18px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.register-form__submit {
  width: 100%;
  margin-top: 10px;
}

.register-shell__footer {
  display: flex;
  gap: 10px;
  margin-top: 22px;
  color: var(--text-soft);
}

.register-shell__footer a {
  color: var(--accent-strong);
  font-weight: 700;
}

@media (max-width: 720px) {
  .register-shell {
    padding: 16px;
  }

  .register-shell__card {
    padding: 24px;
  }

  .register-form__grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 420px) {
  .register-shell {
    padding: 10px;
  }

  .register-shell__card {
    padding: 18px;
  }

  .register-shell__header h1 {
    font-size: 1.9rem;
  }

  .register-shell__footer {
    flex-wrap: wrap;
  }
}
</style>
