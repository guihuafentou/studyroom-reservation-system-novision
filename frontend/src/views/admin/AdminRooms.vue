<template>
  <div>
    <h2 class="page-title">自习室管理</h2>
    <el-tabs v-model="tab">
      <!-- 自习室 -->
      <el-tab-pane label="自习室" name="rooms">
        <el-card>
          <div class="toolbar">
            <el-button type="primary" @click="openRoomDialog()">新增自习室</el-button>
          </div>
          <el-table :data="rooms" stripe>
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="building" label="楼栋" width="100" />
            <el-table-column prop="floor" label="楼层" width="70" />
            <el-table-column prop="capacity" label="容量" width="70" />
            <el-table-column label="开放时间" width="150">
              <template #default="{ row }">{{ row.openTime }} ~ {{ row.closeTime }}</template>
            </el-table-column>
            <el-table-column label="预约模式" width="110">
              <template #default="{ row }">
                <el-tag :type="row.bookingModel === 'FLEXIBLE' ? 'warning' : 'info'" size="small">
                  {{ row.bookingModel === 'FLEXIBLE' ? '弹性时长' : '离散时段' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '开放' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200">
              <template #default="{ row }">
                <el-button size="small" @click="openRoomDialog(row)">编辑</el-button>
                <el-button size="small" type="danger" @click="delRoom(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 座位 -->
      <el-tab-pane label="座位" name="seats">
        <el-card>
          <div class="toolbar">
            <el-select v-model="seatRoomId" placeholder="选择自习室" style="width: 200px" @change="loadSeats">
              <el-option v-for="r in rooms" :key="r.id" :label="r.name" :value="r.id" />
            </el-select>
            <el-button type="primary" :disabled="!seatRoomId" @click="openSeatDialog()">新增座位</el-button>
          </div>
          <el-table :data="seats" stripe>
            <el-table-column prop="seatNo" label="座位号" width="100" />
            <el-table-column prop="rowNo" label="排" width="60" />
            <el-table-column prop="colNo" label="列" width="60" />
            <el-table-column label="类型" width="100">
              <template #default="{ row }">
                <el-tag size="small">{{ ['普通', '靠窗', '电源', '隔断'][row.seatType] }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="['danger', 'success', 'warning', 'warning'][row.status]">
                  {{ ['禁用', '空闲', '已预约', '使用中'][row.status] }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200">
              <template #default="{ row }">
                <el-button size="small" @click="openSeatDialog(row)">编辑</el-button>
                <el-button size="small" type="danger" @click="delSeat(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 时段 -->
      <el-tab-pane label="时段" name="slots">
        <el-card>
          <div class="toolbar">
            <el-select v-model="slotRoomId" placeholder="选择自习室" style="width: 200px" @change="loadSlots">
              <el-option v-for="r in rooms" :key="r.id" :label="r.name" :value="r.id" />
            </el-select>
            <el-button type="primary" :disabled="!slotRoomId" @click="openSlotDialog()">新增时段</el-button>
            <el-button :disabled="!slotRoomId" @click="genDefaultSlots">按 30 分钟自动生成</el-button>
          </div>
          <el-table :data="slots" stripe>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column label="时段" width="160">
              <template #default="{ row }">{{ row.startTime }} ~ {{ row.endTime }}</template>
            </el-table-column>
            <el-table-column label="操作" width="160">
              <template #default="{ row }">
                <el-button size="small" @click="openSlotDialog(row)">编辑</el-button>
                <el-button size="small" type="danger" @click="delSlot(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 自习室对话框 -->
    <el-dialog v-model="roomDlg" :title="roomForm.id ? '编辑自习室' : '新增自习室'" width="460px">
      <el-form :model="roomForm" label-width="90px">
        <el-form-item label="名称" required><el-input v-model="roomForm.name" /></el-form-item>
        <el-form-item label="楼栋" required><el-input v-model="roomForm.building" /></el-form-item>
        <el-form-item label="楼层" required><el-input-number v-model="roomForm.floor" :min="1" /></el-form-item>
        <el-form-item label="容量" required><el-input-number v-model="roomForm.capacity" :min="1" /></el-form-item>
        <el-form-item label="开放时间" required>
          <el-time-picker v-model="roomForm.openTime" format="HH:mm" value-format="HH:mm:ss" placeholder="开放" />
          <el-time-picker v-model="roomForm.closeTime" format="HH:mm" value-format="HH:mm:ss" placeholder="关闭" />
        </el-form-item>
        <el-form-item label="预约模式">
          <el-select v-model="roomForm.bookingModel">
            <el-option value="DISCRETE" label="离散时段（固定 30 分钟时段）" />
            <el-option value="FLEXIBLE" label="弹性时长（自选起止区间）" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="roomForm.status" :active-value="1" :inactive-value="0" active-text="开放" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roomDlg = false">取消</el-button>
        <el-button type="primary" @click="saveRoom">保存</el-button>
      </template>
    </el-dialog>

    <!-- 座位对话框 -->
    <el-dialog v-model="seatDlg" :title="seatForm.id ? '编辑座位' : '新增座位'" width="460px">
      <el-form :model="seatForm" label-width="90px">
        <el-form-item label="座位号" required><el-input v-model="seatForm.seatNo" placeholder="如 A-01" /></el-form-item>
        <el-form-item label="排 / 列" required>
          <el-input-number v-model="seatForm.rowNo" :min="1" /> x
          <el-input-number v-model="seatForm.colNo" :min="1" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="seatForm.seatType">
            <el-option :value="0" label="普通" /><el-option :value="1" label="靠窗" />
            <el-option :value="2" label="电源" /><el-option :value="3" label="隔断" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="seatForm.status">
            <el-option :value="0" label="禁用" /><el-option :value="1" label="空闲" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="seatDlg = false">取消</el-button>
        <el-button type="primary" @click="saveSeat">保存</el-button>
      </template>
    </el-dialog>

    <!-- 时段对话框 -->
    <el-dialog v-model="slotDlg" :title="slotForm.id ? '编辑时段' : '新增时段'" width="420px">
      <el-form label-width="70px">
        <el-form-item label="开始" required>
          <el-time-picker v-model="slotForm.startTime" format="HH:mm" value-format="HH:mm:ss" placeholder="开始时间" />
        </el-form-item>
        <el-form-item label="结束" required>
          <el-time-picker v-model="slotForm.endTime" format="HH:mm" value-format="HH:mm:ss" placeholder="结束时间" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="slotDlg = false">取消</el-button>
        <el-button type="primary" @click="saveSlot">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getRooms, getSlots, getSeats, adminAddRoom, adminEditRoom, adminDeleteRoom,
         adminAddSeat, adminEditSeat, adminDeleteSeat, adminAddSlot, adminEditSlot, adminDeleteSlot } from '../../api'

const tab = ref('rooms')
const rooms = ref([])
const seats = ref([])
const slots = ref([])
const seatRoomId = ref(null)
const slotRoomId = ref(null)

const roomDlg = ref(false)
const seatDlg = ref(false)
const slotDlg = ref(false)

const emptyRoom = () => ({ name: '', building: '', floor: 1, capacity: 10, openTime: '08:00:00', closeTime: '22:00:00', bookingModel: 'DISCRETE', status: 1 })
const roomForm = ref(emptyRoom())
const emptySeat = () => ({ seatNo: '', rowNo: 1, colNo: 1, seatType: 0, status: 1 })
const seatForm = ref(emptySeat())
const emptySlot = () => ({ startTime: '08:00:00', endTime: '08:30:00' })
const slotForm = ref(emptySlot())

onMounted(async () => {
  rooms.value = await getRooms()
  if (rooms.value.length) {
    seatRoomId.value = rooms.value[0].id
    slotRoomId.value = rooms.value[0].id
    await loadSeats()
    await loadSlots()
  }
})

const loadSeats = async () => {
  seats.value = await getSeats(seatRoomId.value)
}
const loadSlots = async () => {
  slots.value = await getSlots(slotRoomId.value)
}

// ---- 自习室 ----
const openRoomDialog = (row) => {
  roomForm.value = row ? { ...row } : emptyRoom()
  roomDlg.value = true
}
const saveRoom = async () => {
  if (roomForm.value.id) await adminEditRoom(roomForm.value)
  else await adminAddRoom(roomForm.value)
  ElMessage.success('保存成功')
  roomDlg.value = false
  rooms.value = await getRooms()
}
const delRoom = async (row) => {
  await ElMessageBox.confirm(`确认删除自习室「${row.name}」？`, '删除确认', { type: 'warning' })
  await adminDeleteRoom(row.id)
  ElMessage.success('已删除')
  rooms.value = await getRooms()
}

// ---- 座位 ----
const openSeatDialog = (row) => {
  seatForm.value = row ? { ...row } : { ...emptySeat(), roomId: seatRoomId.value }
  seatDlg.value = true
}
const saveSeat = async () => {
  const payload = { ...seatForm.value, roomId: seatRoomId.value }
  if (payload.id) await adminEditSeat(payload)
  else await adminAddSeat(payload)
  ElMessage.success('保存成功')
  seatDlg.value = false
  await loadSeats()
}
const delSeat = async (row) => {
  await ElMessageBox.confirm(`确认删除座位 ${row.seatNo}？`, '删除确认', { type: 'warning' })
  await adminDeleteSeat(row.id)
  ElMessage.success('已删除')
  await loadSeats()
}

// ---- 时段 ----
const openSlotDialog = (row) => {
  slotForm.value = row ? { ...row } : emptySlot()
  slotDlg.value = true
}
const saveSlot = async () => {
  const payload = { ...slotForm.value, roomId: slotRoomId.value }
  if (payload.id) await adminEditSlot(payload)
  else await adminAddSlot(payload)
  ElMessage.success('保存成功')
  slotDlg.value = false
  await loadSlots()
}
const delSlot = async (row) => {
  await ElMessageBox.confirm('确认删除该时段？', '删除确认', { type: 'warning' })
  await adminDeleteSlot(row.id)
  ElMessage.success('已删除')
  await loadSlots()
}
const genDefaultSlots = async () => {
  const room = rooms.value.find((r) => r.id === slotRoomId.value)
  if (!room) return
  // 按开放时间每 30 分钟一段
  const startMin = timeToMin(room.openTime)
  const endMin = timeToMin(room.closeTime)
  for (let m = startMin; m + 30 <= endMin; m += 30) {
    await adminAddSlot({ roomId: room.id, startTime: minToTime(m), endTime: minToTime(m + 30) })
  }
  ElMessage.success('时段生成完成')
  await loadSlots()
}
const timeToMin = (t) => {
  const [h, m] = String(t).split(':').map(Number)
  return h * 60 + m
}
const minToTime = (min) => {
  const h = Math.floor(min / 60), m = min % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:00`
}
</script>

<style scoped>
.page-title { color: #303133; }
.toolbar { display: flex; gap: 12px; margin-bottom: 12px; }
</style>
