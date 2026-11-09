import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Landmark, Lock, Mail, User, Phone, ArrowRight, WifiOff, RefreshCw } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { ToastAlert } from '../components/ToastAlert';
import api from '../api/client';

export const Register = () => {
  const navigate = useNavigate();
  const { register } = useAuth();

  const [formData, setFormData] = useState({
    firstName: '',
    lastName: '',
    email: '',
    password: '',
    phoneNumber: '',
  });

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [backendStatus, setBackendStatus] = useState('CHECKING'); // 'ONLINE' | 'OFFLINE' | 'CHECKING'

  // Proactive Backend Connectivity Check
  const checkBackendHealth = async () => {
    try {
      setBackendStatus('CHECKING');
      // Actuator or a lightweight public endpoint
      await api.get('/auth/login', { timeout: 3000 }).catch((err) => {
        // If it returns 405 Method Not Allowed (since GET on POST endpoint) or 400, backend is ALIVE
        if (err.response && err.response.status !== 503) {
          return true;
        }
        throw err;
      });
      setBackendStatus('ONLINE');
    } catch (err) {
      if (err.isNetworkError || !err.response || err.code === 'ERR_NETWORK' || err.code === 'ECONNREFUSED') {
        setBackendStatus('OFFLINE');
      } else {
        setBackendStatus('ONLINE');
      }
    }
  };

  useEffect(() => {
    checkBackendHealth();
  }, []);

  const handleChange = (e) => {
    setFormData((prev) => ({ ...prev, [e.target.name]: e.target.value }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      await register(formData);
      navigate('/dashboard');
    } catch (err) {
      let msg = '';

      // 1. Direct Network Connection Refusal / Server Down
      if (err.isNetworkError || err.userFriendlyMessage) {
        msg = err.userFriendlyMessage;
        setBackendStatus('OFFLINE');
      } else {
        const responseData = err.response?.data;

        // 2. Field-level validation constraints
        if (responseData?.details && typeof responseData.details === 'object') {
          const fieldErrors = Object.values(responseData.details);
          if (fieldErrors.length > 0) {
            msg = fieldErrors.join('. ');
          }
        }

        // 3. Backend response message (e.g. duplicate email, etc.)
        if (!msg) {
          msg = responseData?.message || responseData?.error || 'Registration failed. Please check your inputs.';
        }
      }

      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50 p-6 selection:bg-emerald-500 selection:text-white">
      <div className="w-full max-w-lg">
        {/* Branding */}
        <div className="text-center mb-6">
          <div className="inline-flex w-12 h-12 rounded-xl bg-slate-900 text-emerald-400 items-center justify-center shadow-lg shadow-slate-900/10 mb-3">
            <Landmark className="w-6 h-6" />
          </div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Open a Banking Profile</h1>
          <p className="text-sm text-slate-500 mt-1">Register for secure core banking and P2P transfers</p>
        </div>

        {/* Backend Offline Advisory Banner */}
        {backendStatus === 'OFFLINE' && (
          <div className="mb-4 p-4 rounded-2xl bg-amber-50 border border-amber-200/80 text-amber-900 shadow-sm flex items-start gap-3">
            <WifiOff className="w-5 h-5 text-amber-600 shrink-0 mt-0.5" />
            <div className="flex-1 text-xs leading-relaxed">
              <span className="font-bold block text-sm mb-0.5">Banking Server Unreachable</span>
              The Spring Boot backend is not responding on <code className="bg-amber-100 px-1 py-0.5 rounded font-mono font-semibold">http://localhost:8080</code>.
              Please start the backend server via <code className="bg-amber-100 px-1 py-0.5 rounded font-mono">npm start</code> or <code className="bg-amber-100 px-1 py-0.5 rounded font-mono">start.bat</code> before submitting.
            </div>
            <button
              onClick={checkBackendHealth}
              title="Retry server connection"
              className="p-1 rounded-lg hover:bg-amber-200/60 transition-colors text-amber-700"
            >
              <RefreshCw className="w-4 h-4" />
            </button>
          </div>
        )}

        {/* Card */}
        <div className="bg-white rounded-2xl shadow-xl shadow-slate-200/60 border border-slate-200/80 p-8">
          {error && (
            <div className="mb-5">
              <ToastAlert type="error" message={error} onClose={() => setError('')} />
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
                  First Name
                </label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-400">
                    <User className="w-4 h-4" />
                  </div>
                  <input
                    type="text"
                    required
                    name="firstName"
                    value={formData.firstName}
                    onChange={handleChange}
                    placeholder="Alice"
                    className="w-full pl-10 pr-4 py-2.5 bg-slate-50/50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-slate-900 focus:bg-white text-slate-800"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
                  Last Name
                </label>
                <input
                  type="text"
                  required
                  name="lastName"
                  value={formData.lastName}
                  onChange={handleChange}
                  placeholder="Smith"
                  className="w-full px-4 py-2.5 bg-slate-50/50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-slate-900 focus:bg-white text-slate-800"
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
                Email Address
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-400">
                  <Mail className="w-4 h-4" />
                </div>
                <input
                  type="email"
                  required
                  name="email"
                  value={formData.email}
                  onChange={handleChange}
                  placeholder="alice@banking.com"
                  className="w-full pl-10 pr-4 py-2.5 bg-slate-50/50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-slate-900 focus:bg-white text-slate-800"
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
                Phone Number <span className="text-slate-400 lowercase">(optional)</span>
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-400">
                  <Phone className="w-4 h-4" />
                </div>
                <input
                  type="text"
                  name="phoneNumber"
                  value={formData.phoneNumber}
                  onChange={handleChange}
                  placeholder="+1-555-0199"
                  className="w-full pl-10 pr-4 py-2.5 bg-slate-50/50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-slate-900 focus:bg-white text-slate-800"
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
                Password
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-400">
                  <Lock className="w-4 h-4" />
                </div>
                <input
                  type="password"
                  required
                  name="password"
                  value={formData.password}
                  onChange={handleChange}
                  placeholder="Min 8 chars, 1 uppercase, 1 lowercase, 1 digit"
                  className="w-full pl-10 pr-4 py-2.5 bg-slate-50/50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-slate-900 focus:bg-white text-slate-800"
                />
              </div>
              <p className="text-[11px] text-slate-500 mt-1">
                Must contain at least 8 characters with at least one uppercase letter, one lowercase letter, and one number. Special characters are accepted.
              </p>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full mt-2 py-3 px-4 bg-slate-900 hover:bg-slate-800 text-white rounded-xl text-sm font-semibold shadow-md shadow-slate-900/10 flex items-center justify-center gap-2 transition-all disabled:opacity-50"
            >
              {loading ? (
                <div className="w-5 h-5 border-2 border-white/20 border-t-white rounded-full animate-spin"></div>
              ) : (
                <>
                  <span>Create Account Profile</span>
                  <ArrowRight className="w-4 h-4" />
                </>
              )}
            </button>
          </form>

          <div className="mt-6 pt-5 border-t border-slate-100 text-center">
            <p className="text-xs text-slate-500">
              Already enrolled?{' '}
              <Link to="/login" className="font-semibold text-emerald-600 hover:text-emerald-700">
                Sign in to portal
              </Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};
