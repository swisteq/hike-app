import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

function AuthForm({ title, onSubmit, isLogin }) {
  const [form, setForm] = useState({ name: '', email: '', password: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handle = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await onSubmit(form);
    } catch (err) {
      setError(err.response?.data?.message || 'Wystąpił błąd. Spróbuj ponownie.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-mountain-800 to-mountain-600 px-4">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-md p-8">
        <div className="text-center mb-8">
          <h1 className="text-2xl font-bold text-gray-900">{title}</h1>
          <p className="text-gray-500 text-sm mt-1">Górskie Wyprawy</p>
        </div>

        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg text-sm mb-5">
            {error}
          </div>
        )}

        <form onSubmit={handle} className="space-y-4">
          {!isLogin && (
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Imię i nazwisko</label>
              <input
                type="text"
                required
                value={form.name}
                onChange={e => setForm({ ...form, name: e.target.value })}
                className="w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-mountain-400"
                placeholder="Jan Kowalski"
              />
            </div>
          )}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
            <input
              type="email"
              required
              value={form.email}
              onChange={e => setForm({ ...form, email: e.target.value })}
              className="w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-mountain-400"
              placeholder="jan@example.com"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Hasło</label>
            <input
              type="password"
              required
              value={form.password}
              onChange={e => setForm({ ...form, password: e.target.value })}
              className="w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-mountain-400"
              placeholder={isLogin ? '••••••••' : 'Min. 6 znaków'}
            />
          </div>
          <button
            type="submit"
            disabled={loading}
            className="w-full bg-mountain-600 hover:bg-mountain-700 disabled:bg-mountain-400 text-white py-2.5 rounded-lg font-medium transition-colors mt-2"
          >
            {loading ? 'Ładowanie...' : (isLogin ? 'Zaloguj się' : 'Zarejestruj się')}
          </button>
        </form>

        <p className="text-center text-sm text-gray-500 mt-6">
          {isLogin ? (
            <>Nie masz konta? <Link to="/register" className="text-mountain-600 hover:underline font-medium">Zarejestruj się</Link></>
          ) : (
            <>Masz już konto? <Link to="/login" className="text-mountain-600 hover:underline font-medium">Zaloguj się</Link></>
          )}
        </p>
      </div>
    </div>
  );
}

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleLogin = async ({ email, password }) => {
    await login(email, password);
    navigate('/');
  };

  return <AuthForm title="Zaloguj się" onSubmit={handleLogin} isLogin />;
}

export function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();

  const handleRegister = async ({ name, email, password }) => {
    await register(name, email, password);
    navigate('/');
  };

  return <AuthForm title="Utwórz konto" onSubmit={handleRegister} isLogin={false} />;
}
