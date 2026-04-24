<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import PageHero from '@/components/PageHero.vue'
import CollapsiblePanel from '@/components/CollapsiblePanel.vue'
import { uploadAvatar } from '@/api/user'
import { useAuthStore } from '@/stores/auth'
import { DEFAULT_AVATAR_PRESETS, isPresetAvatar, resolveAvatarSrc } from '@/utils/avatar'
import { formatDateOnly, initials } from '@/utils/format'

const authStore = useAuthStore()
const saving = ref(false)
const avatarUploading = ref(false)
const avatarInputRef = ref<HTMLInputElement | null>(null)

const profileForm = reactive({
  nickname: '',
  email: '',
  avatar: ''
})

const avatarPresets = DEFAULT_AVATAR_PRESETS
const supportedAvatarTypes = new Set(['image/png', 'image/jpeg', 'image/webp', 'image/gif'])
const maxAvatarBytes = 2 * 1024 * 1024

watch(
  () => authStore.user,
  (user) => {
    profileForm.nickname = user?.nickname || ''
    profileForm.email = user?.email || ''
    profileForm.avatar = user?.avatar || ''
  },
  {
    immediate: true
  }
)

onMounted(() => {
  if (!authStore.user) {
    void authStore.refreshProfile()
  }
})

const previewName = computed(() => profileForm.nickname.trim() || authStore.user?.username || '知识创作者')
const avatarPreviewSrc = computed(() => resolveAvatarSrc(profileForm.avatar))
const hasAvatarSelection = computed(() => Boolean(profileForm.avatar))

const avatarStatusText = computed(() => {
  if (avatarUploading.value) {
    return '正在上传新头像...'
  }

  if (!profileForm.avatar) {
    return '当前使用文字头像，保存资料后会显示昵称首字。'
  }

  if (isPresetAvatar(profileForm.avatar)) {
    return '已选择默认头像，保存资料后会同步到协作者和个人资料页。'
  }

  return '已上传自定义头像，保存资料后会同步到协作者和个人资料页。'
})

const summaryCards = computed(() => [
  {
    label: '账号角色',
    value: authStore.user?.role || '普通用户'
  },
  {
    label: '用户名',
    value: authStore.user?.username || '未获取到'
  },
  {
    label: '创建时间',
    value: formatDateOnly(authStore.user?.createTime)
  }
])

function triggerAvatarUpload() {
  avatarInputRef.value?.click()
}

function choosePresetAvatar(avatarValue: string) {
  profileForm.avatar = avatarValue
  ElMessage.success('已选择默认头像，保存资料后生效')
}

function clearAvatarSelection() {
  profileForm.avatar = ''
  ElMessage.success('已切换为文字头像，保存资料后生效')
}

async function handleAvatarFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]

  if (!file) {
    return
  }

  if (!supportedAvatarTypes.has(file.type.toLowerCase())) {
    ElMessage.error('仅支持 PNG、JPG、WEBP 或 GIF 图片')
    input.value = ''
    return
  }

  if (file.size > maxAvatarBytes) {
    ElMessage.error('头像图片请控制在 2MB 以内')
    input.value = ''
    return
  }

  avatarUploading.value = true

  try {
    profileForm.avatar = await uploadAvatar(file)
    ElMessage.success('头像已上传，保存资料后生效')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '头像上传失败，请稍后重试')
  } finally {
    avatarUploading.value = false
    input.value = ''
  }
}

async function saveProfile() {
  saving.value = true

  try {
    await authStore.saveProfile({
      nickname: profileForm.nickname,
      email: profileForm.email,
      avatar: profileForm.avatar
    })

    ElMessage.success('个人资料已保存')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="profile-view page-shell">
    <PageHero
      kicker="Profile"
      title="个人中心"
      description="维护头像、昵称、邮箱等资料，让协作时的身份展示更完整、更清晰。"
    >
      <template #actions>
        <el-button type="primary" :loading="saving" @click="saveProfile">保存资料</el-button>
      </template>
    </PageHero>

    <div class="profile-view__layout">
      <CollapsiblePanel
        class="profile-editor"
        kicker="Profile Form"
        title="资料编辑"
        meta="头像、昵称和邮箱会同步到协作身份展示"
        body-class="profile-editor__body"
        :initially-open="true"
      >
        <div class="profile-editor__header">
          <el-avatar :size="92" class="profile-editor__avatar" :src="avatarPreviewSrc">
            {{ initials(previewName) }}
          </el-avatar>

          <div class="profile-editor__identity">
            <span class="section-kicker">Identity</span>
            <h3 class="section-title">{{ previewName }}</h3>
            <p class="soft-text">{{ profileForm.email || authStore.user?.email }}</p>
            <p class="soft-text">{{ avatarStatusText }}</p>

            <div class="profile-editor__header-actions">
              <el-button plain :loading="avatarUploading" @click="triggerAvatarUpload">上传头像</el-button>
              <el-button text :disabled="!hasAvatarSelection" @click="clearAvatarSelection">使用文字头像</el-button>
            </div>
          </div>
        </div>

        <input
          ref="avatarInputRef"
          type="file"
          accept="image/png,image/jpeg,image/webp,image/gif"
          class="profile-editor__input-hidden"
          @change="handleAvatarFileChange"
        />

        <section class="profile-editor__avatar-section">
          <div class="profile-editor__section-head">
            <div>
              <span class="section-kicker">Avatar</span>
              <h3 class="section-title">选择默认头像</h3>
            </div>

            <small>支持本地上传，建议 2MB 以内</small>
          </div>

          <div class="profile-editor__presets">
            <button
              v-for="preset in avatarPresets"
              :key="preset.id"
              type="button"
              class="profile-editor__preset"
              :class="{ 'is-active': profileForm.avatar === preset.value }"
              @click="choosePresetAvatar(preset.value)"
            >
              <el-avatar :size="58" :src="preset.preview">{{ initials(preset.label) }}</el-avatar>
              <strong>{{ preset.label }}</strong>
              <span>{{ profileForm.avatar === preset.value ? '当前已选择' : '设为默认头像' }}</span>
            </button>
          </div>
        </section>

        <el-form label-position="top" class="profile-editor__form">
          <el-form-item label="昵称">
            <el-input v-model="profileForm.nickname" placeholder="显示给协作者的名称" />
          </el-form-item>

          <el-form-item label="邮箱">
            <el-input v-model="profileForm.email" placeholder="用于账号通知与联系" />
          </el-form-item>
        </el-form>
      </CollapsiblePanel>

      <aside class="profile-view__aside">
        <CollapsiblePanel
          class="profile-summary"
          kicker="Overview"
          title="账号概览"
          :initially-open="true"
        >
          <div class="profile-summary__cards">
            <article v-for="card in summaryCards" :key="card.label">
              <span>{{ card.label }}</span>
              <strong>{{ card.value }}</strong>
            </article>
          </div>
        </CollapsiblePanel>

        <CollapsiblePanel
          class="profile-summary"
          kicker="Avatar Tips"
          title="头像说明"
          :initially-open="false"
        >
          <ul class="profile-summary__tips">
            <li>默认头像会直接在个人中心、协作者面板和实时协作条中生效。</li>
            <li>上传头像支持 PNG、JPG、WEBP、GIF，上传成功后仍需点击“保存资料”。</li>
            <li>如果不想使用图片头像，可以切换回文字头像，系统会显示昵称首字。</li>
          </ul>
        </CollapsiblePanel>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.profile-view__layout {
  display: grid;
  gap: 24px;
  grid-template-columns: minmax(0, 1fr) 320px;
}

.profile-editor,
.profile-summary {
  padding: 24px;
}

.profile-editor {
  padding: 24px;
}

.profile-editor__body {
  display: grid;
  gap: 24px;
}

.profile-editor__header {
  display: flex;
  align-items: center;
  gap: 20px;
}

.profile-editor__avatar {
  flex-shrink: 0;
  color: var(--paper-strong);
  background: linear-gradient(135deg, var(--moss), var(--accent));
}

.profile-editor__identity {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.profile-editor__identity h3,
.profile-editor__identity p {
  margin: 0;
}

.profile-editor__header-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 6px;
}

.profile-editor__input-hidden {
  display: none;
}

.profile-editor__avatar-section {
  display: grid;
  gap: 16px;
}

.profile-editor__section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.profile-editor__section-head small {
  color: var(--text-soft);
}

.profile-editor__presets {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
}

.profile-editor__preset {
  display: grid;
  justify-items: start;
  gap: 10px;
  padding: 16px;
  border: 1px solid rgba(184, 92, 56, 0.14);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.64);
  cursor: pointer;
  text-align: left;
  transition:
    transform 0.2s ease,
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.profile-editor__preset:hover {
  transform: translateY(-2px);
  border-color: rgba(184, 92, 56, 0.3);
  box-shadow: 0 16px 30px rgba(46, 58, 74, 0.08);
}

.profile-editor__preset.is-active {
  border-color: rgba(184, 92, 56, 0.5);
  background: rgba(255, 246, 237, 0.9);
}

.profile-editor__preset span {
  color: var(--text-soft);
  font-size: 0.84rem;
}

.profile-editor__form {
  margin-top: 4px;
}

.profile-view__aside {
  display: grid;
  align-content: start;
  gap: 24px;
}

.profile-summary__cards {
  display: grid;
  gap: 14px;
  margin-top: 18px;
}

.profile-summary__cards article {
  display: grid;
  gap: 8px;
  padding: 16px 18px;
  border: 1px solid rgba(184, 92, 56, 0.14);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.54);
}

.profile-summary__cards span,
.profile-summary__tips {
  color: var(--text-soft);
}

.profile-summary__tips {
  margin: 18px 0 0;
  padding-left: 18px;
  line-height: 1.9;
}

@media (max-width: 1180px) {
  .profile-view__layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .profile-editor__header,
  .profile-editor__section-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .profile-editor__presets {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 520px) {
  .profile-view__layout,
  .profile-view__aside,
  .profile-editor__body {
    gap: 16px;
  }

  .profile-editor,
  .profile-summary {
    padding: 16px;
  }

  .profile-editor__header-actions {
    width: 100%;
    align-items: stretch;
  }

  .profile-editor__header-actions :deep(.el-button) {
    flex: 1 1 128px;
    min-width: 0;
  }

  .profile-editor__presets {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 420px) {
  .profile-editor,
  .profile-summary {
    padding: 14px;
  }
}
</style>
