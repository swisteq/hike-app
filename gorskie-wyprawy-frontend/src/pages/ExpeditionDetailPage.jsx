import { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { MapContainer, TileLayer, Polyline, Marker, Popup } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

const FEATURE_COLORS = {
  'T.PK': '#747887', 'T.PKLT': '#747887', 'T.MT': '#747887',
  'T.PASS': '#747887', 'T.SADL': '#747887', 'T.RDG': '#747887',
  'T.VAL': '#16a34a',
  'S.HUT': '#92400e', 'S.RSRT': '#92400e',
  'H.LK': '#2563eb', 'H.FLLS': '#2563eb', 'H.SPNG': '#2563eb',
  'L.PRK': '#9333ea',
};

function getFeatureColor(featureClass, featureCode) {
  return FEATURE_COLORS[`${featureClass}.${featureCode}`] || '#6b7280';
}

function makeLocationIcon(featureClass, featureCode) {
  const color = getFeatureColor(featureClass, featureCode);
  return L.divIcon({
    className: '',
    html: `<div style="width:10px;height:10px;border-radius:50%;background:${color};border:2px solid white;box-shadow:0 1px 3px rgba(0,0,0,0.4)"></div>`,
    iconAnchor: [6, 6],
  });
}

function makeStartIcon() {
  return L.divIcon({
    className: '',
    html: `<div style="width:14px;height:14px;border-radius:50%;background:#16a34a;border:2px solid white;box-shadow:0 1px 4px rgba(0,0,0,0.5)"></div>`,
    iconAnchor: [7, 7],
  });
}

function makeEndIcon() {
  return L.divIcon({
    className: '',
    html: `<div style="width:14px;height:14px;border-radius:50%;background:#dc2626;border:2px solid white;box-shadow:0 1px 4px rgba(0,0,0,0.5)"></div>`,
    iconAnchor: [7, 7],
  });
}
import {
  getExpedition, deleteExpedition, inviteToExpedition,
  respondToInvite, addComment, updateExpedition, getExpeditionTrack,
  pinComment, generateInviteLink, removeMember, leaveExpedition,
  joinExpedition, approveMember, searchUsers, cancelExpedition, markExpeditionCompleted, markExpeditionUnrealized,
  updateMemberRole, updateExpeditionEquipment, changeExpeditionTrail, getAuditLogs,
  addDayTrail, clearDayTrail, getDayTrack, setDayAccommodation, removeDayAccommodation,
  addTransportOption, updateTransportOption, deleteTransportOption, approveTransportOption, resolvePlaceName,
  deleteTransportSection,
} from '../api/client';
import { useAuth } from '../context/AuthContext';

const STATUS_LABELS = { PLANNED: 'Planowana', ONGOING: 'W trakcie', COMPLETED: 'Zakończona', CANCELLED: 'Odwołana', UNREALIZED: 'Niezrealizowana' };

const EQUIPMENT_ITEMS = [
  { value: 'RACZKI',                   label: 'Raczki' },
  { value: 'RAKI',                     label: 'Raki' },
  { value: 'CZEKAN',                   label: 'Czekan' },
  { value: 'KIJKI_TREKKINGOWE',        label: 'Kijki trekkingowe' },
  { value: 'STUPTUTY',                 label: 'Stuptuty' },
  { value: 'KASK',                     label: 'Kask' },
  { value: 'LATARKA_CZOLOWA',          label: 'Latarka czołowa' },
  { value: 'KREM_Z_FILTREM',           label: 'Krem z filtrem' },
  { value: 'OKULARY_PRZECIWSLONECZNE', label: 'Okulary przeciwsłoneczne' },
];

// ---- Komponent pojedynczego komentarza (rekurencyjny) ----
function Comment({ comment, expeditionId, isOrganizer, onRefresh }) {
  const [replying, setReplying] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const [replyText, setReplyText] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const hasReplies = comment.replies?.length > 0;

  const handleReply = async (e) => {
    e.preventDefault();
    if (!replyText.trim()) return;
    setSubmitting(true);
    try {
      await addComment(expeditionId, replyText, comment.id);
      setReplyText('');
      setReplying(false);
      setExpanded(true);
      onRefresh();
    } catch (err) {
      alert(err.response?.data?.message || 'Błąd wysyłania odpowiedzi');
    } finally {
      setSubmitting(false);
    }
  };

  const handlePin = async () => {
    try {
      await pinComment(expeditionId, comment.id);
      onRefresh();
    } catch (err) {
      alert(err.response?.data?.message || 'Błąd przypinania');
    }
  };

  return (
    <div className={`${comment.pinned ? 'bg-amber-50 border border-amber-200 rounded-lg p-3' : ''}`}>
      <div className="flex gap-3">
        <div className="w-7 h-7 rounded-full bg-mountain-100 text-mountain-700 flex items-center justify-center text-xs font-medium shrink-0">
          {comment.author?.name?.[0]}
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-baseline gap-2 flex-wrap">
            {comment.pinned && (
              <span className="text-xs text-amber-600 font-semibold">Przypięty</span>
            )}
            <span className="text-sm font-medium">{comment.author?.name}</span>
            <span className="text-xs text-gray-400">
              {new Date(comment.createdAt).toLocaleDateString('pl-PL', {
                day: 'numeric', month: 'short', year: 'numeric',
              })}
            </span>
          </div>

          <p className="text-sm text-gray-700 mt-0.5 whitespace-pre-wrap">{comment.content}</p>

          {/* Akcje */}
          <div className="flex gap-3 mt-1.5 text-xs">
            <button
              onClick={() => setReplying(r => !r)}
              className="text-gray-400 hover:text-mountain-600 transition-colors"
            >
              Odpowiedz
            </button>
            {isOrganizer && !comment.parentId && (
              <button
                onClick={handlePin}
                className="text-gray-400 hover:text-amber-600 transition-colors"
              >
                {comment.pinned ? 'Odepnij' : 'Przypisz'}
              </button>
            )}
            {hasReplies && (
              <button
                onClick={() => setExpanded(e => !e)}
                className="text-gray-400 hover:text-mountain-600 transition-colors"
              >
                {expanded
                  ? `Ukryj odpowiedzi`
                  : `Pokaż ${comment.replies.length} odpowiedź/odpowiedzi`}
              </button>
            )}
          </div>

          {/* Formularz odpowiedzi */}
          {replying && (
            <form onSubmit={handleReply} className="mt-2">
              <textarea
                value={replyText}
                onChange={e => setReplyText(e.target.value)}
                placeholder={`Odpowiedz dla ${comment.author?.name}...`}
                rows={2}
                autoFocus
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-mountain-400 resize-none"
              />
              <div className="flex gap-2 mt-1.5">
                <button
                  type="submit"
                  disabled={submitting || !replyText.trim()}
                  className="bg-mountain-600 hover:bg-mountain-700 disabled:bg-mountain-300 text-white px-3 py-1 rounded-lg text-xs font-medium"
                >
                  Wyślij
                </button>
                <button
                  type="button"
                  onClick={() => { setReplying(false); setReplyText(''); }}
                  className="text-gray-400 hover:text-gray-600 text-xs px-2"
                >
                  Anuluj
                </button>
              </div>
            </form>
          )}

          {/* Odpowiedzi (rozwijane) */}
          {expanded && hasReplies && (
            <div className="mt-3 pl-4 border-l-2 border-gray-100 space-y-3">
              {comment.replies.map(reply => (
                <Comment
                  key={reply.id}
                  comment={reply}
                  expeditionId={expeditionId}
                  isOrganizer={isOrganizer}
                  onRefresh={onRefresh}
                />
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

// ---- Formularz zapraszania z podpowiedziami ----
function InviteSearchInput({ expeditionId, alreadyMemberIds, onInvited }) {
  const [query, setQuery] = useState('');
  const [suggestions, setSuggestions] = useState([]);
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const debounceRef = useRef(null);
  const wrapperRef = useRef(null);

  // Zamknij dropdown po kliknięciu poza
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
        // Odfiltruj już istniejących członków
        setSuggestions(data.filter(u => !alreadyMemberIds.has(u.id)));
        setOpen(true);
      } catch { /* ignoruj */ }
    }, 300);
  };

  const handleSelect = async (u) => {
    setOpen(false);
    setQuery('');
    setSuggestions([]);
    setBusy(true);
    try {
      await inviteToExpedition(expeditionId, u.name);
      onInvited();
    } catch (err) {
      alert(err.response?.data?.message || 'Błąd zaproszenia');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div ref={wrapperRef} className="relative">
      <p className="text-xs text-gray-500 mb-1.5">Zaproś użytkownika:</p>
      <div className="flex gap-2">
        <input
          type="text"
          value={query}
          onChange={handleChange}
          onFocus={() => suggestions.length > 0 && setOpen(true)}
          placeholder="Wpisz nazwę..."
          disabled={busy}
          className="flex-1 border border-gray-300 rounded-lg px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-mountain-400 disabled:bg-gray-50"
        />
        {busy && (
          <div className="w-8 flex items-center justify-center">
            <div className="w-4 h-4 border-2 border-mountain-400 border-t-transparent rounded-full animate-spin" />
          </div>
        )}
      </div>
      {open && suggestions.length > 0 && (
        <ul className="absolute z-10 mt-1 w-full bg-white border border-gray-200 rounded-lg shadow-lg overflow-hidden">
          {suggestions.map(u => (
            <li key={u.id}>
              <button
                type="button"
                onMouseDown={() => handleSelect(u)}
                className="w-full flex items-center gap-2 px-3 py-2 text-sm hover:bg-mountain-50 transition-colors text-left"
              >
                <div className="w-6 h-6 rounded-full bg-mountain-100 text-mountain-700 flex items-center justify-center text-xs font-medium shrink-0">
                  {u.name[0].toUpperCase()}
                </div>
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

const TRANSPORT_TYPE_LABELS = { CAR: 'Auto', PUBLIC_TRANSPORT: 'Komunikacja' };
const TRANSPORT_TYPES = ['CAR', 'PUBLIC_TRANSPORT'];
const EMPTY_OPTION = { meetingPoint: '', transportType: null, description: '', url: '', seats: '' };

function TypeSelector({ value, onChange }) {
  return (
    <div className="flex gap-1.5">
      {TRANSPORT_TYPES.map(t => (
        <button key={t} type="button" onClick={() => onChange(t)}
          className={`text-xs px-2.5 py-1 rounded-lg border transition-colors ${
            value === t
              ? 'bg-mountain-600 text-white border-mountain-600'
              : 'text-gray-500 border-gray-200 hover:border-gray-300 hover:text-gray-700'
          }`}>
          {TRANSPORT_TYPE_LABELS[t]}
        </button>
      ))}
    </div>
  );
}

function TypeBadge({ type, block }) {
  if (!type) return null;
  if (block) return (
    <div className="text-sm px-3 py-1.5 rounded-lg border font-medium bg-blue-50 text-blue-700 border-blue-200">
      {TRANSPORT_TYPE_LABELS[type]}
    </div>
  );
  return (
    <span className="text-xs text-gray-400 bg-gray-100 px-1.5 py-0.5 rounded shrink-0">
      {TRANSPORT_TYPE_LABELS[type]}
    </span>
  );
}

const isGoogleMapsUrl = (val) => /https?:\/\/maps\.app\.goo\.gl/.test(val);

function OptionForm({ input, setInput, onSave, onCancel, saveLabel, loading }) {
  const [resolving, setResolving] = useState(false);
  const [resolvedPreview, setResolvedPreview] = useState(null);

  useEffect(() => {
    if (isGoogleMapsUrl(input.meetingPoint)) {
      setResolving(true);
      resolvePlaceName(input.meetingPoint)
        .then(({ data }) => setResolvedPreview(data.name ? { name: data.name, url: input.meetingPoint } : null))
        .catch(() => setResolvedPreview(null))
        .finally(() => setResolving(false));
    } else {
      setResolvedPreview(null);
    }
  }, []);

  const handleMeetingPointPaste = async (e) => {
    const pasted = e.clipboardData.getData('text');
    if (!isGoogleMapsUrl(pasted)) return;
    setResolving(true);
    setResolvedPreview(null);
    try {
      const { data } = await resolvePlaceName(pasted);
      if (data.name) setResolvedPreview({ name: data.name, url: pasted });
    } catch {}
    finally { setResolving(false); }
  };

  return (
    <div className="space-y-1.5">
      <div>
        <input type="text" value={input.meetingPoint}
          onChange={e => { setInput(p => ({ ...p, meetingPoint: e.target.value })); if (!isGoogleMapsUrl(e.target.value)) setResolvedPreview(null); }}
          onPaste={handleMeetingPointPaste}
          placeholder="Miejsce zbiórki — nazwa lub link Google Maps"
          className="w-full border border-gray-200 rounded-lg px-3 py-1.5 text-sm focus:outline-none focus:border-mountain-400" />
        {resolving && <p className="text-xs text-gray-400 mt-0.5">Pobieranie nazwy miejsca...</p>}
        {resolvedPreview && (
          <p className="text-xs mt-0.5 text-gray-500">
            <a href={resolvedPreview.url} target="_blank" rel="noopener noreferrer"
              className="text-mountain-600 hover:underline">{resolvedPreview.name}</a>
          </p>
        )}
      </div>
      <TypeSelector value={input.transportType}
        onChange={v => setInput(p => ({ ...p, transportType: v, seats: v === 'CAR' ? p.seats : '' }))} />
      {!input.transportType && <p className="text-xs text-red-400">Wybór rodzaju transportu jest wymagany</p>}
      {input.transportType === 'CAR' && (
        <div className="flex items-center gap-2">
          <input type="number" min="1" max="50" value={input.seats}
            onChange={e => setInput(p => ({ ...p, seats: e.target.value }))}
            placeholder="Liczba miejsc"
            className="w-36 border border-gray-200 rounded-lg px-3 py-1.5 text-sm focus:outline-none focus:border-mountain-400" />
          <span className="text-xs text-gray-400">Kierowca: Ty</span>
        </div>
      )}
      <input type="text" value={input.description}
        onChange={e => setInput(p => ({ ...p, description: e.target.value }))}
        placeholder={input.transportType === 'CAR' ? 'np. Kraków - Kuźnice' : 'np. Bus Kraków → Zakopane, linia 304, godz. 8:30'}
        className="w-full border border-gray-200 rounded-lg px-3 py-1.5 text-sm focus:outline-none focus:border-mountain-400" />
      {input.transportType !== 'CAR' && (
        <input type="text" value={input.url}
          onChange={e => setInput(p => ({ ...p, url: e.target.value }))}
          placeholder="Link do rezerwacji (opcjonalnie)"
          className="w-full border border-gray-200 rounded-lg px-3 py-1.5 text-sm focus:outline-none focus:border-mountain-400" />
      )}
      <div className="flex gap-2">
        <button onClick={onSave}
          disabled={loading || !input.description.trim() || !input.transportType || (input.transportType === 'CAR' && !input.seats)}
          className="text-xs bg-mountain-600 hover:bg-mountain-700 text-white px-3 py-1.5 rounded-lg disabled:opacity-50 transition-colors">
          {loading ? '...' : saveLabel}
        </button>
        <button onClick={onCancel} className="text-xs text-gray-400 hover:text-gray-600">Anuluj</button>
      </div>
    </div>
  );
}

// ---- Podsekcja transportu ----
function TransportSectionBlock({ type, label, section, canAdd, canEdit, expeditionId, onRefresh, onDeleteSection }) {
  const [addingOption, setAddingOption] = useState(false);
  const [optionInput, setOptionInput] = useState(EMPTY_OPTION);
  const [editingOptionId, setEditingOptionId] = useState(null);
  const [editingOptionInput, setEditingOptionInput] = useState(EMPTY_OPTION);
  const [loading, setLoading] = useState(false);

  const hasContent = section?.options?.length > 0;
  if (!canAdd && !canEdit && !hasContent) return null;

  const handleApprove = async (optionId) => {
    try { await approveTransportOption(expeditionId, optionId); onRefresh(); }
    catch { alert('Błąd zatwierdzania formy transportu'); }
  };

  const startAdd = () => { setOptionInput(EMPTY_OPTION); setAddingOption(true); };
  const cancelAdd = () => { setAddingOption(false); setOptionInput(EMPTY_OPTION); };

  const handleAdd = async () => {
    if (!optionInput.description.trim()) return;
    setLoading(true);
    try {
      await addTransportOption(expeditionId, type, {
        meetingPoint: optionInput.meetingPoint.trim() || null,
        transportType: optionInput.transportType,
        description: optionInput.description.trim(),
        url: optionInput.url.trim() || null,
        seats: optionInput.seats ? parseInt(optionInput.seats) : null,
      });
      cancelAdd(); onRefresh();
    } catch { alert('Błąd dodawania formy transportu'); }
    finally { setLoading(false); }
  };

  const startEdit = (opt) => {
    setEditingOptionId(opt.id);
    setEditingOptionInput({
      meetingPoint: opt.meetingPointUrl || opt.meetingPoint || '',
      transportType: opt.transportType || null,
      description: opt.description,
      url: opt.url || '',
      seats: opt.seats || '',
    });
  };
  const cancelEdit = () => { setEditingOptionId(null); setEditingOptionInput(EMPTY_OPTION); };

  const handleUpdate = async (optionId) => {
    if (!editingOptionInput.description.trim()) return;
    setLoading(true);
    try {
      await updateTransportOption(expeditionId, optionId, {
        meetingPoint: editingOptionInput.meetingPoint.trim() || null,
        transportType: editingOptionInput.transportType,
        description: editingOptionInput.description.trim(),
        url: editingOptionInput.url.trim() || null,
        seats: editingOptionInput.seats ? parseInt(editingOptionInput.seats) : null,
      });
      cancelEdit(); onRefresh();
    } catch { alert('Błąd aktualizacji formy transportu'); }
    finally { setLoading(false); }
  };

  const handleDelete = async (optionId) => {
    if (!window.confirm('Usunąć tę formę transportu?')) return;
    try { await deleteTransportOption(expeditionId, optionId); onRefresh(); }
    catch { alert('Błąd usuwania formy transportu'); }
  };

  return (
    <div className="border border-gray-300 rounded-lg p-3">
      <div className="flex items-center justify-between mb-2">
        <span className="font-bold text-sm text-gray-700">{label}</span>
        {onDeleteSection && canEdit && (
          <button type="button" onClick={onDeleteSection}
            className="text-xs text-gray-400 hover:text-red-500 border border-gray-200 hover:border-red-300 px-2 py-0.5 rounded transition-colors">
            Usuń
          </button>
        )}
      </div>
      {editingOptionId ? (
        <div className="mt-1">
          {section?.options?.map(opt => opt.id === editingOptionId ? (
            <OptionForm key={opt.id} input={editingOptionInput} setInput={setEditingOptionInput}
              onSave={() => handleUpdate(opt.id)} onCancel={cancelEdit} saveLabel="Zapisz" loading={loading} />
          ) : null)}
        </div>
      ) : (
        <>
          {section?.options?.length > 0 && (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2 mt-1">
              {section.options.map(opt => (
                <div key={opt.id} className="border border-gray-300 rounded-lg p-2.5 text-sm space-y-1">
                  <TypeBadge type={opt.transportType} block />
                  {opt.meetingPoint && (
                    <div className="text-xs text-gray-500">
                      {opt.meetingPointUrl ? (
                        <a href={opt.meetingPointUrl} target="_blank" rel="noopener noreferrer"
                          className="text-mountain-600 hover:underline">{opt.meetingPoint}</a>
                      ) : opt.meetingPoint}
                    </div>
                  )}
                  <div>
                    {opt.url ? (
                      <a href={opt.url} target="_blank" rel="noopener noreferrer"
                        className="text-mountain-600 hover:underline">{opt.description}</a>
                    ) : (
                      <span className="text-gray-700">{opt.description}</span>
                    )}
                  </div>
                  {opt.transportType === 'CAR' && (opt.seats || opt.driverUsername) && (
                    <div className="text-xs text-gray-500 flex gap-3">
                      {opt.seats && <span>Miejsca: {opt.seats}</span>}
                      {opt.driverUsername && <span>Kierowca: {opt.driverUsername}</span>}
                    </div>
                  )}
                  {!opt.approved && (
                    <span className="text-xs text-amber-600 bg-amber-50 border border-amber-200 px-1.5 py-0.5 rounded">
                      Oczekuje na zatwierdzenie
                    </span>
                  )}
                  {(canEdit || canAdd) && (
                    <div className="flex gap-1.5 pt-0.5 flex-wrap">
                      {canEdit && !opt.approved && (
                        <button onClick={() => handleApprove(opt.id)}
                          className="text-xs text-green-600 hover:text-green-800 border border-green-200 hover:border-green-400 px-2 py-0.5 rounded-lg transition-colors">
                          Zatwierdź
                        </button>
                      )}
                      {canEdit && (
                        <>
                          <button onClick={() => startEdit(opt)}
                            className="text-xs text-gray-500 hover:text-gray-700 border border-gray-200 hover:border-gray-300 px-2 py-0.5 rounded-lg transition-colors">
                            Edytuj
                          </button>
                          <button onClick={() => handleDelete(opt.id)}
                            className="text-xs text-gray-500 hover:text-red-500 border border-gray-200 hover:border-red-300 px-2 py-0.5 rounded-lg transition-colors">
                            Usuń
                          </button>
                        </>
                      )}
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
          {(canAdd || canEdit) && (
            addingOption ? (
              <div className="mt-2">
                <OptionForm input={optionInput} setInput={setOptionInput}
                  onSave={handleAdd} onCancel={cancelAdd} saveLabel="Dodaj" loading={loading} />
              </div>
            ) : (
              <button onClick={startAdd}
                className="mt-2 text-xs text-gray-500 hover:text-gray-700 px-2.5 py-1 rounded-lg border border-gray-200 hover:border-gray-300 transition-colors">
                Dodaj
              </button>
            )
          )}
        </>
      )}
    </div>
  );
}

// ---- Główna strona ----
export default function ExpeditionDetailPage() {
  const { id } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();

  const [expedition, setExpedition] = useState(null);
  const [loading, setLoading] = useState(true);
  const [trackPoints, setTrackPoints] = useState([]);

  const [inviteLink, setInviteLink] = useState('');
  const [generatingLink, setGeneratingLink] = useState(false);

  const [newComment, setNewComment] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const [editingEquipment, setEditingEquipment] = useState(false);
  const [equipmentDraft, setEquipmentDraft] = useState({});
  const [savingEquipment, setSavingEquipment] = useState(false);
  const [equipmentError, setEquipmentError] = useState('');

  const [accommodationForm, setAccommodationForm] = useState(null); // null | dayNumber
  const [accommodationInput, setAccommodationInput] = useState('');
  const [accommodationLoading, setAccommodationLoading] = useState(false);

  const [transportExpanded, setTransportExpanded] = useState(true);
  const [addedDayNumbers, setAddedDayNumbers] = useState(new Set());
  const [managingMembers, setManagingMembers] = useState(false);
  const [changingRoleFor, setChangingRoleFor] = useState(null);
  const [changingTrail, setChangingTrail] = useState(false);
  const [changingTrailDay, setChangingTrailDay] = useState(null); // null = main, int = day number
  const [trailFile, setTrailFile] = useState(null);
  const [trailChangeLoading, setTrailChangeLoading] = useState(false);
  const [trailFileError, setTrailFileError] = useState('');

  const [selectedDayNumber, setSelectedDayNumber] = useState(1);
  const [dayTrackPoints, setDayTrackPoints] = useState([]);

  const [showLogs, setShowLogs] = useState(false);
  const [auditLogs, setAuditLogs] = useState([]);
  const [logsLoading, setLogsLoading] = useState(false);

  const refresh = () => {
    getExpedition(id)
      .then(({ data }) => setExpedition(data))
      .catch(() => navigate('/expeditions'))
      .finally(() => setLoading(false));
  };

  useEffect(() => { refresh(); }, [id]);

  useEffect(() => {
    if (!id) return;
    getExpeditionTrack(id)
      .then(({ data }) => setTrackPoints(data))
      .catch(() => {});
  }, [id]);

  useEffect(() => {
    if (!expedition || !expedition.days?.length) return;
    getDayTrack(id, selectedDayNumber)
      .then(({ data }) => setDayTrackPoints(data))
      .catch(() => setDayTrackPoints([]));
  }, [id, expedition, selectedDayNumber]);

  if (loading) return <div className="text-center py-16 text-gray-400">Ładowanie...</div>;
  if (!expedition) return null;

  const viewerRole = expedition.viewerRole; // ORGANIZER | MEMBER | NAWIGATOR | LOGISTYK | PENDING | INVITED | VISITOR
  const isOrganizer = viewerRole === 'ORGANIZER';
  const isNavigator = viewerRole === 'NAWIGATOR';
  const isMember = ['MEMBER', 'NAWIGATOR', 'LOGISTYK'].includes(viewerRole);
  const hasFullAccess = isOrganizer || isMember;
  const canManageTrailAndEquipment = isOrganizer || isNavigator;
  const myMembership = expedition.members?.find(m => m.user?.id === user?.userId);

  const handleDelete = async () => {
    if (!window.confirm('Czy na pewno chcesz usunąć tę wyprawę?')) return;
    await deleteExpedition(id);
    navigate('/expeditions');
  };



  const handleClearDayTrail = async (dayNumber) => {
    if (!window.confirm('Oznaczyć ten dzień jako dzień odpoczynku? Trasa i dane GPX zostaną usunięte.')) return;
    try {
      await clearDayTrail(id, dayNumber);
      setDayTrackPoints([]);
      refresh();
    } catch {
      alert('Błąd usuwania trasy dnia');
    }
  };

  const handleGenerateLink = async () => {
    setGeneratingLink(true);
    try {
      const { data } = await generateInviteLink(id);
      setInviteLink(`${window.location.origin}/join/${data.token}`);
    } catch (err) {
      alert('Błąd generowania linku');
    } finally {
      setGeneratingLink(false);
    }
  };

  const handleCopyLink = () => {
    navigator.clipboard.writeText(inviteLink);
  };

  const handleRespond = async (accept) => {
    await respondToInvite(id, accept);
    refresh();
  };

  const handleRemoveMember = async (userId, name) => {
    if (!window.confirm(`Usunąć ${name} z wyprawy?`)) return;
    try {
      await removeMember(id, userId);
      refresh();
    } catch (err) {
      alert(err.response?.data?.message || 'Błąd usuwania uczestnika');
    }
  };

  const handleLeave = async () => {
    if (!window.confirm('Czy na pewno chcesz opuścić tę wyprawę?')) return;
    try {
      await leaveExpedition(id);
      navigate('/expeditions');
    } catch (err) {
      alert(err.response?.data?.message || 'Błąd opuszczania wyprawy');
    }
  };

  const handleComment = async (e) => {
    e.preventDefault();
    if (!newComment.trim()) return;
    setSubmitting(true);
    try {
      await addComment(id, newComment);
      setNewComment('');
      refresh();
    } finally {
      setSubmitting(false);
    }
  };


  const handleCancel = async () => {
    if (!window.confirm('Czy na pewno chcesz odwołać tę wyprawę?')) return;
    try {
      await cancelExpedition(id);
      refresh();
    } catch (err) {
      alert(err.response?.data?.message || 'Błąd odwoływania wyprawy');
    }
  };

  const handleMarkCompleted = async () => {
    if (!window.confirm('Czy na pewno chcesz oznaczyć tę wyprawę jako zakończoną?')) return;
    try {
      await markExpeditionCompleted(id);
      refresh();
    } catch (err) {
      alert(err.response?.data?.message || 'Błąd zmiany statusu wyprawy');
    }
  };

  const handleMarkUnrealized = async () => {
    if (!window.confirm('Czy na pewno chcesz oznaczyć tę wyprawę jako niezrealizowaną?')) return;
    try {
      await markExpeditionUnrealized(id);
      refresh();
    } catch (err) {
      alert(err.response?.data?.message || 'Błąd zmiany statusu wyprawy');
    }
  };

  const handleJoin = async () => {
    try {
      await joinExpedition(id);
      refresh();
    } catch (err) {
      alert(err.response?.data?.message || 'Błąd dołączania do wyprawy');
    }
  };

  const handleApprove = async (userId) => {
    try {
      await approveMember(id, userId);
      refresh();
    } catch (err) {
      alert(err.response?.data?.message || 'Błąd zatwierdzania uczestnika');
    }
  };

  const handleTrailChange = async () => {
    if (!trailFile) return;
    setTrailChangeLoading(true);
    setTrailFileError('');
    try {
      const fd = new FormData();
      fd.append('file', trailFile);
      if (changingTrailDay != null) {
        await addDayTrail(id, changingTrailDay, fd);
        setDayTrackPoints([]);
      } else {
        await changeExpeditionTrail(id, fd);
      }
      setChangingTrail(false);
      setChangingTrailDay(null);
      setTrailFile(null);
      refresh();
    } catch (err) {
      setTrailFileError(err.response?.data?.message || 'Błąd zmiany trasy');
    } finally {
      setTrailChangeLoading(false);
    }
  };

  const loadAndShowLogs = async () => {
    setLogsLoading(true);
    try {
      const { data } = await getAuditLogs(id);
      setAuditLogs(data);
      setShowLogs(true);
    } catch { /* ignoruj */ } finally {
      setLogsLoading(false);
    }
  };

  const totalComments = (comments) =>
    comments.reduce((acc, c) => acc + 1 + (c.replies?.length ?? 0), 0);

  return (
    <div className="max-w-4xl mx-auto px-4 py-8">
      <Link
        to={hasFullAccess ? '/expeditions' : '/browse'}
        className="text-mountain-600 hover:text-mountain-800 text-sm mb-4 inline-block"
      >
        {hasFullAccess ? '← Moje wyprawy' : '← Przeglądaj wyprawy'}
      </Link>

      {/* Nagłówek */}
      <div className="bg-white rounded-xl border border-gray-200 p-6 mb-4">
        <div className="flex items-start justify-between gap-4 mb-4">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">{expedition.name}</h1>
            <p className="text-gray-500 text-sm mt-1">
              Organizator: <span className="font-medium">{expedition.organizer?.name}</span>
            </p>
          </div>
          <div className="flex items-center gap-2 flex-wrap justify-end">
            <span className={`text-sm px-3 py-1.5 rounded-lg border font-medium ${
              expedition.status === 'PLANNED'   ? 'bg-blue-50 text-blue-700 border-blue-200' :
              expedition.status === 'ONGOING'   ? 'bg-green-50 text-green-700 border-green-200' :
              expedition.status === 'COMPLETED' ? 'bg-gray-100 text-gray-600 border-gray-200' :
                                                  'bg-red-50 text-red-600 border-red-200'
            }`}>
              {STATUS_LABELS[expedition.status]}
            </span>
            {isOrganizer && (expedition.status === 'PLANNED' || expedition.status === 'ONGOING') && (
              <button
                onClick={handleCancel}
                className="text-sm text-orange-500 hover:text-orange-700 border border-orange-200 hover:border-orange-400 px-3 py-1.5 rounded-lg transition-colors"
              >
                Odwołaj
              </button>
            )}
            {isOrganizer && expedition.awaitingStatusDeclaration && (
              <>
                <button
                  onClick={handleMarkCompleted}
                  className="text-sm text-green-600 hover:text-green-800 border border-green-200 hover:border-green-400 px-3 py-1.5 rounded-lg transition-colors"
                >
                  Zakończona
                </button>
                <button
                  onClick={handleMarkUnrealized}
                  className="text-sm text-yellow-600 hover:text-yellow-800 border border-yellow-200 hover:border-yellow-400 px-3 py-1.5 rounded-lg transition-colors"
                >
                  Niezrealizowana
                </button>
              </>
            )}
            {isOrganizer && (
              <button
                onClick={handleDelete}
                className="text-sm text-red-500 hover:text-red-700 border border-red-200 hover:border-red-400 px-3 py-1.5 rounded-lg transition-colors"
              >
                Usuń
              </button>
            )}
          </div>
        </div>

        <div className="grid grid-cols-2 gap-4 text-sm">
          <div>
            <span className="text-gray-500">
              {expedition.endDate ? 'Termin:' : 'Data i godzina startu:'}
            </span>
            <span className="ml-2 font-medium">
              {expedition.endDate ? (
                <>
                  {new Date(expedition.plannedDate).toLocaleDateString('pl-PL', { day: 'numeric', month: 'long', year: 'numeric' })}
                  {' — '}
                  {new Date(expedition.endDate).toLocaleDateString('pl-PL', { day: 'numeric', month: 'long', year: 'numeric' })}
                  {expedition.days?.length ? ` (${expedition.days.length} dni)` : ''}
                </>
              ) : (
                <>
                  {new Date(expedition.plannedDate).toLocaleDateString('pl-PL', {
                    weekday: 'long', day: 'numeric', month: 'long', year: 'numeric'
                  })}
                  {expedition.startTime && `, godz. ${expedition.startTime.substring(0, 5)}`}
                </>
              )}
            </span>
          </div>
          {!expedition.days?.length && (
            <div>
              <span className="text-gray-500">Trasa:</span>
              <span className="ml-2 font-medium">{expedition.routeLabel || expedition.trailName || expedition.trail?.name || '—'}</span>
            </div>
          )}
        </div>

        {(expedition.distanceKm || expedition.elevationGainM || expedition.durationFormatted) && (
          <div className="flex gap-4 mt-3 text-xs text-gray-500">
            {expedition.distanceKm && <span>Dystans: {expedition.distanceKm} km</span>}
            {expedition.elevationGainM && <span>Podejście: +{expedition.elevationGainM} m</span>}
            {expedition.elevationLossM && <span>Zejście: -{expedition.elevationLossM} m</span>}
            {expedition.maxElevationM && <span>Maks. wys.: {expedition.maxElevationM} m n.p.m.</span>}
            {expedition.durationFormatted && <span>Czas: {expedition.durationFormatted}</span>}
          </div>
        )}

        {/* Transport */}
        {(() => {
          const canEditTransport = isOrganizer || viewerRole === 'LOGISTYK';
          const canAddTransport = canEditTransport || ['MEMBER', 'NAWIGATOR'].includes(viewerRole);
          const sections = expedition.transportSections || [];
          const findSection = (sType, dayNum = null) =>
            sections.find(s => s.sectionType === sType && (s.dayNumber ?? null) === dayNum) || null;
          const hasAnyContent = sections.some(s => s.options?.length > 0);
          if (!canAddTransport && !hasAnyContent) return null;
          const sectionProps = { canAdd: canAddTransport, canEdit: canEditTransport, expeditionId: id, onRefresh: refresh };

          // Dni pośrednie: wszystkie poza ostatnim
          const allInterDays = expedition.days?.length > 1 ? expedition.days.slice(0, -1) : [];
          const daysWithContent = new Set(
            sections.filter(s => s.sectionType === 'DAY_TRANSITION' && s.options?.length > 0)
                    .map(s => s.dayNumber)
          );
          const visibleDayNumbers = new Set([...daysWithContent, ...addedDayNumbers]);
          const visibleDays = allInterDays.filter(d => visibleDayNumbers.has(d.dayNumber));
          const availableDays = allInterDays.filter(d => !visibleDayNumbers.has(d.dayNumber));

          return (
            <div className="mt-4 pt-4 border-t border-gray-100">
              <button
                onClick={() => setTransportExpanded(v => !v)}
                className="flex items-center justify-between w-full text-left mb-2"
              >
                <h2 className="font-semibold text-gray-800">Transport</h2>
                <span className="text-xs text-gray-400">{transportExpanded ? 'zwiń' : 'rozwiń'}</span>
              </button>
              {transportExpanded && (
                <div className="space-y-2">
                  <TransportSectionBlock {...sectionProps} type="arrival" label="Dojazd"
                    section={findSection('ARRIVAL')} />
                  {visibleDays.map(day => {
                    const backendSection = findSection('DAY_TRANSITION', day.dayNumber);
                    const handleDeleteDay = async () => {
                      if (!window.confirm(`Usunąć sekcję "Dzień ${day.dayNumber}" wraz ze wszystkimi opcjami transportu?`)) return;
                      if (backendSection) {
                        try { await deleteTransportSection(id, backendSection.id); }
                        catch { alert('Błąd usuwania sekcji transportu'); return; }
                      }
                      setAddedDayNumbers(prev => { const s = new Set(prev); s.delete(day.dayNumber); return s; });
                      refresh();
                    };
                    return (
                      <TransportSectionBlock key={day.dayNumber} {...sectionProps}
                        type={`day-${day.dayNumber}`}
                        label={`Dzień ${day.dayNumber}`}
                        section={backendSection}
                        onDeleteSection={handleDeleteDay} />
                    );
                  })}
                  <TransportSectionBlock {...sectionProps} type="return" label="Powrót"
                    section={findSection('RETURN')} />
                  {canAddTransport && availableDays.length > 0 ? (
                    <div className="flex flex-wrap gap-1.5 pt-1 items-center">
                      {availableDays.map(day => (
                        <button key={day.dayNumber} type="button"
                          onClick={() => setAddedDayNumbers(prev => new Set([...prev, day.dayNumber]))}
                          className="text-xs text-gray-500 hover:text-gray-700 border border-dashed border-gray-300 hover:border-gray-400 px-2.5 py-1 rounded-lg transition-colors">
                          + Dzień {day.dayNumber}
                        </button>
                      ))}
                      {isOrganizer && (
                        <button type="button" onClick={loadAndShowLogs} disabled={logsLoading}
                          title="Logi zmian"
                          className="ml-auto w-6 h-6 flex items-center justify-center rounded-full border border-gray-200 hover:border-gray-400 text-gray-400 hover:text-gray-600 text-xs font-bold transition-colors disabled:opacity-50">
                          !
                        </button>
                      )}
                    </div>
                  ) : isOrganizer ? (
                    <div className="flex justify-end pt-1">
                      <button type="button" onClick={loadAndShowLogs} disabled={logsLoading}
                        title="Logi zmian"
                        className="w-6 h-6 flex items-center justify-center rounded-full border border-gray-200 hover:border-gray-400 text-gray-400 hover:text-gray-600 text-xs font-bold transition-colors disabled:opacity-50">
                        !
                      </button>
                    </div>
                  ) : null}
                </div>
              )}
            </div>
          );
        })()}

        {expedition.description && (
          <p className="text-gray-600 text-sm mt-3 leading-relaxed">{expedition.description}</p>
        )}

        {viewerRole === 'INVITED' && (
          <div className="mt-4 p-4 bg-yellow-50 border border-yellow-200 rounded-lg flex items-center justify-between">
            <span className="text-sm text-yellow-800">Zostałeś zaproszony do tej wyprawy!</span>
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
            <span className="text-sm text-blue-800">
              Twoja prośba o dołączenie oczekuje na akceptację organizatora.
            </span>
          </div>
        )}

        {viewerRole === 'VISITOR' && expedition.status === 'PLANNED' && (
          <div className="mt-4 p-4 bg-mountain-50 border border-mountain-200 rounded-lg flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-mountain-800">Chcesz wziąć udział?</p>
              <p className="text-xs text-mountain-600 mt-0.5">
                {expedition.joinMode === 'APPROVAL_REQUIRED'
                  ? 'Organizator musi zaakceptować Twoją prośbę.'
                  : 'Dołącz od razu i uzyskaj dostęp do czatu i listy uczestników.'}
              </p>
            </div>
            <button
              onClick={handleJoin}
              className="bg-mountain-600 hover:bg-mountain-700 text-white px-4 py-2 rounded-lg text-sm font-medium shrink-0"
            >
              {expedition.joinMode === 'APPROVAL_REQUIRED' ? 'Poproś o dołączenie' : 'Dołącz'}
            </button>
          </div>
        )}

      </div>

      {/* Modal — logi zmian */}
      {showLogs && (
        <div
          className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/50"
          onClick={e => { if (e.target === e.currentTarget) setShowLogs(false); }}
        >
          <div className="bg-white rounded-xl shadow-xl w-full max-w-lg mx-4 p-6 max-h-[80vh] flex flex-col">
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-semibold text-gray-900 text-lg">Logi zmian</h3>
              <button
                onClick={() => setShowLogs(false)}
                className="text-gray-400 hover:text-gray-600 text-xl leading-none"
              >
                ×
              </button>
            </div>
            <div className="overflow-y-auto flex-1">
              {auditLogs.length === 0 ? (
                <p className="text-sm text-gray-400">Brak zarejestrowanych zmian.</p>
              ) : (
                <div className="space-y-3">
                  {auditLogs.map(log => (
                    <div key={log.id} className="border-l-2 border-gray-100 pl-3">
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${
                          log.changeType === 'TRAIL_CHANGED'     ? 'bg-blue-50 text-blue-700' :
                          log.changeType === 'DAY_TRAIL_CHANGED' ? 'bg-blue-50 text-blue-700' :
                          log.changeType === 'DAY_TRAIL_CLEARED' ? 'bg-gray-100 text-gray-600' :
                          log.changeType === 'EQUIPMENT_CHANGED' ? 'bg-amber-50 text-amber-700' :
                          log.changeType === 'ACCOMMODATION_CHANGED' ? 'bg-purple-50 text-purple-700' :
                          log.changeType === 'TRANSPORT_CHANGED' ? 'bg-green-50 text-green-700' :
                          'bg-gray-100 text-gray-500'
                        }`}>
                          { log.changeType === 'TRAIL_CHANGED'         ? 'Trasa' :
                            log.changeType === 'DAY_TRAIL_CHANGED'     ? 'Trasa dnia' :
                            log.changeType === 'DAY_TRAIL_CLEARED'     ? 'Odpoczynek' :
                            log.changeType === 'EQUIPMENT_CHANGED'     ? 'Sprzęt' :
                            log.changeType === 'ACCOMMODATION_CHANGED' ? 'Nocleg' :
                            log.changeType === 'TRANSPORT_CHANGED'     ? 'Transport' :
                            log.changeType }
                        </span>
                        <span className="text-gray-500 text-xs">{log.user?.name}</span>
                        <span className="text-gray-400 text-xs ml-auto">
                          {new Date(log.createdAt).toLocaleString('pl-PL', {
                            day: 'numeric', month: 'short', year: 'numeric',
                            hour: '2-digit', minute: '2-digit'
                          })}
                        </span>
                      </div>
                      <p className="text-sm text-gray-600 mt-0.5">{log.description}</p>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Modal — zmiana trasy */}
      {changingTrail && (
        <div
          className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/50"
          onClick={e => { if (e.target === e.currentTarget) { setChangingTrail(false); setChangingTrailDay(null); setTrailFile(null); setTrailFileError(''); } }}
        >
          <div className="bg-white rounded-xl shadow-xl w-full max-w-md mx-4 p-6">
            <h3 className="font-semibold text-gray-900 text-lg mb-4">
              {changingTrailDay != null ? `Zmień trasę GPX — Dzień ${changingTrailDay}` : 'Zmień trasę GPX'}
            </h3>
            <label className="flex flex-col items-center justify-center w-full h-24 border-2 border-dashed border-gray-300 rounded-lg cursor-pointer bg-gray-50 hover:bg-gray-100 transition-colors mb-3">
              <div className="text-center">
                {trailFile ? (
                  <>
                    <div className="text-mountain-600 font-medium text-sm">{trailFile.name}</div>
                    <div className="text-gray-400 text-xs mt-0.5">Kliknij, aby zmienić</div>
                  </>
                ) : (
                  <>
                    <div className="text-gray-500 text-sm">Kliknij lub przeciągnij plik .gpx</div>
                    <div className="text-gray-400 text-xs mt-0.5">Trasa zostanie przeliczona od nowa</div>
                  </>
                )}
              </div>
              <input
                type="file" accept=".gpx" className="hidden"
                onChange={e => {
                  const f = e.target.files[0];
                  if (f && !f.name.toLowerCase().endsWith('.gpx')) {
                    setTrailFileError('Plik musi mieć rozszerzenie .gpx');
                    return;
                  }
                  setTrailFileError('');
                  setTrailFile(f || null);
                }}
              />
            </label>
            {trailFileError && <p className="text-xs text-red-600 mb-3">{trailFileError}</p>}
            <div className="flex gap-2 justify-end">
              <button
                onClick={() => { setChangingTrail(false); setChangingTrailDay(null); setTrailFile(null); setTrailFileError(''); }}
                className="px-4 py-2 border border-gray-300 rounded-lg text-sm text-gray-600 hover:bg-gray-50"
              >
                Anuluj
              </button>
              <button
                onClick={handleTrailChange}
                disabled={!trailFile || trailChangeLoading}
                className="px-4 py-2 bg-mountain-600 hover:bg-mountain-700 disabled:bg-mountain-400 text-white rounded-lg text-sm font-medium"
              >
                {trailChangeLoading ? 'Przetwarzanie...' : 'Zapisz trasę'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Mapa trasy — wielodniowa */}
      {expedition.days?.length > 0 && (() => {
        const currentDay = expedition.days.find(d => d.dayNumber === selectedDayNumber);
        const pts = dayTrackPoints;
        const dayLocs = (expedition.locations ?? []).filter(loc =>
          loc.dayNumber === selectedDayNumber || loc.dayNumber == null);
        return (
          <div className="bg-white rounded-xl border border-gray-200 p-5 mb-4">
            <div className="flex items-center justify-between mb-3">
              <h2 className="font-semibold text-gray-800">Trasa na mapie</h2>
              {canManageTrailAndEquipment && (
                <div className="flex gap-2">
                  <button
                    onClick={() => { setChangingTrail(true); setChangingTrailDay(selectedDayNumber); setTrailFile(null); setTrailFileError(''); }}
                    className="text-xs text-mountain-600 hover:text-mountain-800 border border-mountain-300 hover:border-mountain-500 px-2.5 py-1 rounded-lg"
                  >
                    Zmień trasę dnia
                  </button>
                  <button
                    onClick={() => handleClearDayTrail(selectedDayNumber)}
                    className="text-xs text-mountain-600 hover:text-mountain-800 border border-mountain-300 hover:border-mountain-500 px-2.5 py-1 rounded-lg"
                  >
                    Dzień odpoczynku
                  </button>
                </div>
              )}
            </div>

            {/* Zakładki dni */}
            <div className="flex gap-1 mb-3 flex-wrap">
              {expedition.days.map(day => (
                <button
                  key={day.dayNumber}
                  onClick={() => setSelectedDayNumber(day.dayNumber)}
                  className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${
                    selectedDayNumber === day.dayNumber
                      ? 'bg-mountain-600 text-white'
                      : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                  }`}
                >
                  Dzień {day.dayNumber}
                  <span className="ml-1 opacity-70">
                    {new Date(day.dayDate).toLocaleDateString('pl-PL', { day: 'numeric', month: 'short' })}
                  </span>
                </button>
              ))}
            </div>

            {/* Statystyki dnia */}
            {currentDay && (
              <>
                {(currentDay.trailName || currentDay.highestPeakName) && (
                  <div className="flex gap-4 mb-1 text-xs text-gray-500">
                    {currentDay.trailName && <span className="font-bold text-gray-700">{currentDay.trailName}</span>}
                    {currentDay.highestPeakName && (
                      <span className="text-mountain-700 font-medium">Najwyższy szczyt: {currentDay.highestPeakName}{currentDay.highestPeakElevationM ? ` (${currentDay.highestPeakElevationM} m)` : ''}</span>
                    )}
                  </div>
                )}
                {(currentDay.distanceKm || currentDay.elevationGainM || currentDay.durationFormatted || currentDay.maxElevationM) && (
                  <div className="flex gap-4 mb-3 text-xs text-gray-500">
                    {currentDay.distanceKm && <span>Dystans: {currentDay.distanceKm} km</span>}
                    {currentDay.elevationGainM && <span>Podejście: +{currentDay.elevationGainM} m</span>}
                    {currentDay.elevationLossM && <span>Zejście: -{currentDay.elevationLossM} m</span>}
                    {currentDay.maxElevationM && <span>Maks. wys.: {currentDay.maxElevationM} m n.p.m.</span>}
                    {currentDay.durationFormatted && <span>Czas: {currentDay.durationFormatted}</span>}
                  </div>
                )}
              </>
            )}

            {pts.length > 0 ? (
              <>
                <div className="rounded-lg overflow-hidden border border-gray-200" style={{ height: 360 }}>
                  <MapContainer key={`day-${selectedDayNumber}`} center={pts[0]} zoom={13} style={{ height: '100%', width: '100%' }}>
                    <TileLayer
                      url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                      attribution='© <a href="https://openstreetmap.org">OpenStreetMap</a>'
                    />
                    <Polyline positions={pts} color="#1d6a3a" weight={3} opacity={0.85} />
                    <Marker position={pts[0]} icon={makeStartIcon()}><Popup>Start trasy</Popup></Marker>
                    <Marker position={pts[pts.length - 1]} icon={makeEndIcon()}><Popup>Koniec trasy</Popup></Marker>
                    {dayLocs.map(loc => (
                      <Marker
                        key={loc.id}
                        position={[loc.latitude, loc.longitude]}
                        icon={makeLocationIcon(loc.featureClass, loc.featureCode)}
                      >
                        <Popup>
                          <div className="text-sm">
                            <div className="font-semibold">{loc.name}</div>
                            <div className="text-gray-500 text-xs">{loc.typeLabelPl}</div>
                            {loc.distanceM && (
                              <div className="text-gray-400 text-xs">{Math.round(loc.distanceM)} m od trasy</div>
                            )}
                          </div>
                        </Popup>
                      </Marker>
                    ))}
                  </MapContainer>
                </div>
                <p className="text-xs text-gray-400 mt-2">
                  Start: {pts[0][0].toFixed(5)}, {pts[0][1].toFixed(5)}
                  {currentDay?.distanceKm && ` · ${currentDay.distanceKm} km`}
                </p>
              </>
            ) : (
              <div className="flex items-center justify-between py-8 border border-dashed border-gray-200 rounded-lg px-4">
                <span className="text-sm text-gray-400">Brak trasy dla tego dnia.</span>
                {canManageTrailAndEquipment && (
                  <button
                    onClick={() => { setChangingTrail(true); setChangingTrailDay(selectedDayNumber); setTrailFile(null); setTrailFileError(''); }}
                    className="text-xs text-mountain-600 hover:text-mountain-800 font-medium"
                  >
                    Wgraj trasę GPX
                  </button>
                )}
              </div>
            )}

            {dayLocs.length > 0 && (
              <div className="mt-4 pt-4 border-t border-gray-100">
                <p className="text-xs font-medium text-gray-500 mb-2">Miejsca na trasie ({dayLocs.length})</p>
                <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
                  {dayLocs.map(loc => (
                    <div key={loc.id} className="flex items-center gap-2 text-sm p-2 rounded-lg bg-gray-50">
                      <span
                        className="w-3 h-3 rounded-full shrink-0"
                        style={{ backgroundColor: getFeatureColor(loc.featureClass, loc.featureCode) }}
                      />
                      <div className="min-w-0">
                        <div className="font-medium truncate text-xs">{loc.name}</div>
                        <div className="text-xs text-gray-400">{loc.typeLabelPl}</div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            <div className="mt-4 pt-4 border-t border-gray-100">
              <div className="flex items-center justify-between mb-3">
                <h2 className="font-semibold text-gray-800">Nocleg</h2>
                {(isOrganizer || viewerRole === 'LOGISTYK') && accommodationForm !== selectedDayNumber && (
                  currentDay?.accommodationName ? (
                    <div className="flex gap-2">
                      <button
                        onClick={() => { setAccommodationForm(selectedDayNumber); setAccommodationInput(currentDay.accommodationUrl || currentDay.accommodationName || ''); }}
                        className="text-xs text-mountain-600 hover:text-mountain-800 border border-mountain-300 hover:border-mountain-500 px-2.5 py-1 rounded-lg"
                      >
                        Edytuj
                      </button>
                      <button
                        onClick={async () => {
                          if (!window.confirm('Usunąć nocleg?')) return;
                          try { await removeDayAccommodation(id, selectedDayNumber); refresh(); } catch {}
                        }}
                        className="text-xs text-gray-500 hover:text-red-500 border border-gray-200 hover:border-red-300 px-2.5 py-1 rounded-lg transition-colors"
                      >
                        Usuń
                      </button>
                    </div>
                  ) : (
                    <button
                      onClick={() => { setAccommodationForm(selectedDayNumber); setAccommodationInput(''); }}
                      className="text-xs text-mountain-600 hover:text-mountain-800 border border-mountain-300 hover:border-mountain-500 px-2.5 py-1 rounded-lg"
                    >
                      Dodaj nocleg
                    </button>
                  )
                )}
              </div>

              {accommodationForm === selectedDayNumber ? (
                <div className="flex gap-2 items-center">
                  <input
                    type="text"
                    value={accommodationInput}
                    onChange={e => setAccommodationInput(e.target.value)}
                    placeholder="Nazwa miejsca lub link Google Maps / Booking.com"
                    className="flex-1 border border-gray-200 rounded-lg px-3 py-1.5 text-sm focus:outline-none focus:border-mountain-400"
                    autoFocus
                  />
                  <button
                    disabled={accommodationLoading || !accommodationInput.trim()}
                    onClick={async () => {
                      setAccommodationLoading(true);
                      try {
                        const val = accommodationInput.trim();
                        await setDayAccommodation(id, selectedDayNumber, val.startsWith('http') ? { url: val } : { name: val });
                        setAccommodationForm(null);
                        setAccommodationInput('');
                        refresh();
                      } catch {
                        alert('Błąd zapisywania noclegu');
                      } finally {
                        setAccommodationLoading(false);
                      }
                    }}
                    className="text-sm bg-mountain-600 hover:bg-mountain-700 text-white px-3 py-1.5 rounded-lg transition-colors disabled:opacity-50 shrink-0"
                  >
                    {accommodationLoading ? 'Zapisywanie...' : 'Zapisz'}
                  </button>
                  <button
                    onClick={() => { setAccommodationForm(null); setAccommodationInput(''); }}
                    className="text-sm text-gray-400 hover:text-gray-600 shrink-0"
                  >
                    Anuluj
                  </button>
                </div>
              ) : currentDay?.accommodationName ? (
                currentDay.accommodationUrl ? (
                  <a
                    href={currentDay.accommodationUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-sm text-mountain-600 hover:text-mountain-800 hover:underline"
                  >
                    {currentDay.accommodationName}
                  </a>
                ) : (
                  <span className="text-sm text-gray-700">{currentDay.accommodationName}</span>
                )
              ) : (
                <p className="text-sm text-gray-400">Brak informacji o noclegu.</p>
              )}
            </div>

          </div>
        );
      })()}

      {/* Mapa trasy — jednodniowa */}
      {!expedition.days?.length && trackPoints.length > 0 && (
        <div className="bg-white rounded-xl border border-gray-200 p-5 mb-4">
          <div className="flex items-center justify-between mb-3">
            <h2 className="font-semibold text-gray-800">Trasa na mapie</h2>
            {canManageTrailAndEquipment && (
              <button
                onClick={() => { setChangingTrail(true); setChangingTrailDay(null); setTrailFile(null); setTrailFileError(''); }}
                className="text-xs text-mountain-600 hover:text-mountain-800 border border-mountain-300 hover:border-mountain-500 px-2.5 py-1 rounded-lg"
              >
                Zmień trasę
              </button>
            )}
          </div>
          <div className="rounded-lg overflow-hidden border border-gray-200" style={{ height: 360 }}>
            <MapContainer center={trackPoints[0]} zoom={13} style={{ height: '100%', width: '100%' }}>
              <TileLayer
                url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                attribution='© <a href="https://openstreetmap.org">OpenStreetMap</a>'
              />
              <Polyline positions={trackPoints} color="#1d6a3a" weight={3} opacity={0.85} />
              <Marker position={trackPoints[0]} icon={makeStartIcon()}><Popup>Start trasy</Popup></Marker>
              <Marker position={trackPoints[trackPoints.length - 1]} icon={makeEndIcon()}><Popup>Koniec trasy</Popup></Marker>
              {expedition.locations?.map(loc => (
                <Marker
                  key={loc.id}
                  position={[loc.latitude, loc.longitude]}
                  icon={makeLocationIcon(loc.featureClass, loc.featureCode)}
                >
                  <Popup>
                    <div className="text-sm">
                      <div className="font-semibold">{loc.name}</div>
                      <div className="text-gray-500 text-xs">{loc.typeLabelPl}</div>
                      {loc.distanceM && (
                        <div className="text-gray-400 text-xs">{Math.round(loc.distanceM)} m od trasy</div>
                      )}
                    </div>
                  </Popup>
                </Marker>
              ))}
            </MapContainer>
          </div>
          <p className="text-xs text-gray-400 mt-2">
            Start: {trackPoints[0][0].toFixed(5)}, {trackPoints[0][1].toFixed(5)}
            {expedition.distanceKm && ` · ${expedition.distanceKm} km`}
          </p>

          {expedition.locations?.length > 0 && (
            <div className="mt-4 pt-4 border-t border-gray-100">
              <p className="text-xs font-medium text-gray-500 mb-2">Miejsca na trasie ({expedition.locations.length})</p>
              <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
                {expedition.locations.map(loc => (
                  <div key={loc.id} className="flex items-center gap-2 text-sm p-2 rounded-lg bg-gray-50">
                    <span
                      className="w-3 h-3 rounded-full shrink-0"
                      style={{ backgroundColor: getFeatureColor(loc.featureClass, loc.featureCode) }}
                    />
                    <div className="min-w-0">
                      <div className="font-medium truncate text-xs">{loc.name}</div>
                      <div className="text-xs text-gray-400">{loc.typeLabelPl}</div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}

      {/* Przycisk zmiany trasy gdy brak mapy (jednodniowa) */}
      {!expedition.days?.length && trackPoints.length === 0 && canManageTrailAndEquipment && (
        <div className="bg-white rounded-xl border border-gray-200 p-5 mb-4 flex items-center justify-between">
          <span className="text-sm text-gray-500">Brak danych GPX trasy.</span>
          <button
            onClick={() => { setChangingTrail(true); setChangingTrailDay(null); setTrailFile(null); setTrailFileError(''); }}
            className="text-xs text-mountain-600 hover:text-mountain-800 font-medium"
          >
            Wgraj trasę GPX
          </button>
        </div>
      )}

      {/* Sprzęt */}
      {(expedition.equipment?.length > 0 || canManageTrailAndEquipment) && (
        <div className="bg-white rounded-xl border border-gray-200 p-5 mb-4">
          <div className="flex items-center justify-between mb-3">
            <h2 className="font-semibold text-gray-800">Sprzęt</h2>
            {canManageTrailAndEquipment && !editingEquipment && (
              <button
                onClick={() => {
                  const draft = {};
                  expedition.equipment?.forEach(e => { draft[e.item] = e.level; });
                  setEquipmentDraft(draft);
                  setEditingEquipment(true);
                }}
                className="text-xs text-mountain-600 hover:text-mountain-800 border border-mountain-300 hover:border-mountain-500 px-2.5 py-1 rounded-lg"
              >
                Edytuj
              </button>
            )}
          </div>

          {editingEquipment ? (
            <>
              <div className="border border-gray-200 rounded-lg divide-y divide-gray-100 mb-3">
                {EQUIPMENT_ITEMS.map(item => (
                  <div key={item.value} className="flex items-center justify-between px-3 py-2">
                    <span className="text-sm text-gray-700">{item.label}</span>
                    <div className="flex gap-1">
                      {[
                        { level: null,          label: 'brak',     active: 'bg-gray-100 text-gray-500' },
                        { level: 'REQUIRED',    label: 'wymagany', active: 'bg-red-100 text-red-700' },
                        { level: 'RECOMMENDED', label: 'zalecany', active: 'bg-amber-100 text-amber-700' },
                      ].map(opt => (
                        <button
                          key={String(opt.level)}
                          type="button"
                          onClick={() => setEquipmentDraft(prev => ({ ...prev, [item.value]: opt.level }))}
                          className={`text-xs px-2 py-1 rounded transition-colors ${
                            (equipmentDraft[item.value] ?? null) === opt.level
                              ? opt.active + ' font-medium'
                              : 'text-gray-400 hover:bg-gray-50'
                          }`}
                        >
                          {opt.label}
                        </button>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
              {equipmentError && (
                <p className="text-xs text-red-600">{equipmentError}</p>
              )}
              <div className="flex gap-2 justify-end">
                <button
                  onClick={() => { setEditingEquipment(false); setEquipmentError(''); }}
                  className="text-xs px-3 py-1.5 border border-gray-300 rounded-lg text-gray-600 hover:bg-gray-50"
                >
                  Anuluj
                </button>
                <button
                  disabled={savingEquipment}
                  onClick={async () => {
                    setSavingEquipment(true);
                    setEquipmentError('');
                    try {
                      const list = Object.entries(equipmentDraft)
                        .filter(([, level]) => level)
                        .map(([item, level]) => ({ item, level }));
                      await updateExpeditionEquipment(id, list);
                      setEditingEquipment(false);
                      setEquipmentError('');
                      refresh();
                    } catch (err) {
                      setEquipmentError(err.response?.data?.message || 'Błąd zapisu sprzętu');
                    } finally {
                      setSavingEquipment(false);
                    }
                  }}
                  className="text-xs px-3 py-1.5 bg-mountain-600 hover:bg-mountain-700 disabled:bg-mountain-400 text-white rounded-lg"
                >
                  {savingEquipment ? 'Zapisywanie...' : 'Zapisz'}
                </button>
              </div>
            </>
          ) : expedition.equipment?.length > 0 ? (
            <div className="space-y-3">
              {['REQUIRED', 'RECOMMENDED'].map(level => {
                const items = expedition.equipment.filter(e => e.level === level);
                if (items.length === 0) return null;
                return (
                  <div key={level}>
                    <p className="text-xs font-medium text-gray-500 mb-1.5">
                      {level === 'REQUIRED' ? 'Wymagany' : 'Zalecany'}
                    </p>
                    <div className="flex flex-wrap gap-2">
                      {items.map(e => {
                        const label = EQUIPMENT_ITEMS.find(i => i.value === e.item)?.label ?? e.item;
                        return (
                          <span
                            key={e.item}
                            className={`text-xs px-2.5 py-1 rounded-full font-medium ${
                              level === 'REQUIRED'
                                ? 'bg-red-50 text-red-700 border border-red-200'
                                : 'bg-amber-50 text-amber-700 border border-amber-200'
                            }`}
                          >
                            {label}
                          </span>
                        );
                      })}
                    </div>
                  </div>
                );
              })}
            </div>
          ) : (
            <p className="text-sm text-gray-400">Brak informacji o sprzęcie.</p>
          )}
        </div>
      )}

      {hasFullAccess && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">

          {/* Uczestnicy + zaproszenia */}
          <div className="bg-white rounded-xl border border-gray-200 p-5">
            <div className="flex items-center justify-between mb-3">
              <h2 className="font-semibold text-gray-800">
                Uczestnicy ({(expedition.members?.length ?? 0) + 1})
              </h2>
              {isOrganizer && (
                <button
                  onClick={() => { setManagingMembers(true); setChangingRoleFor(null); }}
                  className="text-xs text-gray-500 hover:text-gray-700 border border-gray-200 hover:border-gray-300 px-2.5 py-1 rounded-lg transition-colors"
                >
                  Zarządzaj
                </button>
              )}
            </div>

            <div className="space-y-2">
              <div className="flex items-center gap-2 text-sm">
                <div className="w-7 h-7 rounded-full bg-mountain-100 text-mountain-700 flex items-center justify-center text-xs font-medium">
                  {expedition.organizer?.name?.[0]}
                </div>
                <span className="font-medium">{expedition.organizer?.name}</span>
                <span className="text-xs text-mountain-600 ml-auto">organizator</span>
              </div>
              {expedition.members?.map(m => (
                <div key={m.id} className="flex items-center gap-2 text-sm">
                  <div className="w-7 h-7 rounded-full bg-gray-100 text-gray-600 flex items-center justify-center text-xs font-medium shrink-0">
                    {m.user?.name?.[0]}
                  </div>
                  <span className="truncate flex-1">{m.user?.name}</span>
                  <span className={`text-xs shrink-0 ${
                    m.status === 'ACCEPTED' ? 'text-green-600' :
                    m.status === 'DECLINED' ? 'text-red-500' :
                    m.status === 'PENDING'  ? 'text-blue-500' : 'text-yellow-600'
                  }`}>
                    {m.status === 'ACCEPTED'
                      ? { MEMBER: 'uczestnik', NAWIGATOR: 'nawigator', LOGISTYK: 'logistyk' }[m.memberRole ?? 'MEMBER'] ?? 'uczestnik'
                      : m.status === 'DECLINED' ? 'odrzucony'
                      : m.status === 'PENDING'  ? 'oczekuje' : 'zaproszony'}
                  </span>
                </div>
              ))}

              {isMember && (
                <button
                  onClick={handleLeave}
                  className="mt-3 w-full text-xs text-red-400 hover:text-red-600 border border-red-200 hover:border-red-400 py-1.5 rounded-lg transition-colors"
                >
                  Opuść wyprawę
                </button>
              )}
            </div>

            {isOrganizer && (
              <div className="mt-4 pt-4 border-t border-gray-100 space-y-3">
                {/* Tryb dołączania */}
                <div className="flex items-center justify-between">
                  <span className="text-xs text-gray-500">Tryb dołączania:</span>
                  <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${
                    expedition.joinMode === 'APPROVAL_REQUIRED'
                      ? 'bg-amber-100 text-amber-700'
                      : 'bg-green-100 text-green-700'
                  }`}>
                    {expedition.joinMode === 'APPROVAL_REQUIRED' ? 'Za akceptacją' : 'Automatyczny'}
                  </span>
                </div>

                {/* Zaproszenie z wyszukiwaniem */}
                <InviteSearchInput
                  expeditionId={id}
                  alreadyMemberIds={new Set([
                    expedition.organizer?.id,
                    ...(expedition.members?.map(m => m.user?.id) ?? [])
                  ])}
                  onInvited={refresh}
                />

                {/* Link zaproszenia */}
                <div>
                  <p className="text-xs text-gray-500 mb-1.5">Lub wygeneruj link zaproszenia:</p>
                  {inviteLink ? (
                    <div className="flex gap-1">
                      <input
                        readOnly
                        value={inviteLink}
                        className="flex-1 border border-gray-200 rounded-lg px-2 py-1.5 text-xs bg-gray-50 text-gray-600 truncate"
                      />
                      <button
                        onClick={handleCopyLink}
                        className="border border-gray-300 hover:bg-gray-50 px-2 py-1.5 rounded-lg text-xs text-gray-600 shrink-0"
                      >
                        Kopiuj
                      </button>
                    </div>
                  ) : (
                    <button
                      onClick={handleGenerateLink}
                      disabled={generatingLink}
                      className="w-full border border-gray-300 hover:bg-gray-50 text-gray-600 py-1.5 rounded-lg text-xs transition-colors"
                    >
                      {generatingLink ? 'Generowanie...' : 'Generuj link'}
                    </button>
                  )}
                  {inviteLink && (
                    <p className="text-xs text-gray-400 mt-1">
                      Link jest ważny — każdy kto go ma może dołączyć.{' '}
                      <button
                        onClick={() => { setInviteLink(''); handleGenerateLink(); }}
                        className="text-mountain-600 hover:underline"
                      >
                        Odnów
                      </button>
                    </p>
                  )}
                </div>
              </div>
            )}
          </div>

          {/* Komentarze */}
          <div className="md:col-span-2 bg-white rounded-xl border border-gray-200 p-5">
            <h2 className="font-semibold text-gray-800 mb-3">
              Komentarze ({totalComments(expedition.comments ?? [])})
            </h2>

            <div className="space-y-4 mb-4 max-h-96 overflow-y-auto pr-1">
              {expedition.comments?.length === 0 && (
                <p className="text-sm text-gray-400">Brak komentarzy. Bądź pierwszy!</p>
              )}
              {expedition.comments?.map(c => (
                <Comment
                  key={c.id}
                  comment={c}
                  expeditionId={id}
                  isOrganizer={isOrganizer}
                  onRefresh={refresh}
                />
              ))}
            </div>

            <form onSubmit={handleComment} className="border-t border-gray-100 pt-4">
              <textarea
                value={newComment}
                onChange={e => setNewComment(e.target.value)}
                placeholder="Napisz komentarz dla uczestników wyprawy..."
                rows={2}
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-mountain-400 resize-none"
              />
              <div className="flex justify-end mt-2">
                <button
                  type="submit"
                  disabled={submitting || !newComment.trim()}
                  className="bg-mountain-600 hover:bg-mountain-700 disabled:bg-mountain-300 text-white px-4 py-1.5 rounded-lg text-sm font-medium"
                >
                  Wyślij
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {!hasFullAccess && (
        <div className="bg-gray-50 border border-gray-200 rounded-xl p-6 text-center text-gray-500 text-sm">
          Lista uczestników i czat są dostępne po dołączeniu do wyprawy.
        </div>
      )}

      {/* Modal zarządzania uczestnikami */}
      {managingMembers && (
        <div
          className="fixed inset-0 bg-black/40 flex items-center justify-center z-50"
          onClick={e => { if (e.target === e.currentTarget) { setManagingMembers(false); setChangingRoleFor(null); } }}
        >
          <div className="bg-white rounded-xl shadow-xl w-full max-w-md mx-4 p-6">
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-semibold text-gray-900 text-lg">Zarządzaj uczestnikami</h3>
              <button
                onClick={() => { setManagingMembers(false); setChangingRoleFor(null); }}
                className="text-gray-400 hover:text-gray-600 text-xl leading-none"
              >
                ✕
              </button>
            </div>

            <div className="space-y-3 max-h-96 overflow-y-auto">
              {expedition.members?.map(m => (
                <div key={m.id} className="flex items-center gap-2 text-sm py-1 border-b border-gray-50 last:border-0">
                  <div className="w-7 h-7 rounded-full bg-gray-100 text-gray-600 flex items-center justify-center text-xs font-medium shrink-0">
                    {m.user?.name?.[0]}
                  </div>
                  <span className="truncate flex-1 font-medium">{m.user?.name}</span>

                  {m.status === 'ACCEPTED' && (
                    <>
                      {changingRoleFor === m.id ? (
                        <div className="flex items-center gap-1 shrink-0">
                          {[['MEMBER','uczestnik'],['NAWIGATOR','nawigator'],['LOGISTYK','logistyk']].map(([val, label]) => (
                            <button
                              key={val}
                              onClick={async () => {
                                try { await updateMemberRole(id, m.user?.id, val); refresh(); } catch {}
                                setChangingRoleFor(null);
                              }}
                              className={`text-xs px-1.5 py-0.5 rounded border transition-colors ${
                                (m.memberRole ?? 'MEMBER') === val
                                  ? 'bg-mountain-600 text-white border-mountain-600'
                                  : 'text-gray-600 border-gray-200 hover:border-gray-400'
                              }`}
                            >
                              {label}
                            </button>
                          ))}
                          <button onClick={() => setChangingRoleFor(null)} className="text-xs text-gray-400 hover:text-gray-600 px-1">✕</button>
                        </div>
                      ) : (
                        <div className="flex items-center gap-1.5 shrink-0">
                          <span className="text-xs text-green-600">
                            {{ MEMBER: 'uczestnik', NAWIGATOR: 'nawigator', LOGISTYK: 'logistyk' }[m.memberRole ?? 'MEMBER']}
                          </span>
                          <button
                            onClick={() => setChangingRoleFor(m.id)}
                            className="text-xs text-gray-400 hover:text-gray-600 border border-gray-200 hover:border-gray-300 px-1.5 py-0.5 rounded transition-colors"
                          >
                            Zmień rolę
                          </button>
                        </div>
                      )}
                    </>
                  )}

                  {m.status === 'PENDING' && (
                    <button
                      onClick={async () => { await handleApprove(m.user?.id); }}
                      className="text-xs text-green-600 hover:text-green-800 border border-green-200 hover:border-green-400 px-1.5 py-0.5 rounded transition-colors shrink-0"
                    >
                      Akceptuj
                    </button>
                  )}

                  {m.status !== 'ACCEPTED' && m.status !== 'PENDING' && (
                    <span className={`text-xs shrink-0 ${m.status === 'DECLINED' ? 'text-red-400' : 'text-yellow-500'}`}>
                      {m.status === 'DECLINED' ? 'odrzucony' : 'zaproszony'}
                    </span>
                  )}

                  <button
                    onClick={async () => { await handleRemoveMember(m.user?.id, m.user?.name); }}
                    className="text-xs text-gray-300 hover:text-red-500 transition-colors shrink-0 ml-1"
                    title="Usuń"
                  >
                    Usuń
                  </button>
                </div>
              ))}
              {(!expedition.members || expedition.members.length === 0) && (
                <p className="text-sm text-gray-400 text-center py-4">Brak uczestników.</p>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
