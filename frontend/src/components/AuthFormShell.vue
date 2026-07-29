<template>
  <section class="auth-page" :aria-labelledby="titleId">
    <div class="auth-shell">
      <AuthBrandSection />
      <div class="form-section">
        <div class="form-card">
          <header class="form-heading">
            <h1 :id="titleId">{{ title }}</h1>
            <p>{{ subtitle }}</p>
          </header>
          <slot />
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import AuthBrandSection from '@/components/AuthBrandSection.vue'

defineProps<{ title: string; subtitle: string; titleId: string }>()
</script>

<style scoped>
.auth-page {
  min-height: calc(100dvh - 64px);
  padding: 40px 20px;
  display: grid;
  place-items: center;
  background: var(--surface-page);
}

.auth-shell {
  display: flex;
  width: min(920px, 100%);
  min-height: 560px;
  overflow: hidden;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-xl);
  background: var(--surface-panel);
  box-shadow: var(--shadow-raised);
}

.form-section {
  flex: 1;
  display: grid;
  place-items: center;
  padding: 48px;
}

.form-card {
  width: min(360px, 100%);
}

.form-heading {
  margin-bottom: 28px;
}

.form-heading h1 {
  margin: 0 0 8px;
  font-size: 28px;
  line-height: 1.25;
}

.form-heading p {
  margin: 0;
  color: var(--text-muted);
  font-size: 14px;
}

@media (max-width: 768px) {
  .auth-page {
    padding: 20px 16px 32px;
    place-items: start center;
  }

  .auth-shell {
    min-height: auto;
    flex-direction: column;
  }

  .form-section {
    padding: 32px 24px;
  }

  .form-heading h1 {
    font-size: 24px;
  }
}
</style>
