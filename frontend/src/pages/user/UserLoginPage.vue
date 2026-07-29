<template>
  <AuthFormShell title="欢迎回来" subtitle="登录后继续创作或查看历史成果。" title-id="login-title">
    <div v-if="errorMessage" class="auth-feedback" role="alert" aria-live="polite">
      <span>{{ errorMessage }}</span>
      <button type="button" aria-label="关闭错误提示" @click="errorMessage = ''">关闭</button>
    </div>

    <form
      name="login"
      autocomplete="on"
      class="auth-form"
      @submit.prevent="handleSubmit"
    >
      <div class="auth-field">
        <label for="login-account">账号</label>
        <div class="auth-input-shell">
          <UserOutlined class="auth-input-icon" aria-hidden="true" />
          <input
            id="login-account"
            v-model="formState.userAccount"
            name="userAccount"
            type="text"
            placeholder="请输入账号"
            autocomplete="username"
            required
          />
        </div>
      </div>
      <div class="auth-field">
        <label for="login-password">密码</label>
        <div class="auth-input-shell">
          <LockOutlined class="auth-input-icon" aria-hidden="true" />
          <input
            id="login-password"
            v-model="formState.userPassword"
            name="userPassword"
            type="password"
            placeholder="请输入密码"
            autocomplete="current-password"
            minlength="8"
            required
          />
        </div>
      </div>
      <button type="submit" class="auth-submit" :disabled="submitting">
        <span v-if="submitting" class="auth-submit-spinner" aria-hidden="true"></span>
        {{ submitting ? '正在登录' : '登录' }}
      </button>
    </form>

    <div class="auth-form-footer">
      <span>还没有账号？</span>
      <RouterLink to="/user/register">立即注册</RouterLink>
    </div>
  </AuthFormShell>
</template>

<script lang="ts" setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { LockOutlined, UserOutlined } from '@ant-design/icons-vue'
import AuthFormShell from '@/components/AuthFormShell.vue'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import '@/styles/auth-form.css'

const formState = reactive<API.UserLoginRequest>({ userAccount: '', userPassword: '' })
const router = useRouter()
const route = useRoute()
const loginUserStore = useLoginUserStore()
const submitting = ref(false)
const errorMessage = ref('')

const handleSubmit = async () => {
  submitting.value = true
  errorMessage.value = ''
  try {
    const { userLogin } = await import('@/api/userController.ts')
    const res = await userLogin(formState)
    if (res.data.code === 0 && res.data.data) {
      await loginUserStore.fetchLoginUser()
      const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
      await router.replace(redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : '/')
      return
    }
    errorMessage.value = res.data.message || '账号或密码不正确，请检查后重试。'
  } catch {
    errorMessage.value = '暂时无法连接登录服务，请稍后重试。'
  } finally {
    submitting.value = false
  }
}
</script>
