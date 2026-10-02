<template>
  <div class="announcements-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="title">📢 系统公告</span>
        </div>
      </template>

      <el-empty v-if="loading === false && list.length === 0" description="暂无公告" />

      <div v-loading="loading">
        <div
          v-for="a in list"
          :key="a.id"
          class="announcement-item"
          @click="openDetail(a)"
        >
          <div class="row">
            <el-tag v-if="a.isTop === 1" size="small" type="danger" effect="dark">置顶</el-tag>
            <span class="ann-title">{{ a.title }}</span>
            <span class="ann-time">{{ formatTime(a.createTime) }}</span>
          </div>
          <div class="ann-preview">{{ a.content }}</div>
        </div>
      </div>
    </el-card>

    <el-dialog v-model="detailVisible" :title="detail?.title || '公告详情'" width="640px">
      <div class="detail-meta">
        发布时间：{{ formatTime(detail?.createTime) }}
      </div>
      <div class="detail-content" v-html="renderContent(detail?.content)"></div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getAnnouncements } from '../api'

const list = ref([])
const loading = ref(true)
const detailVisible = ref(false)
const detail = ref(null)

const load = async () => {
  loading.value = true
  try {
    const res = await getAnnouncements({ limit: 50 })
    list.value = res.data || []
  } finally {
    loading.value = false
  }
}

const openDetail = (a) => {
  detail.value = a
  detailVisible.value = true
}

const formatTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '')

const renderContent = (c) => (c || '').replace(/\n/g, '<br/>')

onMounted(load)
</script>

<style scoped>
.announcements-page { max-width: 900px; margin: 0 auto; padding: 20px 16px; }
.card-header .title { font-size: 17px; font-weight: 700; color: #303133; }
.announcement-item {
  padding: 14px 6px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  transition: background 0.2s;
}
.announcement-item:hover { background: #f7faff; }
.row { display: flex; align-items: center; gap: 8px; }
.ann-title { font-size: 15px; font-weight: 600; color: #303133; }
.ann-time { margin-left: auto; color: #909399; font-size: 12px; }
.ann-preview {
  margin-top: 6px;
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.detail-meta { color: #909399; font-size: 12px; margin-bottom: 12px; }
.detail-content { color: #303133; line-height: 1.8; white-space: pre-wrap; }
</style>
