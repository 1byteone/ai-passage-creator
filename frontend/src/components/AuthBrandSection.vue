<template>
  <aside class="brand-section" aria-label="产品能力">
    <div class="brand-content">
      <div class="brand-logo">
        <img
          src="@/assets/logo.webp"
          alt=""
          width="64"
          height="64"
          decoding="async"
          class="logo-img"
        />
      </div>
      <p class="brand-eyebrow">AI 内容工作台</p>
      <p class="brand-title">AI 爆款文章创作器</p>
      <p class="brand-subtitle">从选题、大纲到正文与配图，让创作过程更清晰、更连续。</p>
      <ul class="brand-features">
        <li><CheckCircleOutlined /><span>比较标题方案后再确定方向</span></li>
        <li><CheckCircleOutlined /><span>确认文章结构后生成正文</span></li>
        <li><CheckCircleOutlined /><span>自动配图并保留历史成果</span></li>
      </ul>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { CheckCircleOutlined } from '@ant-design/icons-vue'
</script>

<style scoped>
.brand-section {
  position: relative;
  flex: 0 0 44%;
  display: flex;
  align-items: center;
  overflow: hidden;
  padding: 56px 48px;
  background: var(--color-secondary);
  color: var(--text-inverse);
}

.brand-section::after {
  content: '';
  position: absolute;
  right: -72px;
  bottom: -96px;
  width: 240px;
  aspect-ratio: 1;
  border: 48px solid rgba(34, 197, 94, 0.16);
  border-radius: 50%;
}

.brand-content {
  position: relative;
  z-index: 1;
  max-width: 340px;
}

.brand-logo {
  margin-bottom: 40px;
}

.logo-img {
  width: 64px;
  height: 64px;
  object-fit: contain;
  padding: 8px;
  border-radius: var(--radius-lg);
  background: var(--surface-panel);
}

.brand-eyebrow {
  margin: 0 0 10px;
  color: var(--color-primary-light);
  font-size: 13px;
  font-weight: 600;
}

.brand-title {
  margin: 0 0 12px;
  color: var(--text-inverse);
  font-family: var(--font-heading);
  font-size: 28px;
  font-weight: 700;
  line-height: 1.25;
}

.brand-subtitle {
  margin: 0 0 32px;
  color: #cbd5e1;
  font-size: 15px;
  line-height: 1.75;
}

.brand-features {
  margin: 0;
  padding: 24px 0 0;
  border-top: 1px solid rgba(255, 255, 255, 0.14);
  list-style: none;
}

.brand-features li {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  color: #e2e8f0;
  font-size: 14px;
}

.brand-features li:last-child {
  margin-bottom: 0;
}

.brand-features :deep(svg) {
  flex: 0 0 auto;
  color: var(--color-primary-light);
  font-size: 16px;
}

@media (max-width: 768px) {
  .brand-section {
    flex-basis: auto;
    padding: 24px;
  }

  .brand-content {
    display: grid;
    grid-template-columns: 48px 1fr;
    align-items: center;
    max-width: none;
    column-gap: 14px;
  }

  .brand-logo {
    grid-row: 1 / 4;
    margin: 0;
  }

  .logo-img {
    width: 48px;
    height: 48px;
    border-radius: var(--radius-md);
  }

  .brand-eyebrow {
    margin-bottom: 2px;
  }

  .brand-title {
    margin: 0;
    font-size: 20px;
  }

  .brand-subtitle {
    margin: 4px 0 0;
    font-size: 13px;
    line-height: 1.5;
  }

  .brand-features {
    display: none;
  }
}
</style>
