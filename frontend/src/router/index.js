import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../store/user'

const routes = [
  { path: '/login', name: 'Login', component: () => import('../views/Login.vue'), meta: { public: true } },
  { path: '/register', name: 'Register', component: () => import('../views/Register.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('../views/Layout.vue'),
    children: [
      { path: '', redirect: '/rooms' },
      { path: 'rooms', name: 'Rooms', component: () => import('../views/RoomList.vue') },
      { path: 'rooms/:id', name: 'SeatMap', component: () => import('../views/SeatMap.vue') },
      { path: 'my-reservations', name: 'MyReservations', component: () => import('../views/MyReservations.vue') },
      { path: 'waiting', name: 'Waiting', component: () => import('../views/WaitingList.vue') },
      { path: 'announcements', name: 'Announcements', component: () => import('../views/Announcements.vue') },
      { path: 'admin', redirect: '/admin/dashboard', meta: { admin: true } },
      { path: 'admin/dashboard', name: 'AdminDashboard', component: () => import('../views/admin/AdminDashboard.vue'), meta: { admin: true } },
      { path: 'admin/users', name: 'AdminUsers', component: () => import('../views/admin/AdminUsers.vue'), meta: { admin: true } },
      { path: 'admin/rooms', name: 'AdminRooms', component: () => import('../views/admin/AdminRooms.vue'), meta: { admin: true } },
      { path: 'admin/reservations', name: 'AdminReservations', component: () => import('../views/admin/AdminReservations.vue'), meta: { admin: true } },
      { path: 'admin/audit', name: 'AdminAudit', component: () => import('../views/admin/AdminAudit.vue'), meta: { admin: true } },
      { path: 'admin/announcements', name: 'AdminAnnouncements', component: () => import('../views/admin/AdminAnnouncements.vue'), meta: { admin: true } },
      { path: 'admin/configs', name: 'AdminConfigs', component: () => import('../views/admin/AdminConfigs.vue'), meta: { admin: true } },
      { path: 'admin/waiting', name: 'AdminWaiting', component: () => import('../views/admin/AdminWaiting.vue'), meta: { admin: true } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/rooms' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const userStore = useUserStore()
  if (to.meta.public) return true
  if (!userStore.token) return { path: '/login' }
  if (to.meta.admin && userStore.user?.role !== 1) return { path: '/rooms' }
  return true
})

export default router
