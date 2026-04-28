<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type Vditor from 'vditor'

const props = withDefaults(
  defineProps<{
    modelValue: string
    placeholder?: string
    height?: string | number
    disabled?: boolean
  }>(),
  {
    modelValue: '',
    placeholder: '开始记录你的知识脉络...',
    height: 620,
    disabled: false
  }
)

type HtmlChangeMeta = {
  external?: boolean
}

const emit = defineEmits<{
  (event: 'update:modelValue', value: string): void
  (event: 'html-change', value: string, meta?: HtmlChangeMeta): void
  (event: 'editing-region-change', value: string): void
  (event: 'ready'): void
}>()

const editorHost = ref<HTMLDivElement | null>(null)
let editor: Vditor | null = null
let syncingExternally = false
let externalSyncTimer: number | null = null

const VDITOR_LOCAL_CDN = '/vditor'

function clearExternalSyncTimer() {
  if (externalSyncTimer === null) {
    return
  }

  window.clearTimeout(externalSyncTimer)
  externalSyncTimer = null
}

function beginExternalSync() {
  clearExternalSyncTimer()
  syncingExternally = true
}

function finishExternalSyncSoon() {
  clearExternalSyncTimer()
  externalSyncTimer = window.setTimeout(() => {
    syncingExternally = false
    externalSyncTimer = null
  }, 0)
}

function normalizeHeadingText(value: string) {
  return value.replace(/\s+/g, ' ').trim()
}

function isSelectionInsideEditor() {
  const host = editorHost.value
  const selection = window.getSelection()
  const anchorNode = selection?.anchorNode

  return Boolean(host && anchorNode && host.contains(anchorNode))
}

function resolveActiveRegion() {
  const host = editorHost.value
  const selection = window.getSelection()
  const anchorNode = selection?.anchorNode

  if (!host || !anchorNode || !host.contains(anchorNode)) {
    return '正文'
  }

  const anchorElement =
    anchorNode.nodeType === Node.ELEMENT_NODE ? (anchorNode as HTMLElement) : anchorNode.parentElement

  if (!anchorElement) {
    return '正文'
  }

  const directHeading = anchorElement.closest<HTMLElement>('h1, h2, h3, h4, h5, h6, [data-type="heading"]')
  if (directHeading && host.contains(directHeading)) {
    return normalizeHeadingText(directHeading.textContent || '') || '当前标题'
  }

  const headings = Array.from(
    host.querySelectorAll<HTMLElement>('h1, h2, h3, h4, h5, h6, [data-type="heading"]')
  )
  const activeHeading = headings
    .filter((heading) => {
      const relation = heading.compareDocumentPosition(anchorElement)
      return heading === anchorElement || heading.contains(anchorElement) || Boolean(relation & Node.DOCUMENT_POSITION_FOLLOWING)
    })
    .at(-1)

  return normalizeHeadingText(activeHeading?.textContent || '') || '正文'
}

function emitActiveRegion() {
  if (!isSelectionInsideEditor()) {
    return
  }

  emit('editing-region-change', resolveActiveRegion())
}

function scrollToHeading(headingText: string) {
  const host = editorHost.value

  if (!host) {
    return false
  }

  const normalizedHeading = normalizeHeadingText(headingText)
  const headingElements = Array.from(
    host.querySelectorAll<HTMLElement>('h1, h2, h3, h4, h5, h6, [data-type="heading"]')
  )

  const target = headingElements.find(
    (element) => normalizeHeadingText(element.textContent || '') === normalizedHeading
  )

  if (!target) {
    editor?.focus()
    return false
  }

  target.scrollIntoView({
    behavior: 'smooth',
    block: 'center'
  })
  target.classList.add('editor-shell__heading-highlight')
  window.setTimeout(() => {
    target.classList.remove('editor-shell__heading-highlight')
  }, 1400)
  editor?.focus()
  return true
}

function syncEditorValue(value: string) {
  if (!editor || value === editor.getValue()) {
    return
  }

  beginExternalSync()

  try {
    editor.setValue(value)
    emit('html-change', editor.getHTML(), { external: true })
  } finally {
    finishExternalSyncSoon()
  }
}

onMounted(async () => {
  if (!editorHost.value) {
    return
  }

  const { default: VditorConstructor } = await import('vditor')

  editor = new VditorConstructor(editorHost.value, {
    cdn: VDITOR_LOCAL_CDN,
    height: props.height,
    minHeight: 480,
    mode: 'ir',
    icon: 'material',
    cache: {
      enable: false
    },
    counter: {
      enable: true
    },
    toolbar: [
      'emoji',
      'headings',
      'bold',
      'italic',
      'strike',
      'link',
      '|',
      'list',
      'ordered-list',
      'check',
      'outdent',
      'indent',
      '|',
      'quote',
      'line',
      'code',
      'inline-code',
      'table',
      '|',
      'undo',
      'redo',
      'fullscreen',
      'preview'
    ],
    placeholder: props.placeholder,
    input(value) {
      if (syncingExternally) {
        return
      }

      emit('update:modelValue', value)
      emit('html-change', editor?.getHTML() ?? '')
      emitActiveRegion()
    },
    blur() {
      emit('html-change', editor?.getHTML() ?? '', { external: syncingExternally })
    },
    after() {
      beginExternalSync()

      try {
        if (props.modelValue) {
          editor?.setValue(props.modelValue)
        }

        if (props.disabled) {
          editor?.disabled()
        }

        emit('html-change', editor?.getHTML() ?? '', { external: true })
        emit('ready')
      } finally {
        finishExternalSyncSoon()
      }
    }
  })

  editorHost.value.addEventListener('pointerup', emitActiveRegion)
  editorHost.value.addEventListener('keyup', emitActiveRegion)
})

watch(
  () => props.modelValue,
  (value) => {
    syncEditorValue(value)
  }
)

watch(
  () => props.disabled,
  (value) => {
    if (!editor) {
      return
    }

    if (value) {
      editor.disabled()
    } else {
      editor.enable()
    }
  }
)

defineExpose({
  setValue(value: string) {
    syncEditorValue(value)
  },
  getValue() {
    return editor?.getValue() ?? ''
  },
  getHTML() {
    return editor?.getHTML() ?? ''
  },
  focus() {
    editor?.focus()
  },
  getActiveRegion() {
    return resolveActiveRegion()
  },
  scrollToHeading(headingText: string) {
    return scrollToHeading(headingText)
  }
})

onBeforeUnmount(() => {
  clearExternalSyncTimer()
  editorHost.value?.removeEventListener('pointerup', emitActiveRegion)
  editorHost.value?.removeEventListener('keyup', emitActiveRegion)
  editor?.destroy()
  editor = null
})
</script>

<template>
  <div class="editor-shell">
    <div ref="editorHost" class="editor-shell__host" />
  </div>
</template>

<style scoped>
.editor-shell {
  width: 100%;
}

.editor-shell__host {
  width: 100%;
}

.editor-shell__host :deep(.editor-shell__heading-highlight) {
  outline: 2px solid rgba(184, 92, 56, 0.36);
  border-radius: 12px;
  box-shadow: 0 0 0 6px rgba(184, 92, 56, 0.12);
  transition: box-shadow 0.2s ease, outline-color 0.2s ease;
}

.editor-shell__host :deep(.vditor-toolbar) {
  overflow-x: auto;
  scrollbar-width: thin;
}

@media (max-width: 640px) {
  .editor-shell__host :deep(.vditor) {
    height: min(68dvh, 620px) !important;
    min-height: 460px !important;
  }

  .editor-shell__host :deep(.vditor-toolbar) {
    flex-wrap: nowrap;
    padding: 6px;
  }

  .editor-shell__host :deep(.vditor-content) {
    min-width: 0;
  }
}

@media (max-width: 420px) {
  .editor-shell__host :deep(.vditor) {
    height: min(66dvh, 560px) !important;
    min-height: 420px !important;
  }
}
</style>
