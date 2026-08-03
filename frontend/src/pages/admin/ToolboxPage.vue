<template>
  <div id="adminToolboxPage">
    <header class="page-heading">
      <div>
        <span class="page-kicker">后台管理</span>
        <h1>系统工具箱</h1>
        <p>查看外部依赖熔断状态，测试出站 Webhook 通知。</p>
      </div>
      <div class="heading-actions">
        <a-button :loading="breakerLoading" @click="loadBreakers">
          <template #icon><ReloadOutlined /></template>刷新状态
        </a-button>
      </div>
    </header>

    <!-- 熔断器状态 -->
    <section class="panel" aria-labelledby="breaker-title">
      <div class="panel-heading">
        <div>
          <span class="section-label">外部依赖</span>
          <h2 id="breaker-title">熔断器状态</h2>
        </div>
        <span v-if="breakerError" class="error-text">{{ breakerError }}</span>
      </div>

      <a-skeleton v-if="breakerLoading && !breakerData" active :paragraph="{ rows: 3 }" />

      <a-empty
        v-else-if="breakerError && !breakerData"
        description="暂时无法加载熔断状态"
      >
        <a-button @click="loadBreakers">重试</a-button>
      </a-empty>

      <a-alert
        v-else-if="breakerError && breakerData"
        type="warning"
        show-icon
        closable
        message="刷新失败，当前仍显示上一次状态"
        :description="breakerError"
        @close="breakerError = ''"
        class="breaker-stale"
        aria-live="polite"
      />

      <a-empty v-else-if="breakerData && !Object.keys(breakerData).length" description="暂无熔断器状态" />

      <div v-else-if="breakerData" class="breaker-grid">
        <div
          v-for="(item, service) in breakerData"
          :key="service"
          class="breaker-card"
          :class="`breaker--${item.status === 'OPEN' ? 'open' : 'closed'}`"
        >
          <div class="breaker-head">
            <span class="breaker-name">{{ serviceLabel(service) }}</span>
            <span class="breaker-status">{{ item.status === 'OPEN' ? '已熔断' : '正常' }}</span>
          </div>
          <div class="breaker-failures">
            连续失败：<strong>{{ item.failures ?? 0 }}</strong>
          </div>
        </div>
      </div>
    </section>

    <!-- Webhook 测试 -->
    <section class="panel" aria-labelledby="webhook-title">
      <div class="panel-heading">
        <div>
          <span class="section-label">出站通知</span>
          <h2 id="webhook-title">Webhook 测试</h2>
        </div>
      </div>

      <p class="panel-hint">
        向指定 URL 发送一条测试通知。仅支持 HTTPS 地址，发送失败将自动重试（最多 5 次）。
      </p>

      <a-form class="webhook-form" layout="inline" @finish="doSendWebhook">
        <a-form-item style="flex: 1">
          <a-input
            v-model:value="webhookUrl"
            placeholder="https://example.com/webhook"
            :disabled="sendingWebhook"
            autocomplete="off"
            aria-label="Webhook 目标地址"
          />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit" :loading="sendingWebhook">
            发送测试通知
          </a-button>
        </a-form-item>
      </a-form>

      <a-alert
        v-if="webhookNotice"
        :type="webhookNotice.type"
        show-icon
        closable
        :message="webhookNotice.message"
        :description="webhookNotice.description"
        class="webhook-notice"
        aria-live="polite"
        @close="webhookNotice = null"
      />
    </section>
  </div>
</template>

<script lang="ts" setup>
import { onMounted, ref } from 'vue'
import {
  Alert as AAlert,
  Empty as AEmpty,
  Form as AForm,
  FormItem as AFormItem,
  Skeleton as ASkeleton,
  message,
} from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { getBreakerStatus, sendTestWebhook } from '@/api/adminToolboxController'

const SERVICE_LABELS: Record<string, string> = {
  llm: '大模型',
  websearch: '联网搜索',
  image: '图片生成',
  cos: '对象存储',
  stripe: '支付',
}

const serviceLabel = (key: string) => SERVICE_LABELS[key] || key

// ── 熔断器 ──

const breakerData = ref<API.BreakerStatus | null>(null)
const breakerLoading = ref(false)
const breakerError = ref('')

const loadBreakers = async () => {
  if (breakerLoading.value) return
  breakerLoading.value = true
  breakerError.value = ''
  try {
    const res = await getBreakerStatus()
    if (res.data.code !== 0) throw new Error(res.data.message || '加载失败')
    breakerData.value = res.data.data ?? {}
  } catch (e) {
    breakerError.value = e instanceof Error ? e.message : '网络或服务暂时不可用'
    // 保留旧数据以便展示「仍显示上一次状态」
  } finally {
    breakerLoading.value = false
  }
}

// ── Webhook 测试 ──

const webhookUrl = ref('')
const sendingWebhook = ref(false)
const webhookNotice = ref<{ type: 'success' | 'error'; message: string; description: string } | null>(null)

const doSendWebhook = async () => {
  if (sendingWebhook.value) return
  const url = webhookUrl.value.trim()
  if (!url) {
    message.error('请输入 Webhook 地址')
    return
  }
  let parsed: URL
  try {
    parsed = new URL(url)
  } catch {
    message.error('Webhook 地址格式不正确')
    return
  }
  if (parsed.protocol !== 'https:') {
    message.error('仅支持 HTTPS 地址')
    return
  }
  sendingWebhook.value = true
  webhookNotice.value = null
  try {
    const res = await sendTestWebhook({ url })
    if (res.data.code !== 0) throw new Error(res.data.message || '发送失败')
    webhookNotice.value = {
      type: 'success',
      message: '测试通知已发送',
      description: '若目标地址在线，应能收到一条 webhook.test 事件。',
    }
  } catch (e) {
    webhookNotice.value = {
      type: 'error',
      message: '发送失败',
      description: e instanceof Error ? e.message : '网络或服务暂时不可用',
    }
  } finally {
    sendingWebhook.value = false
  }
}

onMounted(() => {
  void loadBreakers()
})
</script>

<style scoped lang="scss">
#adminToolboxPage {
  min-height: calc(100vh - 64px);
  padding: 40px 24px 72px;
  background: var(--surface-page);
  color: var(--text-body);
}

.page-heading,
.panel {
  width: min(100%, 1120px);
  margin-right: auto;
  margin-left: auto;
}

.page-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 32px;
  margin-bottom: 28px;
}

.page-kicker,
.section-label {
  display: block;
  margin-bottom: 7px;
  color: var(--state-success-text);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.page-heading h1 {
  margin: 0 0 8px;
  color: var(--text-strong);
  font-family: var(--font-heading);
  font-size: 36px;
  font-weight: 700;
  letter-spacing: -0.03em;
}

.page-heading p {
  margin: 0;
  color: var(--text-subtle);
  font-size: 14px;
}

.heading-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.panel {
  margin-bottom: 24px;
  padding: 24px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  background: var(--surface-panel);
}

.panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;

  h2 {
    margin: 0;
    color: var(--text-strong);
    font-family: var(--font-heading);
    font-size: 20px;
    font-weight: 650;
  }
}

.error-text {
  color: var(--state-error-text);
  font-size: 12px;
}

.panel-hint {
  margin: 0 0 16px;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.6;
}

// ── 熔断器卡片 ──

.breaker-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}

.breaker-card {
  padding: 18px 20px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: var(--surface-page);
}

.breaker-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.breaker-name {
  color: var(--text-strong);
  font-size: 14px;
  font-weight: 650;
}

.breaker-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 650;

  &::before {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    content: '';
  }
}

.breaker--closed .breaker-status {
  color: var(--state-success-text);
  &::before { background: var(--color-success); }
}

.breaker--open .breaker-status {
  color: var(--state-error-text);
  &::before { background: var(--color-error); }
}

.breaker-failures {
  color: var(--text-muted);
  font-size: 12px;

  strong {
    color: var(--text-strong);
    font-family: var(--font-mono);
  }
}

.breaker--open {
  border-color: var(--color-error);
}

.breaker-stale {
  margin-bottom: 16px;
}

// ── Webhook ──

.webhook-form {
  display: flex;
  gap: 12px;

  :deep(.ant-form-item) {
    margin-bottom: 0;
  }
}

.webhook-notice {
  margin-top: 16px;
}

@media (max-width: 768px) {
  #adminToolboxPage {
    padding: 28px 16px 56px;
  }

  .page-heading {
    flex-direction: column;
    align-items: flex-start;
    gap: 18px;
  }

  .page-heading h1 {
    font-size: 30px;
  }

  .webhook-form {
    flex-direction: column;
    align-items: stretch;
  }
}

@media (prefers-reduced-motion: reduce) {
  #adminToolboxPage :deep(*) {
    scroll-behavior: auto !important;
    transition-duration: 0.01ms !important;
  }
}
</style>