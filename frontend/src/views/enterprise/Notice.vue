<template>
  <el-card v-loading="loading">
    <template #header>
      <div class="card-header">
        <span>通知浏览</span>
        <el-input v-model="keyword" placeholder="按标题搜索" :prefix-icon="Search" clearable
                  style="width:240px" @keyup.enter="refresh" @clear="refresh" />
      </div>
    </template>

    <el-empty v-if="!rows.length" description="暂无通知" />
    <div v-else>
      <div v-for="n in rows" :key="n.id" class="notice-item" @click="open(n)">
        <div class="title">
          <el-tag size="small" :type="n.publisherType === 'province' ? 'danger' : 'warning'" effect="plain" style="margin-right:8px">
            {{ n.publisherType === 'province' ? '省级' : '市级' }}
          </el-tag>
          <span>{{ n.title }}</span>
        </div>
        <div class="meta">
          <span>{{ n.publisherRealName }}</span>
          <span>{{ n.createdAt?.substring(0, 16) }}</span>
        </div>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="active?.title" width="640px">
      <div class="meta-bar">
        <el-tag size="small" :type="active?.publisherType === 'province' ? 'danger' : 'warning'" effect="plain">
          {{ active?.publisherType === 'province' ? '省级通知' : '市级通知' }}
        </el-tag>
        <span style="color:#666">{{ active?.publisherRealName }}</span>
        <span style="color:#999">{{ active?.createdAt }}</span>
        <span v-if="active?.validUntil" style="color:#999">有效期至 {{ active.validUntil }}</span>
      </div>
      <el-divider />
      <pre class="content">{{ active?.content }}</pre>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { listVisibleNotices } from '@/api/notice'

const loading = ref(false)
const rows = ref([])
const keyword = ref('')
const dialogVisible = ref(false)
const active = ref(null)

onMounted(refresh)

async function refresh() {
  loading.value = true
  try {
    const r = await listVisibleNotices(keyword.value || undefined)
    rows.value = r.data || []
  } finally { loading.value = false }
}

function open(n) {
  active.value = n
  dialogVisible.value = true
}
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.notice-item { padding: 14px 12px; border-bottom: 1px solid #eee; cursor: pointer; transition: background .15s; }
.notice-item:hover { background: #f6f7fa; }
.notice-item .title { font-size: 15px; color: #333; font-weight: 500; }
.notice-item .meta { margin-top: 6px; font-size: 12px; color: #999; display: flex; gap: 12px; }
.meta-bar { display: flex; gap: 12px; align-items: center; font-size: 13px; }
.content { white-space: pre-wrap; font-family: inherit; font-size: 14px; line-height: 1.7; color: #333; margin: 0; }
</style>
