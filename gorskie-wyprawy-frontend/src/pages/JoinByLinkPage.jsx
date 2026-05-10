import { useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { joinByInviteLink } from '../api/client';
import { useAuth } from '../context/AuthContext';

export default function JoinByLinkPage() {
  const { token } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [status, setStatus] = useState('loading'); // loading | success | error | auth
  const [expeditionId, setExpeditionId] = useState(null);
  const [message, setMessage] = useState('');

  useEffect(() => {
    if (!user) {
      setStatus('auth');
      return;
    }
    joinByInviteLink(token)
      .then(({ data }) => {
        setExpeditionId(data.expedition?.id);
        setStatus('success');
        setTimeout(() => navigate(`/expeditions/${data.expedition?.id ?? ''}`), 2000);
      })
      .catch(err => {
        setMessage(err.response?.data?.message || 'Nieprawidłowy lub wygasły link');
        setStatus('error');
      });
  }, [token, user]);

  if (status === 'auth') {
    return (
      <div className="max-w-md mx-auto px-4 py-16 text-center">
        <div className="text-5xl mb-4">🔒</div>
        <h2 className="text-xl font-semibold text-gray-800 mb-2">Wymagane logowanie</h2>
        <p className="text-gray-500 text-sm mb-6">Zaloguj się, aby dołączyć do wyprawy przez link zaproszenia.</p>
        <Link
          to={`/login?redirect=/join/${token}`}
          className="bg-mountain-600 hover:bg-mountain-700 text-white px-6 py-2.5 rounded-lg font-medium transition-colors"
        >
          Zaloguj się
        </Link>
      </div>
    );
  }

  if (status === 'loading') {
    return (
      <div className="max-w-md mx-auto px-4 py-16 text-center">
        <div className="text-gray-400">Dołączanie do wyprawy...</div>
      </div>
    );
  }

  if (status === 'success') {
    return (
      <div className="max-w-md mx-auto px-4 py-16 text-center">
        <div className="text-5xl mb-4">✅</div>
        <h2 className="text-xl font-semibold text-gray-800 mb-2">Dołączono do wyprawy!</h2>
        <p className="text-gray-500 text-sm">Za chwilę zostaniesz przekierowany...</p>
      </div>
    );
  }

  return (
    <div className="max-w-md mx-auto px-4 py-16 text-center">
      <div className="text-5xl mb-4">❌</div>
      <h2 className="text-xl font-semibold text-gray-800 mb-2">Nie udało się dołączyć</h2>
      <p className="text-gray-500 text-sm mb-6">{message}</p>
      <Link to="/" className="text-mountain-600 hover:underline text-sm">Wróć do strony głównej</Link>
    </div>
  );
}
