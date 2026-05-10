import { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { getPublicGroups, getMyGroups, createGroup, joinGroup } from '../api/client';
import { useAuth } from '../context/AuthContext';

const JOIN_LABELS = {
  OWNER:   'Twoja grupa',
  MEMBER:  'Należysz',
  PENDING: 'Oczekuje',
  INVITED: 'Zaproszono',
  VISITOR: null,
};

function GroupCard({ group, onJoin, joining }) {
  const { user } = useAuth();
  const role = group.viewerRole;
  const badge = JOIN_LABELS[role];

  return (
    <div className="bg-white rounded-xl border border-gray-200 p-5 hover:shadow-md hover:border-mountain-300 transition-all">
      <div className="flex items-start justify-between gap-4">
        <div className="flex-1 min-w-0">
          <Link to={`/groups/${group.id}`} className="group">
            <h3 className="font-semibold text-gray-900 group-hover:text-mountain-700 transition-colors">
              {group.name}
            </h3>
          </Link>
          <p className="text-xs text-gray-400 mt-0.5">
            Właściciel: <span className="font-medium text-gray-600">{group.owner?.name}</span>
          </p>
          {group.description && (
            <p className="text-sm text-gray-500 mt-1.5 line-clamp-2">{group.description}</p>
          )}
          <p className="text-xs text-gray-400 mt-2">
            {group.memberCount + 1} członk{group.memberCount + 1 === 1 ? '' : 'ów'}
          </p>
        </div>
        <div className="shrink-0 flex flex-col items-end gap-2">
          <span className="text-xs text-gray-400">
            {new Date(group.createdAt).toLocaleDateString('pl-PL', { day: 'numeric', month: 'long', year: 'numeric' })}
          </span>
          {user && (
            badge ? (
              <span className={`text-xs font-medium px-2 py-1 rounded-lg ${
                role === 'OWNER' || role === 'MEMBER' ? 'bg-mountain-100 text-mountain-700' :
                role === 'INVITED' ? 'bg-yellow-100 text-yellow-700' :
                'bg-gray-100 text-gray-500'
              }`}>{badge}</span>
            ) : (
              <button
                onClick={() => onJoin(group.id)}
                disabled={joining === group.id}
                className="text-xs bg-mountain-600 hover:bg-mountain-700 disabled:bg-mountain-400 text-white px-3 py-1.5 rounded-lg transition-colors"
              >
                {joining === group.id ? 'Wysyłanie...' : 'Dołącz'}
              </button>
            )
          )}
        </div>
      </div>
    </div>
  );
}

function CreateGroupForm({ onCreated }) {
  const [form, setForm] = useState({ name: '', description: '' });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    try {
      const { data } = await createGroup(form);
      onCreated?.();
      navigate(`/groups/${data.id}`);
    } catch (err) {
      setError(err.response?.data?.message || 'Błąd tworzenia grupy');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="bg-white rounded-xl border border-gray-200 p-5 mb-6">
      <h2 className="font-semibold text-gray-800 mb-4">Nowa grupa</h2>
      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 px-3 py-2 rounded-lg text-sm mb-4">{error}</div>
      )}
      <form onSubmit={handleSubmit} className="space-y-3">
        <input
          type="text"
          required
          value={form.name}
          onChange={e => setForm({ ...form, name: e.target.value })}
          placeholder="Nazwa grupy *"
          className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-mountain-400"
        />
        <textarea
          value={form.description}
          onChange={e => setForm({ ...form, description: e.target.value })}
          placeholder="Opis (opcjonalnie)"
          rows={2}
          className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-mountain-400 resize-none"
        />
        <button
          type="submit"
          disabled={loading || !form.name.trim()}
          className="bg-mountain-600 hover:bg-mountain-700 disabled:bg-mountain-400 text-white px-4 py-2 rounded-lg text-sm font-medium transition-colors"
        >
          {loading ? 'Tworzenie...' : 'Utwórz grupę'}
        </button>
      </form>
    </div>
  );
}

export default function GroupsPage() {
  const { user } = useAuth();
  const [tab, setTab] = useState('browse');
  const [groups, setGroups] = useState([]);
  const [loading, setLoading] = useState(true);
  const [joining, setJoining] = useState(null);
  const [showCreate, setShowCreate] = useState(false);

  const load = () => {
    setLoading(true);
    const fetch = (user && tab === 'my') ? getMyGroups() : getPublicGroups();
    fetch
      .then(({ data }) => setGroups(data))
      .catch(() => {})
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, [tab, user]);

  const handleJoin = async (id) => {
    setJoining(id);
    try {
      await joinGroup(id);
      load();
    } catch (err) {
      alert(err.response?.data?.message || 'Błąd dołączania do grupy');
    } finally {
      setJoining(null);
    }
  };

  const tabs = user ? [
    { key: 'browse', label: 'Wszystkie grupy' },
    {
      key: 'my',
      label: 'Moje grupy',
      badge: null, // wypełniamy po załadowaniu
    },
  ] : [];

  // Na zakładce "moje" pokazujemy wszystkie — OWNER, MEMBER, INVITED, PENDING
  const filtered = groups;

  return (
    <div className="max-w-3xl mx-auto px-4 py-8">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Grupy</h1>
        {user && (
          <button
            onClick={() => setShowCreate(v => !v)}
            className="bg-mountain-600 hover:bg-mountain-700 text-white px-4 py-2 rounded-lg text-sm font-medium transition-colors"
          >
            {showCreate ? 'Anuluj' : '+ Nowa grupa'}
          </button>
        )}
      </div>

      {showCreate && <CreateGroupForm onCreated={() => { setShowCreate(false); load(); }} />}

      {user && (
        <div className="flex gap-1 mb-5 bg-gray-100 p-1 rounded-xl">
          {[
            { key: 'browse', label: 'Wszystkie grupy' },
            {
              key: 'my',
              label: 'Moje grupy',
              badge: tab === 'my'
                ? groups.filter(g => g.viewerRole === 'INVITED').length || null
                : null,
            },
          ].map(t => (
            <button
              key={t.key}
              onClick={() => setTab(t.key)}
              className={`flex-1 flex items-center justify-center gap-1.5 py-2 text-sm font-medium rounded-lg transition-colors ${
                tab === t.key ? 'bg-white text-mountain-700 shadow-sm' : 'text-gray-500 hover:text-gray-700'
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
      )}

      {loading ? (
        <div className="text-center py-16 text-gray-400 text-sm">Ładowanie...</div>
      ) : filtered.length === 0 ? (
        <div className="text-center py-16 text-gray-400 text-sm">
          {tab === 'my' ? 'Nie należysz jeszcze do żadnej grupy.' : 'Brak grup.'}
        </div>
      ) : (
        <div className="space-y-3">
          {filtered.map(g => (
            <GroupCard key={g.id} group={g} onJoin={handleJoin} joining={joining} />
          ))}
        </div>
      )}
    </div>
  );
}
