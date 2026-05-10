import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { getNotifications, markNotificationRead, markAllNotificationsRead } from '../api/client';

const TYPE_ICONS = {
  FRIEND_REQUEST_RECEIVED:  { dot: 'bg-blue-400',    label: 'Znajomi' },
  FRIEND_REQUEST_ACCEPTED:  { dot: 'bg-green-400',   label: 'Znajomi' },
  FRIEND_REQUEST_DECLINED:  { dot: 'bg-red-400',     label: 'Znajomi' },
  GROUP_INVITATION:         { dot: 'bg-purple-400',  label: 'Grupy' },
  GROUP_JOIN_REQUEST:       { dot: 'bg-purple-400',  label: 'Grupy' },
  GROUP_JOIN_APPROVED:      { dot: 'bg-green-400',   label: 'Grupy' },
  GROUP_JOIN_DECLINED:      { dot: 'bg-red-400',     label: 'Grupy' },
  EXPEDITION_INVITATION:    { dot: 'bg-mountain-400', label: 'Wyprawa' },
  EXPEDITION_JOIN_REQUEST:  { dot: 'bg-mountain-400', label: 'Wyprawa' },
  EXPEDITION_JOIN_APPROVED: { dot: 'bg-green-400',   label: 'Wyprawa' },
  EXPEDITION_JOIN_DECLINED: { dot: 'bg-red-400',     label: 'Wyprawa' },
  EXPEDITION_STATUS_CHANGED:{ dot: 'bg-amber-400',   label: 'Status' },
};

function timeAgo(dateStr) {
  const diff = Date.now() - new Date(dateStr).getTime();
  const m = Math.floor(diff / 60000);
  if (m < 1) return 'przed chwilą';
  if (m < 60) return `${m} min temu`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h} godz. temu`;
  return `${Math.floor(h / 24)} dni temu`;
}

export default function NotificationsPage() {
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  const load = () =>
    getNotifications()
      .then(({ data }) => setNotifications(data))
      .finally(() => setLoading(false));

  useEffect(() => { load(); }, []);

  const handleClick = async (n) => {
    if (!n.read) {
      await markNotificationRead(n.id);
      setNotifications(prev => prev.map(x => x.id === n.id ? { ...x, read: true } : x));
    }
    if (n.relatedUrl) navigate(n.relatedUrl);
  };

  const handleMarkAll = async () => {
    await markAllNotificationsRead();
    setNotifications(prev => prev.map(n => ({ ...n, read: true })));
  };

  const unreadCount = notifications.filter(n => !n.read).length;

  if (loading) return <div className="text-center py-16 text-gray-400">Ładowanie...</div>;

  return (
    <div className="max-w-2xl mx-auto px-4 py-8">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">
          Powiadomienia
          {unreadCount > 0 && (
            <span className="ml-2 text-sm font-medium bg-red-500 text-white px-2 py-0.5 rounded-full">
              {unreadCount}
            </span>
          )}
        </h1>
        {unreadCount > 0 && (
          <button
            onClick={handleMarkAll}
            className="text-sm text-mountain-600 hover:text-mountain-800 transition-colors"
          >
            Oznacz wszystkie jako przeczytane
          </button>
        )}
      </div>

      {notifications.length === 0 ? (
        <div className="text-center py-16 text-gray-400 text-sm">
          Brak powiadomień.
        </div>
      ) : (
        <ul className="space-y-1">
          {notifications.map(n => {
            const meta = TYPE_ICONS[n.type] || { dot: 'bg-gray-300', label: '' };
            return (
              <li key={n.id}>
                <button
                  onClick={() => handleClick(n)}
                  className={`w-full flex items-start gap-3 px-4 py-3 rounded-xl text-left transition-colors ${
                    n.read ? 'hover:bg-gray-50' : 'bg-mountain-50 hover:bg-mountain-100'
                  }`}
                >
                  <div className="mt-1.5 shrink-0">
                    <div className={`w-2.5 h-2.5 rounded-full ${n.read ? 'bg-gray-200' : meta.dot}`} />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className={`text-sm ${n.read ? 'text-gray-600' : 'text-gray-900 font-medium'}`}>
                      {n.message}
                    </p>
                    <div className="flex items-center gap-2 mt-0.5">
                      <span className="text-xs text-gray-400">{timeAgo(n.createdAt)}</span>
                      <span className="text-xs text-gray-300">·</span>
                      <span className="text-xs text-gray-400">{meta.label}</span>
                    </div>
                  </div>
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
