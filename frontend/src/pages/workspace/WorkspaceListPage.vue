<template>
  <div id="workspaceListPage">
    <header class="page-heading">
      <div>
        <span class="page-kicker">团队协作</span>
        <h1>协作空间</h1>
        <p>创建或加入团队空间，与成员共享文章与内容资产。</p>
      </div>
      <div class="heading-actions">
        <a-button type="primary" @click="openCreateModal">
          <template #icon><PlusOutlined /></template>创建空间
        </a-button>
      </div>
    </header>

    <div v-if="operationNotice" class="page-feedback" aria-live="polite">
      <a-alert
        :type="operationNotice.type"
        show-icon
        closable
        :message="operationNotice.message"
        :description="operationNotice.description"
        @close="operationNotice = null"
      />
    </div>

    <section class="workspace-section" aria-labelledby="workspace-list-title">
      <div class="list-heading">
        <div>
          <span class="section-label">我的空间</span>
          <h2 id="workspace-list-title">{{ workspaces.length ? '全部空间' : '暂无空间' }}</h2>
        </div>
        <span v-if="workspaces.length" class="count">{{ workspaces.length }} 个</span>
      </div>

      <div v-if="loading" class="loading-state" aria-live="polite">
        <a-skeleton active :paragraph="{ rows: 5 }" />
      </div>

      <a-result
        v-else-if="loadError"
        class="result-state"
        status="error"
        title="暂时无法加载空间"
        :sub-title="loadError"
      >
        <template #extra>
          <a-button type="primary" :loading="loading" @click="loadWorkspaces">重新加载</a-button>
        </template>
      </a-result>

      <a-empty v-else-if="!workspaces.length" class="empty-state" description="还没有加入任何协作空间">
        <span class="empty-hint">创建空间后，可邀请成员共同协作。</span>
        <a-button type="primary" @click="openCreateModal">创建第一个空间</a-button>
      </a-empty>

      <div v-else class="workspace-grid">
        <RouterLink
          v-for="ws in workspaces"
          :key="ws.id"
          :to="`/workspace/${ws.id}`"
          class="workspace-card"
        >
          <div class="workspace-head">
            <div class="workspace-icon">{{ ws.name?.slice(0, 1) || '空' }}</div>
            <a-tag v-if="ws.status === 'ARCHIVED'" color="default">已归档</a-tag>
          </div>
          <h3 class="workspace-name">{{ ws.name || '未命名空间' }}</h3>
          <p class="workspace-desc">{{ ws.description || '暂无描述' }}</p>
          <div class="workspace-meta">
            <span><TeamOutlined /> {{ ws.memberCount ?? 0 }} 名成员</span>
            <span class="workspace-time">{{ formatDateTime(ws.createTime, 'YYYY-MM-DD') }}</span>
          </div>
        </RouterLink>
      </div>
    </section>

    <!-- 创建空间弹窗 -->
    <a-modal
      v-model:open="createModalOpen"
      title="创建协作空间"
      :confirm-loading="creating"
      ok-text="创建"
      cancel-text="取消"
      @ok="doCreate"
      @cancel="resetCreateForm"
    >
      <a-form ref="createFormRef" layout="vertical" :model="createForm">
        <a-form-item
          label="空间名称"
          name="name"
          :rules="[{ type: 'string' as const, required: true, message: '请输入空间名称' }, { type: 'string' as const, max: 128, message: '名称最长 128 字符' }]"
        >
          <a-input v-model:value="createForm.name" :maxlength="128" size="large" show-count placeholder="例如：内容编辑部" />
        </a-form-item>
        <a-form-item label="空间描述" name="description">
          <a-textarea v-model:value="createForm.description" :maxlength="512" :rows="3" size="large" show-count placeholder="这个空间用来做什么？（可选）" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script lang="ts" setup>
import { onMounted, reactive, ref } from 'vue'
import {
  Alert as AAlert,
  Empty as AEmpty,
  Form as AForm,
  FormItem as AFormItem,
  Modal as AModal,
  Result as AResult,
  Skeleton as ASkeleton,
  Tag as ATag,
  message,
} from 'ant-design-vue'
import { PlusOutlined, TeamOutlined } from '@ant-design/icons-vue'
import { createWorkspace, listMyWorkspaces } from '@/api/workspaceController'
import { formatDateTime } from '@/utils/date'
import type { OperationNotice } from '@/types/operationNotice'

// ── 列表 ──

const workspaces = ref<API.Workspace[]>([])
const loading = ref(false)
const loadError = ref('')

const operationNotice = ref<OperationNotice | null>(null)


const loadWorkspaces = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const res = await listMyWorkspaces()
    if (res.data.code !== 0) throw new Error(res.data.message || '加载失败')
    workspaces.value = res.data.data ?? []
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '网络或服务暂时不可用'
  } finally {
    loading.value = false
  }
}

// ── 创建 ──

const createModalOpen = ref(false)
const creating = ref(false)
const createFormRef = ref()

const createForm = reactive({
  name: '',
  description: '',
})

const openCreateModal = () => {
  resetCreateForm()
  createModalOpen.value = true
}

const resetCreateForm = () => {
  createForm.name = ''
  createForm.description = ''
  createFormRef.value?.clearValidate()
}

const doCreate = async () => {
  try { await createFormRef.value?.validate() } catch { return }
  if (creating.value) return
  creating.value = true
  try {
    const res = await createWorkspace({
      name: createForm.name.trim(),
      description: createForm.description.trim() || undefined,
    })
    if (res.data.code !== 0) throw new Error(res.data.message || '创建失败')
    createModalOpen.value = false
    operationNotice.value = {
      type: 'success',
      message: '空间已创建',
      description: `「${createForm.name.trim()}」创建成功，可以开始邀请成员。`,
    }
    await loadWorkspaces()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '创建失败，请稍后重试')
  } finally {
    creating.value = false
  }
}

onMounted(() => {
  void loadWorkspaces()
})
</script>

<style scoped lang="scss">
#workspaceListPage {
  min-height: calc(100vh - 64px);
  padding: 40px 24px 72px;
  background: var(--surface-page);
  color: var(--text-body);
}

.page-heading,
.page-feedback,
.workspace-section {
  width: min(100%, 1120px);
  margin-right: auto;
  margin-left: auto;
}







.workspace-section {
  overflow: hidden;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  background: var(--surface-panel);
}

.list-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 24px 24px 18px;
  border-bottom: 1px solid var(--border-default);

  h2 {
    margin: 0;
    color: var(--text-strong);
    font-family: var(--font-heading);
    font-size: 20px;
    font-weight: 650;
  }
}

.count {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 12px;
}

.loading-state,
.empty-state {
  min-height: 320px;
  padding: 48px 32px;
}

.result-state {
  min-height: 320px;
}

.empty-hint {
  display: block;
  margin-top: 8px;
  margin-bottom: 12px;
  color: var(--text-muted);
  font-size: 12px;
}

.workspace-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
  padding: 24px;
}

.workspace-card {
  display: flex;
  flex-direction: column;
  padding: 20px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--surface-page);
  color: inherit;
  text-decoration: none;
  transition: box-shadow var(--transition-fast), border-color var(--transition-fast);

  &:hover {
    border-color: var(--color-primary);
    box-shadow: var(--shadow-md);
  }
}

.workspace-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.workspace-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md);
  background: var(--gradient-primary);
  color: #fff;
  font-size: 18px;
  font-weight: 700;
}

.workspace-name {
  margin: 0 0 6px;
  color: var(--text-strong);
  font-size: 16px;
  font-weight: 650;
}

.workspace-desc {
  display: -webkit-box;
  overflow: hidden;
  margin: 0 0 14px;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.workspace-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 14px;
  border-top: 1px solid var(--border-subtle);
  color: var(--text-muted);
  font-size: 12px;

  span {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }
}

.workspace-time {
  font-family: var(--font-mono);
  font-size: 11px;
}

@media (max-width: 768px) {
  #workspaceListPage {
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

  .heading-actions {
    width: 100%;
  }

  .workspace-grid {
    grid-template-columns: 1fr;
    padding: 16px;
  }
}

@media (prefers-reduced-motion: reduce) {
  #workspaceListPage :deep(*) {
    scroll-behavior: auto !important;
    transition-duration: 0.01ms !important;
  }
}
</style>