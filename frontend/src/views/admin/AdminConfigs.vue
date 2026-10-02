<template>
  <div class="admin-configs">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>系统参数配置</span>
          <el-button type="primary" size="small" :loading="saving" @click="saveAll">保存全部</el-button>
        </div>
      </template>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 14px"
        title="修改参数后点击「保存全部」即时生效，无需重启服务。请谨慎设置，不合理取值可能导致预约规则异常。"
      />

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column label="参数键" width="240">
          <template #default="{ row }">
            <code class="cfg-key">{{ row.configKey }}</code>
          </template>
        </el-table-column>
        <el-table-column label="参数值" width="220">
          <template #default="{ row }">
            <el-input v-model="row.configValue" size="small" />
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="240" />
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { adminGetConfigs, adminSaveConfigs } from '../../api'

const list = ref([])
const loading = ref(false)
const saving = ref(false)

const load = async () => {
  loading.value = true
  try {
    const res = await adminGetConfigs()
    list.value = res.data || []
  } finally {
    loading.value = false
  }
}

const saveAll = async () => {
  if (list.value.some((c) => !String(c.configValue ?? '').trim())) {
    return ElMessage.warning('存在空参数值，请补全后再保存')
  }
  saving.value = true
  try {
    const map = {}
    list.value.forEach((c) => {
      map[c.configKey] = String(c.configValue).trim()
    })
    await adminSaveConfigs(map)
    ElMessage.success('参数已更新并即时生效')
    load()
  } finally {
    saving.value = false
  }
}

const formatTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '')

onMounted(load)
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.cfg-key { font-family: Consolas, monospace; color: #1f6feb; background: #f0f5ff; padding: 2px 6px; border-radius: 4px; }
</style>
