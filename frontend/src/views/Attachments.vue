<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHero from '@/components/PageHero.vue'
import { deleteAttachment, getAttachments, uploadAttachment } from '@/api/attachment'
import type { AttachmentItem } from '@/types'
import {
  MAX_ATTACHMENT_BYTES,
  attachmentIcon,
  attachmentMarkdown,
  attachmentTypeLabel,
  formatFileSize,
  isSupportedAttachmentFile,
  resolveAttachmentUrl
} from '@/utils/attachment'
import { formatDateTime } from '@/utils/format'

type AttachmentFilter = 'ALL' | 'IMAGE' | 'PDF' | 'WORD'

const loading = ref(false)
const uploading = ref(false)
const attachments = ref<AttachmentItem[]>([])
const fileInputRef = ref<HTMLInputElement | null>(null)
const fileType = ref<AttachmentFilter>('ALL')
const unusedOnly = ref(false)
const keyword = ref('')

const filterOptions = [
  { label: '全部', value: 'ALL' },
  { label: '图片', value: 'IMAGE' },
  { label: 'PDF', value: 'PDF' },
  { label: 'Word', value: 'WORD' }
]

const totalSize = computed(() => attachments.value.reduce((sum, item) => sum + (item.fileSize || 0), 0))
const unusedCount = computed(() => attachments.value.filter((item) => !item.used).length)
const usedCount = computed(() => attachments.value.filter((item) => item.used).length)

const pageDescription = computed(() => {
  if (!attachments.value.length) {
    return '集中存放图片、PDF 和 Word，插入笔记后会自动追踪引用关系。'
  }

  return `当前筛选下有 ${attachments.value.length} 个附件，其中 ${unusedCount.value} 个未使用，可安全清理。`
})

let searchTimer: number | null = null

watch([fileType, unusedOnly], () => {
  void loadAttachments()
})

watch(keyword, () => {
  if (searchTimer !== null) {
    window.clearTimeout(searchTimer)
  }

  searchTimer = window.setTimeout(() => {
    void loadAttachments()
  }, 260)
})

onMounted(() => {
  void loadAttachments()
})

async function loadAttachments() {
  loading.value = true

  try {
    attachments.value = await getAttachments({
      fileType: fileType.value === 'ALL' ? undefined : fileType.value,
      unusedOnly: unusedOnly.value,
      keyword: keyword.value.trim() || undefined
    })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载附件失败')
  } finally {
    loading.value = false
  }
}

function triggerUpload() {
  fileInputRef.value?.click()
}

async function handleFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]

  if (!file) {
    return
  }

  if (!isSupportedAttachmentFile(file)) {
    ElMessage.error('仅支持图片、PDF、Word .doc 或 .docx 文件')
    input.value = ''
    return
  }

  if (file.size > MAX_ATTACHMENT_BYTES) {
    ElMessage.error('单个附件请控制在 25MB 以内')
    input.value = ''
    return
  }

  uploading.value = true

  try {
    await uploadAttachment(file)
    ElMessage.success('附件已上传')
    await loadAttachments()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '上传附件失败')
  } finally {
    uploading.value = false
    input.value = ''
  }
}

async function copyMarkdown(attachment: AttachmentItem) {
  try {
    await navigator.clipboard.writeText(attachmentMarkdown(attachment))
    ElMessage.success('引用 Markdown 已复制')
  } catch {
    ElMessage.warning('复制失败，请稍后重试')
  }
}

function openAttachment(attachment: AttachmentItem) {
  window.open(resolveAttachmentUrl(attachment), '_blank', 'noopener,noreferrer')
}

async function deleteUnusedAttachment(attachment: AttachmentItem) {
  if (attachment.used) {
    ElMessage.warning('这个附件仍被笔记引用，不能直接删除')
    return
  }

  try {
    await ElMessageBox.confirm(`确定删除「${attachment.originalName}」吗？此操作会同时删除文件。`, '删除未使用附件', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })

    await deleteAttachment(attachment.id)
    attachments.value = attachments.value.filter((item) => item.id !== attachment.id)
    ElMessage.success('附件已删除')
  } catch (error) {
    if (error instanceof Error) {
      ElMessage.error(error.message)
    }
  }
}
</script>

<template>
  <div class="attachment-center page-shell">
    <PageHero
      kicker="Attachment Center"
      title="附件中心"
      :description="pageDescription"
    >
      <template #actions>
        <input
          ref="fileInputRef"
          class="attachment-center__file-input"
          type="file"
          accept="image/png,image/jpeg,image/webp,image/gif,application/pdf,.doc,.docx,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
          @change="handleFileChange"
        />
        <el-button type="primary" :loading="uploading" @click="triggerUpload">上传附件</el-button>
        <el-button plain :loading="loading" @click="loadAttachments">刷新</el-button>
      </template>
    </PageHero>

    <section class="attachment-center__summary">
      <article>
        <span>当前附件</span>
        <strong>{{ attachments.length }}</strong>
      </article>
      <article>
        <span>已引用</span>
        <strong>{{ usedCount }}</strong>
      </article>
      <article class="attachment-center__summary-card--alert">
        <span>未使用</span>
        <strong>{{ unusedCount }}</strong>
      </article>
      <article>
        <span>占用空间</span>
        <strong>{{ formatFileSize(totalSize) }}</strong>
      </article>
    </section>

    <section class="attachment-center__filters panel">
      <el-segmented v-model="fileType" :options="filterOptions" />
      <el-input
        v-model="keyword"
        clearable
        placeholder="搜索附件名称"
      />
      <el-switch
        v-model="unusedOnly"
        active-text="只看未使用"
        inactive-text="全部附件"
      />
    </section>

    <section v-if="loading" class="attachment-center__grid">
      <div v-for="index in 8" :key="index" class="attachment-card attachment-card--skeleton" />
    </section>

    <section v-else-if="attachments.length" class="attachment-center__grid">
      <article
        v-for="attachment in attachments"
        :key="attachment.id"
        class="attachment-card"
        :class="`attachment-card--${attachment.fileType.toLowerCase()}`"
      >
        <div class="attachment-card__preview">
          <img
            v-if="attachment.fileType === 'IMAGE'"
            :src="resolveAttachmentUrl(attachment)"
            :alt="attachment.originalName"
          />
          <span v-else>{{ attachmentIcon(attachment.fileType) }}</span>
        </div>

        <div class="attachment-card__body">
          <div class="attachment-card__head">
            <span>{{ attachmentTypeLabel(attachment.fileType) }}</span>
            <strong>{{ attachment.used ? `${attachment.referenceCount} 处引用` : '未使用' }}</strong>
          </div>

          <h3>{{ attachment.originalName }}</h3>

          <p>
            {{ formatFileSize(attachment.fileSize) }} / 上传于 {{ formatDateTime(attachment.createTime) }}
          </p>
        </div>

        <div class="attachment-card__actions">
          <el-button plain size="small" @click="openAttachment(attachment)">打开</el-button>
          <el-button plain size="small" @click="copyMarkdown(attachment)">复制引用</el-button>
          <el-button
            plain
            size="small"
            type="danger"
            :disabled="attachment.used"
            @click="deleteUnusedAttachment(attachment)"
          >
            删除
          </el-button>
        </div>
      </article>
    </section>

    <section v-else class="attachment-center__empty panel">
      <strong>{{ unusedOnly || keyword ? '没有匹配的附件' : '还没有上传附件' }}</strong>
      <span>可以上传图片、PDF 或 Word，然后在笔记编辑页里一键插入引用。</span>
      <el-button type="primary" :loading="uploading" @click="triggerUpload">上传第一个附件</el-button>
    </section>
  </div>
</template>

<style scoped>
.attachment-center {
  display: grid;
  gap: 16px;
}

.attachment-center__file-input {
  display: none;
}

.attachment-center__summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.attachment-center__summary article {
  display: grid;
  gap: 6px;
  padding: 14px;
  border: 1px solid rgba(93, 113, 92, 0.12);
  border-radius: 18px;
  background:
    radial-gradient(circle at top right, rgba(54, 92, 75, 0.1), transparent 34%),
    rgba(255, 255, 255, 0.68);
}

.attachment-center__summary span {
  color: var(--text-soft);
}

.attachment-center__summary strong {
  color: var(--text-main);
  font-size: 1.36rem;
}

.attachment-center__summary-card--alert strong {
  color: #8d4529;
}

.attachment-center__filters {
  display: grid;
  grid-template-columns: auto minmax(220px, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 12px;
}

.attachment-center__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 12px;
}

.attachment-card {
  display: grid;
  gap: 10px;
  padding: 12px;
  border: 1px solid rgba(93, 113, 92, 0.12);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 12px 28px rgba(54, 92, 75, 0.06);
}

.attachment-card__preview {
  display: grid;
  place-items: center;
  height: 118px;
  overflow: hidden;
  border-radius: 16px;
  background:
    radial-gradient(circle at top left, rgba(197, 157, 88, 0.2), transparent 42%),
    linear-gradient(135deg, rgba(54, 92, 75, 0.12), rgba(255, 249, 239, 0.74));
}

.attachment-card__preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.attachment-card__preview span {
  color: #365c4b;
  font-size: 2rem;
  font-weight: 800;
}

.attachment-card__body {
  display: grid;
  gap: 8px;
}

.attachment-card__head,
.attachment-card__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.attachment-card__head span,
.attachment-card__head strong {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(54, 92, 75, 0.08);
  color: #365c4b;
  font-size: 0.78rem;
}

.attachment-card__head strong {
  background: rgba(184, 92, 56, 0.1);
  color: #8d4529;
}

.attachment-card h3 {
  margin: 0;
  overflow: hidden;
  color: var(--text-main);
  font-size: 1rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-card p {
  margin: 0;
  color: var(--text-soft);
  font-size: 0.86rem;
}

.attachment-card__actions {
  justify-content: flex-start;
}

.attachment-card--skeleton {
  min-height: 230px;
  background:
    linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.86), transparent),
    rgba(255, 255, 255, 0.54);
  background-size: 220% 100%;
  animation: attachment-skeleton 1.3s ease-in-out infinite;
}

.attachment-center__empty {
  display: grid;
  gap: 10px;
  justify-items: center;
  padding: 36px 16px;
  color: var(--text-soft);
  text-align: center;
}

.attachment-center__empty strong {
  color: var(--text-main);
  font-size: 1.2rem;
}

@keyframes attachment-skeleton {
  from {
    background-position: 120% 0;
  }

  to {
    background-position: -120% 0;
  }
}

@media (max-width: 980px) {
  .attachment-center__summary,
  .attachment-center__filters {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .attachment-center {
    gap: 16px;
  }

  .attachment-center__summary {
    display: flex;
    gap: 10px;
    overflow-x: auto;
    scrollbar-width: none;
  }

  .attachment-center__summary::-webkit-scrollbar {
    display: none;
  }

  .attachment-center__summary article,
  .attachment-center__filters,
  .attachment-card {
    padding: 12px;
    border-radius: 18px;
  }

  .attachment-center__summary article {
    flex: 0 0 132px;
  }

  .attachment-center__summary strong {
    font-size: 1.2rem;
  }

  .attachment-center__grid {
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .attachment-card__preview {
    height: 108px;
    border-radius: 18px;
  }

  .attachment-card__actions {
    align-items: stretch;
  }

  .attachment-card__actions :deep(.el-button) {
    flex: 1 1 112px;
    min-width: 0;
  }
}

@media (max-width: 420px) {
  .attachment-center__summary article,
  .attachment-center__filters,
  .attachment-card {
    padding: 14px;
  }

  .attachment-card__actions :deep(.el-button) {
    flex: 1 1 112px;
  }
}
</style>
