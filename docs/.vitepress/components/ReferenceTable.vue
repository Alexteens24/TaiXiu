<script setup>
defineProps({ columns: { type: Array, required: true }, grid: { type: String, default: '1fr 1fr' } })
</script>

<template>
  <div class="reference-table">
    <div class="table-header" :style="{ gridTemplateColumns: grid }">
      <strong v-for="column in columns" :key="column">{{ column }}</strong>
    </div>
    <slot />
  </div>
</template>

<style scoped>
.reference-table { margin-top: 24px; overflow: hidden; border: 1px solid var(--vp-c-border); border-radius: 10px; }
.table-header { display: grid; gap: 18px; padding: 12px 20px; color: var(--vp-c-text-2); background: var(--vp-c-bg-mute); font-size: .78rem; letter-spacing: .05em; text-transform: uppercase; }
@media (max-width: 720px) { .table-header { display: none; } }
</style>
