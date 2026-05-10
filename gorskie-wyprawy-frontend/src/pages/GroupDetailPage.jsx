import { useState, useEffect, useRef } from 'react';
import { Link, useParams, useNavigate } from 'react-router-dom';
import {
  getGroup, deleteGroup, updateGroup,
  joinGroup, inviteToGroup, respondToGroupInvite,
  approveGroupMember, removeGroupMember, leaveGroup,
  getGroupMessages, sendGroupMessage, searchUsers
} from '../api/client';
import { useAuth } from '../context/AuthContext';

function Avatar({ name, size = 'md' }) {
  const sz = size === 'sm' ? 'w-7 h-7 text-xs' : 'w-8 h-8 text-sm';
  return (
    <div className={`${sz} rounded-full bg-mountain-100 text-mountain-700 flex items-center justify-center font-medium shrink-0`}>
      {name?.[0]?.toUpperCase()}
    </div>
  );
}

function InviteSearchInput({ groupId, alreadyMemberIds, onInvited }) {
  const [query, setQuery] = useState('');
  const [suggestions, setSuggestions] = useState([]);
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const debounceRef = useRef(null);
  const wrapperRef = useRef(null);

  useEffect(() => {
    const handler = (e) => {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target)) setOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const handleChange = (e) => {
    const val = e.target.value;
    setQuery(val);
    clearTimeout(debounceRef.current);
    if (val.trim().length < 2) { setSuggestions([]); setOpen(false); return; }
    debounceRef.current = setTimeout(async () => {
      try {
        const { data } = await searchUsers(val.trim());
        setSuggestions(data.filter(u => !alreadyMemberIds.has(u.id)));
        setOpen(true);
      } catch { /* ignoruj */ }
    }, 300);
  };

  const handleSelect = async (u) => {
    setOpen(false); setQuery(''); setSuggestions([]);
    setBusy(true);
    try {
      await inviteToGroup(groupId, u.name);
      onInvited();
    } catch (err) {
      alert(err.response?.data?.message || 'Błąd zaproszenia');
    } finally { setBusy(false); }
  };

  return (
    <div ref={wrapperRef} className="relative">
      <p className="text-xs text-gray-500 mb-1.5">Zaproś użytkownika:</p>
      <input
        type="text"
        value={query}
        onChange={handleChange}
        onFocus={() => suggestions.length > 0 && setOpen(true)}
        placeholder="Wpisz nazwę..."
        disabled={busy}
        className="w-full border border-gray-300 rounded-lg px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-mountain-400 disabled:bg-gray-50"
      />
      {open && suggestions.length > 0 && (
        <ul className="absolute z-10 mt-1 w-full bg-white border border-gray-200 rounded-lg shadow-lg overflow-hidden">
          {suggestions.map(u => (
            <li key={u.id}>
              <button type="button" onMouseDown={() => handleSelect(u)}
                className="w-full flex items-center gap-2 px-3 py-2 text-sm hover:bg-mountain-50 text-left">
                <Avatar name={u.name} size="sm" />
                <span className="flex-1">{u.name}</span>
                {u.friendshipStatus === 'ACCEPTED' && (
                  <span className="text-xs text-mountain-600">znajomy</span>
                )}
              </button>
            </li>
          ))}
        </ul>
      )}
      {open && query.length >= 2 && suggestions.length === 0 && (
        <div className="absolute z-10 mt-1 w-full bg-white border border-gray-200 rounded-lg shadow-lg px-3 py-2 text-sm text-gray-400">
          Brak wyników
        </div>
      )}
    </div>
  );
}

function Chat({ groupId, currentUserId }) {
  const [messages, setMessages] = useState([]);
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);
  const bottomRef = useRef(null);

  const loadMessages = () =>
    getGroupMessages(groupId)
      .then(({ data }) => setMessages(data))
      .catch(() => {});

  useEffect(() => { loadMessages(); }, [groupId]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSend = async (e) => {
    e.preventDefault();
    if (!text.trim()) return;
    setSending(true);
    try {
      await sendGroupMessage(groupId, text.trim());
      setText('');
      loadMessages();
    } catch (err) {
      alert(err.response?.data?.message || 'Błąd wysyłania');
    } finally { setSending(false); }
  };

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center justify-between mb-3">
        <h2 className="font-semibold text-gray-800">Czat grupowy</h2>
        <button onClick={loadMessages} className="text-xs text-gray-400 hover:text-gray-600">
          Odśwież
        </button>
      </div>

      <div className="flex-1 overflow-y-auto space-y-3 mb-4 max-h-96 pr-1">
        {messages.length === 0 && (
          <p className="text-sm text-gray-400 text-center py-8">Brak wiadomości. Napisz coś!</p>
        )}
        {messages.map(m => {
          const isMe = m.author?.id === currentUserId;
          return (
            <div key={m.id} className={`flex gap-2 ${isMe ? 'flex-row-reverse' : ''}`}>
              <Avatar name={m.author?.name} size="sm" />
              <div className={`max-w-[75%] ${isMe ? 'items-end' : 'items-start'} flex flex-col`}>
                {!isMe && (
                  <span className="text-xs text-gray-400 mb-0.5">{m.author?.name}</span>
                )}
                <div className={`px-3 py-2 rounded-xl text-sm ${
                  isMe
                    ? 'bg-mountain-600 text-white rounded-tr-sm'
                    : 'bg-gray-100 text-gray-800 rounded-tl-sm'
                }`}>
                  {m.content}
                </div>
                <span className="text-xs text-gray-300 mt-0.5">
                  {new Date(m.createdAt).toLocaleTimeString('pl-PL', { hour: '2-digit', minute: '2-digit' })}
                </span>
              </div>
            </div>
          );
        })}
        <div ref={bottomRef} />
      </div>

      <form onSubmit={handleSend} className="flex gap-2 border-t border-gray-100 pt-3">
        <input
          type="text"
          value={text}
          onChange={e => setText(e.target.value)}
          placeholder="Napisz wiadomość..."
          className="flex-1 border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-mountain-400"
        />
        <button
          type="submit"
          disabled={sending || !text.trim()}
          className="bg-mountain-600 hover:bg-mountain-700 disabled:bg-mountain-400 text-white px-4 py-2 rounded-lg text-sm font-medium"
        >
          Wyślij
        </button>
      </form>
    </div>
  );
}

export default function GroupDetailPage() {
  const { id } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();

  const [group, setGroup] = useState(null);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);
  const [editForm, setEditForm] = useState({ name: '', description: '' });

  const refresh = () =>
    getGroup(id)
      .then(({ data }) => setGroup(data))
      .catch(() => navigate('/groups'))
      .finally(() => setLoading(false));

  useEffect(() => { refresh(); }, [id]);

  if (loading) return <div className="text-center py-16 text-gray-400">Ładowanie...</div>;
  if (!group) return null;

  const viewerRole = group.viewerRole;
  const isOwner = viewerRole === 'OWNER';
  const isMember = viewerRole === 'MEMBER';
  const hasFullAccess = isOwner || isMember;

  const alreadyMemberIds = new Set([
    group.owner?.id,
    ...(group.members?.map(m => m.user?.id) ?? [])
  ]);

  const handleDelete = async () => {
    if (!window.confirm('Czy na pewno chcesz usunąć tę grupę?')) return;
    await deleteGroup(id);
    navigate('/groups');
  };

  const handleJoin = async () => {
    try { await joinGroup(id); refresh(); }
    catch (err) { alert(err.response?.data?.message || 'Błąd dołączania'); }
  };

  const handleRespond = async (accept) => {
    try { await respondToGroupInvite(id, accept); refresh(); }
    catch (err) { alert(err.response?.data?.message || 'Błąd odpowiedzi'); }
  };

  const handleApprove = async (userId) => {
    try { await approveGroupMember(id, userId); refresh(); }
    catch (err) { alert(err.response?.data?.message || 'Błąd zatwierdzania'); }
  };

  const handleRemove = async (userId, name) => {
    if (!window.confirm(`Usunąć ${name} z grupy?`)) return;
    try { await removeGroupMember(id, userId); refresh(); }
    catch (err) { alert(err.response?.data?.message || 'Błąd usuwania'); }
  };

  const handleLeave = async () => {
    if (!window.confirm('Czy na pewno chcesz opuścić tę grupę?')) return;
    try { await leaveGroup(id); navigate('/groups'); }
    catch (err) { alert(err.response?.data?.message || 'Błąd opuszczania'); }
  };

  const handleEditSave = async () => {
    try {
      await updateGroup(id, editForm);
      setEditing(false);
      refresh();
    } catch (err) { alert(err.response?.data?.message || 'Błąd edycji'); }
  };

  return (
    <div className="max-w-4xl mx-auto px-4 py-8">
      <Link to="/groups" className="text-mountain-600 hover:text-mountain-800 text-sm mb-4 inline-block">
        {hasFullAccess ? '← Grupy' : '← Przeglądaj grupy'}
      </Link>

      {/* Nagłówek */}
      <div className="bg-white rounded-xl border border-gray-200 p-6 mb-4">
        <div className="flex items-start justify-between gap-4 mb-3">
          <div className="flex-1">
            {editing ? (
              <div className="space-y-2">
                <input
                  value={editForm.name}
                  onChange={e => setEditForm({ ...editForm, name: e.target.value })}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 text-lg font-bold focus:outline-none focus:ring-2 focus:ring-mountain-400"
                />
                <textarea
                  value={editForm.description}
                  onChange={e => setEditForm({ ...editForm, description: e.target.value })}
                  rows={2}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-mountain-400 resize-none"
                />
                <div className="flex gap-2">
                  <button onClick={handleEditSave}
                    className="bg-mountain-600 hover:bg-mountain-700 text-white px-3 py-1.5 rounded-lg text-sm">
                    Zapisz
                  </button>
                  <button onClick={() => setEditing(false)}
                    className="border border-gray-300 text-gray-600 px-3 py-1.5 rounded-lg text-sm">
                    Anuluj
                  </button>
                </div>
              </div>
            ) : (
              <>
                <h1 className="text-2xl font-bold text-gray-900">{group.name}</h1>
                <p className="text-gray-500 text-sm mt-1">
                  Właściciel: <span className="font-medium">{group.owner?.name}</span>
                </p>
                {group.description && (
                  <p className="text-gray-600 text-sm mt-2 leading-relaxed">{group.description}</p>
                )}
              </>
            )}
          </div>
          {!editing && (
            <div className="flex gap-2 shrink-0">
              {isOwner && (
                <>
                  <button
                    onClick={() => { setEditForm({ name: group.name, description: group.description || '' }); setEditing(true); }}
                    className="text-sm border border-gray-300 hover:bg-gray-50 px-3 py-1.5 rounded-lg text-gray-600 transition-colors"
                  >
                    Edytuj
                  </button>
                  <button onClick={handleDelete}
                    className="text-sm text-red-500 hover:text-red-700 border border-red-200 hover:border-red-400 px-3 py-1.5 rounded-lg transition-colors">
                    Usuń
                  </button>
                </>
              )}
            </div>
          )}
        </div>

        <p className="text-xs text-gray-400">
          {group.memberCount + 1} członk{group.memberCount + 1 === 1 ? '' : 'ów'} ·
          Utworzona {new Date(group.createdAt).toLocaleDateString('pl-PL', { day: 'numeric', month: 'long', year: 'numeric' })}
        </p>

        {/* Bannery */}
        {viewerRole === 'INVITED' && (
          <div className="mt-4 p-4 bg-yellow-50 border border-yellow-200 rounded-lg flex items-center justify-between">
            <span className="text-sm text-yellow-800">Zostałeś zaproszony do tej grupy!</span>
            <div className="flex gap-2">
              <button onClick={() => handleRespond(true)}
                className="bg-green-600 hover:bg-green-700 text-white px-3 py-1.5 rounded-lg text-sm">
                Akceptuj
              </button>
              <button onClick={() => handleRespond(false)}
                className="bg-gray-200 hover:bg-gray-300 text-gray-700 px-3 py-1.5 rounded-lg text-sm">
                Odrzuć
              </button>
            </div>
          </div>
        )}
        {viewerRole === 'PENDING' && (
          <div className="mt-4 p-4 bg-blue-50 border border-blue-200 rounded-lg">
            <span className="text-sm text-blue-800">Twoja prośba o dołączenie oczekuje na akceptację właściciela.</span>
          </div>
        )}
        {viewerRole === 'VISITOR' && (
          <div className="mt-4 p-4 bg-mountain-50 border border-mountain-200 rounded-lg flex items-center justify-between">
            <span className="text-sm text-mountain-800">Dołącz do grupy, aby zobaczyć członków i czat.</span>
            <button onClick={handleJoin}
              className="bg-mountain-600 hover:bg-mountain-700 text-white px-4 py-2 rounded-lg text-sm font-medium shrink-0">
              Dołącz
            </button>
          </div>
        )}
      </div>

      {/* Pełny widok — tylko dla OWNER i MEMBER */}
      {hasFullAccess && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">

          {/* Członkowie */}
          <div className="bg-white rounded-xl border border-gray-200 p-5">
            <h2 className="font-semibold text-gray-800 mb-3">
              Członkowie ({(group.members?.length ?? 0) + 1})
            </h2>
            <div className="space-y-2">
              {/* Właściciel */}
              <div className="flex items-center gap-2 text-sm">
                <Avatar name={group.owner?.name} size="sm" />
                <span className="font-medium flex-1 truncate">{group.owner?.name}</span>
                <span className="text-xs text-mountain-600">właściciel</span>
              </div>

              {/* Reszta */}
              {group.members?.map(m => (
                <div key={m.id} className="flex items-center gap-2 text-sm">
                  <Avatar name={m.user?.name} size="sm" />
                  <span className="truncate flex-1">{m.user?.name}</span>
                  <span className={`text-xs shrink-0 ${
                    m.status === 'ACCEPTED' ? 'text-green-600' :
                    m.status === 'DECLINED' ? 'text-red-500' :
                    m.status === 'PENDING'  ? 'text-blue-500' : 'text-yellow-600'
                  }`}>
                    {m.status === 'ACCEPTED' ? 'członek' :
                     m.status === 'DECLINED' ? 'odrzucony' :
                     m.status === 'PENDING'  ? 'oczekuje' : 'zaproszony'}
                  </span>
                  {isOwner && m.status === 'PENDING' && (
                    <button onClick={() => handleApprove(m.user?.id)}
                      className="text-green-600 hover:text-green-800 text-xs font-medium shrink-0">
                      Akceptuj
                    </button>
                  )}
                  {isOwner && (
                    <button onClick={() => handleRemove(m.user?.id, m.user?.name)}
                      className="text-gray-400 hover:text-red-500 text-xs shrink-0 transition-colors">
                      Usuń
                    </button>
                  )}
                </div>
              ))}

              {isMember && (
                <button onClick={handleLeave}
                  className="mt-3 w-full text-xs text-red-400 hover:text-red-600 border border-red-200 hover:border-red-400 py-1.5 rounded-lg transition-colors">
                  Opuść grupę
                </button>
              )}
            </div>

            {isOwner && (
              <div className="mt-4 pt-4 border-t border-gray-100">
                <InviteSearchInput
                  groupId={id}
                  alreadyMemberIds={alreadyMemberIds}
                  onInvited={refresh}
                />
              </div>
            )}
          </div>

          {/* Czat */}
          <div className="md:col-span-2 bg-white rounded-xl border border-gray-200 p-5">
            <Chat groupId={id} currentUserId={user?.userId} />
          </div>
        </div>
      )}

      {!hasFullAccess && (
        <div className="bg-gray-50 border border-gray-200 rounded-xl p-6 text-center text-gray-500 text-sm">
          Lista członków i czat są dostępne po dołączeniu do grupy.
        </div>
      )}
    </div>
  );
}
