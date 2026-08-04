<template>
  <div id="userManagePage">
    <header class="page-heading">
      <div>
        <span class="page-kicker">后台管理</span>
        <h1>用户管理</h1>
        <p>查找账号、确认权限，并安全维护用户资料与角色。</p>
      </div>
      <div class="heading-actions">
        <span class="user-count">{{ total }} 个用户</span>
        <a-button :loading="loading" @click="refreshData">
          <template #icon>
            <ReloadOutlined />
          </template>
          刷新数据
        </a-button>
      </div>
    </header>

    <section class="filter-panel" aria-labelledby="filter-title">
      <div class="filter-heading">
        <div>
          <span class="section-label">筛选条件</span>
          <h2 id="filter-title">查找用户</h2>
        </div>
        <span v-if="hasActiveFilters" class="filter-summary">{{ activeFilterSummary }}</span>
      </div>

      <a-form
        name="user-filter"
        class="filter-form"
        layout="vertical"
        :model="searchParams"
        @finish="doSearch"
      >
        <a-form-item label="账号" name="userAccount">
          <a-input
            v-model:value="searchParams.userAccount"
            allow-clear
            autocomplete="off"
            placeholder="输入账号关键字"
          />
        </a-form-item>
        <a-form-item label="用户名" name="userName">
          <a-input
            v-model:value="searchParams.userName"
            allow-clear
            autocomplete="off"
            placeholder="输入用户名关键字"
          />
        </a-form-item>
        <a-form-item label="角色" name="userRole">
          <a-select
            v-model:value="searchParams.userRole"
            allow-clear
            placeholder="全部角色"
            :options="roleOptions"
          />
        </a-form-item>
        <div class="filter-actions">
          <a-button type="primary" html-type="submit" :loading="loading">
            <template #icon>
              <SearchOutlined />
            </template>
            应用筛选
          </a-button>
          <a-button :disabled="!hasActiveFilters || loading" @click="resetSearch">清除</a-button>
        </div>
      </a-form>
    </section>

    <div v-if="loadError && data.length" class="page-feedback" aria-live="polite">
      <a-alert
        type="warning"
        show-icon
        closable
        message="刷新失败，当前仍显示上一次数据"
        :description="loadError"
        @close="loadError = ''"
      />
    </div>

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

    <section class="user-section" aria-labelledby="user-list-title">
      <div class="list-heading">
        <div>
          <span class="section-label">账号目录</span>
          <h2 id="user-list-title">{{ listTitle }}</h2>
        </div>
        <span class="page-range">{{ pageRange }}</span>
      </div>

      <div v-if="initialLoading" class="loading-state" aria-live="polite">
        <a-skeleton active :paragraph="{ rows: 6 }" />
      </div>

      <a-result
        v-else-if="initialError"
        class="result-state"
        status="error"
        title="暂时无法加载用户"
        :sub-title="loadError"
      >
        <template #extra>
          <a-button type="primary" :loading="loading" @click="refreshData">重新加载</a-button>
        </template>
      </a-result>

      <a-empty
        v-else-if="!data.length"
        class="empty-state"
        :description="hasActiveFilters ? '没有符合当前条件的用户' : '当前没有用户数据'"
      >
        <a-button v-if="hasActiveFilters" @click="resetSearch">清除筛选</a-button>
      </a-empty>

      <template v-else>
        <a-table
          class="desktop-table"
          :columns="columns"
          :data-source="data"
          :loading="loading"
          :pagination="false"
          row-key="id"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'identity'">
              <div class="identity-cell">
                <a-avatar :src="getAvatar(record.userAvatar, displayName(record))" :size="40">
                  {{ avatarFallback(record) }}
                </a-avatar>
                <div>
                  <div class="identity-name">
                    <span>{{ displayName(record) }}</span>
                    <span v-if="isCurrentUser(record)" class="self-label">当前账号</span>
                  </div>
                  <span class="identity-account">{{ record.userAccount || '未设置账号' }}</span>
                </div>
              </div>
            </template>
            <template v-else-if="column.key === 'role'">
              <span class="role-status" :class="`role-status--${safeRole(record.userRole)}`">
                {{ roleLabel(record.userRole) }}
              </span>
            </template>
            <template v-else-if="column.key === 'profile'">
              <span class="profile-text">{{ record.userProfile || '未填写简介' }}</span>
            </template>
            <template v-else-if="column.key === 'created'">
              <time class="time-text" :datetime="record.createTime">
                {{ formatDateTime(record.createTime, undefined, '时间未知') }}
              </time>
            </template>
            <template v-else-if="column.key === 'action'">
              <div class="row-actions">
                <a-button type="link" @click="openUserDrawer(record)">查看与编辑</a-button>
                <a-popconfirm
                  :disabled="isCurrentUser(record)"
                  :title="`确定删除账号 ${record.userAccount || record.id}？`"
                  description="删除后无法恢复。"
                  ok-text="删除"
                  cancel-text="取消"
                  ok-type="danger"
                  @confirm="doDelete(record)"
                >
                  <a-button type="link" danger :disabled="isCurrentUser(record)">删除</a-button>
                </a-popconfirm>
              </div>
            </template>
          </template>
        </a-table>

        <div class="mobile-user-list">
          <article v-for="record in data" :key="record.id" class="mobile-user-item">
            <div class="mobile-user-head">
              <div class="identity-cell">
                <a-avatar :src="getAvatar(record.userAvatar, displayName(record))" :size="40">
                  {{ avatarFallback(record) }}
                </a-avatar>
                <div>
                  <div class="identity-name">
                    <span>{{ displayName(record) }}</span>
                    <span v-if="isCurrentUser(record)" class="self-label">当前账号</span>
                  </div>
                  <span class="identity-account">{{ record.userAccount || '未设置账号' }}</span>
                </div>
              </div>
              <span class="role-status" :class="`role-status--${safeRole(record.userRole)}`">
                {{ roleLabel(record.userRole) }}
              </span>
            </div>
            <dl class="mobile-user-facts">
              <div>
                <dt>用户简介</dt>
                <dd>{{ record.userProfile || '未填写简介' }}</dd>
              </div>
              <div>
                <dt>创建时间</dt>
                <dd>{{ formatDateTime(record.createTime, undefined, '时间未知') }}</dd>
              </div>
            </dl>
            <a-button class="mobile-detail-button" @click="openUserDrawer(record)">
              查看与编辑
            </a-button>
          </article>
        </div>

        <div class="pagination-bar">
          <span>共 {{ total }} 条记录</span>
          <a-pagination
            v-page-size-label
            :current="searchParams.current"
            :page-size="searchParams.pageSize"
            :total="total"
            :show-size-changer="true"
            :page-size-options="['10', '20', '50']"
            :show-less-items="true"
            @change="doPageChange"
            @show-size-change="doPageSizeChange"
          />
        </div>
      </template>
    </section>

    <a-drawer
      v-model:open="drawerOpen"
      class="user-drawer"
      title="用户详情"
      placement="right"
      width="min(440px, 100vw)"
      :destroy-on-close="true"
    >
      <template v-if="selectedUser">
        <div class="drawer-identity">
          <a-avatar :src="getAvatar(selectedUser.userAvatar, displayName(selectedUser))" :size="52">
            {{ avatarFallback(selectedUser) }}
          </a-avatar>
          <div>
            <h2>{{ displayName(selectedUser) }}</h2>
            <p>{{ selectedUser.userAccount }}</p>
          </div>
          <span
            class="role-status"
            :class="`role-status--${safeRole(selectedUser.userRole)}`"
          >
            {{ roleLabel(selectedUser.userRole) }}
          </span>
        </div>

        <dl class="drawer-facts">
          <div>
            <dt>用户 ID</dt>
            <dd>{{ selectedUser.id }}</dd>
          </div>
          <div>
            <dt>创建时间</dt>
            <dd>{{ formatDateTime(selectedUser.createTime, undefined, '时间未知') }}</dd>
          </div>
        </dl>

        <a-alert
          v-if="isCurrentUser(selectedUser)"
          class="drawer-alert"
          type="info"
          show-icon
          message="这是当前登录账号"
          description="可以更新资料，但不能移除自己的管理员权限或删除当前账号。"
        />

        <a-alert
          v-if="drawerError"
          class="drawer-alert"
          type="error"
          show-icon
          closable
          message="保存失败"
          :description="drawerError"
          @close="drawerError = ''"
        />

        <a-form
          name="user-edit"
          class="edit-form"
          layout="vertical"
          :model="editForm"
          @finish="saveUser"
        >
          <a-form-item label="用户名" name="userName">
            <a-input
              v-model:value="editForm.userName"
              :maxlength="50"
              show-count
              placeholder="输入用户显示名称"
            />
          </a-form-item>
          <a-form-item label="用户角色" name="userRole">
            <a-select
              v-model:value="editForm.userRole"
              :disabled="isCurrentUser(selectedUser)"
              :options="roleOptions"
            />
          </a-form-item>
          <a-form-item label="用户简介" name="userProfile">
            <a-textarea
              v-model:value="editForm.userProfile"
              :maxlength="200"
              :rows="4"
              show-count
              placeholder="填写用户简介"
            />
          </a-form-item>

          <div class="drawer-actions">
            <a-button @click="drawerOpen = false">取消</a-button>
            <a-button type="primary" html-type="submit" :loading="savingUser">
              保存修改
            </a-button>
          </div>
        </a-form>

        <div class="danger-zone">
          <div>
            <h3>删除账号</h3>
            <p>该操作不可恢复，当前登录账号不可删除。</p>
          </div>
          <a-popconfirm
            :disabled="isCurrentUser(selectedUser)"
            :title="`确定删除账号 ${selectedUser.userAccount || selectedUser.id}？`"
            description="删除后无法恢复。"
            ok-text="删除"
            cancel-text="取消"
            ok-type="danger"
            @confirm="doDelete(selectedUser)"
          >
            <a-button danger :disabled="isCurrentUser(selectedUser)" :loading="deletingUser">
              删除账号
            </a-button>
          </a-popconfirm>
        </div>
      </template>
    </a-drawer>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  Alert as AAlert,
  Drawer as ADrawer,
  Form as AForm,
  FormItem as AFormItem,
  Pagination as APagination,
  Popconfirm as APopconfirm,
  Select as ASelect,
  Table as ATable,
  type TableProps,
} from 'ant-design-vue'
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { deleteUser, listUserVoByPage, updateUser } from '@/api/userController'
import { useLoginUserStore } from '@/stores/loginUser'
import { formatDateTime } from '@/utils/date'
import { getAvatar } from '@/utils/avatar'
import type { OperationNotice } from '@/types/operationNotice'

const labelPageSizeControl = (element: HTMLElement) => {
  element
    .querySelector<HTMLElement>('.ant-pagination-options-size-changer [role="combobox"]')
    ?.setAttribute('aria-label', '每页显示用户数量')
}

const vPageSizeLabel = {
  mounted: labelPageSizeControl,
  updated: labelPageSizeControl,
}

const columns: TableProps<API.UserVO>['columns'] = [
  {
    title: '用户',
    key: 'identity',
    width: 250,
  },
  {
    title: '角色',
    key: 'role',
    width: 110,
  },
  {
    title: '简介',
    key: 'profile',
  },
  {
    title: '创建时间',
    key: 'created',
    width: 150,
  },
  {
    title: '操作',
    key: 'action',
    width: 160,
    align: 'right',
  },
]

const roleOptions = [
  { label: '普通用户', value: 'user' },
  { label: '永久会员', value: 'vip' },
  { label: '管理员', value: 'admin' },
]

const loginUserStore = useLoginUserStore()
const data = ref<API.UserVO[]>([])
const total = ref(0)
const loading = ref(false)
const loadedOnce = ref(false)
const loadError = ref('')
const operationNotice = ref<OperationNotice | null>(null)
const drawerOpen = ref(false)
const selectedUser = ref<API.UserVO | null>(null)
const drawerError = ref('')
const savingUser = ref(false)
const deletingUser = ref(false)

const searchParams = reactive<API.UserQueryRequest>({
  current: 1,
  pageSize: 10,
  userAccount: '',
  userName: '',
  userRole: undefined,
})

const editForm = reactive<API.UserUpdateRequest>({
  id: undefined,
  userName: '',
  userProfile: '',
  userRole: 'user',
})

const hasActiveFilters = computed(
  () =>
    Boolean(searchParams.userAccount?.trim()) ||
    Boolean(searchParams.userName?.trim()) ||
    Boolean(searchParams.userRole),
)

const activeFilterSummary = computed(() => {
  const count = [
    searchParams.userAccount?.trim(),
    searchParams.userName?.trim(),
    searchParams.userRole,
  ].filter(Boolean).length
  return `已应用 ${count} 项条件`
})

const initialLoading = computed(() => loading.value && !loadedOnce.value)
const initialError = computed(
  () => loadedOnce.value && Boolean(loadError.value) && data.value.length === 0,
)
const listTitle = computed(() => (hasActiveFilters.value ? '筛选结果' : '全部用户'))

const pageRange = computed(() => {
  if (!total.value || !data.value.length) return '暂无记录'
  const current = searchParams.current ?? 1
  const pageSize = searchParams.pageSize ?? 10
  const start = (current - 1) * pageSize + 1
  const end = Math.min(start + data.value.length - 1, total.value)
  return `${start}–${end} / ${total.value}`
})

const roleLabel = (role?: string) => {
  const option = roleOptions.find((item) => item.value === role)
  return option?.label ?? '未知角色'
}

const safeRole = (role?: string) => {
  return roleOptions.some((item) => item.value === role) ? role : 'unknown'
}

const displayName = (user: API.UserVO) => {
  return user.userName?.trim() || user.userAccount?.trim() || '未命名用户'
}

const avatarFallback = (user: API.UserVO) => {
  return displayName(user).slice(0, 1).toUpperCase()
}

const isCurrentUser = (user: API.UserVO) => {
  return String(user.id) === String(loginUserStore.loginUser.id)
}

let fetchSeq = 0

const fetchData = async () => {
  loading.value = true
  loadError.value = ''
  const seq = ++fetchSeq

  try {
    const response = await listUserVoByPage({
      ...searchParams,
      userAccount: searchParams.userAccount?.trim() || undefined,
      userName: searchParams.userName?.trim() || undefined,
    })

    // 不是最新的请求，忽略响应
    if (seq !== fetchSeq) return

    if (response.data.code !== 0 || !response.data.data) {
      throw new Error(response.data.message || '用户数据返回异常')
    }

    data.value = response.data.data.records ?? []
    total.value = response.data.data.totalRow ?? 0
  } catch (error) {
    if (seq !== fetchSeq) return
    console.error('获取用户数据失败:', error)
    loadError.value = error instanceof Error ? error.message : '网络或服务暂时不可用，请稍后重试。'
  } finally {
    loading.value = false
    loadedOnce.value = true
  }
}

const refreshData = () => {
  operationNotice.value = null
  void fetchData()
}

const doSearch = () => {
  searchParams.current = 1
  operationNotice.value = null
  void fetchData()
}

const resetSearch = () => {
  searchParams.userAccount = ''
  searchParams.userName = ''
  searchParams.userRole = undefined
  searchParams.current = 1
  operationNotice.value = null
  void fetchData()
}

const doPageChange = (page: number) => {
  searchParams.current = page
  void fetchData()
}

const doPageSizeChange = (_current: number, size: number) => {
  searchParams.current = 1
  searchParams.pageSize = size
  void fetchData()
}

const openUserDrawer = (record: API.UserVO) => {
  selectedUser.value = record
  editForm.id = record.id
  editForm.userName = record.userName ?? ''
  editForm.userProfile = record.userProfile ?? ''
  editForm.userRole = record.userRole ?? 'user'
  drawerError.value = ''
  drawerOpen.value = true
}

const saveUser = async () => {
  if (!editForm.id) return

  savingUser.value = true
  drawerError.value = ''
  try {
    const response = await updateUser({
      id: editForm.id,
      userName: editForm.userName?.trim() || undefined,
      userProfile: editForm.userProfile?.trim() || undefined,
      userRole: editForm.userRole,
    })
    if (response.data.code !== 0 || response.data.data !== true) {
      throw new Error(response.data.message || '用户资料未能保存')
    }

    if (selectedUser.value && isCurrentUser(selectedUser.value)) {
      await loginUserStore.fetchLoginUser()
    }
    drawerOpen.value = false
    operationNotice.value = {
      type: 'success',
      message: '用户资料已更新',
      description: `${selectedUser.value?.userAccount || '该账号'} 的资料和角色已保存。`,
    }
    await fetchData()
  } catch (error) {
    console.error('更新用户失败:', error)
    drawerError.value = error instanceof Error ? error.message : '网络或服务暂时不可用，请稍后重试。'
  } finally {
    savingUser.value = false
  }
}

const doDelete = async (record: API.UserVO) => {
  if (!record.id || isCurrentUser(record)) return

  deletingUser.value = true
  operationNotice.value = null
  try {
    const response = await deleteUser({ id: record.id })
    if (response.data.code !== 0 || response.data.data !== true) {
      throw new Error(response.data.message || '用户未能删除')
    }

    if (selectedUser.value?.id === record.id) {
      drawerOpen.value = false
    }
    if (data.value.length === 1 && (searchParams.current ?? 1) > 1) {
      searchParams.current = (searchParams.current ?? 1) - 1
    }
    operationNotice.value = {
      type: 'success',
      message: '用户已删除',
      description: `账号 ${record.userAccount || record.id} 已从系统中移除。`,
    }
    await fetchData()
  } catch (error) {
    console.error('删除用户失败:', error)
    operationNotice.value = {
      type: 'error',
      message: '删除失败',
      description: error instanceof Error ? error.message : '网络或服务暂时不可用，请稍后重试。',
    }
  } finally {
    deletingUser.value = false
  }
}

onMounted(() => {
  void fetchData()
})
</script>

<style scoped lang="scss">
#userManagePage {
  min-height: calc(100vh - 64px);
  padding: 40px 24px 72px;
  background: var(--surface-page);
  color: var(--text-body);
}

.page-heading,
.filter-panel,
.page-feedback,
.user-section {
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
  gap: 16px;
}

.user-count,
.page-range {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 12px;
  white-space: nowrap;
}

.filter-panel {
  margin-bottom: 20px;
  padding: 24px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  background: var(--surface-panel);
}

.filter-heading,
.list-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
}

.filter-heading {
  margin-bottom: 18px;
}

.filter-heading h2,
.list-heading h2 {
  margin: 0;
  color: var(--text-strong);
  font-family: var(--font-heading);
  font-size: 20px;
  font-weight: 650;
}

.filter-summary {
  color: var(--state-info-text);
  font-size: 12px;
  font-weight: 600;
}

.filter-form {
  display: grid;
  grid-template-columns: 1fr 1fr 180px auto;
  align-items: end;
  gap: 16px;
}

.filter-form :deep(.ant-form-item) {
  margin-bottom: 0;
}

.filter-form :deep(.ant-form-item-label > label) {
  color: var(--text-subtle);
  font-size: 13px;
  font-weight: 600;
}

.filter-actions {
  display: flex;
  gap: 8px;
  padding-bottom: 1px;
}

.filter-actions :deep(.ant-btn) {
  min-height: var(--control-height-md);
}

.page-feedback {
  margin-bottom: 20px;
}

.user-section {
  overflow: hidden;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  background: var(--surface-panel);
}

.list-heading {
  padding: 24px 24px 18px;
  border-bottom: 1px solid var(--border-default);
}

.loading-state,
.empty-state {
  min-height: 360px;
  padding: 48px 32px;
}

.result-state {
  min-height: 360px;
}

.desktop-table :deep(.ant-table) {
  border-radius: 0;
}

.desktop-table :deep(.ant-table-thead > tr > th) {
  padding: 13px 16px;
  border-bottom: 1px solid var(--border-default);
  background: var(--surface-muted);
  color: var(--text-subtle);
  font-size: 12px;
  font-weight: 700;
}

.desktop-table :deep(.ant-table-tbody > tr > td) {
  padding: 16px;
  border-bottom: 1px solid var(--border-subtle);
}

.desktop-table :deep(.ant-table-tbody > tr:hover > td) {
  background: var(--surface-page);
}

.identity-cell {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 12px;
}

.identity-cell :deep(.ant-avatar) {
  flex: 0 0 auto;
  border: 1px solid var(--border-default);
  background: var(--color-secondary);
  color: var(--text-inverse);
  font-size: 14px;
  font-weight: 700;
}

.identity-name {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
  color: var(--text-strong);
  font-size: 14px;
  font-weight: 650;
}

.identity-name > span:first-child,
.identity-account {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.identity-account {
  display: block;
  max-width: 180px;
  margin-top: 3px;
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 11px;
}

.self-label {
  flex: 0 0 auto;
  color: var(--state-info-text);
  font-size: 11px;
  font-weight: 600;
}

.role-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--text-subtle);
  font-size: 12px;
  font-weight: 650;
  white-space: nowrap;
}

.role-status::before {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--text-disabled);
  content: '';
}

.role-status--admin {
  color: #6d28d9;
}

.role-status--admin::before {
  background: #8b5cf6;
}

.role-status--vip {
  color: var(--state-success-text);
}

.role-status--vip::before {
  background: var(--color-success);
}

.role-status--user {
  color: var(--state-info-text);
}

.role-status--user::before {
  background: var(--color-info);
}

.profile-text {
  display: -webkit-box;
  overflow: hidden;
  color: var(--text-subtle);
  font-size: 13px;
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.time-text {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 11px;
}

.row-actions {
  display: flex;
  justify-content: flex-end;
}

.row-actions :deep(.ant-btn) {
  padding-right: 6px;
  padding-left: 6px;
  font-size: 13px;
}

.mobile-user-list {
  display: none;
}

.pagination-bar {
  display: flex;
  min-height: 68px;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 14px 24px;
  border-top: 1px solid var(--border-default);
  color: var(--text-muted);
  font-size: 12px;
}

.drawer-identity {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--border-default);
}

.drawer-identity :deep(.ant-avatar) {
  background: var(--color-secondary);
  color: var(--text-inverse);
  font-weight: 700;
}

.drawer-identity h2 {
  margin: 0 0 3px;
  color: var(--text-strong);
  font-size: 18px;
  font-weight: 650;
}

.drawer-identity p {
  overflow: hidden;
  margin: 0;
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.drawer-facts {
  margin: 0 0 24px;
}

.drawer-facts div {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 12px 0;
  border-bottom: 1px solid var(--border-subtle);
}

.drawer-facts dt,
.drawer-facts dd {
  margin: 0;
  font-size: 12px;
}

.drawer-facts dt {
  color: var(--text-muted);
}

.drawer-facts dd {
  overflow-wrap: anywhere;
  color: var(--text-strong);
  font-family: var(--font-mono);
  text-align: right;
}

.drawer-alert {
  margin-bottom: 20px;
}

.edit-form :deep(.ant-form-item-label > label) {
  color: var(--text-subtle);
  font-size: 13px;
  font-weight: 600;
}

.drawer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 4px;
}

.danger-zone {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid var(--border-default);
}

.danger-zone h3 {
  margin: 0 0 4px;
  color: var(--state-error-text);
  font-size: 13px;
  font-weight: 700;
}

.danger-zone p {
  margin: 0;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.5;
}

@media (max-width: 900px) {
  .filter-form {
    grid-template-columns: 1fr 1fr;
  }

  .filter-actions {
    grid-column: 1 / -1;
  }
}

@media (max-width: 768px) {
  #userManagePage {
    padding: 28px 16px 56px;
  }

  .page-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 18px;
  }

  .page-heading h1 {
    font-size: 30px;
  }

  .heading-actions {
    width: 100%;
    justify-content: space-between;
  }

  .filter-panel {
    padding: 20px;
  }

  .filter-form {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .filter-actions {
    grid-column: auto;
  }

  .filter-actions :deep(.ant-btn:first-child) {
    flex: 1;
  }

  .desktop-table {
    display: none;
  }

  .mobile-user-list {
    display: block;
  }

  .mobile-user-item {
    padding: 20px;
    border-bottom: 1px solid var(--border-default);
  }

  .mobile-user-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
  }

  .mobile-user-facts {
    margin: 18px 0;
    padding: 12px 0;
    border-top: 1px solid var(--border-subtle);
    border-bottom: 1px solid var(--border-subtle);
  }

  .mobile-user-facts div {
    display: grid;
    grid-template-columns: 80px minmax(0, 1fr);
    gap: 12px;
    padding: 5px 0;
  }

  .mobile-user-facts dt,
  .mobile-user-facts dd {
    margin: 0;
    font-size: 12px;
  }

  .mobile-user-facts dt {
    color: var(--text-muted);
  }

  .mobile-user-facts dd {
    overflow-wrap: anywhere;
    color: var(--text-subtle);
  }

  .mobile-detail-button {
    width: 100%;
    min-height: var(--touch-target-min);
  }

  .pagination-bar {
    align-items: flex-start;
    flex-direction: column;
    padding: 16px 20px 20px;
  }

  .pagination-bar :deep(.ant-pagination) {
    width: 100%;
  }

  .pagination-bar :deep(.ant-pagination-options) {
    display: none;
  }

  :deep(.user-drawer .ant-drawer-content-wrapper) {
    width: min(100vw, 440px) !important;
  }
}

@media (prefers-reduced-motion: reduce) {
  #userManagePage :deep(*) {
    scroll-behavior: auto !important;
    transition-duration: 0.01ms !important;
  }
}
</style>
