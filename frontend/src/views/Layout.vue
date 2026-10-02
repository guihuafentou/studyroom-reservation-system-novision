<template>
  <el-container class="layout">
    <el-header class="header">
      <div class="logo" @click="goHome">📚 自习室预约系统</div>
      <el-menu mode="horizontal" :default-active="activeMenu" router class="nav-menu" :ellipsis="false">
        <el-menu-item index="/rooms">自习室</el-menu-item>
        <el-menu-item index="/my-reservations">我的预约</el-menu-item>
        <el-menu-item index="/waiting">候补队列</el-menu-item>
        <el-menu-item index="/announcements">公告</el-menu-item>
        <template v-if="userStore.isAdmin">
          <el-sub-menu index="/admin">
            <template #title>管理后台</template>
            <el-menu-item index="/admin/dashboard">数据看板</el-menu-item>
            <el-menu-item index="/admin/users">用户管理</el-menu-item>
            <el-menu-item index="/admin/rooms">自习室管理</el-menu-item>
            <el-menu-item index="/admin/reservations">预约管理</el-menu-item>
            <el-menu-item index="/admin/audit">操作审计</el-menu-item>
            <el-menu-item index="/admin/announcements">公告管理</el-menu-item>
            <el-menu-item index="/admin/configs">参数配置</el-menu-item>
            <el-menu-item index="/admin/waiting">候补队列</el-menu-item>
          </el-sub-menu>
        </template>
      </el-menu>
      <div class="user-area">
        <el-dropdown @command="onCommand">
          <span class="user-name">
            {{ userStore.user?.realName || userStore.user?.username }}
            <el-tag v-if="userStore.isAdmin" size="small" type="warning" style="margin-left: 4px">管理员</el-tag>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>
    <el-main class="main">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeMenu = computed(() => {
  if (route.path.startsWith('/admin')) return '/admin/dashboard'
  return route.path
})

const goHome = () => router.push('/rooms')

const onCommand = (cmd) => {
  if (cmd === 'logout') {
    userStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.layout { min-height: 100vh; }
.header { display: flex; align-items: center; background: #fff; border-bottom: 1px solid #e4e7ed; padding: 0 20px; }
.logo { font-size: 18px; font-weight: 700; color: #1f6feb; cursor: pointer; margin-right: 32px; white-space: nowrap; }
.nav-menu { flex: 1; border-bottom: none; }
.user-area { margin-left: auto; }
.user-name { cursor: pointer; color: #303133; font-size: 14px; }
.main { background: #f5f7fa; }
</style>
