<template>
  <div id="adminKnowledgePage">
    <header class="page-heading">
      <div>
        <span class="page-kicker">后台管理</span>
        <h1>知识库管理</h1>
        <p>上传文档到共享向量库，AI 生成内容时参考已有知识。</p>
      </div>
    </header>

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
        <a-table-column title="来源" data-index="sourceType" />
        <a-table-column title="路径" data-index="sourcePath" ellipsis />
        <a-table-column title="上传时间" data-index="createTime" />
        <a-table-column title="操作">
          <template #default="{ record }">
            <a-button v-if="record.status === 'PENDING_REVIEW'" type="link" size="small" @click="handleApprove(record.id)">审核通过</a-button>
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
import { listRagDocuments, deleteRagDocument, uploadRagDocument, approveRagDocument, syncRagKnowledge, getRagSyncJob, retryRagSyncJob } from '@/api/ragController'

const records = ref<API.RagDocument[]>([])
const totalRow = ref(0)
const loading = ref(false)
const keyword = ref('')
const pageNum = ref(1)
const pageSize = 20
const submitting = ref(false)
const syncing = ref(false)
const syncJob = ref<API.RagSyncJob | null>(null)
let syncTimer: number | undefined
const form = ref({ title: '', source: '', text: '' })

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
    loadDocuments()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '审核失败')
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
        loadDocuments()
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
    loadDocuments()
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
    loadDocuments()
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

onMounted(loadDocuments)
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
