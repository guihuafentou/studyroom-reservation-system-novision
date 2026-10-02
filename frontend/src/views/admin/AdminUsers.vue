<template>
  <div>
    <h2 class="page-title">用户管理</h2>
    <el-card>
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索用户名/学号/姓名" clearable style="width: 260px" @change="load(1)" />
        <el-button type="primary" @click="load(1)">搜索</el-button>
      </div>
      <el-table :data="list" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="studentNo" label="学号" width="120" />
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="realName" label="姓名" width="120" />
        <el-table-column prop="violationCount" label="违约次数" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '正常' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button v-if="row.status === 1" size="small" type="danger" @click="toggle(row, 0)">禁用</el-button>
            <el-button v-else size="small" type="success" @click="toggle(row, 1)">启用</el-button>
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
import { adminGetUsers, adminSetUserStatus } from '../../api'

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const loading = ref(false)

const load = async (p) => {
  if (p) page.value = p
  loading.value = true
  try {
    const data = await adminGetUsers({ page: page.value, size: size.value, keyword: keyword.value || undefined })
    list.value = data.records
    total.value = Number(data.total)
  } finally {
    loading.value = false
  }
}

onMounted(() => load(1))

const toggle = async (row, status) => {
  await ElMessageBox.confirm(`确认${status === 1 ? '启用' : '禁用'}用户 ${row.username}？`, '操作确认', { type: 'warning' })
  await adminSetUserStatus(row.id, status)
  ElMessage.success('操作成功')
  await load()
}
</script>

<style scoped>
.page-title { color: #303133; }
.toolbar { display: flex; gap: 12px; margin-bottom: 12px; }
</style>
