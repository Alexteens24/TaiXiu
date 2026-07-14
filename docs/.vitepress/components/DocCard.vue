<script setup>
defineProps({ title: String, icon: String, link: String, desc: String })
</script>

<template>
  <a :href="link" class="doc-card">
    <span class="icon">{{ icon }}</span>
    <h3>{{ title }}</h3>
    <p>{{ desc }}</p>
  </a>
</template>

<style scoped>
.doc-card {
  display: block;
  padding: 22px;
  color: var(--vp-c-text-1);
  background: var(--vp-c-bg-soft);
  border: 1px solid var(--vp-c-border);
  border-radius: 12px;
  text-decoration: none !important;
  transition: transform .2s, border-color .2s, box-shadow .2s;
}
.doc-card:hover {
  border-color: var(--vp-c-brand-1);
  transform: translateY(-3px);
  box-shadow: 0 10px 24px rgba(0, 0, 0, .08);
}
.icon { display: block; margin-bottom: 12px; font-size: 2rem; }
h3 { margin: 0 !important; font-size: 1.08rem; }
p { margin: 8px 0 0 !important; color: var(--vp-c-text-2); font-size: .9rem; line-height: 1.55; }
</style>
