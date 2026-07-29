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
    const response = await fetch('/api/user/get/login', {
      method: 'GET',
      credentials: 'include',
      headers: {
        Accept: 'application/json',
      },
    })
    if (!response.ok) {
      throw new Error(`登录状态请求失败：HTTP ${response.status}`)
    }

    const payload = (await response.json()) as API.BaseResponseLoginUserVO
    if (payload.code === 0 && payload.data) {
      loginUser.value = payload.data
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
