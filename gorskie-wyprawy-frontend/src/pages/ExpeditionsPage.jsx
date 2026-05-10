import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { getMyExpeditions } from '../api/client';

const STATUS_LABELS = {
  PLANNED: { label: 'Planowana', color: 'bg-blue-50 text-blue-700 border-blue-200' },
  ONGOING: { label: 'W trakcie', color: 'bg-green-50 text-green-700 border-green-200' },
  COMPLETED: { label: 'Zakończona', color: 'bg-gray-50 text-gray-600 border-gray-200' },
  CANCELLED: { label: 'Odwołana', color: 'bg-red-50 text-red-600 border-red-200' },
};

const STATUS_FILTERS = [
  { value: '',          label: 'Wszystkie' },
  { value: 'PLANNED',   label: 'Planowane' },
  { value: 'ONGOING',   label: 'W trakcie' },
  { value: 'COMPLETED', label: 'Zakończone' },
  { value: 'CANCELLED', label: 'Odwołane' },
];

export default function ExpeditionsPage() {
  const [expeditions, setExpeditions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState('');

  useEffect(() => {
    getMyExpeditions()
      .then(({ data }) => setExpeditions(data))
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  const displayed = statusFilter
    ? expeditions.filter(e => e.status === statusFilter)
    : expeditions;

  if (loading) return <div className="text-center py-16 text-gray-400">Ładowanie wypraw...</div>;

  return (
    <div className="max-w-4xl mx-auto px-4 py-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Moje wyprawy</h1>
          <p className="text-gray-500 text-sm mt-1">{displayed.length} z {expeditions.length} wypraw</p>
        </div>
        <Link
          to="/expeditions/new"
          className="bg-mountain-600 hover:bg-mountain-700 text-white px-4 py-2 rounded-lg text-sm font-medium transition-colors"
        >
          + Nowa wyprawa
        </Link>
      </div>

      {/* Filtr statusu */}
      <div className="flex gap-2 mb-6 flex-wrap">
        {STATUS_FILTERS.map(f => (
          <button
            key={f.value}
            onClick={() => setStatusFilter(f.value)}
            className={`px-3 py-1.5 rounded-lg text-sm font-medium border transition-colors ${
              statusFilter === f.value
                ? 'bg-mountain-600 text-white border-mountain-600'
                : 'bg-white text-gray-600 border-gray-300 hover:border-mountain-400'
            }`}
          >
            {f.label}
            {f.value && (
              <span className="ml-1.5 text-xs opacity-70">
                ({expeditions.filter(e => e.status === f.value).length})
              </span>
            )}
          </button>
        ))}
      </div>

      {displayed.length === 0 ? (
        <div className="text-center py-16 text-gray-400">
          <p className="mb-4">Nie masz jeszcze żadnych wypraw.</p>
          <Link to="/" className="text-mountain-600 hover:underline">
            Przeglądaj dostępne trasy →
          </Link>
        </div>
      ) : (
        <div className="space-y-4">
          {displayed.map(exp => {
            const status = STATUS_LABELS[exp.status] || STATUS_LABELS.PLANNED;
            return (
              <Link key={exp.id} to={`/expeditions/${exp.id}`} className="block group">
                <div className="bg-white rounded-xl border border-gray-200 p-5 hover:shadow-md hover:border-mountain-300 transition-all">
                  <div className="flex items-start justify-between gap-4">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-1">
                        <h3 className="font-semibold text-gray-900 group-hover:text-mountain-700 transition-colors truncate">
                          {exp.name}
                        </h3>
                        <span className={`text-xs px-2 py-0.5 rounded-full border ${status.color} whitespace-nowrap`}>
                          {status.label}
                        </span>
                      </div>
                      <p className="text-sm text-gray-500 truncate">{exp.routeLabel || exp.trailName || exp.trail?.name}</p>
                    </div>
                    <div className="text-right shrink-0">
                      <div className="font-medium text-gray-800">
                        {new Date(exp.plannedDate).toLocaleDateString('pl-PL', {
                          day: 'numeric', month: 'long', year: 'numeric'
                        })}
                      </div>
                      <div className="text-xs text-gray-400 mt-0.5">
                        {exp.memberCount + 1} uczestnik{exp.memberCount !== 0 ? 'ów' : ''}
                      </div>
                    </div>
                  </div>

                  <div className="flex gap-4 mt-3 text-xs text-gray-500">
                    <span>Dystans: {exp.distanceKm ?? exp.trail?.distanceKm} km</span>
                    <span>Podejście: +{exp.elevationGainM ?? exp.trail?.elevationGainM} m</span>
                    <span>Czas: {exp.durationFormatted ?? exp.trail?.durationFormatted}</span>
                    {exp.highestPeakName && (
                      <span className="text-mountain-700 font-medium">
                        Szczyt: {exp.highestPeakName}
                        {exp.highestPeakElevationM && ` (${exp.highestPeakElevationM} m)`}
                      </span>
                    )}
                    {exp.comments?.length > 0 && (
                      <span>{exp.comments.length} komentarz{exp.comments.length !== 1 ? 'y' : ''}</span>
                    )}
                  </div>
                </div>
              </Link>
            );
          })}
        </div>
      )}
    </div>
  );
}
