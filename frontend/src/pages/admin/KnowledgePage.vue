<template>
  <div id="adminKnowledgePage">
    <header class="page-heading">
      <div>
        <span class="page-kicker">后台管理</span>
        <h1>知识库管理</h1>
        <p>上传文档到共享向量库，AI 生成内容时参考已有知识。</p>
      </div>
    </header>

    <section class="health-strip" aria-label="知识库健康状态">
      <div><span class="section-label">索引状态</span><strong>{{ health?.status || '加载中' }}</strong></div>
      <span>当前版本：{{ health?.activeCommitSha || '暂无 active 批次' }}</span>
      <span>已索引 {{ health?.indexedDocuments ?? 0 }} / {{ health?.totalDocuments ?? 0 }}</span>
      <span v-if="health?.failedDocuments">失败 {{ health.failedDocuments }}</span>
      <span v-if="health?.pendingReviewDocuments">待审核 {{ health.pendingReviewDocuments }}</span>
      <a-button size="small" :loading="healthLoading" @click="loadHealth">刷新状态</a-button>
    </section>

    <!-- 上传区 -->
    <section class="panel" aria-labelledby="upload-title">
      <div class="panel-heading">
        <div>
          <span class="section-label">文档入库</span>
          <h2 id="upload-title">写入知识库</h2>
        </div>
      </div>
      <a-space direction="vertical" style="width: 100%">
        <a-input v-model:value="form.title" placeholder="文档标题（可选，检索时展示）" />
        <a-input v-model:value="form.source" placeholder="source 唯一标识（必填，重复上传覆盖）" />
        <a-textarea v-model:value="form.text" :rows="6" placeholder="粘贴文档正文，或上传 .md/.txt 文件" />
        <a-space>
          <a-button type="primary" :loading="submitting" @click="handleUpload">
            <template #icon><UploadOutlined /></template>写入知识库
          </a-button>
          <a-button :loading="syncing" @click="handleSync">
            <template #icon><SyncOutlined /></template>同步 Git 文档
          </a-button>
          <a-upload :before-upload="handleFile" :show-upload-list="false" accept=".md,.txt">
            <a-button><template #icon><FileOutlined /></template>上传 .md/.txt 文件</a-button>
          </a-upload>
        </a-space>
        <div v-if="syncJob" class="sync-status" role="status">
          <span>同步任务 #{{ syncJob.id }}：{{ syncJob.status }}</span>
          <span v-if="syncJob.totalFiles">{{ syncJob.processedFiles ?? 0 }}/{{ syncJob.totalFiles }} 个文件，{{ syncJob.indexedSections ?? 0 }}/{{ syncJob.totalSections ?? 0 }} 个章节</span>
          <span v-if="syncJob.status === 'FAILED'" class="sync-error">{{ syncJob.errorMessage }}</span>
          <a-button v-if="syncJob.status === 'FAILED'" size="small" :loading="syncing" @click="handleRetrySync">重试同步</a-button>
        </div>
      </a-space>
    </section>

    <!-- 文档列表 -->
    <section class="panel" aria-labelledby="list-title">
      <div class="panel-heading">
        <div>
          <span class="section-label">文档列表</span>
          <h2 id="list-title">已入库文档</h2>
        </div>
      </div>
      <a-space style="margin-bottom: 12px">
        <a-input v-model:value="keyword" placeholder="搜索标题/source" style="width: 240px" @press-enter="pageNum = 1; loadDocuments()" />
        <a-button @click="pageNum = 1; loadDocuments()">搜索</a-button>
      </a-space>
      <a-table :data-source="records" :loading="loading" row-key="id"
        :pagination="{ current: pageNum, pageSize, total: totalRow, onChange: (p: number) => { pageNum = p; loadDocuments() } }">
        <a-table-column title="标题" data-index="title" />
        <a-table-column title="source" data-index="source" ellipsis />
        <a-table-column title="字数" data-index="text">
          <template #default="{ record }">{{ (record.text || '').length }}</template>
        </a-table-column>
        <a-table-column title="状态" data-index="status" />
        <a-table-column title="索引尝试" data-index="indexAttempts" />
        <a-table-column title="索引异常" data-index="indexError" ellipsis />
        <a-table-column title="来源" data-index="sourceType" />
        <a-table-column title="路径" data-index="sourcePath" ellipsis />
        <a-table-column title="行号">
          <template #default="{ record }">
            {{ record.lineStart && record.lineEnd ? `${record.lineStart}-${record.lineEnd}` : '-' }}
          </template>
        </a-table-column>
        <a-table-column title="上传时间" data-index="createTime" />
        <a-table-column title="操作">
          <template #default="{ record }">
            <a-button v-if="record.status === 'PENDING_REVIEW'" type="link" size="small" @click="handleApprove(record.id)">审核通过</a-button>
            <a-button v-if="record.status === 'INDEX_FAILED'" type="link" size="small" @click="handleReindex(record.id)">重试索引</a-button>
            <a-popconfirm title="确认删除该文档？删除后向量索引同步清除。" @confirm="handleDelete(record.id)">
              <a-button type="link" danger size="small">删除</a-button>
            </a-popconfirm>
          </template>
        </a-table-column>
      </a-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { UploadOutlined, FileOutlined, SyncOutlined } from '@ant-design/icons-vue'
import { listRagDocuments, deleteRagDocument, uploadRagDocument, approveRagDocument, reindexRagDocument, syncRagKnowledge, getRagSyncJob, retryRagSyncJob, getRagKnowledgeHealth } from '@/api/ragController'

const records = ref<API.RagDocument[]>([])
const totalRow = ref(0)
const loading = ref(false)
const keyword = ref('')
const pageNum = ref(1)
const pageSize = 20
const submitting = ref(false)
const syncing = ref(false)
const syncJob = ref<API.RagSyncJob | null>(null)
const health = ref<API.RagKnowledgeHealth | null>(null)
const healthLoading = ref(false)
let syncTimer: number | undefined
const form = ref({ title: '', source: '', text: '' })

const loadHealth = async () => {
  healthLoading.value = true
  try {
    const res = await getRagKnowledgeHealth()
    if (res.data.code !== 0) throw new Error(res.data.message || '健康状态加载失败')
    health.value = res.data.data ?? null
  } catch (e) {
    message.error(e instanceof Error ? e.message : '健康状态加载失败')
  } finally {
    healthLoading.value = false
  }
}

const loadDocuments = async () => {
  loading.value = true
  try {
    const res = await listRagDocuments({ pageNum: pageNum.value, pageSize, keyword: keyword.value || undefined })
    if (res.data.code !== 0) throw new Error(res.data.message || '加载失败')
    records.value = res.data.data?.records ?? []
    totalRow.value = res.data.data?.totalRow ?? 0
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

const handleApprove = async (id?: number) => {
  if (!id) return
  try {
    const res = await approveRagDocument(id)
    if (res.data.code !== 0) throw new Error(res.data.message || '审核失败')
    message.success('审核通过，已建立向量索引')
    await loadDocuments()
    await loadHealth()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '审核失败')
  }
}

const handleReindex = async (id?: number) => {
  if (!id) return
  try {
    const res = await reindexRagDocument(id)
    if (res.data.code !== 0 || !res.data.data) throw new Error(res.data.message || '重试索引失败')
    message.success('已重新开始建立向量索引')
    await loadDocuments()
    await loadHealth()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '重试索引失败')
  }
}

const handleSync = async () => {
  syncing.value = true
  try {
    const res = await syncRagKnowledge()
    if (res.data.code !== 0) throw new Error(res.data.message || '同步失败')
    syncJob.value = res.data.data ?? null
    message.info('Git 文档同步任务已创建，后台将持续处理')
    pollSyncJob()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '同步失败')
  } finally {
    if (!syncJob.value || ['SUCCEEDED', 'FAILED'].includes(syncJob.value.status || '')) syncing.value = false
  }
}

const pollSyncJob = () => {
  if (syncTimer) window.clearTimeout(syncTimer)
  const id = syncJob.value?.id
  if (!id) return
  syncTimer = window.setTimeout(async () => {
    try {
      const res = await getRagSyncJob(id)
      if (res.data.code !== 0) throw new Error(res.data.message || '同步状态查询失败')
      syncJob.value = res.data.data ?? syncJob.value
      const job = syncJob.value
      if (!job) throw new Error('同步任务不存在')
      const status = job?.status
      if (status === 'SUCCEEDED') {
        syncing.value = false
        message.success(`Git 文档同步完成：${job.totalFiles ?? 0} 个文件，${job.indexedSections ?? 0} 个章节`)
        await loadDocuments()
        await loadHealth()
      } else if (status === 'FAILED') {
        syncing.value = false
        message.error(job.errorMessage || 'Git 文档同步失败，旧版本仍保持可用')
      } else {
        syncing.value = true
        pollSyncJob()
      }
    } catch (e) {
      syncing.value = false
      message.error(e instanceof Error ? e.message : '同步状态查询失败')
    }
  }, 1000)
}

const handleRetrySync = async () => {
  const id = syncJob.value?.id
  if (!id) return
  syncing.value = true
  try {
    const res = await retryRagSyncJob(id)
    if (res.data.code !== 0) throw new Error(res.data.message || '重试失败')
    syncJob.value = res.data.data ?? null
    message.info('已创建新的同步批次')
    pollSyncJob()
  } catch (e) {
    syncing.value = false
    message.error(e instanceof Error ? e.message : '重试失败')
  }
}

const handleUpload = async () => {
  if (!form.value.source.trim()) {
    message.warning('请填写 source')
    return
  }
  submitting.value = true
  try {
    const res = await uploadRagDocument({ ...form.value })
    if (res.data.code !== 0) throw new Error(res.data.message || '上传失败')
    message.success('文档已提交审核，审核通过后进入检索')
    form.value = { title: '', source: '', text: '' }
    await loadDocuments()
    await loadHealth()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '上传失败')
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (id: number) => {
  try {
    const res = await deleteRagDocument(id)
    if (res.data.code !== 0) throw new Error(res.data.message || '删除失败')
    message.success('已删除')
    await loadDocuments()
    await loadHealth()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '删除失败')
  }
}

const readFileAsText = (file: File) =>
  new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result ?? ''))
    reader.onerror = reject
    reader.readAsText(file)
  })

const handleFile = async (file: File) => {
  const body = await readFileAsText(file)
  form.value.text = body
  if (!form.value.title) form.value.title = file.name.replace(/\.(md|txt)$/i, '')
  if (!form.value.source) form.value.source = `file://${file.name}`
  message.info('已读取文件内容，点击「写入知识库」提交')
  return false
}

onMounted(() => {
  void loadDocuments()
  void loadHealth()
})
onUnmounted(() => {
  if (syncTimer) window.clearTimeout(syncTimer)
})
</script>

<style scoped lang="scss">
#adminKnowledgePage {
  padding: 24px;
}
.page-heading {
  margin-bottom: 24px;
}
.health-strip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px;
  padding: 12px 16px;
  margin-bottom: 16px;
  border: 1px solid var(--color-border-secondary);
  border-radius: 8px;
  background: var(--color-bg-container);
  color: var(--color-text-secondary);
  font-size: 13px;

  div { display: flex; align-items: center; gap: 8px; }
  strong { color: var(--color-text); }
}
.page-kicker {
  font-size: 12px;
  color: var(--color-text-secondary);
  text-transform: uppercase;
  letter-spacing: 1px;
}
h1 {
  margin: 4px 0 0;
  font-size: 22px;
  font-weight: 600;
}
.panel {
  background: var(--color-bg-container);
  border: 1px solid var(--color-border-secondary);
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 16px;
}
.panel-heading {
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.section-label {
  font-size: 12px;
  color: var(--color-primary);
  font-weight: 500;
}
.sync-status {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  color: var(--color-text-secondary);
  font-size: 13px;
}
.sync-error {
  color: var(--color-error);
}
.panel-heading h2 {
  margin: 2px 0 0;
  font-size: 16px;
  font-weight: 500;
}
</style>
