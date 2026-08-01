import { useLoginUserStore } from '@/stores/loginUser'
import router from '@/router'
import { USER_ROLE_ADMIN } from '@/constants/user'

// 是否为首次获取登录用户
let firstFetchLoginUser = true

/**
 * 全局权限校验
 */
const refreshLoginUser = async () => {
  const loginUserStore = useLoginUserStore()
  try {
    await loginUserStore.fetchLoginUser()
  } catch (error) {
    console.warn('获取登录用户失败:', error)
  }
  return loginUserStore.loginUser
}

const scheduleLoginUserRefresh = () => {
  const refresh = () => {
    void refreshLoginUser()
  }

  if ('requestIdleCallback' in window) {
    window.requestIdleCallback(refresh, { timeout: 2_000 })
    return
  }
  globalThis.setTimeout(refresh, 1_000)
}

router.beforeEach(async (to, from, next) => {
  const loginUserStore = useLoginUserStore()
  let loginUser = loginUserStore.loginUser

  const toUrl = to.fullPath
  const requiresAuth = to.meta.requiresAuth || toUrl.startsWith('/admin')

  // 如果目标页面需要认证，始终重新验证 session 有效性
  // 解决：Redis Session 过期后 store 中仍残留旧用户数据，导致守卫误放行的问题
  if (requiresAuth) {
    loginUser = await refreshLoginUser()
    firstFetchLoginUser = false
  } else if (firstFetchLoginUser) {
    firstFetchLoginUser = false
    scheduleLoginUserRefresh()
  }

  if (to.meta.requiresAuth && !loginUser?.id) {
    const { default: message } = await import('ant-design-vue/es/message')
    message.warning('请先登录后继续操作')
    next({
      path: '/user/login',
      query: {
        redirect: to.fullPath,
      },
    })
    return
  }
  if (toUrl.startsWith('/admin')) {
    if (!loginUser || loginUser.userRole !== USER_ROLE_ADMIN) {
      const { default: message } = await import('ant-design-vue/es/message')
      message.error('没有权限')
      next({
        path: '/user/login',
        query: {
          redirect: to.fullPath,
        },
      })
      return
    }
  }
  next()
})
