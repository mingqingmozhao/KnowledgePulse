<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import CollapsiblePanel from '@/components/CollapsiblePanel.vue'
import { useWorkspaceStore } from '@/stores/workspace'
import type { NoteTemplate, NoteTemplateRequest } from '@/types'
import { normalizeTags, relativeTime } from '@/utils/format'
import { buildDraftNoteRoute } from '@/utils/noteWorkspace'

const router = useRouter()
const workspaceStore = useWorkspaceStore()

const searchKeyword = ref('')
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const activeTemplateId = ref<number | null>(null)

const templateForm = reactive({
  name: '',
  category: '通用',
  tagsText: '',
  description: '',
  content: ''
})

const filteredTemplates = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()

  if (!keyword) {
    return workspaceStore.templates
  }

  return workspaceStore.templates.filter((template) => {
    const searchable = [
      template.name,
      template.description || '',
      template.category || '',
      template.tags.join(' '),
      template.content
    ]
      .join(' ')
      .toLowerCase()

    return searchable.includes(keyword)
  })
})

const groupedTemplates = computed(() => {
  const groups = new Map<string, NoteTemplate[]>()

  filteredTemplates.value.forEach((template) => {
    const category = template.category?.trim() || '通用'
    groups.set(category, [...(groups.get(category) ?? []), template])
  })

  return [...groups.entries()]
    .sort(([leftCategory], [rightCategory]) => leftCategory.localeCompare(rightCategory, 'zh-CN'))
    .map(([category, templates]) => ({
      category,
      templates: [...templates].sort((left, right) => {
        if (left.system !== right.system) {
          return left.system ? -1 : 1
        }

        return new Date(right.updateTime ?? right.createTime ?? 0).getTime() -
          new Date(left.updateTime ?? left.createTime ?? 0).getTime()
      })
    }))
})

const customTemplateCount = computed(() => workspaceStore.templates.filter((template) => !template.system).length)
const systemTemplateCount = computed(() => workspaceStore.templates.filter((template) => template.system).length)

onMounted(() => {
  void loadTemplates()
})

async function loadTemplates() {
  try {
    await workspaceStore.loadTemplates()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载模板失败')
  }
}

function resetForm(template?: NoteTemplate) {
  activeTemplateId.value = template?.id ?? null
  templateForm.name = template?.name ?? ''
  templateForm.category = template?.category || '通用'
  templateForm.tagsText = template?.tags.join(', ') ?? ''
  templateForm.description = template?.description ?? ''
  templateForm.content =
    template?.content ??
    '# 新模板\n\n## 使用场景\n- \n\n## 内容结构\n- \n\n## 下一步\n- \n'
}

function openCreateDialog() {
  dialogMode.value = 'create'
  resetForm()
  dialogVisible.value = true
}

function openEditDialog(template: NoteTemplate) {
  if (template.system) {
    ElMessage.info('系统模板可以直接起草，暂不支持编辑')
    return
  }

  dialogMode.value = 'edit'
  resetForm(template)
  dialogVisible.value = true
}

async function submitTemplate() {
  if (!templateForm.name.trim()) {
    ElMessage.warning('请先填写模板名称')
    return
  }

  const payload: NoteTemplateRequest = {
    name: templateForm.name.trim(),
    category: templateForm.category.trim() || '通用',
    description: templateForm.description.trim(),
    content: templateForm.content,
    htmlContent: '',
    tags: normalizeTags(templateForm.tagsText)
  }

  submitting.value = true

  try {
    if (dialogMode.value === 'edit' && activeTemplateId.value) {
      await workspaceStore.updateTemplate(activeTemplateId.value, payload)
      ElMessage.success('模板已更新')
    } else {
      await workspaceStore.createTemplate(payload)
      ElMessage.success('模板已创建')
    }

    dialogVisible.value = false
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存模板失败')
  } finally {
    submitting.value = false
  }
}

async function deleteTemplateAction(template: NoteTemplate) {
  if (template.system) {
    return
  }

  try {
    await ElMessageBox.confirm(`确定删除模板「${template.name}」吗？已创建的笔记不会受影响。`, '删除模板', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })

    await workspaceStore.deleteTemplate(template.id)
    ElMessage.success('模板已删除')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error instanceof Error ? error.message : '删除模板失败')
    }
  }
}

async function startFromTemplate(template: NoteTemplate) {
  await router.push(buildDraftNoteRoute(undefined, template.id))
}

function isGroupInitiallyOpen(index: number) {
  return Boolean(searchKeyword.value.trim()) || index === 0
}

function getGroupMeta(templates: NoteTemplate[]) {
  const systemCount = templates.filter((template) => template.system).length
  const customCount = templates.length - systemCount
  const segments = [`${templates.length} 个模板`]

  if (systemCount) {
    segments.push(`${systemCount} 个系统`)
  }

  if (customCount) {
    segments.push(`${customCount} 个自定义`)
  }

  return segments.join(' / ')
}

function getTemplatePreview(template: NoteTemplate) {
  const normalized = template.content.replace(/[#>*`\-\[\]\n\r]+/g, ' ').replace(/\s+/g, ' ').trim()
  return normalized || template.description || '暂无模板内容，点击起草后可以继续补充。'
}
</script>

<template>
  <div class="templates-page page-shell">
    <section class="templates-hero panel">
      <div class="templates-hero__copy">
        <span class="section-kicker">Template Center</span>
        <h1>模板中心</h1>
        <p>把高频笔记结构沉淀成模板，起草项目复盘、会议纪要、读书笔记时不用每次从空白开始。</p>
      </div>

      <div class="templates-hero__actions">
        <el-input
          v-model="searchKeyword"
          class="templates-hero__search"
          clearable
          placeholder="搜索模板名称、分类或标签"
        />
        <el-button type="primary" @click="openCreateDialog">新建模板</el-button>
      </div>
    </section>

    <section class="templates-stats">
      <article class="templates-stat">
        <span>系统模板</span>
        <strong>{{ systemTemplateCount }}</strong>
        <small>开箱即用</small>
      </article>
      <article class="templates-stat">
        <span>我的模板</span>
        <strong>{{ customTemplateCount }}</strong>
        <small>可编辑复用</small>
      </article>
      <article class="templates-stat templates-stat--accent">
        <span>可见模板</span>
        <strong>{{ filteredTemplates.length }}</strong>
        <small>当前筛选结果</small>
      </article>
    </section>

    <el-skeleton :loading="workspaceStore.templateLoading" animated :rows="8">
      <template #default>
        <div v-if="groupedTemplates.length" class="templates-groups">
          <CollapsiblePanel
            v-for="(group, index) in groupedTemplates"
            :key="group.category"
            class="templates-group"
            tag="section"
            kicker="Category"
            :title="group.category"
            :meta="getGroupMeta(group.templates)"
            :initially-open="isGroupInitiallyOpen(index)"
            body-class="templates-group__body"
          >
            <div class="templates-list">
              <article v-for="template in group.templates" :key="template.id" class="template-card">
                <div class="template-card__main">
                  <div class="template-card__identity">
                    <span class="template-card__badge" :class="{ 'template-card__badge--system': template.system }">
                      {{ template.system ? '系统' : '我的' }}
                    </span>
                    <h3>{{ template.name }}</h3>
                  </div>

                  <p>{{ template.description || getTemplatePreview(template) }}</p>

                  <div class="template-card__tags">
                    <span v-for="tag in template.tags.slice(0, 4)" :key="tag">#{{ tag }}</span>
                    <span v-if="!template.tags.length">#未标记</span>
                  </div>
                </div>

                <div class="template-card__preview">
                  {{ getTemplatePreview(template).slice(0, 96) }}
                </div>

                <div class="template-card__side">
                  <small>{{ relativeTime(template.updateTime || template.createTime) }}</small>
                  <div class="template-card__actions">
                    <el-button type="primary" plain size="small" @click="startFromTemplate(template)">起草</el-button>
                    <el-button v-if="!template.system" plain size="small" @click="openEditDialog(template)">编辑</el-button>
                    <el-button v-if="!template.system" text size="small" type="danger" @click="deleteTemplateAction(template)">
                      删除
                    </el-button>
                  </div>
                </div>
              </article>
            </div>
          </CollapsiblePanel>
        </div>

        <section v-else class="empty-state templates-empty">
          <strong>还没有找到模板</strong>
          <p>可以换个关键词，或者先创建一个你常用的笔记结构。</p>
          <el-button type="primary" @click="openCreateDialog">创建第一个模板</el-button>
        </section>
      </template>
    </el-skeleton>

    <el-dialog
      v-model="dialogVisible"
      class="template-dialog"
      :title="dialogMode === 'edit' ? '编辑模板' : '新建模板'"
      width="760px"
      destroy-on-close
    >
      <el-form label-position="top" class="template-form">
        <div class="template-form__grid">
          <el-form-item label="模板名称">
            <el-input v-model="templateForm.name" placeholder="例如：客户回访纪要" />
          </el-form-item>
          <el-form-item label="分类">
            <el-input v-model="templateForm.category" placeholder="例如：协作 / 学习 / 工程" />
          </el-form-item>
        </div>

        <el-form-item label="标签">
          <el-input v-model="templateForm.tagsText" placeholder="多个标签用逗号分隔，例如：会议，待办，复盘" />
        </el-form-item>

        <el-form-item label="描述">
          <el-input
            v-model="templateForm.description"
            type="textarea"
            :rows="2"
            placeholder="简单说明这个模板适合什么场景"
          />
        </el-form-item>

        <el-form-item label="模板正文">
          <el-input
            v-model="templateForm.content"
            type="textarea"
            :rows="14"
            resize="vertical"
            placeholder="可以直接写 Markdown 结构"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitTemplate">保存模板</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.templates-page {
  display: grid;
  gap: 20px;
}

.templates-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 28px;
  overflow: hidden;
  background:
    radial-gradient(circle at 10% 20%, rgba(255, 193, 79, 0.24), transparent 28%),
    linear-gradient(135deg, #fffaf0 0%, #f4fbf6 54%, #eef7ff 100%);
}

.templates-hero__copy {
  max-width: 620px;
}

.templates-hero h1 {
  margin: 8px 0 10px;
  font-size: clamp(30px, 4vw, 48px);
  color: #243026;
}

.templates-hero p {
  margin: 0;
  color: #667060;
  line-height: 1.8;
}

.templates-hero__actions {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: min(460px, 100%);
}

.templates-hero__search {
  flex: 1;
}

.templates-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.templates-stat {
  padding: 18px 20px;
  border: 1px solid rgba(82, 102, 84, 0.12);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.84);
  box-shadow: 0 18px 48px rgba(45, 59, 49, 0.08);
}

.templates-stat span,
.templates-stat small {
  display: block;
  color: #6f7b6c;
}

.templates-stat strong {
  display: block;
  margin: 8px 0 4px;
  font-size: 32px;
  color: #273329;
}

.templates-stat--accent {
  background: linear-gradient(135deg, #173f35, #386f4b);
}

.templates-stat--accent span,
.templates-stat--accent small,
.templates-stat--accent strong {
  color: #f8fff7;
}

.templates-groups {
  display: grid;
  gap: 14px;
}

.templates-group {
  padding: 18px;
  border-color: rgba(93, 113, 92, 0.12);
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.94), rgba(247, 250, 243, 0.88)),
    #ffffff;
}

:deep(.templates-group__body) {
  gap: 10px;
}

.templates-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.template-card {
  display: grid;
  grid-template-rows: auto auto auto;
  align-content: space-between;
  gap: 10px;
  min-height: 190px;
  padding: 14px;
  border: 1px solid rgba(93, 113, 92, 0.14);
  border-radius: 20px;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.98), rgba(250, 247, 239, 0.72)),
    #ffffff;
  box-shadow: 0 12px 34px rgba(45, 59, 49, 0.06);
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease,
    border-color 0.2s ease;
}

.template-card:hover {
  border-color: rgba(65, 120, 82, 0.34);
  box-shadow: 0 16px 42px rgba(45, 59, 49, 0.1);
  transform: translateY(-1px);
}

.template-card__main {
  min-width: 0;
  display: grid;
  gap: 7px;
}

.template-card__identity {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
}

.template-card__identity h3 {
  overflow: hidden;
  margin: 0;
  color: #1f2e26;
  font-size: 17px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.template-card__badge {
  flex: 0 0 auto;
  padding: 4px 8px;
  border-radius: 999px;
  color: #31533a;
  background: rgba(101, 154, 90, 0.14);
  font-size: 11px;
  font-weight: 700;
}

.template-card__badge--system {
  color: #845b1d;
  background: rgba(255, 184, 77, 0.22);
}

.template-card p {
  overflow: hidden;
  margin: 0;
  color: #687268;
  font-size: 13px;
  line-height: 1.55;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.template-card__preview {
  overflow: hidden;
  min-height: 48px;
  padding: 9px 11px;
  border-radius: 15px;
  color: #4f5d52;
  background: rgba(244, 247, 239, 0.78);
  font-size: 12px;
  line-height: 1.55;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.template-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 22px;
}

.template-card__tags span {
  padding: 3px 8px;
  border-radius: 999px;
  color: #577060;
  background: rgba(74, 119, 83, 0.1);
  font-size: 11px;
}

.template-card__side {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.template-card__side small {
  color: #8a9284;
  font-size: 12px;
}

.template-card__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  flex-wrap: wrap;
}

.templates-empty {
  padding: 60px 24px;
}

.template-form {
  display: grid;
  gap: 4px;
}

.template-form__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

@media (max-width: 900px) {
  .templates-hero,
  .templates-hero__actions {
    flex-direction: column;
    align-items: stretch;
  }

  .templates-stats {
    grid-template-columns: 1fr;
  }

  .templates-list {
    grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  }
}

@media (max-width: 640px) {
  .templates-hero {
    padding: 22px;
  }

  .templates-hero p {
    display: none;
  }

  .templates-stats {
    display: flex;
    gap: 10px;
    overflow-x: auto;
    padding-bottom: 2px;
    scrollbar-width: none;
  }

  .templates-stats::-webkit-scrollbar {
    display: none;
  }

  .templates-stat {
    flex: 0 0 138px;
    padding: 12px;
  }

  .templates-stat strong {
    margin: 4px 0 2px;
    font-size: 24px;
  }

  .template-form__grid {
    grid-template-columns: 1fr;
  }

  .templates-list {
    grid-template-columns: 1fr;
  }

  .template-card {
    min-height: auto;
  }

  .template-card__preview {
    display: none;
  }

  .template-card__side {
    align-items: center;
    flex-direction: row;
  }

  .template-card__actions {
    align-items: center;
    flex-direction: row;
  }
}

@media (max-width: 420px) {
  .templates-page {
    gap: 16px;
  }

  .templates-hero,
  .templates-group,
  .template-card {
    padding: 16px;
  }

  .templates-hero h1 {
    font-size: 2rem;
  }

  .templates-stat {
    padding: 16px;
  }

  .template-card__actions :deep(.el-button) {
    flex: 1 1 72px;
    min-width: 0;
  }
}
</style>
