<script setup lang="ts">
import { computed } from 'vue'
import CollapsiblePanel from '@/components/CollapsiblePanel.vue'
import type { TagBucket } from '@/types'

const props = withDefaults(
  defineProps<{
    tags: TagBucket[]
    activeTag?: string | null
    title?: string
    initiallyOpen?: boolean
  }>(),
  {
    activeTag: null,
    title: '标签云',
    initiallyOpen: false
  }
)

const emit = defineEmits<{
  (event: 'select', tag: string): void
}>()

const maxCount = computed(() => Math.max(...props.tags.map((tag) => tag.count), 1))

function tagStyle(count: number) {
  const ratio = count / maxCount.value
  const scale = 0.92 + ratio * 0.36

  return {
    transform: `scale(${scale})`,
    opacity: `${0.76 + ratio * 0.24}`
  }
}
</script>

<template>
  <CollapsiblePanel
    class="tag-panel"
    kicker="Tags"
    :title="title"
    :meta="`${tags.length} 个主题`"
    :initially-open="initiallyOpen"
  >
    <div v-if="tags.length" class="tag-panel__cloud">
      <button
        v-for="tag in tags"
        :key="tag.label"
        class="tag-panel__tag"
        :class="{ 'tag-panel__tag--active': activeTag === tag.label }"
        :style="tagStyle(tag.count)"
        @click="emit('select', tag.label)"
      >
        <span>#{{ tag.label }}</span>
        <strong>{{ tag.count }}</strong>
      </button>
    </div>

    <div v-else class="tag-panel__empty">保存几篇带标签的笔记后，这里会自动长出主题云。</div>
  </CollapsiblePanel>
</template>

<style scoped>
.tag-panel {
  padding: 22px;
}

.tag-panel__cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  transform-origin: left top;
}

.tag-panel__tag {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border: 1px solid rgba(184, 92, 56, 0.15);
  border-radius: 999px;
  background: rgba(255, 252, 246, 0.9);
  color: var(--accent-strong);
  cursor: pointer;
  transition: transform 0.2s ease, border-color 0.2s ease, background 0.2s ease;
}

.tag-panel__tag strong {
  color: var(--text-soft);
  font-size: 0.84rem;
}

.tag-panel__tag:hover,
.tag-panel__tag--active {
  border-color: rgba(184, 92, 56, 0.42);
  background: rgba(184, 92, 56, 0.1);
}

.tag-panel__empty {
  color: var(--text-soft);
  line-height: 1.7;
}
</style>
