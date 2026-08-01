<template>
  <div class="vip-page">
    <div class="vip-container">
      <header class="page-heading">
        <div class="page-kicker">
          <CrownOutlined aria-hidden="true" />
          <span>会员与权限</span>
        </div>
        <h1>{{ pageTitle }}</h1>
        <p>{{ pageDescription }}</p>
      </header>

      <div v-if="paymentNotice" class="payment-feedback" aria-live="polite">
        <a-alert
          :type="paymentNotice.type"
          :message="paymentNotice.message"
          :description="paymentNotice.description"
          show-icon
          closable
          @close="paymentNotice = null"
        />
        <a-button
          v-if="paymentNotice.canRetry"
          class="refresh-button"
          type="link"
          :loading="refreshingMembership"
          @click="refreshMembershipStatus"
        >
          重新检查会员状态
        </a-button>
      </div>

      <section class="membership-panel" aria-labelledby="membership-status-title">
        <div
          class="membership-summary"
          :class="{ 'membership-summary--active': hasMembershipAccess }"
        >
          <div class="status-line">
            <span class="status-dot" aria-hidden="true"></span>
            <span>{{ statusLabel }}</span>
          </div>

          <h2 id="membership-status-title">{{ membershipHeading }}</h2>
          <p class="membership-copy">{{ membershipDescription }}</p>

          <dl class="membership-facts">
            <div v-for="fact in membershipFacts" :key="fact.label">
              <dt>{{ fact.label }}</dt>
              <dd>{{ fact.value }}</dd>
            </div>
          </dl>

          <div v-if="hasMembershipAccess" class="membership-actions">
            <router-link class="primary-link" to="/create">
              <EditOutlined aria-hidden="true" />
              开始创作
            </router-link>
            <router-link class="secondary-link" to="/skill">查看 AI 工具</router-link>
          </div>
          <div v-else class="membership-actions membership-actions--purchase">
            <a-button
              class="purchase-button"
              type="primary"
              size="large"
              :loading="purchasing"
              @click="handlePurchase"
            >
              <template #icon>
                <ThunderboltOutlined />
              </template>
              升级永久会员 · $199
            </a-button>
            <div class="payment-note">
              <SafetyOutlined aria-hidden="true" />
              <span>Stripe 托管支付 · 一次购买</span>
            </div>
          </div>
        </div>

        <div class="capability-list">
          <div class="capability-heading">
            <div>
              <span class="section-label">已验证能力</span>
              <h2>会员能力范围</h2>
            </div>
            <span class="capability-count">4 项</span>
          </div>

          <ul>
            <li v-for="capability in capabilities" :key="capability.title">
              <span class="capability-icon" aria-hidden="true">
                <component :is="capability.icon" />
              </span>
              <div class="capability-content">
                <h3>{{ capability.title }}</h3>
                <p>{{ capability.description }}</p>
              </div>
              <span
                class="capability-status"
                :class="{ 'capability-status--active': hasMembershipAccess }"
              >
                {{ hasMembershipAccess ? '已解锁' : '升级后解锁' }}
              </span>
            </li>
          </ul>
        </div>
      </section>

      <section class="faq-section" aria-labelledby="faq-title">
        <div class="section-heading">
          <span class="section-label">使用说明</span>
          <h2 id="faq-title">常见问题</h2>
        </div>
        <div class="faq-list">
          <details v-for="faq in faqs" :key="faq.question">
            <summary>
              <span>{{ faq.question }}</span>
              <PlusOutlined class="faq-toggle" aria-hidden="true" />
            </summary>
            <p>{{ faq.answer }}</p>
          </details>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Alert as AAlert, message } from 'ant-design-vue'
import {
  AppstoreOutlined,
  CrownOutlined,
  EditOutlined,
  PictureOutlined,
  PlusOutlined,
  RocketOutlined,
  SafetyOutlined,
  ThunderboltOutlined
} from '@ant-design/icons-vue'
import { createVipPaymentSession } from '@/api/paymentController'
import { useLoginUserStore } from '@/stores/loginUser'
import { isAdmin as checkIsAdmin, isVip as checkIsVip } from '@/utils/permission'

type AlertType = 'success' | 'info' | 'warning' | 'error'

interface PaymentNotice {
  type: AlertType
  message: string
  description: string
  canRetry?: boolean
}

const router = useRouter()
const route = useRoute()
const loginUserStore = useLoginUserStore()
const purchasing = ref(false)
const refreshingMembership = ref(false)
const paymentNotice = ref<PaymentNotice | null>(null)

const isAdmin = computed(() => checkIsAdmin(loginUserStore.loginUser))
const isVip = computed(() => loginUserStore.loginUser.userRole === 'vip')
const hasMembershipAccess = computed(() => checkIsVip(loginUserStore.loginUser))

const pageTitle = computed(() => {
  if (isAdmin.value) return '管理员权限'
  if (isVip.value) return '永久会员权益'
  return '永久会员'
})

const pageDescription = computed(() => {
  if (isAdmin.value) return '当前账号已具备全部会员能力，无需重复购买。'
  if (isVip.value) return '当前账号已开通永久会员，可以直接使用高级创作能力。'
  return '一次升级，解锁无限文章创作配额和高级内容能力。'
})

const statusLabel = computed(() => {
  if (isAdmin.value) return '管理员权限已生效'
  if (isVip.value) return '永久会员已生效'
  return '当前为基础账号'
})

const membershipHeading = computed(() => {
  if (isAdmin.value) return '全部高级能力可用'
  if (isVip.value) return '会员状态正常'
  return '升级后永久有效'
})

const membershipDescription = computed(() => {
  if (isAdmin.value) return '管理员账号继承会员权限，可直接进入创作流程使用全部高级能力。'
  if (isVip.value) return '您的会员权限已同步到账，无需续费，可随时开始新的创作。'
  return '永久会员为一次性购买方案，支付完成并确认后自动更新账号权限。'
})

const formattedVipTime = computed(() => {
  const vipTime = loginUserStore.loginUser.vipTime
  if (!vipTime) return '已生效'

  const date = new Date(vipTime)
  if (Number.isNaN(date.getTime())) return '已生效'

  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric'
  }).format(date)
})

const membershipFacts = computed(() => {
  if (isAdmin.value) {
    return [
      { label: '账号状态', value: '管理员' },
      { label: '权限范围', value: '全部可用' },
      { label: '购买需求', value: '无需购买' }
    ]
  }

  if (isVip.value) {
    return [
      { label: '会员状态', value: '已开通' },
      { label: '开通时间', value: formattedVipTime.value },
      { label: '有效期', value: '永久' }
    ]
  }

  return [
    { label: '会员方案', value: '永久会员' },
    { label: '方案价格', value: '$199' },
    { label: '有效期', value: '永久' }
  ]
})

const capabilities = [
  {
    icon: RocketOutlined,
    title: '无限文章创作配额',
    description: '会员创作文章时不受基础账号配额限制。'
  },
  {
    icon: PictureOutlined,
    title: 'Nano Banana AI 配图',
    description: '在创作流程中使用 Nano Banana 生成文章配图。'
  },
  {
    icon: AppstoreOutlined,
    title: 'SVG 概念示意图',
    description: '为文章生成结构清晰、可直接展示的 SVG 图示。'
  },
  {
    icon: EditOutlined,
    title: 'AI 大纲编辑',
    description: '使用 AI 助手调整和优化文章大纲。'
  }
]

const faqs = [
  {
    question: '支付后什么时候生效？',
    answer: 'Stripe 确认支付结果后，系统会自动更新账号权限。若返回后仍显示基础账号，可稍后重新检查会员状态。'
  },
  {
    question: '会员是否需要续费？',
    answer: '不需要。永久会员为一次性购买方案，开通后长期有效。'
  },
  {
    question: '会员包含哪些高级配图能力？',
    answer: '会员额外解锁 Nano Banana AI 配图和 SVG 概念示意图，具体入口位于文章创作流程。'
  },
  {
    question: '如何完成支付？',
    answer: '点击升级按钮后将前往 Stripe 托管的支付页面，完成或取消支付后会返回当前应用。'
  }
]

const replacePaymentQuery = () => {
  void router.replace({ path: '/vip' })
}

const refreshMembershipStatus = async () => {
  refreshingMembership.value = true
  try {
    await loginUserStore.fetchLoginUser()
    if (hasMembershipAccess.value) {
      paymentNotice.value = {
        type: 'success',
        message: '会员权限已生效',
        description: '账号状态已更新，现在可以使用全部会员能力。'
      }
    } else {
      paymentNotice.value = {
        type: 'warning',
        message: '支付状态确认中',
        description: '当前账号尚未更新为会员，请稍后重新检查。',
        canRetry: true
      }
    }
  } catch (error) {
    console.error('刷新会员状态失败:', error)
    paymentNotice.value = {
      type: 'error',
      message: '暂时无法检查会员状态',
      description: '网络或服务暂时不可用，请稍后重试。',
      canRetry: true
    }
  } finally {
    refreshingMembership.value = false
  }
}

onMounted(async () => {
  if (route.query.success === 'true') {
    await refreshMembershipStatus()
    replacePaymentQuery()
  } else if (route.query.cancelled === 'true') {
    paymentNotice.value = {
      type: 'info',
      message: '支付已取消',
      description: '本次操作未产生会员状态变更，您可以稍后重新发起支付。'
    }
    replacePaymentQuery()
  }
})

const handlePurchase = async () => {
  if (!loginUserStore.loginUser.id) {
    message.warning('请先登录')
    await router.push('/user/login')
    return
  }

  if (hasMembershipAccess.value) {
    message.info('当前账号已具备会员权限')
    return
  }

  paymentNotice.value = null
  purchasing.value = true
  try {
    const response = await createVipPaymentSession()
    if (response.data.code === 0 && response.data.data) {
      window.location.assign(response.data.data)
      return
    }

    paymentNotice.value = {
      type: 'error',
      message: '暂时无法创建支付',
      description: response.data.message || '请稍后重试，或刷新页面后重新发起支付。'
    }
  } catch (error) {
    console.error('创建支付失败:', error)
    paymentNotice.value = {
      type: 'error',
      message: '暂时无法创建支付',
      description: '网络或支付服务暂时不可用，请稍后重试。'
    }
  } finally {
    purchasing.value = false
  }
}
</script>

<style scoped lang="scss">
/* Product-state layout: membership status first, capabilities second. */
.vip-page {
  min-height: calc(100vh - 64px);
  padding: 40px 24px 72px;
  background: var(--surface-page);
  color: var(--text-body);
}

.vip-container {
  width: min(100%, 1120px);
  margin: 0 auto;
}

.page-heading {
  max-width: 720px;
  margin-bottom: 28px;
}

.page-kicker {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  color: var(--state-success-text);
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.page-heading h1 {
  margin: 0 0 10px;
  color: var(--text-strong);
  font-family: var(--font-heading);
  font-size: clamp(32px, 4vw, 44px);
  font-weight: 700;
  letter-spacing: -0.035em;
  line-height: 1.08;
}

.page-heading p {
  margin: 0;
  color: var(--text-subtle);
  font-size: 16px;
  line-height: 1.7;
}

.payment-feedback {
  position: relative;
  margin-bottom: 20px;
}

.refresh-button {
  position: absolute;
  right: 38px;
  bottom: 8px;
  font-weight: 600;
}

.membership-panel {
  display: grid;
  grid-template-columns: minmax(330px, 0.85fr) minmax(0, 1.35fr);
  overflow: hidden;
  margin-bottom: 48px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-xl);
  background: var(--surface-panel);
  box-shadow: var(--shadow-subtle);
}

.membership-summary {
  display: flex;
  min-height: 520px;
  flex-direction: column;
  padding: 36px;
  border-right: 1px solid var(--border-default);
  background: var(--surface-panel);
}

.membership-summary--active {
  border-right-color: var(--color-secondary-light);
  background: var(--color-secondary);
  color: var(--text-inverse);
}

.status-line {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-bottom: 32px;
  color: var(--text-muted);
  font-size: 13px;
  font-weight: 600;
}

.membership-summary--active .status-line {
  color: #bbf7d0;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--text-disabled);
  box-shadow: 0 0 0 4px var(--surface-muted);
}

.membership-summary--active .status-dot {
  background: var(--color-primary-light);
  box-shadow: 0 0 0 4px rgba(74, 222, 128, 0.16);
}

.membership-summary h2 {
  margin: 0 0 12px;
  color: var(--text-strong);
  font-family: var(--font-heading);
  font-size: 27px;
  font-weight: 650;
  letter-spacing: -0.02em;
}

.membership-summary--active h2 {
  color: var(--text-inverse);
}

.membership-copy {
  min-height: 72px;
  margin: 0 0 32px;
  color: var(--text-subtle);
  font-size: 14px;
  line-height: 1.7;
}

.membership-summary--active .membership-copy {
  color: #cbd5e1;
}

.membership-facts {
  margin: 0;
  border-top: 1px solid var(--border-default);
}

.membership-summary--active .membership-facts {
  border-top-color: rgba(226, 232, 240, 0.18);
}

.membership-facts div {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 14px 0;
  border-bottom: 1px solid var(--border-default);
}

.membership-summary--active .membership-facts div {
  border-bottom-color: rgba(226, 232, 240, 0.18);
}

.membership-facts dt,
.membership-facts dd {
  margin: 0;
  font-size: 13px;
}

.membership-facts dt {
  color: var(--text-muted);
}

.membership-facts dd {
  color: var(--text-strong);
  font-weight: 650;
  text-align: right;
}

.membership-summary--active .membership-facts dt {
  color: #94a3b8;
}

.membership-summary--active .membership-facts dd {
  color: var(--text-inverse);
}

.membership-actions {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 10px;
  margin-top: auto;
  padding-top: 32px;
}

.membership-actions--purchase {
  display: flex;
  flex-direction: column;
}

.primary-link,
.secondary-link {
  display: inline-flex;
  min-height: var(--touch-target-min);
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-radius: var(--radius-md);
  font-size: 14px;
  font-weight: 650;
  text-decoration: none;
  transition:
    background-color var(--transition-fast),
    border-color var(--transition-fast),
    color var(--transition-fast);
}

.primary-link {
  border: 1px solid var(--color-primary);
  background: var(--color-primary);
  color: var(--color-secondary);
}

.primary-link:hover {
  border-color: var(--color-primary-light);
  background: var(--color-primary-light);
  color: var(--color-secondary);
}

.secondary-link {
  padding: 0 16px;
  border: 1px solid rgba(226, 232, 240, 0.32);
  color: #e2e8f0;
}

.secondary-link:hover {
  border-color: rgba(226, 232, 240, 0.6);
  color: var(--text-inverse);
}

.primary-link:focus-visible,
.secondary-link:focus-visible,
.faq-list summary:focus-visible {
  outline: 3px solid rgba(34, 197, 94, 0.32);
  outline-offset: 3px;
}

.purchase-button {
  width: 100%;
  min-height: var(--control-height-lg);
  border-color: var(--color-primary) !important;
  background: var(--color-primary) !important;
  box-shadow: none !important;
  color: var(--color-secondary) !important;
  font-weight: 700;
}

.purchase-button:hover {
  border-color: var(--color-primary-dark) !important;
  background: var(--color-primary-dark) !important;
  color: var(--text-inverse) !important;
}

.payment-note {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  color: var(--text-muted);
  font-size: 12px;
}

.capability-list {
  padding: 36px 40px 24px;
}

.capability-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 18px;
}

.section-label {
  display: block;
  margin-bottom: 6px;
  color: var(--state-success-text);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.capability-heading h2,
.section-heading h2 {
  margin: 0;
  color: var(--text-strong);
  font-family: var(--font-heading);
  font-size: 22px;
  font-weight: 650;
  letter-spacing: -0.015em;
}

.capability-count {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 12px;
}

.capability-list ul {
  margin: 0;
  padding: 0;
  border-top: 1px solid var(--border-default);
  list-style: none;
}

.capability-list li {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr) auto;
  align-items: center;
  gap: 16px;
  min-height: 92px;
  border-bottom: 1px solid var(--border-default);
}

.capability-icon {
  display: inline-flex;
  width: 40px;
  height: 40px;
  align-items: center;
  justify-content: center;
  border: 1px solid #bbf7d0;
  border-radius: var(--radius-md);
  background: var(--state-success-bg);
  color: var(--state-success-text);
  font-size: 17px;
}

.capability-content h3 {
  margin: 0 0 4px;
  color: var(--text-strong);
  font-size: 14px;
  font-weight: 650;
}

.capability-content p {
  margin: 0;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.55;
}

.capability-status {
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 650;
  white-space: nowrap;
}

.capability-status--active {
  color: var(--state-success-text);
}

.faq-section {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: 48px;
  padding-top: 8px;
}

.section-heading {
  padding-top: 17px;
}

.faq-list {
  border-top: 1px solid var(--border-strong);
}

.faq-list details {
  border-bottom: 1px solid var(--border-default);
}

.faq-list summary {
  display: flex;
  min-height: 64px;
  cursor: pointer;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  color: var(--text-strong);
  font-size: 14px;
  font-weight: 650;
  list-style: none;
}

.faq-list summary::-webkit-details-marker {
  display: none;
}

.faq-toggle {
  flex: 0 0 auto;
  color: var(--text-muted);
  transition: transform var(--transition-fast);
}

.faq-list details[open] .faq-toggle {
  transform: rotate(45deg);
}

.faq-list details p {
  max-width: 680px;
  margin: -4px 40px 20px 0;
  color: var(--text-subtle);
  font-size: 13px;
  line-height: 1.7;
}

@media (max-width: 900px) {
  .membership-panel {
    grid-template-columns: 1fr;
  }

  .membership-summary {
    min-height: auto;
    border-right: 0;
    border-bottom: 1px solid var(--border-default);
  }

  .membership-summary--active {
    border-bottom-color: var(--color-secondary-light);
  }

  .membership-copy {
    min-height: 0;
  }

  .membership-actions {
    margin-top: 28px;
  }

  .faq-section {
    grid-template-columns: 1fr;
    gap: 16px;
  }
}

@media (max-width: 640px) {
  .vip-page {
    padding: 28px 16px 56px;
  }

  .page-heading {
    margin-bottom: 22px;
  }

  .page-heading h1 {
    font-size: 32px;
  }

  .page-heading p {
    font-size: 14px;
  }

  .refresh-button {
    position: static;
    width: 100%;
    margin-top: 4px;
  }

  .membership-panel {
    margin-bottom: 40px;
    border-radius: var(--radius-lg);
  }

  .membership-summary,
  .capability-list {
    padding: 24px 20px;
  }

  .status-line {
    margin-bottom: 24px;
  }

  .membership-summary h2 {
    font-size: 24px;
  }

  .membership-actions {
    grid-template-columns: 1fr;
  }

  .secondary-link {
    min-height: var(--touch-target-min);
  }

  .capability-list li {
    grid-template-columns: 40px minmax(0, 1fr);
    gap: 12px;
    padding: 16px 0;
  }

  .capability-status {
    grid-column: 2;
  }

  .faq-list summary {
    min-height: 60px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .primary-link,
  .secondary-link,
  .faq-toggle {
    transition: none;
  }
}
</style>
