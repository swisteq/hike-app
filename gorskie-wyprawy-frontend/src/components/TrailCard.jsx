import { Link } from 'react-router-dom';

export default function TrailCard({ trail }) {
  return (
    <Link to={`/trails/${trail.id}`} className="block group">
      <div className="bg-white rounded-xl border border-gray-200 p-5 hover:shadow-md hover:border-mountain-300 transition-all">
        <h3 className="font-semibold text-gray-900 group-hover:text-mountain-700 transition-colors mb-3 truncate">
          {trail.name}
        </h3>

        <div className="grid grid-cols-2 gap-2 text-sm">
          <div className="text-gray-600">
            <span className="text-gray-400 text-xs">Dystans: </span>{trail.distanceKm} km
          </div>
          <div className="text-gray-600">
            <span className="text-gray-400 text-xs">Czas: </span>{trail.durationFormatted}
          </div>
          <div className="text-gray-600">
            <span className="text-gray-400 text-xs">Podejście: </span>+{trail.elevationGainM} m
          </div>
          <div className="text-gray-600">
            <span className="text-gray-400 text-xs">Maks. wys.: </span>{trail.maxElevationM} m n.p.m.
          </div>
        </div>

      </div>
    </Link>
  );
}
