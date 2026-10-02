<template>
  <div>
    <h2 class="page-title">预约管理</h2>
    <el-card>
      <div class="toolbar">
        <el-date-picker v-model="date" type="date" placeholder="按日期筛选" value-format="YYYY-MM-DD" clearable style="width: 150px" @change="load(1)" />
        <el-select v-model="status" placeholder="按状态筛选" clearable style="width: 130px" @change="load(1)">
          <el-option v-for="s in statuses" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-button type="primary" @click="load(1)">查 询</el-button>
      </div>
      <el-table :data="list" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="studentNo" label="学号" width="110" />
        <el-table-column prop="userName" label="姓名" width="90" />
        <el-table-column prop="roomName" label="自习室" width="140" />
        <el-table-column prop="seatNo" label="座位" width="80" />
        <el-table-column prop="reserveDate" label="日期" width="110" />
        <el-table-column prop="timeRange" label="时段" width="130" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.statusText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button v-if="row.status === 0 || row.status === 1" size="small" type="danger" @click="forceCancel(row)">强制取消</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" :page-size="size" :total="total"
                     layout="total, prev, pager, next" @current-change="load" style="margin-top: 12px" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminGetReservations, adminForceCancel } from '../../api'

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const date = ref('')
const status = ref(null)
const loading = ref(false)

const statuses = [
  { value: 0, label: '待签到' }, { value: 1, label: '已签到' }, { value: 2, label: '已完成' },
  { value: 3, label: '已取消' }, { value: 4, label: '违约' }, { value: 5, label: '提前结束' }
]

const statusType = (s) => ({ 0: 'info', 1: 'success', 2: 'success', 3: 'info', 4: 'danger', 5: 'warning' }[s])

const load = async (p) => {
  if (p) page.value = p
  loading.value = true
  try {
    const data = await adminGetReservations({
      page: page.value, size: size.value,
      date: date.value || undefined, status: status.value ?? undefined
    })
    list.value = data.records
    total.value = Number(data.total)
  } finally {
    loading.value = false
  }
}

onMounted(() => load(1))

const forceCancel = async (row) => {
  await ElMessageBox.confirm(`确认强制取消 ${row.userName} 在 ${row.reserveDate} ${row.timeRange} 的预约？此操作将写入审计日志。`, '强制取消', { type: 'warning' })
  await adminForceCancel(row.id)
  ElMessage.success('已强制取消')
  await load()
}
</script>

<style scoped>
.page-title { color: #303133; }
.toolbar { display: flex; gap: 12px; margin-bottom: 12px; }
</style>
