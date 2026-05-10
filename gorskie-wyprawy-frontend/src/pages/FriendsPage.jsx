import { useState, useEffect, useRef, useCallback } from 'react';
import {
  searchUsers, getFriends, getFriendRequests,
  sendFriendRequest, respondToFriendRequest, removeFriend
} from '../api/client';

function useDebounce(value, delay) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(t);
  }, [value, delay]);
  return debounced;
}

function Avatar({ name, size = 'md' }) {
  const sz = size === 'sm' ? 'w-7 h-7 text-xs' : 'w-9 h-9 text-sm';
  return (
    <div className={`${sz} rounded-full bg-mountain-100 text-mountain-700 flex items-center justify-center font-medium shrink-0`}>
      {name?.[0]?.toUpperCase()}
    </div>
  );
}

// ---- Zakładka: Szukaj ----
function SearchTab({ onRequestSent }) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [statuses, setStatuses] = useState({}); // userId → friendshipStatus
  const debouncedQuery = useDebounce(query, 300);
  const inputRef = useRef(null);

  useEffect(() => {
    if (debouncedQuery.length < 2) { setResults([]); return; }
    setLoading(true);
    searchUsers(debouncedQuery)
      .then(({ data }) => {
        setResults(data);
        const s = {};
        data.forEach(u => { s[u.id] = { status: u.friendshipStatus, fid: u.friendshipId }; });
        setStatuses(s);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [debouncedQuery]);

  const handleSendRequest = async (userId) => {
    try {
      const { data } = await sendFriendRequest(userId);
      setStatuses(s => ({ ...s, [userId]: { status: 'PENDING_SENT', fid: data.friendshipId } }));
      onRequestSent?.();
    } catch (err) {
      alert(err.response?.data?.message || 'Błąd wysyłania zaproszenia');
    }
  };

  const statusBadge = (userId) => {
    const s = statuses[userId]?.status;
    if (s === 'ACCEPTED') return (
      <span className="text-xs text-green-600 font-medium">Znajomy</span>
    );
    if (s === 'PENDING_SENT') return (
      <span className="text-xs text-gray-400">Zaproszenie wysłane</span>
    );
    if (s === 'PENDING_RECEIVED') return (
      <span className="text-xs text-yellow-600">Czeka na Twoją odpowiedź</span>
    );
    return (
      <button
        onClick={() => handleSendRequest(userId)}
        className="text-xs bg-mountain-600 hover:bg-mountain-700 text-white px-3 py-1 rounded-lg transition-colors"
      >
        + Zaproś
      </button>
    );
  };

  return (
    <div>
      <div className="relative mb-4">
        <input
          ref={inputRef}
          type="text"
          value={query}
          onChange={e => setQuery(e.target.value)}
          placeholder="Wpisz nazwę użytkownika..."
          autoFocus
          className="w-full border border-gray-300 rounded-xl px-4 py-3 pr-10 text-sm focus:outline-none focus:ring-2 focus:ring-mountain-400"
        />
        {loading && (
          <div className="absolute right-3 top-3.5">
            <div className="w-4 h-4 border-2 border-mountain-400 border-t-transparent rounded-full animate-spin" />
          </div>
        )}
        {query && !loading && (
          <button
            onClick={() => { setQuery(''); setResults([]); inputRef.current?.focus(); }}
            className="absolute right-3 top-3 text-gray-400 hover:text-gray-600 text-lg leading-none"
          >
            ×
          </button>
        )}
      </div>

      {query.length > 0 && query.length < 2 && (
        <p className="text-sm text-gray-400 text-center py-4">Wpisz co najmniej 2 znaki</p>
      )}

      {results.length > 0 && (
        <ul className="divide-y divide-gray-100 border border-gray-200 rounded-xl overflow-hidden bg-white">
          {results.map(u => (
            <li key={u.id} className="flex items-center gap-3 px-4 py-3">
              <Avatar name={u.name} />
              <span className="flex-1 font-medium text-sm text-gray-800">{u.name}</span>
              {statusBadge(u.id)}
            </li>
          ))}
        </ul>
      )}

      {debouncedQuery.length >= 2 && !loading && results.length === 0 && (
        <p className="text-sm text-gray-400 text-center py-8">
          Nie znaleziono użytkowników pasujących do „{debouncedQuery}"
        </p>
      )}
    </div>
  );
}

// ---- Zakładka: Zaproszenia ----
function RequestsTab({ requests, onRefresh }) {
  const handleRespond = async (friendshipId, accept) => {
    try {
      await respondToFriendRequest(friendshipId, accept);
      onRefresh();
    } catch (err) {
      alert(err.response?.data?.message || 'Błąd odpowiedzi na zaproszenie');
    }
  };

  if (requests.length === 0) {
    return (
      <p className="text-sm text-gray-400 text-center py-12">Brak oczekujących zaproszeń</p>
    );
  }

  return (
    <ul className="divide-y divide-gray-100 border border-gray-200 rounded-xl overflow-hidden bg-white">
      {requests.map(r => (
        <li key={r.friendshipId} className="flex items-center gap-3 px-4 py-3">
          <Avatar name={r.fromUserName} />
          <div className="flex-1 min-w-0">
            <p className="font-medium text-sm text-gray-800">{r.fromUserName}</p>
            <p className="text-xs text-gray-400">
              {new Date(r.sentAt).toLocaleDateString('pl-PL', { day: 'numeric', month: 'long' })}
            </p>
          </div>
          <div className="flex gap-2 shrink-0">
            <button
              onClick={() => handleRespond(r.friendshipId, true)}
              className="text-xs bg-mountain-600 hover:bg-mountain-700 text-white px-3 py-1.5 rounded-lg"
            >
              Akceptuj
            </button>
            <button
              onClick={() => handleRespond(r.friendshipId, false)}
              className="text-xs border border-gray-300 hover:bg-gray-50 text-gray-600 px-3 py-1.5 rounded-lg"
            >
              Odrzuć
            </button>
          </div>
        </li>
      ))}
    </ul>
  );
}

// ---- Zakładka: Znajomi ----
function FriendsTab({ friends, onRefresh }) {
  const handleRemove = async (userId, name) => {
    if (!window.confirm(`Usunąć ${name} ze znajomych?`)) return;
    try {
      await removeFriend(userId);
      onRefresh();
    } catch (err) {
      alert(err.response?.data?.message || 'Błąd usuwania znajomego');
    }
  };

  if (friends.length === 0) {
    return (
      <p className="text-sm text-gray-400 text-center py-12">
        Nie masz jeszcze znajomych. Zaproś kogoś z zakładki „Szukaj"!
      </p>
    );
  }

  return (
    <ul className="divide-y divide-gray-100 border border-gray-200 rounded-xl overflow-hidden bg-white">
      {friends.map(f => (
        <li key={f.friendshipId} className="flex items-center gap-3 px-4 py-3">
          <Avatar name={f.name} />
          <div className="flex-1 min-w-0">
            <p className="font-medium text-sm text-gray-800">{f.name}</p>
            {f.since && (
              <p className="text-xs text-gray-400">
                Znajomi od {new Date(f.since).toLocaleDateString('pl-PL', { day: 'numeric', month: 'long', year: 'numeric' })}
              </p>
            )}
          </div>
          <button
            onClick={() => handleRemove(f.userId, f.name)}
            className="text-xs text-gray-400 hover:text-red-500 border border-gray-200 hover:border-red-300 px-2 py-1 rounded-lg transition-colors"
            title="Usuń znajomego"
          >
            Usuń
          </button>
        </li>
      ))}
    </ul>
  );
}

// ---- Główna strona ----
export default function FriendsPage() {
  const [tab, setTab] = useState('friends');
  const [friends, setFriends] = useState([]);
  const [requests, setRequests] = useState([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(() => {
    setLoading(true);
    Promise.all([getFriends(), getFriendRequests()])
      .then(([fr, rq]) => {
        setFriends(fr.data);
        setRequests(rq.data);
      })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { load(); }, [load]);

  const tabs = [
    { key: 'friends', label: `Znajomi${friends.length > 0 ? ` (${friends.length})` : ''}` },
    {
      key: 'requests',
      label: 'Zaproszenia',
      badge: requests.length > 0 ? requests.length : null,
    },
    { key: 'search', label: 'Szukaj' },
  ];

  return (
    <div className="max-w-2xl mx-auto px-4 py-8">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Znajomi</h1>

      {/* Zakładki */}
      <div className="flex gap-1 mb-6 bg-gray-100 p-1 rounded-xl">
        {tabs.map(t => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            className={`flex-1 flex items-center justify-center gap-1.5 py-2 text-sm font-medium rounded-lg transition-colors ${
              tab === t.key
                ? 'bg-white text-mountain-700 shadow-sm'
                : 'text-gray-500 hover:text-gray-700'
            }`}
          >
            {t.label}
            {t.badge && (
              <span className="bg-red-500 text-white text-xs w-5 h-5 rounded-full flex items-center justify-center font-bold">
                {t.badge}
              </span>
            )}
          </button>
        ))}
      </div>

      {loading && tab !== 'search' ? (
        <div className="text-center py-12 text-gray-400 text-sm">Ładowanie...</div>
      ) : (
        <>
          {tab === 'friends' && <FriendsTab friends={friends} onRefresh={load} />}
          {tab === 'requests' && <RequestsTab requests={requests} onRefresh={load} />}
          {tab === 'search' && <SearchTab onRequestSent={load} />}
        </>
      )}
    </div>
  );
}
