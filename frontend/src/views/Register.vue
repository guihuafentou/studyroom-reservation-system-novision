<template>
  <div class="auth-page">
    <el-card class="auth-card">
      <h2 class="auth-title">学生注册</h2>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="学号" prop="studentNo">
          <el-input v-model="form.studentNo" placeholder="请输入学号" />
        </el-form-item>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="登录用户名" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="真实姓名（可选）" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="6~32 位" show-password />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirm">
          <el-input v-model="form.confirm" type="password" placeholder="再次输入密码" show-password />
        </el-form-item>
        <el-button type="primary" style="width: 100%" :loading="loading" @click="onRegister">注 册</el-button>
        <div class="auth-footer">
          <router-link to="/login">已有账号？去登录</router-link>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../store/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)
const form = reactive({ studentNo: '', username: '', realName: '', password: '', confirm: '' })

const validateConfirm = (rule, value, callback) => {
  if (value !== form.password) callback(new Error('两次输入的密码不一致'))
  else callback()
}

const rules = {
  studentNo: [{ required: true, message: '请输入学号', trigger: 'blur' }],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度须为 6~32 位', trigger: 'blur' }
  ],
  confirm: [{ required: true, validator: validateConfirm, trigger: 'blur' }]
}

const onRegister = async () => {
  await formRef.value.validate()
  loading.value = true
  try {
    await userStore.register({
      studentNo: form.studentNo,
      username: form.username,
      realName: form.realName,
      password: form.password
    })
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page { display: flex; align-items: center; justify-content: center; height: 100vh; background: linear-gradient(135deg, #1f6feb 0%, #0b57d0 100%); }
.auth-card { width: 420px; padding: 12px 8px; border-radius: 12px; }
.auth-title { text-align: center; color: #1f6feb; margin-bottom: 20px; }
.auth-footer { text-align: center; margin-top: 14px; font-size: 13px; }
.auth-footer a { color: #1f6feb; text-decoration: none; }
</style>
