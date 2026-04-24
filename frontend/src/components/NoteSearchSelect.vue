<script setup lang="ts">
import { computed, ref } from 'vue'
import type { Note } from '@/types'
import { relativeTime } from '@/utils/format'

type NoteOption = {
  value: number
  label: string
  meta: string
  searchText: string
  disabled: boolean
  groupLabel: string
  updateTimestamp: number
}

type NoteGroup = {
  key: string
  label: string
  displayLabel: string
  options: NoteOption[]
  latestTimestamp: number
}

const MAX_SEARCH_RESULTS = 120
const UNGROUPED_LABEL = '未分类'

const props = withDefaults(
  defineProps<{
    modelValue: number | null
    notes: Note[]
    placeholder?: string
    emptyText?: string
    excludeNoteId?: number | null
    disabledIds?: number[]
    clearable?: boolean
  }>(),
  {
    placeholder: '搜索标题、标签、文件夹或 ID',
    emptyText: '没有匹配的笔记',
    excludeNoteId: null,
    disabledIds: () => [],
    clearable: true
  }
)

const emit = defineEmits<{
  (event: 'update:modelValue', value: number | null): void
}>()

const keyword = ref('')

const disabledNoteIds = computed(() => new Set(props.disabledIds))
const normalizedKeyword = computed(() => keyword.value.trim().toLowerCase())

function normalizeSearchValue(value: string) {
  return value.trim().toLowerCase()
}

function getGroupLabel(note: Note) {
  const folderName = note.folderName?.trim()
  return folderName || UNGROUPED_LABEL
}

function getUpdateTimestamp(note: Note) {
  const rawValue = note.updateTime ?? note.createTime ?? null

  if (!rawValue) {
    return 0
  }

  const timestamp = new Date(rawValue).getTime()
  return Number.isFinite(timestamp) ? timestamp : 0
}

function buildSearchText(note: Note, groupLabel: string) {
  return normalizeSearchValue(
    [
      note.title,
      groupLabel,
      note.folderName,
      note.tags.join(' '),
      note.ownerNickname,
      note.ownerUsername,
      note.id
    ]
      .filter(Boolean)
      .join(' ')
  )
}

function buildMeta(note: Note) {
  const segments = [`#${note.id}`]

  if (note.tags.length) {
    segments.push(note.tags.slice(0, 3).join(' / '))
  }

  if (note.updateTime) {
    segments.push(`更新 ${relativeTime(note.updateTime)}`)
  }

  return segments.join(' · ')
}

const noteOptions = computed<NoteOption[]>(() =>
  props.notes
    .filter((note) => note.id !== props.excludeNoteId)
    .map((note) => {
      const groupLabel = getGroupLabel(note)

      return {
        value: note.id,
        label: note.title,
        meta: buildMeta(note),
        searchText: buildSearchText(note, groupLabel),
        disabled: disabledNoteIds.value.has(note.id),
        groupLabel,
        updateTimestamp: getUpdateTimestamp(note)
      }
    })
)

const matchedOptions = computed(() => {
  if (!normalizedKeyword.value) {
    return noteOptions.value
  }

  return noteOptions.value
    .filter((option) => option.searchText.includes(normalizedKeyword.value))
    .slice(0, MAX_SEARCH_RESULTS)
})

const optionGroups = computed<NoteGroup[]>(() => {
  const groupMap = new Map<string, NoteGroup>()

  matchedOptions.value.forEach((option) => {
    const existingGroup = groupMap.get(option.groupLabel)

    if (existingGroup) {
      existingGroup.options.push(option)
      existingGroup.latestTimestamp = Math.max(existingGroup.latestTimestamp, option.updateTimestamp)
      return
    }

    groupMap.set(option.groupLabel, {
      key: option.groupLabel,
      label: option.groupLabel,
      displayLabel: option.groupLabel,
      options: [option],
      latestTimestamp: option.updateTimestamp
    })
  })

  return [...groupMap.values()]
    .map((group) => ({
      ...group,
      displayLabel: `${group.label} · ${group.options.length} 篇`,
      options: [...group.options].sort(
        (left, right) =>
          right.updateTimestamp - left.updateTimestamp || left.label.localeCompare(right.label, 'zh-CN')
      )
    }))
    .sort((left, right) => {
      if (left.label === UNGROUPED_LABEL && right.label !== UNGROUPED_LABEL) {
        return 1
      }

      if (left.label !== UNGROUPED_LABEL && right.label === UNGROUPED_LABEL) {
        return -1
      }

      return right.latestTimestamp - left.latestTimestamp || left.label.localeCompare(right.label, 'zh-CN')
    })
})

const helperText = computed(() => {
  if (normalizedKeyword.value) {
    return `已找到 ${matchedOptions.value.length} 篇笔记，分布在 ${optionGroups.value.length} 个文件夹分组中`
  }

  return `按文件夹分组浏览，共 ${optionGroups.value.length} 个分组；可输入标题、标签、文件夹或 ID 快速定位`
})

function handleFilter(query: string) {
  keyword.value = query
}

function handleVisibleChange(visible: boolean) {
  if (!visible) {
    keyword.value = ''
  }
}

function updateValue(value: number | string | null | undefined) {
  if (value === null || value === undefined || value === '') {
    emit('update:modelValue', null)
    return
  }

  const parsedValue = typeof value === 'number' ? value : Number(value)
  emit('update:modelValue', Number.isFinite(parsedValue) ? parsedValue : null)
}
</script>

<template>
  <div class="note-search-select">
    <el-select
      :model-value="modelValue"
      filterable
      default-first-option
      :clearable="clearable"
      :reserve-keyword="false"
      :placeholder="placeholder"
      :no-data-text="emptyText"
      class="note-search-select__control"
      @update:model-value="updateValue"
      @visible-change="handleVisibleChange"
      :filter-method="handleFilter"
    >
      <el-option-group
        v-for="group in optionGroups"
        :key="group.key"
        :label="group.displayLabel"
      >
        <el-option
          v-for="option in group.options"
          :key="option.value"
          :label="option.label"
          :value="option.value"
          :disabled="option.disabled"
        >
          <div class="note-search-select__option">
            <strong>{{ option.label }}</strong>
            <span>{{ option.meta }}</span>
          </div>
        </el-option>
      </el-option-group>
    </el-select>

    <small class="note-search-select__hint">{{ helperText }}</small>
  </div>
</template>

<style scoped>
.note-search-select {
  display: grid;
  gap: 6px;
}

.note-search-select__control {
  width: 100%;
}

.note-search-select__option {
  display: grid;
  gap: 4px;
  line-height: 1.4;
  padding: 2px 0;
}

.note-search-select__option strong {
  color: var(--text-main);
  font-weight: 600;
}

.note-search-select__option span,
.note-search-select__hint {
  color: var(--text-soft);
  font-size: 12px;
}
</style>
