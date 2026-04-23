<template>
  <el-card v-loading="loading">
    <template #header>
      <div class="card-header">
        <span>通知发布</span>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增通知</el-button>
      </div>
    </template>

    <el-table :data="rows" border stripe>
      <el-table-column prop="title" label="标题" min-width="280" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="发布时间" width="160" />
      <el-table-column prop="publisherRealName" label="发布单位" width="160" />
      <el-table-column label="有效期" width="120">
        <template #default="{row}">{{ row.validUntil || '长期' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{row}">
          <el-tag v-if="isExpired(row)" type="info">已过期</el-tag>
          <el-tag v-else type="success">有效</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{row}">
          <el-button link type="primary" @click="openView(row)">查看</el-button>
          <el-button link type="primary" @click="openEdit(row)">修改</el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="editVisible" :title="form.id ? '修改通知' : '新增通知'" width="640px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="50" show-word-limit placeholder="50 字以内" />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="8" maxlength="2000" show-word-limit placeholder="支持换行；2000 字以内" />
        </el-form-item>
        <el-form-item label="有效期至">
          <el-date-picker v-model="form.validUntil" type="date" value-format="YYYY-MM-DD" placeholder="可选；空表示长期有效" style="width:220px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">{{ form.id ? '保存' : '发布' }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="viewVisible" :title="viewing?.title" width="640px">
      <div class="meta">
        <span>{{ viewing?.publisherRealName }}</span>
        <span>{{ viewing?.createdAt }}</span>
        <span v-if="viewing?.validUntil">有效期至：{{ viewing.validUntil }}</span>
      </div>
      <el-divider />
      <pre class="content">{{ viewing?.content }}</pre>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listMyNotices, createNotice, updateNotice, deleteNotice } from '@/api/notice'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const editVisible = ref(false)
const viewVisible = ref(false)
const viewing = ref(null)
const formRef = ref()
const form = reactive({ id: null, title: '', content: '', validUntil: '' })

const rules = {
  title:   [{ required: true, message: '请输入标题', trigger: 'blur' }, { max: 50, message: '不超过 50 字', trigger: 'blur' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }, { max: 2000, message: '不超过 2000 字', trigger: 'blur' }],
}

onMounted(refresh)

async function refresh() {
  loading.value = true
  try {
    const r = await listMyNotices()
    rows.value = r.data || []
  } finally { loading.value = false }
}

function isExpired(row) {
  if (!row.validUntil) return false
  return row.validUntil < new Date().toISOString().substring(0, 10)
}

function openCreate() {
  Object.assign(form, { id: null, title: '', content: '', validUntil: '' })
  editVisible.value = true
}

function openEdit(row) {
  Object.assign(form, { id: row.id, title: row.title, content: row.content, validUntil: row.validUntil || '' })
  editVisible.value = true
}

function openView(row) {
  viewing.value = row
  viewVisible.value = true
}

async function onSave() {
  await formRef.value.validate()
  saving.value = true
  try {
    const payload = { title: form.title, content: form.content, validUntil: form.validUntil || null }
    if (form.id) await updateNotice(form.id, payload)
    else await createNotice(payload)
    ElMessage.success(form.id ? '已修改' : '已发布')
    editVisible.value = false
    refresh()
  } catch {} finally { saving.value = false }
}

async function onDelete(row) {
  await ElMessageBox.confirm(`确定删除「${row.title}」？删除后所有用户都无法看到。`, '提示', { type: 'warning' })
  await deleteNotice(row.id)
  ElMessage.success('已删除')
  refresh()
}
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.meta { color: #888; font-size: 13px; display: flex; gap: 16px; }
.content { white-space: pre-wrap; font-family: inherit; font-size: 14px; line-height: 1.7; color: #333; margin: 0; }
</style>
