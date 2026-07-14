<script setup>
defineProps({ permission: { type: String, required: true }, defaultValue: { type: String, default: 'op' }, grid: { type: String, default: '2fr 3fr .7fr' } })
</script>

<template>
  <div class="permission-row" :style="{ gridTemplateColumns: grid }">
    <code>{{ permission }}</code>
    <div><slot /></div>
    <span class="default" :class="defaultValue">{{ defaultValue }}</span>
  </div>
</template>

<style scoped>
.permission-row { display: grid; gap: 18px; align-items: start; padding: 16px 20px; border-bottom: 1px solid var(--vp-c-border); font-size: .92rem; }
.permission-row:last-child { border-bottom: 0; }
code { overflow-wrap: anywhere; color: var(--vp-c-indigo-1); }
.default { width: max-content; padding: 3px 8px; border-radius: 5px; font-family: var(--vp-font-family-mono); font-size: .8rem; font-weight: 700; background: var(--vp-c-default-soft); }
.default.true { color: #16a34a; background: rgba(34, 197, 94, .12); }
.default.op { color: #ef4444; background: rgba(239, 68, 68, .12); }
@media (max-width: 720px) { .permission-row { grid-template-columns: 1fr !important; gap: 9px; } }
</style>
