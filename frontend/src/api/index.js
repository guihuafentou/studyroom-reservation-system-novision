import request from './request'

// ---------- 认证 ----------
export const login = (data) => request.post('/auth/login', data)
export const register = (data) => request.post('/auth/register', data)
export const getMe = () => request.get('/auth/me')

// ---------- 自习室 / 时段 / 座位 ----------
export const getRooms = () => request.get('/rooms')
export const getRoom = (id) => request.get(`/rooms/${id}`)
export const getSlots = (id) => request.get(`/rooms/${id}/slots`)
export const getSeats = (id, params) => request.get(`/rooms/${id}/seats`, { params })

// ---------- 预约 ----------
export const createReservation = (data) => request.post('/reservations', data)
export const getMyReservations = () => request.get('/reservations/mine')
export const cancelReservation = (id) => request.put(`/reservations/${id}/cancel`)
export const signReservation = (id) => request.put(`/reservations/${id}/sign`)

// ---------- 管理端 ----------
export const adminGetUsers = (params) => request.get('/admin/users', { params })
export const adminSetUserStatus = (id, status) => request.put(`/admin/users/${id}/status`, null, { params: { status } })
export const adminAddRoom = (data) => request.post('/admin/rooms', data)
export const adminEditRoom = (data) => request.put('/admin/rooms', data)
export const adminDeleteRoom = (id) => request.delete(`/admin/rooms/${id}`)
export const adminAddSeat = (data) => request.post('/admin/seats', data)
export const adminEditSeat = (data) => request.put('/admin/seats', data)
export const adminDeleteSeat = (id) => request.delete(`/admin/seats/${id}`)
export const adminAddSlot = (data) => request.post('/admin/slots', data)
export const adminEditSlot = (data) => request.put('/admin/slots', data)
export const adminDeleteSlot = (id) => request.delete(`/admin/slots/${id}`)
export const adminGetReservations = (params) => request.get('/admin/reservations', { params })
export const adminForceCancel = (id) => request.put(`/admin/reservations/${id}/cancel`)
export const adminGetAuditLogs = (params) => request.get('/admin/audit-logs', { params })
export const adminGetStatsOverview = () => request.get('/admin/stats/overview')
export const adminGetStatsTrend = (params) => request.get('/admin/stats/trend', { params })
export const adminGetStatsHeatmap = () => request.get('/admin/stats/heatmap')

// ---------- 公告 ----------
export const getAnnouncements = (params) => request.get('/announcements', { params })
export const adminGetAnnouncements = (params) => request.get('/admin/announcements', { params })
export const adminAddAnnouncement = (data) => request.post('/admin/announcements', data)
export const adminEditAnnouncement = (data) => request.put('/admin/announcements', data)
export const adminDeleteAnnouncement = (id) => request.delete(`/admin/announcements/${id}`)

// ---------- 系统参数配置 ----------
export const adminGetConfigs = () => request.get('/admin/configs')
export const adminSaveConfigs = (data) => request.put('/admin/configs', data)

// ---------- 候补队列 ----------
export const waitingEnqueue = (data) => request.post('/waiting', data)
export const getMyWaiting = () => request.get('/waiting/mine')
export const waitingQuit = (id) => request.delete(`/waiting/${id}`)
export const adminGetWaiting = (params) => request.get('/admin/waiting', { params })

// ---------- 学习时长统计 ----------
export const getStudyStats = () => request.get('/stats/study')
