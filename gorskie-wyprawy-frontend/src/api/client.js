import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
});

// Dołącz token JWT do każdego requestu
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Wyloguj przy 401
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

// --- Auth ---
export const register = (data) => api.post('/auth/register', data);
export const login = (data) => api.post('/auth/login', data);

// --- Trasy ---
export const getTrails = (params) => api.get('/trails', { params });
export const getTrail = (id) => api.get(`/trails/${id}`);
export const importTrail = (formData) => api.post('/trails/import', formData);
export const getTrailStats = () => api.get('/trails/meta/stats');
export const getLocationTags = () => api.get('/trails/meta/tags');

// --- Wyprawy ---
export const getMyExpeditions = () => api.get('/expeditions');
export const getPublicExpeditions = () => api.get('/expeditions/public');
export const getExpedition = (id) => api.get(`/expeditions/${id}`);
export const joinExpedition = (id) => api.post(`/expeditions/${id}/join`);
export const removeMember = (id, userId) => api.delete(`/expeditions/${id}/members/${userId}`);
export const leaveExpedition = (id) => api.delete(`/expeditions/${id}/leave`);
export const getExpeditionTrack = (id) => api.get(`/expeditions/${id}/track`);
export const createExpedition = (data) => api.post('/expeditions', data);
export const createExpeditionFromGpx = (formData) =>
  api.post('/expeditions/from-gpx', formData, { headers: { 'Content-Type': 'multipart/form-data' } });
export const updateExpedition = (id, data) => api.patch(`/expeditions/${id}`, data);
export const cancelExpedition = (id) => api.post(`/expeditions/${id}/cancel`);
export const deleteExpedition = (id) => api.delete(`/expeditions/${id}`);
export const inviteToExpedition = (id, username) => api.post(`/expeditions/${id}/invite`, { username });
export const respondToInvite = (id, accept) => api.post(`/expeditions/${id}/respond`, { accept });
export const addComment = (id, content, parentId = null) =>
  api.post(`/expeditions/${id}/comments`, { content, parentId });
export const pinComment = (id, commentId) => api.post(`/expeditions/${id}/comments/${commentId}/pin`);
export const generateInviteLink = (id) => api.post(`/expeditions/${id}/generate-link`);
export const joinByInviteLink = (token) => api.post(`/expeditions/join-by-link/${token}`);
export const approveMember = (id, userId) => api.post(`/expeditions/${id}/members/${userId}/approve`);
export const updateMemberRole = (id, userId, role) => api.patch(`/expeditions/${id}/members/${userId}/role`, { role });
export const updateExpeditionEquipment = (id, equipment) => api.put(`/expeditions/${id}/equipment`, equipment);
export const changeExpeditionTrail = (id, formData) =>
  api.post(`/expeditions/${id}/change-trail`, formData, { headers: { 'Content-Type': 'multipart/form-data' } });
export const getAuditLogs = (id) => api.get(`/expeditions/${id}/audit-logs`);
export const createMultiDayExpedition = (data) => api.post('/expeditions/multi-day', data);
export const addDayTrail = (id, dayNumber, formData) =>
  api.post(`/expeditions/${id}/days/${dayNumber}/trail`, formData, { headers: { 'Content-Type': 'multipart/form-data' } });
export const getDayTrack = (id, dayNumber) => api.get(`/expeditions/${id}/days/${dayNumber}/track`);

// --- Znajomi ---
export const searchUsers = (q) => api.get('/friends/search', { params: { q } });
export const getFriends = () => api.get('/friends');
export const getFriendRequests = () => api.get('/friends/requests');
export const getFriendRequestsCount = () => api.get('/friends/requests/count');
export const sendFriendRequest = (userId) => api.post(`/friends/request/${userId}`);
export const respondToFriendRequest = (friendshipId, accept) =>
  api.post(`/friends/${friendshipId}/respond`, { accept });
export const removeFriend = (userId) => api.delete(`/friends/${userId}`);

// --- Grupy ---
export const getPublicGroups = () => api.get('/groups/public');
export const getMyGroups = () => api.get('/groups');
export const createGroup = (data) => api.post('/groups', data);
export const getGroup = (id) => api.get(`/groups/${id}`);
export const updateGroup = (id, data) => api.patch(`/groups/${id}`, data);
export const deleteGroup = (id) => api.delete(`/groups/${id}`);
export const joinGroup = (id) => api.post(`/groups/${id}/join`);
export const inviteToGroup = (id, username) => api.post(`/groups/${id}/invite`, { username });
export const respondToGroupInvite = (id, accept) => api.post(`/groups/${id}/respond`, { accept });
export const approveGroupMember = (id, userId) => api.post(`/groups/${id}/members/${userId}/approve`);
export const removeGroupMember = (id, userId) => api.delete(`/groups/${id}/members/${userId}`);
export const leaveGroup = (id) => api.delete(`/groups/${id}/leave`);
export const getGroupMessages = (id) => api.get(`/groups/${id}/messages`);
export const sendGroupMessage = (id, content) => api.post(`/groups/${id}/messages`, { content });

// --- Powiadomienia ---
export const getNotifications = () => api.get('/notifications');
export const getNotificationsCount = () => api.get('/notifications/count');
export const markNotificationRead = (id) => api.patch(`/notifications/${id}/read`);
export const markAllNotificationsRead = () => api.patch('/notifications/read-all');

export default api;
