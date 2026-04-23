<template>
  <el-card v-loading="loading">
    <template #header>
      <div class="card-header">
        <span>角色管理 — RBAC</span>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增角色</el-button>
      </div>
    </template>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px"
      title="预置角色不可删除（可编辑权限）；非预置角色已分配给用户的需先解除关联再删除。" />

    <el-table :data="rows" border stripe>
      <el-table-column prop="code" label="角色编码" width="180" />
      <el-table-column prop="name" label="角色名称" width="160" />
      <el-table-column prop="description" label="说明" min-width="280" show-overflow-tooltip />
      <el-table-column label="权限" width="80" align="center">
        <template #default="{row}">{{ row.permissions.length }}</template>
      </el-table-column>
      <el-table-column label="用户数" width="80" align="center">
        <template #default="{row}">{{ row.userCount }}</template>
      </el-table-column>
      <el-table-column label="预置" width="80" align="center">
        <template #default="{row}">
          <el-tag v-if="row.builtin" type="info" size="small">预置</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{row}">
          <el-button link type="primary" @click="openEdit(row)">编辑权限</el-button>
          <el-button link type="danger" :disabled="row.builtin" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="editVisible" :title="form.id ? '编辑角色' : '新增角色'" width="780px" top="5vh">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="角色编码" prop="code">
              <el-input v-model="form.code" :disabled="!!form.id" placeholder="如 ROLE_AUDIT_VIEWER" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="角色名称" prop="name">
              <el-input v-model="form.name" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="说明">
              <el-input v-model="form.description" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider>权限分配</el-divider>
        <div class="perm-groups">
          <div v-for="(perms, group) in groupedPerms" :key="group" class="perm-group">
            <div class="group-head">
              <el-checkbox :model-value="isGroupAll(perms)" :indeterminate="isGroupIndeterminate(perms)"
                           @change="toggleGroup(perms, $event)">
                <b>{{ group }}</b>
              </el-checkbox>
              <span class="muted">{{ pickedInGroup(perms) }}/{{ perms.length }}</span>
            </div>
            <div class="group-body">
              <el-checkbox v-for="p in perms" :key="p.code" :model-value="form.permissions.includes(p.code)"
                           @change="togglePerm(p.code, $event)">
                {{ p.name }}
              </el-checkbox>
            </div>
          </div>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listRoles, createRole, updateRole, deleteRole, listPermissions } from '@/api/system'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const allPerms = ref([])

const editVisible = ref(false)
const formRef = ref()
const form = reactive(blank())
function blank() { return { id: null, code: '', name: '', description: '', permissions: [] } }

const rules = {
  code: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
}

const groupedPerms = computed(() => {
  const out = {}
  for (const p of allPerms.value) {
    if (!out[p.group]) out[p.group] = []
    out[p.group].push(p)
  }
  return out
})

onMounted(async () => {
  await Promise.all([refresh(), refreshPerms()])
})

async function refresh() {
  loading.value = true
  try {
    const r = await listRoles()
    rows.value = r.data || []
  } finally { loading.value = false }
}

async function refreshPerms() {
  const r = await listPermissions()
  allPerms.value = r.data || []
}

function openCreate() { Object.assign(form, blank()); editVisible.value = true }
function openEdit(row) {
  Object.assign(form, { id: row.id, code: row.code, name: row.name, description: row.description || '', permissions: [...row.permissions] })
  editVisible.value = true
}

function pickedInGroup(perms) { return perms.filter(p => form.permissions.includes(p.code)).length }
function isGroupAll(perms) { return perms.every(p => form.permissions.includes(p.code)) }
function isGroupIndeterminate(perms) {
  const picked = pickedInGroup(perms)
  return picked > 0 && picked < perms.length
}
function toggleGroup(perms, checked) {
  if (checked) for (const p of perms) { if (!form.permissions.includes(p.code)) form.permissions.push(p.code) }
  else form.permissions = form.permissions.filter(c => !perms.find(p => p.code === c))
}
function togglePerm(code, checked) {
  if (checked) { if (!form.permissions.includes(code)) form.permissions.push(code) }
  else form.permissions = form.permissions.filter(c => c !== code)
}

async function onSave() {
  await formRef.value.validate()
  saving.value = true
  try {
    if (form.id) await updateRole(form.id, form)
    else await createRole(form)
    ElMessage.success(form.id ? '已保存' : '已创建')
    editVisible.value = false
    refresh()
  } catch {} finally { saving.value = false }
}

async function onDelete(row) {
  await ElMessageBox.confirm(`确认删除角色 [${row.name}]？`, '请二次确认', { type: 'error', confirmButtonText: '确认删除' })
  await deleteRole(row.id)
  ElMessage.success('已删除')
  refresh()
}
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.perm-groups { display: flex; flex-direction: column; gap: 12px; max-height: 420px; overflow-y: auto; padding: 4px 8px; }
.perm-group { border: 1px solid #ebeef5; border-radius: 6px; padding: 10px 12px; background: #fafbfc; }
.group-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.group-head .muted { color: #999; font-size: 12px; }
.group-body { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 6px 16px; }
</style>
