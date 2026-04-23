<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <div class="brand-title">云南省企业就业失业数据采集系统</div>
        <div class="brand-sub">Yunnan Enterprise Employment Data Collection</div>
      </div>
      <el-form :model="form" :rules="rules" ref="formRef" label-position="top" @keyup.enter="onSubmit">
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" placeholder="请输入账号" prefix-icon="User" size="large" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" prefix-icon="Lock" size="large" />
        </el-form-item>
        <el-button type="primary" :loading="loading" size="large" style="width:100%" @click="onSubmit">登 录</el-button>
      </el-form>
      <div class="demo-tip">
        <div>演示账号 (密码均为 123456)：</div>
        <div><b>province</b> 省级管理员 · <b>kunming</b> 市级 · <b>ent001</b> 企业</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function onSubmit() {
  await formRef.value.validate()
  loading.value = true
  try {
    await userStore.login(form.username, form.password)
    ElMessage.success('登录成功')
    const redirect = route.query.redirect || userStore.homePath
    router.replace(redirect)
  } catch (e) {
    // request interceptor already shows error message
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
  background: linear-gradient(135deg, #1e3c72 0%, #2a5298 100%);
}
.login-card {
  width: 420px;
  padding: 40px 36px 32px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
}
.brand { text-align: center; margin-bottom: 28px; }
.brand-title { font-size: 20px; font-weight: 600; color: #1e3c72; }
.brand-sub { font-size: 12px; color: #999; margin-top: 6px; letter-spacing: 0.5px; }
.demo-tip { margin-top: 20px; font-size: 12px; color: #888; line-height: 1.7; padding: 10px 12px; background: #f6f7fa; border-radius: 6px; }
</style>
