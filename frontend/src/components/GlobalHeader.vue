<template>
  <header class="header">
    <div class="header-container">
      <div class="header-left">
        <RouterLink to="/" class="logo-link">
          <div class="logo-wrapper">
            <img
              src="@/assets/logo.webp"
              alt=""
              width="36"
              height="36"
              decoding="async"
              class="logo-img"
            />
            <span class="site-title">AI 文章创作器</span>
          </div>
        </RouterLink>
      </div>

      <!-- 中间：导航菜单 -->
      <nav class="nav-center">
        <RouterLink
          v-for="item in coreItems"
          :key="item.key"
          :to="item.key"
          :class="['nav-item', { active: isActive(item.key) }]"
        >
          <component :is="item.icon" class="nav-icon" />
          <span class="nav-label">{{ item.label }}</span>
        </RouterLink>
        <details v-for="group in navigationGroups" :key="group.label" class="nav-group">
          <summary :class="['nav-group-trigger', { active: group.items.some((item) => isActive(item.key)) }]">
            <component :is="group.icon" class="nav-icon" />
            <span class="nav-label">{{ group.label }}</span>
            <DownOutlined class="nav-group-chevron" />
          </summary>
          <div class="nav-group-menu">
            <RouterLink v-for="item in group.items" :key="item.key" :to="item.key" class="nav-group-item">
              <component :is="item.icon" />
              <span>{{ item.label }}</span>
            </RouterLink>
          </div>
        </details>
      </nav>

      <!-- 右侧：用户操作区域 -->
      <div class="header-right">
        <!-- 主题切换 -->
        <ThemeSwitcher class="header-theme-switcher" />
        <div v-if="loginUserStore.loginUser.id" class="user-dropdown">
          <!-- VIP 标识 -->
          <RouterLink v-if="!isVip" to="/vip" class="upgrade-vip-btn">
            <CrownOutlined />
            <span>升级 VIP</span>
          </RouterLink>
          <RouterLink v-else to="/vip" class="vip-badge">
            <CrownOutlined />
            <span>VIP</span>
          </RouterLink>

          <a-dropdown>
            <a-space class="user-info">
              <a-avatar
                :src="getAvatar(loginUserStore.loginUser.userAvatar, loginUserStore.loginUser.userName)"
                :size="36"
                class="user-avatar"
              />
              <span class="user-name">
                {{ loginUserStore.loginUser.userName ?? '无名' }}
              </span>
            </a-space>
            <template #overlay>
              <a-menu class="dropdown-menu">
                <a-menu-item v-if="isVip" key="vip-info" class="vip-info-item" @click="router.push('/vip')">
                  <CrownOutlined />
                  <span>永久会员权益</span>
                </a-menu-item>
                <a-menu-divider v-if="isVip" />
                <a-menu-item v-for="item in dropdownItems" :key="item.key" class="dropdown-item" @click="router.push(item.key)">
                  <component :is="item.icon" />
                  <span>{{ item.label }}</span>
                </a-menu-item>
                <a-menu-divider />
                <a-menu-item @click="doLogout" class="dropdown-item">
                  <LogoutOutlined />
                  <span>退出登录</span>
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </div>
        <div v-else>
          <RouterLink to="/user/login" class="login-btn">登录</RouterLink>
        </div>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import {
  LogoutOutlined,
  HomeOutlined,
  EditOutlined,
  UnorderedListOutlined,
  SettingOutlined,
  CrownOutlined,
  BarChartOutlined,
  AppstoreOutlined,
  KeyOutlined,
  AuditOutlined,
  ToolOutlined,
  TeamOutlined,
  BookOutlined,
  ProfileOutlined,
  DownOutlined,
  ThunderboltOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons-vue'
import { isVip as checkIsVip } from '@/utils/permission'
import { getAvatar } from '@/utils/avatar'
import ThemeSwitcher from '@/components/ThemeSwitcher.vue'

const loginUserStore = useLoginUserStore()
const router = useRouter()
const route = useRoute()

// 判断是否为 VIP（管理员也视为 VIP）
const isVip = computed(() => checkIsVip(loginUserStore.loginUser))

// 菜单配置项
const originItems = [
  {
    key: '/',
    icon: HomeOutlined,
    label: '首页',
  },
  {
    key: '/create',
    icon: EditOutlined,
    label: '创作',
  },
  {
    key: '/skill',
    icon: AppstoreOutlined,
    label: 'AI 工具',
  },
]

type NavigationItem = { key: string; icon: Component; label: string; admin?: boolean }
type NavigationGroup = { label: string; icon: Component; items: NavigationItem[] }

const coreItems: NavigationItem[] = originItems

const navigationGroups = computed<NavigationGroup[]>(() => {
  const groups: NavigationGroup[] = [
    {
      label: '内容资产',
      icon: UnorderedListOutlined,
      items: [
        { key: '/article/list', icon: UnorderedListOutlined, label: '我的文章' },
        { key: '/comic', icon: ProfileOutlined, label: '漫画手帐' },
        { key: '/handwriting', icon: EditOutlined, label: '手写排版' },
        { key: '/english', icon: BookOutlined, label: '英语训练' },
      ],
    },
    {
      label: 'AI 能力',
      icon: AppstoreOutlined,
      items: [
        { key: '/skill', icon: AppstoreOutlined, label: '技能中心' },
        { key: '/skill/chain', icon: ThunderboltOutlined, label: '链式编排' },
        { key: '/skill/history', icon: ClockCircleOutlined, label: '执行历史' },
        { key: '/analytics', icon: BarChartOutlined, label: '数据分析', admin: true },
      ],
    },
    {
      label: '协作与平台',
      icon: TeamOutlined,
      items: [
        { key: '/workspace', icon: TeamOutlined, label: '协作空间' },
        { key: '/approval', icon: AuditOutlined, label: '审批工作台', admin: true },
        { key: '/apikey', icon: KeyOutlined, label: 'API 密钥' },
      ],
    },
  ]

  const adminItems: NavigationItem[] = [
    { key: '/admin/userManage', icon: SettingOutlined, label: '用户管理' },
    { key: '/admin/toolbox', icon: ToolOutlined, label: '工具箱' },
    { key: '/admin/knowledge', icon: BookOutlined, label: '知识库' },
  ]
  if (loginUserStore.loginUser.userRole === 'admin') {
    groups.push({ label: '管理', icon: SettingOutlined, items: adminItems })
  }

  return groups.map((group) => ({
    ...group,
    items: group.items.filter((item) => !item.admin || loginUserStore.loginUser.userRole === 'admin'),
  }))
})

// 用户下拉菜单项目（低频入口）
const dropdownItems = computed(() => {
  const items: Array<{ key: string; icon: Component; label: string }> = [
    { key: '/apikey', icon: KeyOutlined, label: 'API 密钥' },
  ]
  const loginUser = loginUserStore.loginUser
  if (loginUser && loginUser.userRole === 'admin') {
    items.push({ key: '/approval', icon: AuditOutlined, label: '审批工作台' })
  }
  return items
})

const isActive = (path: string) => {
  if (path === '/') {
    return route.path === '/'
  }
  return route.path === path || route.path.startsWith(`${path}/`)
}

// 退出登录
const doLogout = async () => {
  try {
    const [{ default: message }, { userLogout }] = await Promise.all([
      import('ant-design-vue/es/message'),
      import('@/api/userController.ts'),
    ])
    const res = await userLogout()
    if (res.data.code === 0) {
      loginUserStore.resetLoginUser()
      message.success('退出登录成功')
      await router.push('/user/login')
    } else {
      message.error('退出登录失败，' + res.data.message)
    }
  } catch {
    // 网络错误或 API 不可达时仍清除本地状态并跳转
    loginUserStore.resetLoginUser()
    try {
      const [{ default: message }] = await Promise.all([import('ant-design-vue/es/message')])
      message.warning('网络异常，已清除本地登录状态')
    } catch {}
    await router.push('/user/login')
  }
}

</script>

<style scoped>
.header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: var(--glass-bg);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  padding: 0;
  height: 64px;
  border-bottom: 1px solid var(--color-border);
  transition: all var(--transition-normal);
  overflow: visible;
}

.header-container {
  max-width: 1440px;
  margin: 0 auto;
  padding: 0 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 100%;
}

.header-left {
  display: flex;
  align-items: center;
}

.logo-link {
  display: block;
  transition: opacity var(--transition-fast);
}

.logo-link:hover {
  opacity: 0.8;
}

.logo-wrapper {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-img {
  width: 36px;
  height: 36px;
  object-fit: contain;
}

.site-title {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: var(--color-text);
  white-space: nowrap;
  letter-spacing: -0.3px;
}

/* 导航菜单 */
.nav-center {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mobile-more {
  display: none;
  position: relative;
}

.more-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  padding: 0;
  border: 0;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 18px;
  cursor: pointer;
  list-style: none;
}

.more-button::-webkit-details-marker {
  display: none;
}

.mobile-more-menu {
  position: fixed;
  top: 58px;
  right: 12px;
  z-index: 120;
  display: grid;
  min-width: 136px;
  padding: 6px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--surface-panel);
  box-shadow: var(--shadow-lg);
}

.mobile-more-menu a {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 40px;
  padding: 0 10px;
  border-radius: var(--radius-sm);
  color: var(--color-text-secondary);
  font-size: 14px;
}

.mobile-more-menu a:hover,
.mobile-more-menu a:focus-visible {
  background: var(--color-background-secondary);
  color: var(--color-text);
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: var(--radius-md);
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-secondary);
  transition: all var(--transition-fast);
  text-decoration: none;
  white-space: nowrap;
  flex-shrink: 0;
}

.nav-item:hover {
  color: var(--color-text);
  background: var(--color-background-secondary);
}

.nav-item.active {
  color: var(--color-primary-dark);
  background: rgba(34, 197, 94, 0.1);
}

.nav-group {
  position: relative;
}

.nav-group-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 40px;
  padding: 8px 12px;
  border-radius: var(--radius-md);
  color: var(--color-text-secondary);
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
  cursor: pointer;
  list-style: none;
}

.nav-group-trigger::-webkit-details-marker {
  display: none;
}

.nav-group-trigger:hover,
.nav-group-trigger.active {
  color: var(--color-primary-dark);
  background: rgba(34, 197, 94, 0.1);
}

.nav-group-chevron {
  margin-left: 2px;
  font-size: 14px;
  line-height: 1;
  transform: translateY(-1px);
}

.nav-group-menu {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  z-index: 120;
  display: grid;
  min-width: 168px;
  padding: 6px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--surface-panel);
  box-shadow: var(--shadow-lg);
}

.nav-group-item {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 40px;
  padding: 0 10px;
  border-radius: var(--radius-sm);
  color: var(--color-text-secondary);
  font-size: 14px;
  text-decoration: none;
  white-space: nowrap;
}

.nav-group-item:hover,
.nav-group-item:focus-visible,
.nav-group-item.router-link-active {
  background: var(--color-background-secondary);
  color: var(--color-text);
}

.nav-icon {
  font-size: 16px;
}

/* 用户区域 */
.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-theme-switcher {
  flex-shrink: 0;
}

.user-dropdown {
  cursor: pointer;
  height: 64px;
  display: flex;
  align-items: center;
  gap: 16px;
}

.upgrade-vip-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: var(--radius-md);
  font-size: 13px;
  font-weight: 500;
  background: transparent;
  color: var(--color-primary);
  text-decoration: none;
  transition: all var(--transition-fast);

  &:hover {
    background: rgba(34, 197, 94, 0.08);
    color: var(--color-primary-dark);
  }

  .anticon {
    font-size: 13px;
  }
}

.vip-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-primary);
  text-decoration: none;
  transition: all var(--transition-fast);

  &:hover {
    color: var(--color-primary-dark);
  }

  .anticon {
    font-size: 13px;
  }
}

.user-info {
  padding: 6px 12px;
  border-radius: var(--radius-md);
  transition: all var(--transition-fast);
  display: flex;
  align-items: center;
}

.user-info:hover {
  background: var(--color-background-secondary);
}

.user-avatar {
  border: 2px solid var(--color-border);
}

.user-name {
  font-weight: 500;
  color: var(--color-text);
  font-size: 14px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 80px;
}

.login-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 38px;
  padding: 0 24px;
  border-radius: var(--radius-md);
  font-size: 14px;
  font-weight: 600;
  color: white;
  background: var(--bg-primary);
  border: none;
  box-shadow: var(--shadow-green);
  transition: all var(--transition-normal);
  text-decoration: none;
}

.login-btn:hover {
  color: white;
  box-shadow: 0 6px 20px rgba(34, 197, 94, 0.35);
}

.dropdown-menu {
  border-radius: var(--radius-md);
  overflow: hidden;
  box-shadow: var(--shadow-lg);
  border: 1px solid var(--color-border);
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  transition: all var(--transition-fast);
}

.dropdown-item:hover {
  background: var(--color-background-secondary);
}

.vip-info-item {
  color: var(--color-primary-dark);
  background: rgba(34, 197, 94, 0.1);
  font-weight: 600;
  cursor: default;

  &:hover {
    background: rgba(34, 197, 94, 0.15);
  }
}

/* 响应式 */
@media (max-width: 992px) {
  .nav-label {
    display: none;
  }

  .nav-item {
    padding: 8px 12px;
  }
}

@media (max-width: 768px) {
  .header-container {
    padding: 0 16px;
  }

  .site-title {
    font-size: 15px;
  }

  .nav-center {
    gap: 4px;
  }

  .nav-item {
    padding: 8px 10px;
  }

  .user-name {
    display: none;
  }

  .upgrade-vip-btn,
  .vip-badge {
    display: none;
  }
}

@media (max-width: 480px) {
  .header-container {
    gap: 4px;
    padding: 0 10px;
  }

  .logo-img {
    width: 32px;
    height: 32px;
  }

  .nav-center {
    gap: 2px;
  }

  .nav-item {
    width: 40px;
    height: 40px;
    justify-content: center;
    padding: 0;
  }

  .nav-group-trigger {
    width: 40px;
    height: 40px;
    justify-content: center;
    padding: 0;
  }

  .nav-group-chevron {
    display: none;
  }

  .nav-group-menu {
    position: fixed;
    top: 58px;
    right: 12px;
    left: auto;
  }

  .mobile-more {
    display: inline-flex;
  }

  .user-info {
    padding: 4px;
  }

  .user-avatar {
    width: 32px !important;
    height: 32px !important;
  }

  .login-btn {
    height: 36px;
    padding: 0 12px;
  }
}
</style>
