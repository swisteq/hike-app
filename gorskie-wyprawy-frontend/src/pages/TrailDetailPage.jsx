import { useState, useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { MapContainer, TileLayer, Polyline, Marker, Popup } from 'react-leaflet';
import { getTrail } from '../api/client';
import 'leaflet/dist/leaflet.css';

export default function TrailDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [trail, setTrail] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getTrail(id)
      .then(({ data }) => setTrail(data))
      .catch(() => navigate('/'))
      .finally(() => setLoading(false));
  }, [id, navigate]);

  if (loading) return <div className="text-center py-16 text-gray-400">Ładowanie...</div>;
  if (!trail) return null;

  const center = [trail.startLat, trail.startLon];

  return (
    <div className="max-w-5xl mx-auto px-4 py-8">
      <Link to="/" className="text-mountain-600 hover:text-mountain-800 text-sm mb-4 inline-block">
        ← Wróć do listy tras
      </Link>

      <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
        {/* Nagłówek */}
        <div className="p-6 border-b border-gray-100">
          <div className="flex items-start justify-between gap-4">
            <div>
              <h1 className="text-2xl font-bold text-gray-900 mb-2">{trail.name}</h1>
            </div>
          </div>
        </div>

        {/* Metryki */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-0 border-b border-gray-100">
          {[
            { label: 'Dystans', value: `${trail.distanceKm} km` },
            { label: 'Czas', value: trail.durationFormatted },
            { label: 'Podejście', value: `+${trail.elevationGainM} m` },
            { label: 'Maks. wys.', value: `${trail.maxElevationM} m n.p.m.` },
          ].map(({ label, value }) => (
            <div key={label} className="p-5 text-center border-r border-gray-100 last:border-r-0">
              <div className="text-xs text-gray-500 uppercase tracking-wide mb-0.5">{label}</div>
              <div className="font-semibold text-gray-900">{value}</div>
            </div>
          ))}
        </div>

        {/* Dodatkowe metryki */}
        <div className="grid grid-cols-3 gap-0 border-b border-gray-100 bg-gray-50">
          {[
            { label: 'Zejście', value: `-${trail.elevationLossM} m` },
            { label: 'Min. wysokość', value: `${trail.minElevationM} m n.p.m.` },
            { label: 'Punkty GPS', value: trail.trackPointsCount },
          ].map(({ label, value }) => (
            <div key={label} className="p-4 text-center border-r border-gray-100 last:border-r-0">
              <div className="text-xs text-gray-500 mb-0.5">{label}</div>
              <div className="font-medium text-gray-700">{value}</div>
            </div>
          ))}
        </div>

        {/* Mapa */}
        <div className="p-6">
          <h2 className="font-semibold text-gray-800 mb-3">Mapa trasy</h2>
          <div className="rounded-lg overflow-hidden border border-gray-200" style={{ height: 400 }}>
            <MapContainer center={center} zoom={13} style={{ height: '100%', width: '100%' }}>
              <TileLayer
                url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                attribution='© <a href="https://openstreetmap.org">OpenStreetMap</a>'
              />
              <Marker position={center}>
                <Popup>
                  <strong>{trail.name}</strong><br />
                  Start / Meta
                </Popup>
              </Marker>
            </MapContainer>
          </div>
          <p className="text-xs text-gray-400 mt-2">
            Start: {trail.startLat.toFixed(5)}, {trail.startLon.toFixed(5)}
            {' · '}
            <a
              href={`/api/trails/${trail.id}/gpx`}
              download
              className="text-mountain-600 hover:underline"
            >
              Pobierz GPX
            </a>
          </p>
        </div>

        {trail.description && (
          <div className="px-6 pb-6">
            <h2 className="font-semibold text-gray-800 mb-2">Opis</h2>
            <p className="text-gray-600 text-sm leading-relaxed">{trail.description}</p>
          </div>
        )}
      </div>
    </div>
  );
}
