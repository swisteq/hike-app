import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useEffect, useState } from 'react';
import { getFriendRequestsCount, getNotificationsCount } from '../api/client';

export default function Navbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [pendingCount, setPendingCount] = useState(0);
  const [notifCount, setNotifCount] = useState(0);

  useEffect(() => {
    if (!user) { setPendingCount(0); setNotifCount(0); return; }
    const fetchAll = () => {
      getFriendRequestsCount().then(({ data }) => setPendingCount(data.count)).catch(() => {});
      getNotificationsCount().then(({ data }) => setNotifCount(data.count)).catch(() => {});
    };
    fetchAll();
    const interval = setInterval(fetchAll, 30_000);
    return () => clearInterval(interval);
  }, [user]);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <nav className="bg-mountain-700 text-white shadow-lg">
      <div className="max-w-6xl mx-auto px-4 py-3 flex items-center justify-between">
        <Link to="/" className="flex items-center gap-2 text-xl font-bold tracking-tight">
          <span>Górskie Wyprawy</span>
        </Link>

        <div className="flex items-center gap-4">
          <Link to="/" className="hover:text-mountain-200 transition-colors text-sm font-medium">
            Wyprawy
          </Link>
          {user ? (
            <>
              <Link to="/expeditions" className="hover:text-mountain-200 transition-colors text-sm font-medium">
                Moje wyprawy
              </Link>
              <Link to="/groups" className="hover:text-mountain-200 transition-colors text-sm font-medium">
                Grupy
              </Link>
              <Link to="/notifications" className="relative hover:text-mountain-200 transition-colors text-sm font-medium">
                Powiadomienia
                {notifCount > 0 && (
                  <span className="absolute -top-1.5 -right-3 bg-red-500 text-white text-xs w-4 h-4 rounded-full flex items-center justify-center font-bold">
                    {notifCount > 9 ? '9+' : notifCount}
                  </span>
                )}
              </Link>
              <Link to="/friends" className="relative hover:text-mountain-200 transition-colors text-sm font-medium">
                Znajomi
                {pendingCount > 0 && (
                  <span className="absolute -top-1.5 -right-3 bg-red-500 text-white text-xs w-4 h-4 rounded-full flex items-center justify-center font-bold">
                    {pendingCount > 9 ? '9+' : pendingCount}
                  </span>
                )}
              </Link>
              <div className="flex items-center gap-3 ml-2">
                <span className="text-mountain-200 text-sm">{user.name}</span>
                <button
                  onClick={handleLogout}
                  className="bg-mountain-600 hover:bg-mountain-500 px-3 py-1.5 rounded-lg text-sm font-medium transition-colors"
                >
                  Wyloguj
                </button>
              </div>
            </>
          ) : (
            <div className="flex gap-2">
              <Link
                to="/login"
                className="hover:text-mountain-200 transition-colors text-sm font-medium px-3 py-1.5"
              >
                Zaloguj
              </Link>
              <Link
                to="/register"
                className="bg-white text-mountain-700 hover:bg-mountain-50 px-3 py-1.5 rounded-lg text-sm font-medium transition-colors"
              >
                Rejestracja
              </Link>
            </div>
          )}
        </div>
      </div>
    </nav>
  );
}
