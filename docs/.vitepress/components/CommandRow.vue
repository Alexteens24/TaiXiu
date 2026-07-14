<script setup>
import { ref } from 'vue'
const props = defineProps({ commands: { type: [String, Array], required: true }, aliases: { type: [String, Array], default: () => [] }, permission: String })
const copied = ref('')
const list = value => Array.isArray(value) ? value : value ? [value] : []
async function copy(value) {
  try { await navigator.clipboard.writeText(value) } catch {
    const el = document.createElement('textarea'); el.value = value; document.body.appendChild(el); el.select(); document.execCommand('copy'); el.remove()
  }
  copied.value = value
  setTimeout(() => { if (copied.value === value) copied.value = '' }, 1500)
}
</script>

<template>
  <div class="command-row">
    <div class="badges">
      <button v-for="command in list(props.commands)" :key="command" class="badge command" @click="copy(command)">{{ copied === command ? 'Copied!' : command }}</button>
      <button v-for="alias in list(props.aliases)" :key="alias" class="badge alias" @click="copy(alias)">{{ copied === alias ? 'Copied!' : alias }}</button>
    </div>
    <div v-if="permission" class="permission">Permission: <code>{{ permission }}</code></div>
    <div class="description"><slot /></div>
  </div>
</template>

<style scoped>
.command-row { padding: 18px 20px; border-bottom: 1px solid var(--vp-c-border); }
.command-row:last-child { border-bottom: 0; }
.badges { display: flex; flex-wrap: wrap; gap: 7px; }
.badge { padding: 4px 9px; border: 1px solid transparent; border-radius: 6px; font-family: var(--vp-font-family-mono); font-size: .8rem; font-weight: 600; cursor: pointer; }
.command { color: var(--vp-c-brand-1); background: var(--vp-c-brand-soft); border-color: color-mix(in srgb, var(--vp-c-brand-1) 25%, transparent); }
.alias { color: var(--vp-c-text-2); background: var(--vp-c-default-soft); border-color: var(--vp-c-divider); }
.permission { margin-top: 8px; color: var(--vp-c-text-3); font-size: .75rem; text-transform: uppercase; }
.description { margin-top: 11px; padding-top: 9px; border-top: 1px dashed var(--vp-c-divider); font-size: .92rem; }
.description :deep(p) { margin: 0; }
</style>
