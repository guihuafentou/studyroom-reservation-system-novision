<template>
  <div>
    <el-page-header @back="goBack" :content="room?.name || '座位预约'" style="margin-bottom: 16px" />
    <el-card>
      <!-- 模式提示 -->
      <el-alert v-if="isFlexible" type="warning" :closable="false" style="margin-bottom: 12px"
                title="弹性时长模式：可自选开始时间与时长（最短 30 分钟，单次最长 8 小时试点）；开始后 1 小时未签到将自动取消" />
      <el-alert v-else type="info" :closable="false" style="margin-bottom: 12px"
                title="离散时段模式：可多选连续时段批量预约（每段 30 分钟）" />

      <!-- 选择日期与时段（离散模式） -->
      <div class="selector-bar" v-if="!isFlexible">
        <el-date-picker v-model="date" type="date" placeholder="选择日期" :disabled-date="disablePast"
                        value-format="YYYY-MM-DD" style="width: 160px" @change="load" />
        <el-select v-model="slotIds" placeholder="选择时段（可多选连续时段）" multiple collapse-tags
                   style="width: 300px" @change="load">
          <el-option v-for="s in slots" :key="s.id" :label="`${s.startTime} ~ ${s.endTime}`" :value="s.id" />
        </el-select>
        <el-button type="primary" :disabled="!date || !slotIds.length" @click="load">查 询</el-button>
        <el-button @click="refresh">刷 新</el-button>
        <span class="hint">提示：绿色可预约，点击座位即可预约</span>
      </div>

      <!-- 选择日期与区间（弹性模式） -->
      <div class="selector-bar" v-else>
        <el-date-picker v-model="date" type="date" placeholder="选择日期" :disabled-date="disablePast"
                        value-format="YYYY-MM-DD" style="width: 160px" @change="onFlexInput" />
        <el-time-select v-model="flexStart" :start="flexOpenTime" step="00:30" :end="flexCloseTime"
                        placeholder="开始时间" style="width: 140px" @change="onFlexInput" />
        <el-select v-model="flexMinutes" placeholder="预约时长" style="width: 140px" @change="onFlexInput">
          <el-option v-for="m in durationOptions" :key="m" :label="`${m / 60} 小时`" :value="m" />
        </el-select>
        <el-button type="primary" :disabled="!flexValid" @click="load">查 询</el-button>
        <el-button @click="refresh">刷 新</el-button>
        <span class="hint">弹性预约区间：{{ flexRangeLabel }}</span>
      </div>

      <!-- 图例 -->
      <div class="legend">
        <span><i class="dot free"></i>空闲</span>
        <span><i class="dot me"></i>我已预约</span>
        <span><i class="dot other"></i>他人已约</span>
        <span><i class="dot inuse"></i>使用中</span>
        <span><i class="dot disabled"></i>禁用</span>
      </div>

      <!-- 座位网格 -->
      <div class="seat-grid" v-if="seats.length">
        <div v-for="row in rows" :key="row" class="seat-row">
          <div v-for="seat in seatsInRow(row)" :key="seat.id" class="seat-cell" @click="select(seat)">
            <div class="seat" :class="seatClass(seat)">
              <span class="seat-no">{{ seat.seatNo }}</span>
              <el-tag v-if="seat.seatType === 1" size="small" class="type-tag">窗</el-tag>
              <el-tag v-else-if="seat.seatType === 2" size="small" type="warning" class="type-tag">电</el-tag>
              <el-tag v-else-if="seat.seatType === 3" size="small" type="info" class="type-tag">隔</el-tag>
            </div>
          </div>
        </div>
      </div>
      <el-empty v-else description="请选择日期和时段后查询" />
    </el-card>

    <!-- 预约对话框 -->
    <el-dialog v-model="dialogVisible" title="确认预约" width="420px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="自习室">{{ room?.name }}</el-descriptions-item>
        <el-descriptions-item label="座位">{{ current?.seatNo }}</el-descriptions-item>
        <el-descriptions-item label="日期">{{ date }}</el-descriptions-item>
        <el-descriptions-item :label="isFlexible ? '预约区间' : '时段'">{{ rangeLabel }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">确认预约</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getRoom, getSlots, getSeats, createReservation } from '../api'

const route = useRoute()
const router = useRouter()
const roomId = Number(route.params.id)

const room = ref(null)
const slots = ref([])
const seats = ref([])
const date = ref('')
const slotIds = ref([])
const dialogVisible = ref(false)
const submitting = ref(false)
const current = ref(null)

// 弹性模式状态
const flexStart = ref('')
const flexMinutes = ref(60)

const isFlexible = computed(() => room.value?.bookingModel === 'FLEXIBLE')

// 开馆/闭馆时间（跟随自习室配置；闭馆上限减 30 分钟保证至少一个最小时长档）
const flexOpenTime = computed(() => {
  const t = room.value?.openTime || '08:00:00'
  return t.slice(0, 5)
})
const flexCloseTime = computed(() => {
  const t = room.value?.closeTime || '22:00:00'
  const d = new Date(`2000-01-01T${t}`)
  d.setMinutes(d.getMinutes() - 30)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
})

const durationOptions = [30, 60, 120, 180, 240, 300, 360, 420, 480]

const formatDate = (d) => {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

// 完整 ISO 时间：按实际年月日生成（跨午夜区间不会落到当天）
const formatDateTime = (d) => {
  const pad = (n) => String(n).padStart(2, '0')
  return `${formatDate(d)}T${pad(d.getHours())}:${pad(d.getMinutes())}:00`
}

const nowTime = () => {
  const d = new Date()
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

const disablePast = (d) => d.getTime() < new Date(new Date().toDateString()).getTime()

// 弹性区间计算（date + start + 时长）
const flexEnd = computed(() => {
  if (!date.value || !flexStart.value) return null
  const [h, m] = flexStart.value.split(':').map(Number)
  const start = new Date(`${date.value}T${flexStart.value}:00`)
  const end = new Date(start.getTime() + flexMinutes.value * 60000)
  return end
})

const flexValid = computed(() => !!date.value && !!flexStart.value && !!flexEnd.value)

const flexRangeLabel = computed(() => {
  if (!date.value || !flexStart.value) return '请选择开始时间'
  const end = flexEnd.value
  if (!end) return '请选择开始时间'
  const pad = (n) => String(n).padStart(2, '0')
  const endLabel = `${pad(end.getHours())}:${pad(end.getMinutes())}`
  const crossDay = end.toDateString() !== new Date(`${date.value}T00:00:00`).toDateString()
  return `${date.value} ${flexStart.value} ~ ${crossDay ? '次日 ' : ''}${endLabel}`
})

const onFlexInput = () => {
  if (flexValid.value) load()
}

const rows = computed(() => [...new Set(seats.value.map((s) => s.rowNo))])

const seatsInRow = (row) => seats.value.filter((s) => s.rowNo === row)

const slotLabel = computed(() => {
  const selected = slots.value.filter((s) => slotIds.value.includes(s.id))
  return selected.length ? selected.map((s) => `${s.startTime}~${s.endTime}`).join('、') : ''
})

const rangeLabel = computed(() => (isFlexible.value ? flexRangeLabel.value : slotLabel.value))

onMounted(async () => {
  room.value = await getRoom(roomId)
  if (!isFlexible.value) {
    slots.value = await getSlots(roomId)
  }
  const today = new Date()
  date.value = formatDate(today)
  if (!isFlexible.value) {
    const usable = slots.value.filter((s) => s.startTime > nowTime())
    slotIds.value = usable.length ? [usable[0].id] : (slots.value[0] ? [slots.value[0].id] : [])
  } else {
    // 弹性模式默认下一小时整点开始
    const d = new Date()
    d.setHours(d.getHours() + 1, 0, 0, 0)
    flexStart.value = `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  }
  await load()
})

const load = async () => {
  if (isFlexible.value) {
    if (!flexValid.value) return
    seats.value = await getSeats(roomId, {
      date: date.value,
      start: `${date.value}T${flexStart.value}:00`,
      end: formatDateTime(flexEnd.value)
    })
  } else {
    if (!date.value || !slotIds.value.length) return
    // 座位状态按所选第一个时段展示（多时段预约以最终提交校验为准）
    seats.value = await getSeats(roomId, { date: date.value, slotId: slotIds.value[0] })
  }
}

const refresh = async () => {
  await load()
  ElMessage.success('已刷新')
}

const seatClass = (seat) => {
  if (seat.status === 0) return 'disabled'
  if (seat.reservedByMe) return 'me'
  if (seat.reservedByOther || seat.status === 2) return 'other'
  if (seat.status === 3) return 'inuse'
  return 'free'
}

const select = (seat) => {
  if (seat.status === 0) {
    ElMessage.warning('该座位已禁用')
    return
  }
  if (seat.reservedByOther || seat.status === 2 || seat.status === 3) {
    ElMessage.warning('该座位该时段不可预约')
    return
  }
  current.value = seat
  dialogVisible.value = true
}

const submit = async () => {
  submitting.value = true
  try {
    if (isFlexible.value) {
      await createReservation({
        seatId: current.value.id,
        startTime: `${date.value}T${flexStart.value}:00`,
        endTime: formatDateTime(flexEnd.value)
      })
    } else {
      await createReservation({ seatId: current.value.id, date: date.value, slotIds: slotIds.value })
    }
    ElMessage.success('预约成功')
    dialogVisible.value = false
    await load()
  } finally {
    submitting.value = false
  }
}

const goBack = () => router.push('/rooms')
</script>

<style scoped>
.selector-bar { display: flex; gap: 12px; align-items: center; margin-bottom: 14px; flex-wrap: wrap; }
.hint { color: #909399; font-size: 13px; }
.legend { display: flex; gap: 18px; margin-bottom: 14px; font-size: 13px; color: #606266; }
.dot { display: inline-block; width: 12px; height: 12px; border-radius: 3px; margin-right: 4px; vertical-align: -1px; }
.dot.free { background: #67c23a; }
.dot.me { background: #409eff; }
.dot.other { background: #f56c6c; }
.dot.inuse { background: #e6a23c; }
.dot.disabled { background: #c0c4cc; }
.seat-grid { display: flex; flex-direction: column; gap: 12px; align-items: center; }
.seat-row { display: flex; gap: 12px; }
.seat-cell { cursor: pointer; }
.seat { width: 68px; height: 56px; border-radius: 8px; display: flex; flex-direction: column; align-items: center; justify-content: center; position: relative; border: 2px solid transparent; transition: all 0.15s; }
.seat.free { background: #e1f3d8; border-color: #67c23a; }
.seat.me { background: #d9ecff; border-color: #409eff; }
.seat.other { background: #fde2e2; border-color: #f56c6c; }
.seat.inuse { background: #fdf6ec; border-color: #e6a23c; }
.seat.disabled { background: #f4f4f5; border-color: #c0c4cc; cursor: not-allowed; opacity: 0.6; }
.seat-no { font-size: 13px; font-weight: 600; color: #303133; }
.type-tag { position: absolute; top: -6px; right: -6px; transform: scale(0.8); }
</style>
