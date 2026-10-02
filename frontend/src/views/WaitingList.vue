<template>
  <div>
    <h2 class="page-title">候补队列</h2>
    <el-alert type="info" :closable="false" style="margin-bottom: 12px"
              title="热门座位被占时先加入候补：有人取消/超时释放后，系统按加入顺序自动为您转正预约并推送通知" />

    <el-card style="margin-bottom: 16px">
      <template #header>加入候补</template>
      <el-form :inline="true" :model="form" label-width="90px">
        <el-form-item label="自习室类型">
          <el-radio-group v-model="form.mode">
            <el-radio-button value="FLEXIBLE">弹性时段</el-radio-button>
            <el-radio-button value="DISCRETE">固定时段</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="座位ID">
          <el-input-number v-model="form.seatId" :min="1" placeholder="座位ID" style="width: 140px" />
        </el-form-item>
        <template v-if="form.mode === 'FLEXIBLE'">
          <el-form-item label="日期">
            <el-date-picker v-model="form.date" type="date" value-format="YYYY-MM-DD" placeholder="预约日期" style="width: 150px" />
          </el-form-item>
          <el-form-item label="开始">
            <el-time-picker v-model="form.startTime" format="HH:mm" value-format="HH:mm" placeholder="开始时间" style="width: 110px" />
          </el-form-item>
          <el-form-item label="结束">
            <el-time-picker v-model="form.endTime" format="HH:mm" value-format="HH:mm" placeholder="结束时间" style="width: 110px" />
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="日期">
            <el-date-picker v-model="form.date" type="date" value-format="YYYY-MM-DD" placeholder="预约日期" style="width: 150px" />
          </el-form-item>
          <el-form-item label="时段ID">
            <el-input-number v-model="form.slotId" :min="1" placeholder="时段ID" style="width: 140px" />
          </el-form-item>
        </template>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="onSubmit">加入候补</el-button>
        </el-form-item>
      </el-form>
      <div class="hint">提示：固定时段请填写时段ID（在自习室详情页可查看）；候补按“座位 + 时间段”登记，转正后的预约需按时签到，逾期同样计入违约。</div>
    </el-card>

    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>我的候补记录</span>
          <el-button size="small" @click="load">刷新</el-button>
        </div>
      </template>
      <el-table :data="list" stripe v-loading="loading">
        <el-table-column prop="roomName" label="自习室" min-width="140" />
        <el-table-column prop="seatNo" label="座位" width="80" />
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
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" size="small" type="danger" @click="onQuit(row)">放弃</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && list.length === 0" description="暂无候补记录" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyWaiting, waitingEnqueue, waitingQuit } from '../api'

const list = ref([])
const loading = ref(false)
const submitting = ref(false)
const form = reactive({ mode: 'FLEXIBLE', seatId: null, date: null, startTime: null, endTime: null, slotId: null })

const load = async () => {
  loading.value = true
  try {
    list.value = await getMyWaiting()
  } finally {
    loading.value = false
  }
}

const onSubmit = async () => {
  if (!form.seatId || !form.date) {
    ElMessage.warning('请填写座位ID与日期')
    return
  }
  let payload
  if (form.mode === 'FLEXIBLE') {
    if (!form.startTime || !form.endTime) {
      ElMessage.warning('请选择开始与结束时间')
      return
    }
    if (form.startTime >= form.endTime) {
      ElMessage.warning('结束时间必须晚于开始时间')
      return
    }
    payload = {
      seatId: form.seatId,
      startTime: `${form.date}T${form.startTime}:00`,
      endTime: `${form.date}T${form.endTime}:00`
    }
  } else {
    if (!form.slotId) {
      ElMessage.warning('请填写时段ID')
      return
    }
    payload = { seatId: form.seatId, slotId: form.slotId, date: form.date }
  }
  submitting.value = true
  try {
    await waitingEnqueue(payload)
    ElMessage.success('已加入候补，座位释放后将自动转正')
    form.seatId = null
    form.date = null
    form.startTime = null
    form.endTime = null
    form.slotId = null
    await load()
  } finally {
    submitting.value = false
  }
}

const onQuit = async (row) => {
  await ElMessageBox.confirm(`确认放弃座位 ${row.seatNo} ${row.reserveDate} 的候补？`, '放弃候补', { type: 'warning' })
  await waitingQuit(row.id)
  ElMessage.success('已放弃候补')
  await load()
}

onMounted(load)

const statusType = (s) => ({ 0: 'warning', 1: 'success', 2: 'info', 3: 'danger' }[s] || 'info')
</script>

<style scoped>
.page-title { color: #303133; }
.hint { color: #909399; font-size: 12px; margin-top: 4px; }
</style>
