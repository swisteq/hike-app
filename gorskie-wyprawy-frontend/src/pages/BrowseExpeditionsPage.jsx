import { useState, useEffect, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { getPublicExpeditions, joinExpedition } from '../api/client';
import { useAuth } from '../context/AuthContext';

const SORT_OPTIONS = [
  { value: 'plannedDate',    label: 'Data wyprawy' },
  { value: 'name',           label: 'Nazwa' },
  { value: 'distanceKm',     label: 'Długość trasy' },
  { value: 'durationMinutes',label: 'Czas trasy' },
  { value: 'elevationGainM', label: 'Przewyższenie' },
  { value: 'maxElevationM',  label: 'Najwyższy punkt' },
  { value: 'memberCount',    label: 'Liczba uczestników' },
];

const EMPTY_FILTERS = {
  query:        '',
  visibility:   [],
  dateFrom:     '',
  dateTo:       '',
  minDistance:  '',
  maxDistance:  '',
  minDuration:  '',
  maxDuration:  '',
  minGain:      '',
  maxGain:      '',
  minMaxElev:   '',
  maxMaxElev:   '',
  minMembers:   '',
  maxMembers:   '',
};

const VISIBILITY_LABELS = {
  PUBLIC:       'Publiczna',
  FRIENDS_ONLY: 'Tylko znajomi',
  GROUPS_ONLY:  'Tylko grupy',
};

function num(v) { return v === '' ? null : Number(v); }

function applyFilters(expeditions, f) {
  return expeditions.filter(e => {
    const members = (e.memberCount ?? 0) + 1;
    const dist    = e.distanceKm    ?? e.trail?.distanceKm    ?? 0;
    const dur     = e.durationMinutes ?? e.trail?.durationMinutes ?? 0;
    const gain    = e.elevationGainM  ?? e.trail?.elevationGainM  ?? 0;
    const maxEl   = e.maxElevationM   ?? e.trail?.maxElevationM   ?? 0;
    const date    = e.plannedDate;
    const haystack = `${e.name} ${e.trailName ?? ''} ${e.trail?.name ?? ''}`.toLowerCase();

    if (f.query && !haystack.includes(f.query.toLowerCase())) return false;
    if (f.visibility.length > 0 && !f.visibility.includes(e.visibility)) return false;
    if (f.dateFrom && date < f.dateFrom) return false;
    if (f.dateTo   && date > f.dateTo)   return false;
    if (num(f.minDistance) !== null && dist < num(f.minDistance)) return false;
    if (num(f.maxDistance) !== null && dist > num(f.maxDistance)) return false;
    if (num(f.minDuration) !== null && dur  < num(f.minDuration)) return false;
    if (num(f.maxDuration) !== null && dur  > num(f.maxDuration)) return false;
    if (num(f.minGain)     !== null && gain < num(f.minGain))     return false;
    if (num(f.maxGain)     !== null && gain > num(f.maxGain))     return false;
    if (num(f.minMaxElev)  !== null && maxEl < num(f.minMaxElev)) return false;
    if (num(f.maxMaxElev)  !== null && maxEl > num(f.maxMaxElev)) return false;
    if (num(f.minMembers)  !== null && members < num(f.minMembers)) return false;
    if (num(f.maxMembers)  !== null && members > num(f.maxMembers)) return false;
    return true;
  });
}

function applySort(expeditions, sortBy, dir) {
  const sign = dir === 'asc' ? 1 : -1;
  return [...expeditions].sort((a, b) => {
    let av, bv;
    if (sortBy === 'memberCount') {
      av = (a.memberCount ?? 0) + 1;
      bv = (b.memberCount ?? 0) + 1;
    } else if (sortBy === 'distanceKm') {
      av = a.distanceKm ?? a.trail?.distanceKm ?? 0;
      bv = b.distanceKm ?? b.trail?.distanceKm ?? 0;
    } else if (sortBy === 'elevationGainM') {
      av = a.elevationGainM ?? a.trail?.elevationGainM ?? 0;
      bv = b.elevationGainM ?? b.trail?.elevationGainM ?? 0;
    } else if (sortBy === 'maxElevationM') {
      av = a.maxElevationM ?? a.trail?.maxElevationM ?? 0;
      bv = b.maxElevationM ?? b.trail?.maxElevationM ?? 0;
    } else if (sortBy === 'durationMinutes') {
      av = a.durationMinutes ?? a.trail?.durationMinutes ?? 0;
      bv = b.durationMinutes ?? b.trail?.durationMinutes ?? 0;
    } else {
      av = a[sortBy] ?? '';
      bv = b[sortBy] ?? '';
    }
    if (av < bv) return -1 * sign;
    if (av > bv) return  1 * sign;
    return 0;
  });
}

function FilterInput({ label, type = 'number', placeholder, value, onChange }) {
  return (
    <div>
      <label className="block text-xs text-gray-500 mb-1">{label}</label>
      <input
        type={type}
        placeholder={placeholder}
        value={value}
        onChange={e => onChange(e.target.value)}
        className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-mountain-400"
      />
    </div>
  );
}

export default function BrowseExpeditionsPage() {
  const { user } = useAuth();
  const [allExpeditions, setAllExpeditions] = useState([]);
  const [loading, setLoading]               = useState(true);
  const [joining, setJoining]               = useState(null);
  const [joinedIds, setJoinedIds]           = useState(new Set());

  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [sortBy, setSortBy]   = useState('plannedDate');
  const [dir, setDir]         = useState('asc');
  const [filtersOpen, setFiltersOpen] = useState(false);

  useEffect(() => {
    getPublicExpeditions()
      .then(({ data }) => setAllExpeditions(data))
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  const setFilter = (key, value) =>
    setFilters(prev => ({ ...prev, [key]: value }));

  const resetFilters = () => setFilters(EMPTY_FILTERS);

  const activeFilterCount = useMemo(
    () => Object.entries(filters).filter(([, v]) => Array.isArray(v) ? v.length > 0 : v !== '').length,
    [filters]
  );

  const displayed = useMemo(
    () => applySort(applyFilters(allExpeditions, filters), sortBy, dir),
    [allExpeditions, filters, sortBy, dir]
  );

  const handleJoin = async (id) => {
    setJoining(id);
    try {
      await joinExpedition(id);
      setJoinedIds(prev => new Set(prev).add(id));
      setAllExpeditions(prev =>
        prev.map(e => e.id === id ? { ...e, memberCount: (e.memberCount ?? 0) + 1 } : e)
      );
    } catch (err) {
      alert(err.response?.data?.message || 'Nie udało się dołączyć do wyprawy');
    } finally {
      setJoining(null);
    }
  };

  if (loading) return <div className="text-center py-16 text-gray-400">Ładowanie wypraw...</div>;

  return (
    <div className="max-w-5xl mx-auto px-4 py-8">

      {/* Nagłówek */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Planowane wyprawy</h1>
          <p className="text-gray-500 text-sm mt-1">
            {displayed.length} z {allExpeditions.length} wypraw
          </p>
        </div>
        {user && (
          <Link
            to="/expeditions/new"
            className="bg-mountain-600 hover:bg-mountain-700 text-white px-4 py-2 rounded-lg text-sm font-medium transition-colors"
          >
            + Zaplanuj swoją
          </Link>
        )}
      </div>

      {/* Pasek sortowania + toggle filtrów */}
      <div className="bg-white rounded-xl border border-gray-200 p-4 mb-4">
        <div className="flex flex-wrap gap-3 items-center">
          {/* Szukaj */}
          <input
            type="text"
            placeholder="Szukaj po nazwie wyprawy lub trasy..."
            value={filters.query}
            onChange={e => setFilter('query', e.target.value)}
            className="flex-1 min-w-48 border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-mountain-400"
          />

          {/* Sortowanie */}
          <select
            value={sortBy}
            onChange={e => setSortBy(e.target.value)}
            className="border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-mountain-400"
          >
            {SORT_OPTIONS.map(o => (
              <option key={o.value} value={o.value}>{o.label}</option>
            ))}
          </select>
          <button
            onClick={() => setDir(d => d === 'asc' ? 'desc' : 'asc')}
            className="border border-gray-300 rounded-lg px-3 py-2 text-sm hover:bg-gray-50"
            title={dir === 'asc' ? 'Rosnąco' : 'Malejąco'}
          >
            {dir === 'asc' ? '↑' : '↓'}
          </button>

          {/* Toggle filtrów */}
          <button
            onClick={() => setFiltersOpen(o => !o)}
            className={`flex items-center gap-1.5 border rounded-lg px-3 py-2 text-sm transition-colors ${
              filtersOpen || activeFilterCount > 0
                ? 'border-mountain-400 text-mountain-700 bg-mountain-50'
                : 'border-gray-300 text-gray-600 hover:bg-gray-50'
            }`}
          >
            Filtry {activeFilterCount > 0 && (
              <span className="bg-mountain-600 text-white text-xs rounded-full w-5 h-5 flex items-center justify-center">
                {activeFilterCount}
              </span>
            )}
          </button>

          {activeFilterCount > 0 && (
            <button
              onClick={resetFilters}
              className="text-sm text-gray-400 hover:text-red-500 transition-colors"
            >
              Wyczyść
            </button>
          )}
        </div>

        {/* Rozwijany panel filtrów */}
        {filtersOpen && (
          <div className="mt-4 pt-4 border-t border-gray-100 grid grid-cols-2 md:grid-cols-4 gap-4">

            {/* Widoczność */}
            <div className="col-span-2 md:col-span-4">
              <label className="block text-xs text-gray-500 mb-1.5">
                Widoczność wyprawy
                {filters.visibility.length > 0 && (
                  <button
                    type="button"
                    onClick={() => setFilter('visibility', [])}
                    className="ml-2 text-mountain-600 hover:underline"
                  >
                    wyczyść
                  </button>
                )}
              </label>
              <div className="flex gap-2 flex-wrap">
                {Object.entries(VISIBILITY_LABELS).map(([value, label]) => {
                  const active = filters.visibility.includes(value);
                  return (
                    <button
                      key={value}
                      type="button"
                      onClick={() => {
                        const next = active
                          ? filters.visibility.filter(v => v !== value)
                          : [...filters.visibility, value];
                        setFilter('visibility', next);
                      }}
                      className={`px-3 py-1.5 rounded-lg text-sm border transition-colors ${
                        active
                          ? 'bg-mountain-600 text-white border-mountain-600'
                          : 'bg-white text-gray-600 border-gray-300 hover:border-mountain-400'
                      }`}
                    >
                      {label}
                    </button>
                  );
                })}
              </div>
            </div>

            <FilterInput label="Data od"       type="date" value={filters.dateFrom}    onChange={v => setFilter('dateFrom', v)} />
            <FilterInput label="Data do"       type="date" value={filters.dateTo}      onChange={v => setFilter('dateTo', v)} />
            <FilterInput label="Min. uczestników" placeholder="np. 2" value={filters.minMembers}  onChange={v => setFilter('minMembers', v)} />
            <FilterInput label="Max. uczestników" placeholder="np. 10" value={filters.maxMembers} onChange={v => setFilter('maxMembers', v)} />

            <FilterInput label="Min. dystans (km)"   placeholder="np. 5"   value={filters.minDistance} onChange={v => setFilter('minDistance', v)} />
            <FilterInput label="Max. dystans (km)"   placeholder="np. 20"  value={filters.maxDistance} onChange={v => setFilter('maxDistance', v)} />
            <FilterInput label="Min. czas (min)"     placeholder="np. 60"  value={filters.minDuration} onChange={v => setFilter('minDuration', v)} />
            <FilterInput label="Max. czas (min)"     placeholder="np. 300" value={filters.maxDuration} onChange={v => setFilter('maxDuration', v)} />

            <FilterInput label="Min. przewyższenie (m)"    placeholder="np. 200"  value={filters.minGain}    onChange={v => setFilter('minGain', v)} />
            <FilterInput label="Max. przewyższenie (m)"    placeholder="np. 1000" value={filters.maxGain}    onChange={v => setFilter('maxGain', v)} />
            <FilterInput label="Min. najwyższy punkt (m n.p.m.)" placeholder="np. 1000" value={filters.minMaxElev} onChange={v => setFilter('minMaxElev', v)} />
            <FilterInput label="Max. najwyższy punkt (m n.p.m.)" placeholder="np. 2500" value={filters.maxMaxElev} onChange={v => setFilter('maxMaxElev', v)} />
          </div>
        )}
      </div>

      {/* Lista */}
      {displayed.length === 0 ? (
        <div className="text-center py-16 text-gray-400">
          <p className="mb-3">Nie znaleziono wypraw spełniających kryteria.</p>
          {activeFilterCount > 0 && (
            <button onClick={resetFilters} className="text-mountain-600 hover:underline text-sm">
              Wyczyść filtry →
            </button>
          )}
        </div>
      ) : (
        <div className="space-y-3">
          {displayed.map(exp => {
            const isOrganizer = user?.userId === exp.organizer?.id;
            const isMember    = exp.members?.some(m => m.user?.id === user?.userId);
            const alreadyIn   = isOrganizer || isMember || joinedIds.has(exp.id);
            const members     = (exp.memberCount ?? 0) + 1;
            const dist        = exp.distanceKm   ?? exp.trail?.distanceKm;
            const gain        = exp.elevationGainM ?? exp.trail?.elevationGainM;
            const dur         = exp.durationFormatted ?? exp.trail?.durationFormatted;
            const maxEl       = exp.maxElevationM  ?? exp.trail?.maxElevationM;
            const trailLabel  = exp.trailName ?? exp.trail?.name;

            return (
              <div
                key={exp.id}
                className="bg-white rounded-xl border border-gray-200 p-5 hover:shadow-md hover:border-mountain-300 transition-all"
              >
                <div className="flex items-start justify-between gap-4">

                  {/* Lewa kolumna */}
                  <div className="flex-1 min-w-0">
                    <Link to={`/expeditions/${exp.id}`} className="group">
                      <h3 className="font-semibold text-gray-900 group-hover:text-mountain-700 transition-colors">
                        {exp.name}
                      </h3>
                    </Link>
                    {trailLabel && (
                      <p className="text-sm text-gray-500 mt-0.5 truncate">{exp.routeLabel || trailLabel}</p>
                    )}
                    <p className="text-xs text-gray-400 mt-1">
                      Organizator: <span className="font-medium text-gray-600">{exp.organizer?.name}</span>
                    </p>

                    {/* Metryki trasy */}
                    <div className="flex flex-wrap gap-3 mt-2 text-xs text-gray-500">
                      {dist  && <span>Dystans: {dist} km</span>}
                      {gain  && <span>Podejście: +{gain} m</span>}
                      {maxEl && <span>Maks. wys.: {maxEl} m n.p.m.</span>}
                      {dur   && <span>Czas: {dur}</span>}
                      <span>{members} uczestnik{members === 1 ? '' : 'ów'}</span>
                    </div>
                    {exp.highestPeakName && (
                      <div className="mt-1.5 text-xs text-mountain-700 font-medium">
                        Najwyższy szczyt: {exp.highestPeakName}
                        {exp.highestPeakElevationM && ` (${exp.highestPeakElevationM} m n.p.m.)`}
                      </div>
                    )}

                    {exp.description && (
                      <p className="text-xs text-gray-400 italic mt-1.5 line-clamp-1">{exp.description}</p>
                    )}
                  </div>

                  {/* Prawa kolumna */}
                  <div className="flex flex-col items-end gap-2 shrink-0">
                    <div className="text-sm font-medium text-gray-800 text-right">
                      {new Date(exp.plannedDate).toLocaleDateString('pl-PL', {
                        day: 'numeric', month: 'long', year: 'numeric'
                      })}
                      {exp.startTime && (
                        <span className="block text-xs text-gray-500">godz. {exp.startTime.substring(0,5)}</span>
                      )}
                    </div>
                    {exp.visibility && exp.visibility !== 'PUBLIC' && (
                      <span className="text-xs text-amber-600 border border-amber-200 bg-amber-50 px-2 py-0.5 rounded-full">
                        {exp.visibility === 'FRIENDS_ONLY' ? 'Tylko znajomi' : 'Tylko grupy'}
                      </span>
                    )}

                    {user && (
                      alreadyIn ? (
                        <span className="text-xs text-green-600 font-medium">Dołączono</span>
                      ) : (
                        <button
                          onClick={() => handleJoin(exp.id)}
                          disabled={joining === exp.id}
                          className="bg-mountain-600 hover:bg-mountain-700 disabled:bg-mountain-400 text-white px-4 py-1.5 rounded-lg text-xs font-medium transition-colors"
                        >
                          {joining === exp.id ? 'Dołączanie...' : 'Dołącz'}
                        </button>
                      )
                    )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
