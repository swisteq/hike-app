import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import Navbar from './components/Navbar';
import TrailsPage from './pages/TrailsPage';
import TrailDetailPage from './pages/TrailDetailPage';
import { LoginPage, RegisterPage } from './pages/AuthPages';
import ExpeditionsPage from './pages/ExpeditionsPage';
import BrowseExpeditionsPage from './pages/BrowseExpeditionsPage';
import ExpeditionDetailPage from './pages/ExpeditionDetailPage';
import NewExpeditionPage from './pages/NewExpeditionPage';
import JoinByLinkPage from './pages/JoinByLinkPage';
import FriendsPage from './pages/FriendsPage';
import GroupsPage from './pages/GroupsPage';
import GroupDetailPage from './pages/GroupDetailPage';
import NotificationsPage from './pages/NotificationsPage';

function PrivateRoute({ children }) {
  const { user } = useAuth();
  return user ? children : <Navigate to="/login" replace />;
}

function Layout({ children }) {
  return (
    <div className="min-h-screen bg-gray-50">
      <Navbar />
      <main>{children}</main>
    </div>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          {/* Publiczne bez Navbar */}
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />

          {/* Z Navbar */}
          <Route path="/join/:token" element={<Layout><JoinByLinkPage /></Layout>} />
          <Route path="/" element={<Layout><BrowseExpeditionsPage /></Layout>} />
          <Route path="/trasy" element={<Layout><TrailsPage /></Layout>} />
          <Route path="/trails/:id" element={<Layout><TrailDetailPage /></Layout>} />

          {/* Prywatne */}
          <Route path="/notifications" element={
            <PrivateRoute><Layout><NotificationsPage /></Layout></PrivateRoute>
          } />
          <Route path="/friends" element={
            <PrivateRoute><Layout><FriendsPage /></Layout></PrivateRoute>
          } />
          <Route path="/groups" element={<Layout><GroupsPage /></Layout>} />
          <Route path="/groups/:id" element={
            <PrivateRoute><Layout><GroupDetailPage /></Layout></PrivateRoute>
          } />
          <Route path="/expeditions" element={
            <PrivateRoute><Layout><ExpeditionsPage /></Layout></PrivateRoute>
          } />
          <Route path="/expeditions/new" element={
            <PrivateRoute><Layout><NewExpeditionPage /></Layout></PrivateRoute>
          } />
          <Route path="/expeditions/:id" element={
            <PrivateRoute><Layout><ExpeditionDetailPage /></Layout></PrivateRoute>
          } />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
