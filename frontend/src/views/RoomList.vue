<template>
  <div>
    <h2 class="page-title">选择自习室</h2>
    <el-row :gutter="20">
      <el-col :span="8" v-for="room in rooms" :key="room.id">
        <el-card shadow="hover" class="room-card" @click="enter(room)">
          <div class="room-name">{{ room.name }}</div>
          <div class="room-meta">
            <el-tag size="small">{{ room.building }} {{ room.floor }}F</el-tag>
            <el-tag size="small" type="info">{{ room.capacity }} 座</el-tag>
            <el-tag size="small" type="success">{{ room.openTime }} ~ {{ room.closeTime }}</el-tag>
          </div>
          <div class="room-status">
            <el-tag v-if="room.status === 1" type="success" size="small">开放中</el-tag>
            <el-tag v-else type="danger" size="small">已停用</el-tag>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getRooms } from '../api'

const router = useRouter()
const rooms = ref([])

onMounted(async () => {
  rooms.value = await getRooms()
})

const enter = (room) => {
  router.push(`/rooms/${room.id}`)
}
</script>

<style scoped>
.page-title { color: #303133; }
.room-card { margin-bottom: 20px; cursor: pointer; }
.room-name { font-size: 16px; font-weight: 600; color: #303133; margin-bottom: 10px; }
.room-meta { display: flex; gap: 8px; flex-wrap: wrap; }
.room-status { margin-top: 12px; }
</style>
