<script setup lang="ts">
withDefaults(
  defineProps<{
    kicker?: string
    title: string
    description?: string
  }>(),
  {
    kicker: 'Knowledge Pulse',
    description: ''
  }
)
</script>

<template>
  <section class="page-hero panel">
    <div class="page-hero__copy">
      <span class="section-kicker">{{ kicker }}</span>
      <h1 class="page-hero__title">{{ title }}</h1>
      <p v-if="description" class="page-hero__description">
        {{ description }}
      </p>
    </div>

    <div v-if="$slots.actions" class="page-hero__actions">
      <slot name="actions" />
    </div>
  </section>
</template>

<style scoped>
.page-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  padding: 28px 30px;
  background:
    radial-gradient(circle at top right, rgba(197, 157, 88, 0.22), transparent 34%),
    linear-gradient(135deg, rgba(255, 248, 240, 0.96), rgba(246, 236, 221, 0.9));
}

.page-hero__copy {
  max-width: 760px;
}

.page-hero__title {
  margin: 10px 0 0;
  font-family: var(--header-font);
  font-size: clamp(2rem, 3vw, 2.9rem);
  line-height: 1.08;
}

.page-hero__description {
  margin: 12px 0 0;
  color: var(--text-soft);
  font-size: 1rem;
  line-height: 1.7;
}

.page-hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: flex-end;
}

@media (max-width: 900px) {
  .page-hero {
    flex-direction: column;
    align-items: stretch;
  }

  .page-hero__actions {
    justify-content: flex-start;
  }
}

@media (max-width: 640px) {
  .page-hero {
    gap: 12px;
    padding: 16px;
  }

  .page-hero__title {
    font-size: clamp(1.48rem, 7vw, 2rem);
  }

  .page-hero__description {
    display: none;
  }

  .page-hero__actions {
    width: 100%;
  }

  .page-hero__actions :deep(.el-button) {
    flex: 1 1 140px;
    min-width: 0;
  }

  .page-hero__actions :slotted(.el-button) {
    flex: 1 1 140px;
    min-width: 0;
  }
}

@media (max-width: 420px) {
  .page-hero {
    padding: 14px;
  }

  .page-hero__actions :deep(.el-button) {
    flex-basis: 100%;
  }

  .page-hero__actions :slotted(.el-button) {
    flex-basis: 100%;
  }
}
</style>
