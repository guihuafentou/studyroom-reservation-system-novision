<template>
  <div>
    <h2 class="page-title">我的预约</h2>
    <el-alert type="info" :closable="false" style="margin-bottom: 12px"
              title="规则提示：预约开始前 30 分钟起不可取消；开始前 15 分钟至开始后 30 分钟内可签到；弹性预约开始后 1 小时未签到将自动取消并释放座位；连续 3 次违约将暂停预约 7 天" />

    <el-card style="margin-bottom: 16px">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>学习时长统计</span>
          <span class="muted">按已签到/已完成/提前结束的预约累计</span>
        </div>
      </template>
      <el-row :gutter="16">
        <el-col :span="6"><div class="stat-card"><div class="stat-num">{{ fmt(stats.todayMinutes) }}</div><div class="stat-label">今日（分钟）</div></div></el-col>
        <el-col :span="6"><div class="stat-card"><div class="stat-num">{{ fmt(stats.weekMinutes) }}</div><div class="stat-label">本周（分钟）</div></div></el-col>
        <el-col :span="6"><div class="stat-card"><div class="stat-num">{{ fmt(stats.monthMinutes) }}</div><div class="stat-label">本月（分钟）</div></div></el-col>
        <el-col :span="6"><div class="stat-card"><div class="stat-num">{{ fmt(stats.totalMinutes) }}</div><div class="stat-label">累计（分钟）</div></div></el-col>
      </el-row>
      <div class="week-chart">
        <div v-for="item in stats.daily" :key="item.date" class="bar-col">
          <div class="bar-wrap">
            <div class="bar" :style="{ height: barHeight(item.minutes) }" :title="`${item.date} ${item.minutes} 分钟`"></div>
          </div>
          <div class="bar-date">{{ item.date.slice(5) }}</div>
        </div>
      </div>
    </el-card>

    <el-card>
      <el-table :data="list" stripe v-loading="loading">
        <el-table-column label="自习室" min-width="140">
          <template #default="{ row }">{{ row.roomName }}（{{ row.building }}）</template>
        </el-table-column>
        <el-table-column prop="seatNo" label="座位" width="80" />
        <el-table-column prop="reserveDate" label="日期" width="110" />
        <el-table-column label="时段 / 区间" width="170">
          <template #default="{ row }">
            <template v-if="row.bookingModel === 'FLEXIBLE'">
              <span class="flex-tag">弹性</span> {{ row.startDateTime?.slice(11, 16) }} ~ {{ row.endDateTime?.slice(11, 16) }}
            </template>
            <template v-else>{{ row.startTime }} ~ {{ row.endTime }}</template>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.statusText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button v-if="row.status === 0 && row.signable" size="small" type="success" @click="onSign(row)">签到</el-button>
            <el-button v-if="row.status === 0 && !row.cancelExpired" size="small" type="warning" @click="onCancel(row)">取消</el-button>
            <span v-if="row.status === 0 && row.cancelExpired && !row.signable" class="muted">已过操作时间</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyReservations, cancelReservation, signReservation, getStudyStats } from '../api'

const list = ref([])
const loading = ref(false)
const stats = reactive({ todayMinutes: 0, weekMinutes: 0, monthMinutes: 0, totalMinutes: 0, daily: [] })

const load = async () => {
  loading.value = true
  try {
    list.value = await getMyReservations()
    Object.assign(stats, await getStudyStats())
  } finally {
    loading.value = false
  }
}

onMounted(load)

const statusType = (s) => {
  return { 0: 'info', 1: 'success', 2: 'success', 3: 'info', 4: 'danger', 5: 'warning' }[s] || 'info'
}

const timeLabel = (row) => {
  if (row.bookingModel === 'FLEXIBLE') {
    return `${row.startDateTime?.slice(11, 16)}~${row.endDateTime?.slice(11, 16)}`
  }
  return `${row.startTime}~${row.endTime}`
}

const onSign = async (row) => {
  await ElMessageBox.confirm(`确认已到达并签到座位 ${row.seatNo}？`, '签到确认', { type: 'success' })
  await signReservation(row.id)
  ElMessage.success('签到成功，请按预约时段就座')
  await load()
}

const onCancel = async (row) => {
  await ElMessageBox.confirm(`确认取消 ${row.reserveDate} ${timeLabel(row)} 的预约？`, '取消确认', { type: 'warning' })
  await cancelReservation(row.id)
  ElMessage.success('已取消')
  await load()
}

const fmt = (v) => (v ?? 0).toLocaleString()

const barHeight = (minutes) => {
  const max = Math.max(...(stats.daily || []).map((d) => d.minutes || 0), 1)
  return `${Math.max(2, Math.round(((minutes || 0) / max) * 100))}px`
}
</script>

<style scoped>
.page-title { color: #303133; }
.muted { color: #c0c4cc; font-size: 13px; }
.flex-tag { display: inline-block; background: #fdf6ec; color: #e6a23c; border: 1px solid #e6a23c; border-radius: 3px; font-size: 12px; padding: 0 4px; margin-right: 4px; }
.stat-card { background: #f5f7fa; border-radius: 6px; text-align: center; padding: 14px 0; }
.stat-num { font-size: 22px; font-weight: 700; color: #1f6feb; }
.stat-label { font-size: 12px; color: #909399; margin-top: 4px; }
.week-chart { display: flex; gap: 12px; align-items: flex-end; margin-top: 16px; height: 130px; }
.bar-col { flex: 1; display: flex; flex-direction: column; align-items: center; height: 100%; }
.bar-wrap { flex: 1; width: 100%; display: flex; align-items: flex-end; justify-content: center; }
.bar { width: 55%; background: linear-gradient(180deg, #1f6feb, #79bbff); border-radius: 3px 3px 0 0; min-height: 2px; }
.bar-date { font-size: 12px; color: #909399; margin-top: 6px; }
</style>
