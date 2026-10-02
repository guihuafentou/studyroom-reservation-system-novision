<template>
  <div>
    <h2 class="page-title">数据看板</h2>
    <!-- 统计卡片 -->
    <el-row :gutter="16">
      <el-col :span="3" v-for="card in cards" :key="card.label">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value" :style="{ color: card.color }">{{ card.value }}</div>
          <div class="stat-label">{{ card.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表 -->
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="14">
        <el-card>
          <template #header>近 7 日预约趋势</template>
          <div ref="trendRef" style="height: 320px"></div>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card>
          <template #header>时段热度分布（近 7 日）</template>
          <div ref="heatmapRef" style="height: 320px"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import { adminGetStatsOverview, adminGetStatsTrend, adminGetStatsHeatmap } from '../../api'

const overview = ref({})
const trendRef = ref()
const heatmapRef = ref()
let trendChart = null
let heatmapChart = null

const cards = ref([])

onMounted(async () => {
  overview.value = await adminGetStatsOverview()
  cards.value = [
    { label: '今日预约量', value: overview.value.todayReservations, color: '#409eff' },
    { label: '今日已签到', value: overview.value.todaySigned, color: '#67c23a' },
    { label: '今日违约', value: overview.value.todayViolations, color: '#f56c6c' },
    { label: '当前使用中', value: overview.value.inUseSeats, color: '#e6a23c' },
    { label: '上座率', value: overview.value.occupancyRate + '%', color: '#1f6feb' },
    { label: '违约率', value: overview.value.violationRate + '%', color: '#ff6b81' },
    { label: '今日占座告警', value: overview.value.todayAlerts ?? 0, color: '#e6a23c' }
  ]
  await renderCharts()
})

onBeforeUnmount(() => {
  trendChart?.dispose()
  heatmapChart?.dispose()
})

const renderCharts = async () => {
  const trend = await adminGetStatsTrend({ days: 7 })
  const heatmap = await adminGetStatsHeatmap()

  trendChart = echarts.init(trendRef.value)
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, top: 30, bottom: 30 },
    xAxis: { type: 'category', data: trend.map((t) => t.date.slice(5)) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      name: '预约量', type: 'line', smooth: true, areaStyle: { opacity: 0.15 },
      data: trend.map((t) => t.count),
      itemStyle: { color: '#409eff' }
    }]
  })

  heatmapChart = echarts.init(heatmapRef.value)
  heatmapChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 20, top: 30, bottom: 50 },
    xAxis: { type: 'category', data: heatmap.map((h) => h.timeRange), axisLabel: { rotate: 40, fontSize: 10 } },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{ name: '预约次数', type: 'bar', data: heatmap.map((h) => h.count), itemStyle: { color: '#1f6feb' } }]
  })
}
</script>

<style scoped>
.page-title { color: #303133; }
.stat-card { text-align: center; }
.stat-value { font-size: 28px; font-weight: 700; }
.stat-label { margin-top: 6px; color: #909399; font-size: 13px; }
</style>
