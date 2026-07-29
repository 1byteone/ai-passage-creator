<template>
  <AuthFormShell title="创建账号" subtitle="注册后即可开始创作并保留历史成果。" title-id="register-title">
    <div v-if="errorMessage" class="auth-feedback" role="alert" aria-live="polite">
      <span>{{ errorMessage }}</span>
      <button type="button" aria-label="关闭错误提示" @click="errorMessage = ''">关闭</button>
    </div>

    <form
      name="register"
      autocomplete="on"
      class="auth-form"
      @submit.prevent="handleSubmit"
    >
      <div class="auth-field">
        <label for="register-account">账号</label>
        <div class="auth-input-shell">
          <UserOutlined class="auth-input-icon" aria-hidden="true" />
          <input
            id="register-account"
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
        <label for="register-password">密码</label>
        <div class="auth-input-shell">
          <LockOutlined class="auth-input-icon" aria-hidden="true" />
          <input
            id="register-password"
            v-model="formState.userPassword"
            name="userPassword"
            type="password"
            placeholder="至少 8 位字符"
            autocomplete="new-password"
            minlength="8"
            required
          />
        </div>
      </div>
      <div class="auth-field">
        <label for="register-password-confirm">确认密码</label>
        <div class="auth-input-shell">
          <SafetyOutlined class="auth-input-icon" aria-hidden="true" />
          <input
            id="register-password-confirm"
            v-model="formState.checkPassword"
            name="checkPassword"
            type="password"
            placeholder="再次输入密码"
            autocomplete="new-password"
            minlength="8"
            required
          />
        </div>
      </div>
      <button type="submit" class="auth-submit" :disabled="submitting">
        <span v-if="submitting" class="auth-submit-spinner" aria-hidden="true"></span>
        {{ submitting ? '正在注册' : '注册' }}
      </button>
    </form>

    <div class="auth-form-footer">
      <span>已有账号？</span>
      <RouterLink to="/user/login">立即登录</RouterLink>
    </div>
  </AuthFormShell>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { LockOutlined, SafetyOutlined, UserOutlined } from '@ant-design/icons-vue'
import AuthFormShell from '@/components/AuthFormShell.vue'
import '@/styles/auth-form.css'

const router = useRouter()
const formState = reactive<API.UserRegisterRequest>({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
})
const submitting = ref(false)
const errorMessage = ref('')

const handleSubmit = async () => {
  if (formState.userPassword !== formState.checkPassword) {
    errorMessage.value = '两次输入密码不一致'
    return
  }

  submitting.value = true
  errorMessage.value = ''
  try {
    const { userRegister } = await import('@/api/userController.ts')
    const res = await userRegister(formState)
    if (res.data.code === 0) {
      await router.replace('/user/login')
      return
    }
    errorMessage.value = res.data.message || '注册失败，请检查输入后重试。'
  } catch {
    errorMessage.value = '暂时无法连接注册服务，请稍后重试。'
  } finally {
    submitting.value = false
  }
}
</script>
