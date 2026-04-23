<template>
  <el-card>
    <template #header><span>修改密码</span></template>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" style="max-width:520px">
      <el-form-item label="原密码" prop="oldPassword">
        <el-input v-model="form.oldPassword" type="password" show-password />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input v-model="form.newPassword" type="password" show-password />
        <div class="hint">要求：8~20 位，至少包含数字、字母、特殊字符中的两类</div>
      </el-form-item>
      <el-form-item label="确认新密码" prop="confirmPassword">
        <el-input v-model="form.confirmPassword" type="password" show-password />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="onSubmit">提交修改</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { changePassword } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)
const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const rules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 20, message: '长度 8~20 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: (_, v, cb) => v === form.newPassword ? cb() : cb(new Error('两次输入不一致')), trigger: 'blur' },
  ],
}

async function onSubmit() {
  await formRef.value.validate()
  loading.value = true
  try {
    await changePassword(form)
    ElMessage.success('密码已修改，请重新登录')
    userStore.logout()
    router.replace('/login')
  } catch {} finally {
    loading.value = false
  }
}
</script>

<style scoped>
.hint { font-size: 12px; color: #999; line-height: 1.5; margin-top: 4px; }
</style>
