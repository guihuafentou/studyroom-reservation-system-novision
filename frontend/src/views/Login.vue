<template>
  <div class="auth-page">
    <el-card class="auth-card">
      <h2 class="auth-title">校园自习室预约管理系统</h2>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="0">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" size="large" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" size="large" :prefix-icon="Lock" show-password @keyup.enter="onLogin" />
        </el-form-item>
        <el-button type="primary" size="large" style="width: 100%" :loading="loading" @click="onLogin">登 录</el-button>
        <div class="auth-footer">
          <router-link to="/register">没有账号？去注册</router-link>
          <span>管理员账号：admin（密码见 README 环境变量说明）</span>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '../store/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const onLogin = async () => {
  await formRef.value.validate()
  loading.value = true
  try {
    await userStore.login(form)
    ElMessage.success('登录成功')
    router.push(userStore.isAdmin ? '/admin/dashboard' : '/rooms')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page { display: flex; align-items: center; justify-content: center; height: 100vh; background: linear-gradient(135deg, #1f6feb 0%, #0b57d0 100%); }
.auth-card { width: 380px; padding: 12px 8px; border-radius: 12px; }
.auth-title { text-align: center; color: #1f6feb; margin-bottom: 24px; }
.auth-footer { display: flex; justify-content: space-between; margin-top: 14px; font-size: 13px; color: #909399; }
.auth-footer a { color: #1f6feb; text-decoration: none; }
</style>
