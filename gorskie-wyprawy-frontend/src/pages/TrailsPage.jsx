import { useState, useEffect, useCallback } from 'react';
import { getTrails, getTrailStats } from '../api/client';
import TrailCard from '../components/TrailCard';

const SORT_OPTIONS = [
  { value: 'name', label: 'Nazwa' },
  { value: 'distanceKm', label: 'Dystans' },
  { value: 'elevationGainM', label: 'Przewyższenie' },
  { value: 'durationMinutes', label: 'Czas' },
];

export default function TrailsPage() {
  const [trails, setTrails] = useState([]);
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [pagination, setPagination] = useState({ page: 0, totalPages: 1, totalElements: 0 });

  const [filters, setFilters] = useState({
    name: '',
    minDistance: '',
    maxDistance: '',
    minElevation: '',
    maxElevation: '',
    sort: 'name',
    dir: 'asc',
    page: 0,
    size: 12,
  });

  const fetchTrails = useCallback(async () => {
    setLoading(true);
    try {
      const params = Object.fromEntries(
        Object.entries(filters).filter(([, v]) => v !== '' && v !== null)
      );
      const { data } = await getTrails(params);
      setTrails(data.content);
      setPagination({ page: data.page, totalPages: data.totalPages, totalElements: data.totalElements });
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  }, [filters]);

  useEffect(() => { fetchTrails(); }, [fetchTrails]);

  useEffect(() => {
    getTrailStats().then(({ data }) => setStats(data)).catch(() => {});
  }, []);

  const updateFilter = (key, value) => {
    setFilters(prev => ({ ...prev, [key]: value, page: 0 }));
  };

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">

      {/* Nagłówek ze statystykami */}
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-gray-900 mb-2">Trasy górskie</h1>
        {stats && (
          <div className="flex gap-6 text-sm text-gray-500">
            <span>{stats.totalTrails} tras</span>
            <span>{stats.totalDistanceKm?.toFixed(0)} km łącznie</span>
            <span>Najdłuższa: {stats.longestTrailKm?.toFixed(1)} km</span>
          </div>
        )}
      </div>

      {/* Filtry */}
      <div className="bg-white rounded-xl border border-gray-200 p-5 mb-6">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-4">
          <input
            type="text"
            placeholder="Szukaj po nazwie..."
            value={filters.name}
            onChange={e => updateFilter('name', e.target.value)}
            className="border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-mountain-400"
          />
          <div className="flex gap-2">
            <select
              value={filters.sort}
              onChange={e => updateFilter('sort', e.target.value)}
              className="flex-1 border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-mountain-400"
            >
              {SORT_OPTIONS.map(o => (
                <option key={o.value} value={o.value}>{o.label}</option>
              ))}
            </select>
            <button
              onClick={() => updateFilter('dir', filters.dir === 'asc' ? 'desc' : 'asc')}
              className="border border-gray-300 rounded-lg px-3 py-2 text-sm hover:bg-gray-50"
              title={filters.dir === 'asc' ? 'Rosnąco' : 'Malejąco'}
            >
              {filters.dir === 'asc' ? '↑' : '↓'}
            </button>
          </div>
        </div>

        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
          <input type="number" placeholder="Min dystans (km)" value={filters.minDistance}
            onChange={e => updateFilter('minDistance', e.target.value)}
            className="border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-mountain-400" />
          <input type="number" placeholder="Max dystans (km)" value={filters.maxDistance}
            onChange={e => updateFilter('maxDistance', e.target.value)}
            className="border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-mountain-400" />
          <input type="number" placeholder="Min przewyższenie (m)" value={filters.minElevation}
            onChange={e => updateFilter('minElevation', e.target.value)}
            className="border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-mountain-400" />
          <input type="number" placeholder="Max przewyższenie (m)" value={filters.maxElevation}
            onChange={e => updateFilter('maxElevation', e.target.value)}
            className="border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-mountain-400" />
        </div>
      </div>

      {/* Lista tras */}
      {loading ? (
        <div className="text-center py-16 text-gray-400">Ładowanie tras...</div>
      ) : trails.length === 0 ? (
        <div className="text-center py-16 text-gray-400">
          <p>Nie znaleziono tras spełniających kryteria.</p>
        </div>
      ) : (
        <>
          <p className="text-sm text-gray-500 mb-4">Znaleziono {pagination.totalElements} tras</p>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 mb-6">
            {trails.map(trail => <TrailCard key={trail.id} trail={trail} />)}
          </div>

          {/* Paginacja */}
          {pagination.totalPages > 1 && (
            <div className="flex justify-center gap-2">
              <button
                disabled={filters.page === 0}
                onClick={() => updateFilter('page', filters.page - 1)}
                className="px-4 py-2 rounded-lg border border-gray-300 text-sm disabled:opacity-40 hover:bg-gray-50"
              >
                ← Poprzednia
              </button>
              <span className="px-4 py-2 text-sm text-gray-600">
                {filters.page + 1} / {pagination.totalPages}
              </span>
              <button
                disabled={filters.page + 1 >= pagination.totalPages}
                onClick={() => updateFilter('page', filters.page + 1)}
                className="px-4 py-2 rounded-lg border border-gray-300 text-sm disabled:opacity-40 hover:bg-gray-50"
              >
                Następna →
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
