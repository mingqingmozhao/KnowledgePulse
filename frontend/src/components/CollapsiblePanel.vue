<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    title: string
    kicker?: string
    meta?: string
    initiallyOpen?: boolean
    destroyOnClose?: boolean
    bodyClass?: string
    tag?: string
  }>(),
  {
    kicker: '',
    meta: '',
    initiallyOpen: true,
    destroyOnClose: false,
    bodyClass: '',
    tag: 'section'
  }
)

const open = ref(props.initiallyOpen)

watch(
  () => props.initiallyOpen,
  (value) => {
    open.value = value
  }
)

const toggleSymbol = computed(() => (open.value ? '-' : '+'))
const toggleTitle = computed(() => (open.value ? '收起面板' : '展开面板'))
const shouldRenderBody = computed(() => (props.destroyOnClose ? open.value : true))

function togglePanel() {
  open.value = !open.value
}
</script>

<template>
  <component :is="tag" class="collapsible-panel panel">
    <div class="collapsible-panel__title-row">
      <div>
        <span v-if="kicker" class="section-kicker">{{ kicker }}</span>
        <h3 class="section-title">{{ title }}</h3>
        <small v-if="meta || $slots.meta" class="collapsible-panel__meta">
          <slot name="meta">{{ meta }}</slot>
        </small>
      </div>

      <div class="collapsible-panel__header-actions">
        <slot name="header-actions" />
        <button
          type="button"
          class="collapsible-panel__toggle"
          :aria-expanded="open"
          :title="toggleTitle"
          @click="togglePanel"
        >
          {{ toggleSymbol }}
        </button>
      </div>
    </div>

    <transition name="collapsible-panel-collapse">
      <div
        v-if="shouldRenderBody"
        v-show="open"
        class="collapsible-panel__body"
        :class="bodyClass"
      >
        <slot />
      </div>
    </transition>
  </component>
</template>

<style scoped>
.collapsible-panel {
  display: grid;
  gap: 14px;
  align-self: start;
}

.collapsible-panel__title-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.collapsible-panel__meta {
  display: block;
  margin-top: 6px;
  color: var(--text-soft);
  font-size: 0.82rem;
  line-height: 1.5;
}

.collapsible-panel__header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.collapsible-panel__toggle {
  width: 34px;
  height: 34px;
  border: 1px solid rgba(184, 92, 56, 0.2);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  color: #8d4529;
  font-size: 1.2rem;
  line-height: 1;
  cursor: pointer;
  transition: border-color 0.2s ease, background-color 0.2s ease, transform 0.2s ease;
}

.collapsible-panel__toggle:hover {
  border-color: rgba(141, 69, 41, 0.42);
  background: rgba(255, 255, 255, 0.96);
  transform: translateY(-1px);
}

.collapsible-panel__body {
  display: grid;
  gap: 14px;
}

.collapsible-panel-collapse-enter-active,
.collapsible-panel-collapse-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.collapsible-panel-collapse-enter-from,
.collapsible-panel-collapse-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

@media (max-width: 720px) {
  .collapsible-panel__title-row {
    align-items: center;
  }

  .collapsible-panel__header-actions {
    flex: 0 0 auto;
  }

  .collapsible-panel__title-row > div {
    min-width: 0;
  }

  .collapsible-panel__title-row .section-title {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .collapsible-panel__meta {
    overflow: hidden;
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 1;
  }
}

@media (max-width: 420px) {
  .collapsible-panel {
    gap: 12px;
  }

  .collapsible-panel__body {
    gap: 12px;
  }
}
</style>
