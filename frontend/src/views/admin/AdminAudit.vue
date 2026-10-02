<template>
  <div>
    <h2 class="page-title">操作审计</h2>
    <el-card>
      <el-table :data="list" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="adminId" label="管理员ID" width="100" />
        <el-table-column prop="action" label="操作" width="150">
          <template #default="{ row }">
            <el-tag size="small">{{ row.action }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetType" label="对象类型" width="110" />
        <el-table-column prop="targetId" label="对象ID" width="90" />
        <el-table-column prop="detail" label="详情" min-width="160" />
        <el-table-column prop="createTime" label="操作时间" width="170" />
      </el-table>
      <el-pagination v-model:current-page="page" :page-size="size" :total="total"
                     layout="total, prev, pager, next" @current-change="load" style="margin-top: 12px" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { adminGetAuditLogs } from '../../api'

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)

const load = async (p) => {
  if (p) page.value = p
  loading.value = true
  try {
    const data = await adminGetAuditLogs({ page: page.value, size: size.value })
    list.value = data.records
    total.value = Number(data.total)
  } finally {
    loading.value = false
  }
}

onMounted(() => load(1))
</script>

<style scoped>
.page-title { color: #303133; }
</style>
