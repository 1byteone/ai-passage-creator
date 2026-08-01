<template>
  <div class="basic-layout">
    <!-- 顶部导航栏 -->
    <GlobalHeader />
    <!-- 主要内容区域 -->
    <main class="main-content">
      <router-view />
    </main>
    <!-- 底部版权信息 -->
    <GlobalFooter />
  </div>
</template>

<script setup lang="ts">
import GlobalHeader from '@/components/GlobalHeader.vue'
import GlobalFooter from '@/components/GlobalFooter.vue'
</script>

<style scoped>
.basic-layout {
  display: flex;
  min-height: 100dvh;
  flex-direction: column;
  background: none;
}

.main-content {
  flex: 1;
  width: 100%;
  padding: 0;
  background: none;
  margin: 0;
}
</style>
