import { defineStore } from 'pinia'
import { ref } from 'vue'
import { DEFAULT_USERNAME } from '@/constants/user'

// 创建默认用户对象
function createDefaultUser(): API.LoginUserVO {
  return { userName: DEFAULT_USERNAME }
}

/**
 * 登录用户信息
 */
export const useLoginUserStore = defineStore('loginUser', () => {
  // 默认值
  const loginUser = ref<API.LoginUserVO>(createDefaultUser())

  // 获取登录用户信息
  async function fetchLoginUser() {
    // 10 秒超时 + AbortController，防止网络异常阻塞路由守卫
    const controller = new AbortController()
    const timeoutId = setTimeout(() => controller.abort(), 10_000)

    try {
      const response = await fetch('/api/user/get/login', {
        method: 'GET',
        credentials: 'include',
        headers: {
          Accept: 'application/json',
        },
        signal: controller.signal,
      })
      clearTimeout(timeoutId)

      if (!response.ok) {
        throw new Error(`登录状态请求失败：HTTP ${response.status}`)
      }

      const payload = (await response.json()) as API.BaseResponseLoginUserVO
      if (payload.code === 0 && payload.data) {
        loginUser.value = payload.data
      } else {
        // code !== 0 说明 session 过期或无效，重置为默认用户
        // 防止旧用户数据残留导致 access.ts 守卫误放行
        loginUser.value = createDefaultUser()
      }
    } catch (_err) {
      clearTimeout(timeoutId)
      // 网络错误时也重置，避免进入不可控状态
      loginUser.value = createDefaultUser()
      throw _err
    }
  }

  // 更新登录用户信息
  function setLoginUser(newLoginUser: API.LoginUserVO) {
    loginUser.value = newLoginUser
  }

  // 完全重置用户状态（退出登录时使用）
  function resetLoginUser() {
    loginUser.value = createDefaultUser()
  }

  return { loginUser, fetchLoginUser, setLoginUser, resetLoginUser }
})
