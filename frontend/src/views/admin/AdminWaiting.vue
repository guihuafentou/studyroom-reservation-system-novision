<template>
  <div>
    <h2 class="page-title">候补队列管理</h2>
    <el-card>
      <el-table :data="list" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="学生" min-width="150">
          <template #default="{ row }">{{ row.username }}（{{ row.studentNo }}）</template>
        </el-table-column>
        <el-table-column label="座位" min-width="130">
          <template #default="{ row }">{{ row.roomName }} · {{ row.seatNo }}</template>
        </el-table-column>
        <el-table-column prop="reserveDate" label="日期" width="110" />
        <el-table-column label="时间段" width="170">
          <template #default="{ row }">{{ row.startTime?.slice(11, 16) }} ~ {{ row.endTime?.slice(11, 16) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.statusText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="加入时间" width="170">
          <template #default="{ row }">{{ row.createTime?.replace('T', ' ') }}</template>
        </el-table-column>
        <el-table-column label="转正时间" width="170">
          <template #default="{ row }">{{ row.promoteTime ? row.promoteTime.replace('T', ' ') : '-' }}</template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        layout="total, prev, pager, next"
        style="margin-top: 12px; justify-content: flex-end"
        @current-change="load"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { adminGetWaiting } from '../../api'

const list = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)

const load = async () => {
  loading.value = true
  try {
    const res = await adminGetWaiting({ page: page.value, size: size.value })
    list.value = res.records || []
    total.value = Number(res.total || 0)
  } finally {
    loading.value = false
  }
}

onMounted(load)

const statusType = (s) => ({ 0: 'warning', 1: 'success', 2: 'info', 3: 'danger' }[s] || 'info')
</script>

<style scoped>
.page-title { color: #303133; }
</style>
