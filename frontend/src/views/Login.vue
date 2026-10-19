<template>
  <div class="login-page">
    <el-card class="login-card">
      <h2 class="login-title">图书借阅管理系统</h2>
      <p class="login-subtitle">J2EE编程技术期末项目 · P01-F2-R03</p>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="0" size="large">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="账号" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" :prefix-icon="Lock" show-password @keyup.enter="onLogin" />
        </el-form-item>
        <el-button type="primary" class="login-btn" :loading="loading" @click="onLogin">登 录</el-button>
      </el-form>
      <p class="login-tip">初始账号：admin / 123456（系统管理员）、groupadmin / 123456（集团管理员）、user / 123456（普通人员）</p>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { login } from '../api/auth'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref(null)
const loading = ref(false)
const form = reactive({ username: 'admin', password: '123456' })
const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function onLogin() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  loading.value = true
  try {
    const data = await login(form)
    userStore.setLogin(data)
    ElMessage.success('登录成功')
    router.push('/')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f2f5;
}
.login-card {
  width: 400px;
  padding: 20px 10px;
}
.login-title {
  text-align: center;
  margin: 0 0 4px;
  font-size: 22px;
  color: #303133;
}
.login-subtitle {
  text-align: center;
  margin: 0 0 24px;
  font-size: 13px;
  color: #909399;
}
.login-btn {
  width: 100%;
}
.login-tip {
  text-align: center;
  margin: 16px 0 0;
  font-size: 12px;
  color: #c0c4cc;
}
</style>
