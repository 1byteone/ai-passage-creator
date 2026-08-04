<template>
  <div id="workspaceDetailPage">
    <header class="page-heading">
      <div>
        <nav class="breadcrumb">
          <RouterLink to="/workspace">协作空间</RouterLink>
          <span>/</span>
          <span class="current">{{ workspace?.name || '空间详情' }}</span>
        </nav>
        <span class="page-kicker">团队协作</span>
        <h1>{{ workspace?.name || '空间详情' }}</h1>
        <p>{{ workspace?.description || '暂无描述' }}</p>
      </div>
      <div class="heading-actions">
        <a-button :loading="detailLoading" @click="loadDetail">
          <template #icon><ReloadOutlined /></template>刷新
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

    <div v-if="detailLoading" class="page-state">
      <a-skeleton active :paragraph="{ rows: 8 }" />
    </div>

    <a-result
      v-else-if="loadError"
      class="page-state"
      status="error"
      title="空间暂时无法加载"
      :sub-title="loadError"
    >
      <template #extra>
        <a-button type="primary" :loading="detailLoading" @click="loadDetail">重新加载</a-button>
        <RouterLink to="/workspace"><a-button>返回空间列表</a-button></RouterLink>
      </template>
    </a-result>

    <template v-else-if="workspace">
      <a-alert
        v-if="isArchived"
        type="warning"
        show-icon
        message="该空间已归档，仅可查看，不可修改。"
        class="archived-banner"
      />

      <!-- 空间信息 -->
      <section class="panel" aria-labelledby="info-title">
        <div class="panel-heading">
          <div>
            <span class="section-label">空间信息</span>
            <h2 id="info-title">基本信息</h2>
          </div>
          <div class="panel-actions">
            <a-tag :color="isArchived ? 'default' : 'green'">
              {{ isArchived ? '已归档' : '使用中' }}
            </a-tag>
            <a-tag>{{ memberCount }} 名成员</a-tag>
          </div>
        </div>

        <dl class="info-facts">
          <div>
            <dt>空间 ID</dt>
            <dd>{{ workspace.id }}</dd>
          </div>
          <div>
            <dt>创建时间</dt>
            <dd>{{ formatDateTime(workspace.createTime) }}</dd>
          </div>
          <div>
            <dt>最后更新</dt>
            <dd>{{ formatDateTime(workspace.updateTime) }}</dd>
          </div>
        </dl>

        <div class="info-actions">
          <a-button v-if="canManage" :disabled="isArchived" @click="openRenameModal">
            <template #icon><EditOutlined /></template>编辑信息
          </a-button>
          <a-popconfirm
            v-if="isOwner && !isArchived"
            title="确定归档此空间？"
            description="归档后空间转为只读，成员将无法修改。"
            ok-text="归档"
            cancel-text="取消"
            ok-type="danger"
            @confirm="doArchive"
          >
            <a-button danger :loading="archiving">归档空间</a-button>
          </a-popconfirm>
          <a-popconfirm
            v-if="isOwner && isArchived"
            title="确定恢复此空间？"
            description="恢复后空间重新可用，成员可继续协作。"
            ok-text="恢复"
            cancel-text="取消"
            @confirm="doUnarchive"
          >
            <a-button :loading="unarchiving">恢复空间</a-button>
          </a-popconfirm>
        </div>
      </section>

      <!-- 成员管理 -->
      <section class="panel" aria-labelledby="members-title">
        <div class="panel-heading">
          <div>
            <span class="section-label">成员</span>
            <h2 id="members-title">空间成员</h2>
          </div>
          <div class="panel-actions">
            <a-button
              v-if="canManage"
              type="primary"
              size="small"
              :disabled="isArchived"
              @click="openAddMemberModal"
            >
              <template #icon><UserAddOutlined /></template>添加成员
            </a-button>
          </div>
        </div>

        <a-skeleton v-if="membersLoading" active :paragraph="{ rows: 3 }" />

        <a-empty v-else-if="membersError && !members.length" description="成员加载失败">
          <a-button @click="loadMembers">重试</a-button>
        </a-empty>

        <a-empty v-else-if="!members.length" description="暂无成员" />

        <ul v-else class="member-list">
          <li v-for="member in members" :key="member.userId" class="member-item">
            <a-avatar :src="member.userAvatar || undefined" :size="36" class="member-avatar">
              {{ memberName(member).slice(0, 1) }}
            </a-avatar>
            <div class="member-info">
              <span class="member-name">{{ memberName(member) }}</span>
              <span class="member-role">{{ roleLabel(member.role) }}</span>
            </div>
            <a-popconfirm
              v-if="canManage && !isSelf(member) && member.role !== 'owner'"
              :title="`移除成员 ${member.userId}？`"
              description="移除后该成员将无法访问此空间。"
              ok-text="移除"
              cancel-text="取消"
              ok-type="danger"
              @confirm="doRemoveMember(member)"
            >
              <a-button type="link" danger size="small" :disabled="isArchived || removingMember">移除</a-button>
            </a-popconfirm>
            <span v-else-if="member.role === 'owner'" class="owner-label">创建者</span>
          </li>
        </ul>
      </section>
    </template>

    <!-- 编辑信息弹窗 -->
    <a-modal
      v-model:open="renameModalOpen"
      title="编辑空间信息"
      :confirm-loading="savingInfo"
      ok-text="保存"
      cancel-text="取消"
      @ok="doUpdate"
    >
      <a-form ref="renameFormRef" layout="vertical" :model="editForm">
        <a-form-item
          label="空间名称"
          name="name"
          :rules="[{ type: 'string' as const, required: true, message: '请输入空间名称' }, { type: 'string' as const, max: 128, message: '名称最长 128 字符' }]"
        >
          <a-input v-model:value="editForm.name" :maxlength="128" show-count />
        </a-form-item>
        <a-form-item label="空间描述" name="description">
          <a-textarea v-model:value="editForm.description" :maxlength="512" :rows="3" show-count />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 添加成员弹窗 -->
    <a-modal
      v-model:open="addMemberModalOpen"
      title="添加成员"
      :confirm-loading="addingMember"
      ok-text="添加"
      cancel-text="取消"
      @ok="doAddMember"
    >
      <a-form layout="vertical">
        <a-form-item label="用户 ID">
          <a-input-number
            v-model:value="addMemberUserId"
            :min="1"
            :max="2147483647"
            style="width: 100%"
            placeholder="输入用户 ID"
            aria-label="成员用户 ID"
          />
        </a-form-item>
        <a-form-item label="角色">
          <a-select v-model:value="addMemberRole" :options="memberRoleOptions" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  Alert as AAlert,
  Avatar as AAvatar,
  Empty as AEmpty,
  Form as AForm,
  FormItem as AFormItem,
  InputNumber as AInputNumber,
  Modal as AModal,
  Popconfirm as APopconfirm,
  Result as AResult,
  Select as ASelect,
  Skeleton as ASkeleton,
  Tag as ATag,
  message,
} from 'ant-design-vue'
import { EditOutlined, ReloadOutlined, UserAddOutlined } from '@ant-design/icons-vue'
import {
  getWorkspace,
  updateWorkspace,
  archiveWorkspace,
  unarchiveWorkspace,
  addWorkspaceMember,
  removeWorkspaceMember,
  listWorkspaceMembers,
} from '@/api/workspaceController'
import { useLoginUserStore } from '@/stores/loginUser'
import { formatDateTime } from '@/utils/date'
import type { OperationNotice } from '@/types/operationNotice'

const route = useRoute()
const loginUserStore = useLoginUserStore()

const workspaceId = computed(() => Number(route.params.id) || 0)

// ── 数据 ──

const workspace = ref<API.Workspace | null>(null)
const members = ref<API.WorkspaceMember[]>([])
const detailLoading = ref(false)
const membersLoading = ref(false)
const loadError = ref('')
const membersError = ref('')

const operationNotice = ref<OperationNotice | null>(null)

const memberCount = computed(() => workspace.value?.memberCount ?? members.value.length)

const myUserId = computed(() => String(loginUserStore.loginUser.id))

const isOwner = computed(() => String(workspace.value?.ownerId) === myUserId.value)

const myRole = computed(() => {
  const id = loginUserStore.loginUser.id
  return members.value.find((m) => String(m.userId) === String(id))?.role ?? ''
})

// 管理员：owner 或 admin 角色；成员加载失败时用 ownerId 兜底，避免权限按钮丢失
const canManage = computed(() => {
  if (isOwner.value) return true
  const role = myRole.value
  return role === 'admin'
})

const isArchived = computed(() => workspace.value?.status === 'ARCHIVED')

const memberRoleOptions = [
  { label: '成员', value: 'member' },
  { label: '管理员', value: 'admin' },
  { label: '查看者', value: 'viewer' },
]

const ROLE_LABELS: Record<string, string> = {
  owner: '创建者',
  admin: '管理员',
  member: '成员',
  viewer: '查看者',
}

const roleLabel = (role?: string) => ROLE_LABELS[role ?? ''] || role || '—'

// 优先展示真实用户名；后端未 join 时兜底为用户 ID
const memberName = (member: API.WorkspaceMember) =>
  member.userName?.trim() || `用户 #${member.userId}`

const isSelf = (member: API.WorkspaceMember) =>
  String(member.userId) === String(loginUserStore.loginUser.id)


const loadDetail = async () => {
  if (!workspaceId.value) {
    loadError.value = '无效的空间 ID，链接可能已损坏。'
    return
  }
  detailLoading.value = true
  loadError.value = ''
  try {
    const res = await getWorkspace(workspaceId.value)
    if (res.data.code !== 0) throw new Error(res.data.message || '加载失败')
    workspace.value = res.data.data ?? null
    if (workspace.value) void loadMembers()
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '网络或服务暂时不可用'
  } finally {
    detailLoading.value = false
  }
}

const loadMembers = async () => {
  if (!workspaceId.value) return
  membersLoading.value = true
  membersError.value = ''
  try {
    const res = await listWorkspaceMembers(workspaceId.value)
    if (res.data.code === 0) {
      members.value = res.data.data ?? []
    } else {
      throw new Error(res.data.message || '成员加载失败')
    }
  } catch (e) {
    membersError.value = e instanceof Error ? e.message : '成员加载失败'
  } finally {
    membersLoading.value = false
  }
}

watch(
  () => route.params.id,
  () => {
    // 路由参数变化时组件可能被复用，需重置并重新加载
    workspace.value = null
    members.value = []
    membersError.value = ''
    void loadDetail()
  },
)

// ── 编辑信息 ──

const renameModalOpen = ref(false)
const savingInfo = ref(false)
const renameFormRef = ref()
const editForm = reactive({ name: '', description: '' })

const openRenameModal = () => {
  editForm.name = workspace.value?.name ?? ''
  editForm.description = workspace.value?.description ?? ''
  renameModalOpen.value = true
}

const doUpdate = async () => {
  try { await renameFormRef.value?.validate() } catch { return }
  if (savingInfo.value) return
  savingInfo.value = true
  try {
    const res = await updateWorkspace(workspaceId.value, {
      name: editForm.name.trim(),
      description: editForm.description.trim() || undefined,
    })
    if (res.data.code !== 0) throw new Error(res.data.message || '保存失败')
    renameModalOpen.value = false
    operationNotice.value = { type: 'success', message: '空间信息已更新' }
    await loadDetail()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败，请稍后重试')
  } finally {
    savingInfo.value = false
  }
}

// ── 归档 ──

const archiving = ref(false)

const doArchive = async () => {
  if (archiving.value) return
  archiving.value = true
  try {
    const res = await archiveWorkspace(workspaceId.value)
    if (res.data.code !== 0) throw new Error(res.data.message || '归档失败')
    operationNotice.value = { type: 'success', message: '空间已归档' }
    await loadDetail()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '归档失败')
  } finally {
    archiving.value = false
  }
}

// ── 恢复归档 ──

const unarchiving = ref(false)

const doUnarchive = async () => {
  if (unarchiving.value) return
  unarchiving.value = true
  try {
    const res = await unarchiveWorkspace(workspaceId.value)
    if (res.data.code !== 0) throw new Error(res.data.message || '恢复失败')
    operationNotice.value = { type: 'success', message: '空间已恢复' }
    await loadDetail()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '恢复失败')
  } finally {
    unarchiving.value = false
  }
}

// ── 添加成员 ──

const addMemberModalOpen = ref(false)
const addingMember = ref(false)
const addMemberUserId = ref<number | undefined>(undefined)
const addMemberRole = ref('member')

const openAddMemberModal = () => {
  addMemberUserId.value = undefined
  addMemberRole.value = 'member'
  addMemberModalOpen.value = true
}

const doAddMember = async () => {
  if (!addMemberUserId.value) {
    message.error('请输入用户 ID')
    return
  }
  if (addingMember.value) return
  addingMember.value = true
  try {
    const res = await addWorkspaceMember(workspaceId.value, {
      userId: addMemberUserId.value,
      role: addMemberRole.value,
    })
    if (res.data.code !== 0) throw new Error(res.data.message || '添加失败')
    addMemberModalOpen.value = false
    operationNotice.value = { type: 'success', message: '成员已添加' }
    await loadDetail()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '添加失败')
  } finally {
    addingMember.value = false
  }
}

// ── 移除成员 ──

const removingMember = ref(false)

const doRemoveMember = async (member: API.WorkspaceMember) => {
  if (removingMember.value) return
  if (!member.userId) return
  removingMember.value = true
  try {
    const res = await removeWorkspaceMember(workspaceId.value, member.userId)
    if (res.data.code !== 0) throw new Error(res.data.message || '移除失败')
    operationNotice.value = { type: 'success', message: '成员已移除' }
    await loadDetail()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '移除失败')
  } finally {
    removingMember.value = false
  }
}

onMounted(() => {
  void loadDetail()
})
</script>

<style scoped lang="scss">
#workspaceDetailPage {
  min-height: calc(100vh - 64px);
  padding: 40px 24px 72px;
  background: var(--surface-page);
  color: var(--text-body);
}

.page-heading,
.page-feedback,
.page-state,
.archived-banner,
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

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
  color: var(--text-muted);
  font-size: 12px;

  a {
    color: var(--text-subtle);
    text-decoration: none;

    &:hover {
      color: var(--color-primary);
    }
  }

  .current {
    color: var(--text-strong);
    font-weight: 600;
  }
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

.page-feedback,
.archived-banner {
  margin-bottom: 20px;
}

.page-state {
  min-height: 400px;
  padding-top: 24px;
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

.panel-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.info-facts {
  margin: 0 0 20px;

  div {
    display: flex;
    justify-content: space-between;
    gap: 24px;
    padding: 10px 0;
    border-bottom: 1px solid var(--border-subtle);
  }

  dt,
  dd {
    margin: 0;
    font-size: 13px;
  }

  dt {
    color: var(--text-muted);
  }

  dd {
    color: var(--text-strong);
    font-family: var(--font-mono);
  }
}

.info-actions {
  display: flex;
  gap: 8px;
}

.member-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.member-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--border-subtle);

  &:last-child {
    border-bottom: 0;
  }
}

.member-avatar {
  background: var(--color-secondary);
  color: var(--text-inverse);
  font-weight: 700;
  flex-shrink: 0;
}

.member-info {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

.member-name {
  color: var(--text-strong);
  font-size: 14px;
  font-weight: 600;
}

.member-role {
  color: var(--text-muted);
  font-size: 12px;
}

.owner-label {
  color: var(--color-primary-dark);
  font-size: 12px;
  font-weight: 600;
}

@media (max-width: 768px) {
  #workspaceDetailPage {
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

  .info-actions {
    flex-wrap: wrap;
  }
}

@media (prefers-reduced-motion: reduce) {
  #workspaceDetailPage :deep(*) {
    scroll-behavior: auto !important;
    transition-duration: 0.01ms !important;
  }
}
</style>