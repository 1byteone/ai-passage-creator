<template>
  <section class="dataviz-result" aria-label="数据图表报告">
    <a-spin v-if="loading" class="dataviz-loading" tip="报告生成中…" />

    <a-result
      v-else-if="!executionId"
      status="warning"
      title="缺少执行编号"
      :sub-title="missingExecutionIdMessage"
    />

    <template v-else-if="state.htmlReady">
      <iframe
        class="dataviz-frame"
        :src="buildArtifactUrl(executionId, 'html')"
        sandbox=""
        title="数据图表报告预览"
      />
      <div class="dataviz-actions">
        <a-button type="primary" :href="buildArtifactUrl(executionId, 'html')" download="report.html">
          下载 HTML
        </a-button>
        <a-button
          v-if="state.pngReady"
          :href="buildArtifactUrl(executionId, 'png')"
          download="report.png"
        >
          下载 PNG
        </a-button>
        <a-button v-else disabled title="PNG 尚未导出或渲染引擎不可用">下载 PNG</a-button>
      </div>
    </template>

    <a-result
      v-else-if="error"
      status="error"
      title="报告加载失败"
      :sub-title="error"
    >
      <template #extra>
        <a-button @click="restart">重新检查</a-button>
      </template>
    </a-result>

    <a-result
      v-else
      status="warning"
      title="报告尚未生成"
      sub-title="后端仍在处理，可稍后刷新"
    >
      <template #extra>
        <a-button @click="restart">刷新状态</a-button>
      </template>
    </a-result>
  </section>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { Result as AResult, Spin as ASpin } from 'ant-design-vue'
import { getDataVizArtifact } from '@/api/datavizController'
import {
  POLL_INTERVAL_MS,
  buildArtifactUrl,
  isPollExhausted,
  missingExecutionIdMessage,
  shouldStopPolling,
  type ArtifactState,
} from '@/utils/datavizState'

const props = defineProps<{
  executionId?: string
}>()

const state = ref<ArtifactState>({ htmlReady: false, pngReady: false })
const loading = ref(true)
const error = ref('')

let timer: number | null = null
let attempts = 0
let unmounted = false

const clearTimer = () => {
  if (timer !== null) {
    window.clearTimeout(timer)
    timer = null
  }
}

const poll = async () => {
  if (unmounted) return
  if (!props.executionId) {
    loading.value = false
    return
  }
  try {
    const res = await getDataVizArtifact(props.executionId)
    // await 之后组件可能已卸载，继续操作会写入失效状态
    if (unmounted) return
    if (res.data.code !== 0) throw new Error(res.data.message || '报告状态查询失败')
    state.value = {
      htmlReady: res.data.data?.htmlReady ?? false,
      pngReady: res.data.data?.pngReady ?? false,
    }
    error.value = ''
    loading.value = false
    if (shouldStopPolling(state.value)) return
    attempts += 1
    if (isPollExhausted(attempts)) {
      error.value = '报告生成超时，请刷新或重新执行'
      return
    }
    timer = window.setTimeout(poll, POLL_INTERVAL_MS)
  } catch (e) {
    if (unmounted) return
    error.value = e instanceof Error ? e.message : '报告状态查询失败'
    loading.value = false
  }
}

const restart = () => {
  clearTimer()
  attempts = 0
  error.value = ''
  loading.value = true
  void poll()
}

onMounted(() => {
  void poll()
})

onBeforeUnmount(() => {
  unmounted = true
  clearTimer()
})
</script>

<style scoped>
.dataviz-result {
  display: grid;
  gap: 16px;
}

.dataviz-loading {
  display: block;
  padding: 48px 0;
  text-align: center;
}

.dataviz-frame {
  width: 100%;
  height: 620px;
  border: 1px solid var(--color-border);
  background: #f9fafb;
}

.dataviz-actions {
  display: flex;
  gap: 12px;
}

@media (max-width: 560px) {
  .dataviz-frame {
    height: 460px;
  }

  .dataviz-actions {
    flex-direction: column;
  }
}
</style>
