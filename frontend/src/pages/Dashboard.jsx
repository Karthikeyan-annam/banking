import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { Plus, CreditCard, ArrowUpRight, ArrowDownLeft, Shield, Sparkles, RefreshCw } from 'lucide-react';
import api from '../api/client';
import { useAuth } from '../context/AuthContext';
import { Modal } from '../components/Modal';
import { ToastAlert } from '../components/ToastAlert';

export const Dashboard = () => {
  const { user } = useAuth();
  const [accounts, setAccounts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  // Modal State
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [modalLoading, setModalLoading] = useState(false);
  const [accountType, setAccountType] = useState('SAVINGS');
  const [initialDeposit, setInitialDeposit] = useState('100.00');

  const fetchAccounts = async () => {
    try {
      setLoading(true);
      setError('');
      const res = await api.get('/accounts');
      setAccounts(res.data);
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load user accounts.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAccounts();
  }, []);

  const handleCreateAccount = async (e) => {
    e.preventDefault();
    setModalLoading(true);
    setError('');

    try {
      await api.post('/accounts', {
        accountType,
        initialDeposit: parseFloat(initialDeposit) || 0,
        currency: 'USD',
      });
      setIsModalOpen(false);
      setSuccess('New banking account successfully opened.');
      await fetchAccounts();
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to open new account.');
    } finally {
      setModalLoading(false);
    }
  };

  const totalBalance = accounts.reduce(
    (sum, acc) => sum + (parseFloat(acc.balance) || 0),
    0
  );

  return (
    <div className="space-y-8 max-w-7xl mx-auto">
      {/* Notifications */}
      {error && <ToastAlert type="error" message={error} onClose={() => setError('')} />}
      {success && <ToastAlert type="success" message={success} onClose={() => setSuccess('')} />}

      {/* Hero Welcome Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 bg-white p-7 rounded-2xl border border-slate-200/80 shadow-sm">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <span className="text-xs font-semibold uppercase tracking-wider text-emerald-600 bg-emerald-50 px-2 py-0.5 rounded border border-emerald-200">
              Personal Portfolio
            </span>
            <span className="text-xs text-slate-400">Institutional Client</span>
          </div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">
            Welcome back, {user?.firstName}
          </h1>
          <p className="text-sm text-slate-500 mt-0.5">
            Overview of your active deposit and current accounts.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={fetchAccounts}
            className="p-2.5 rounded-xl border border-slate-200 text-slate-600 hover:bg-slate-50 hover:text-slate-900 transition-colors"
            title="Refresh Account Balances"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          </button>
          <button
            onClick={() => setIsModalOpen(true)}
            className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 text-white text-sm font-semibold shadow-md shadow-slate-900/10 transition-all"
          >
            <Plus className="w-4 h-4" />
            <span>Open New Account</span>
          </button>
        </div>
      </div>

      {/* Aggregate Balance Metric */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
        <div className="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-sm">
          <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Total Net Balance</p>
          <div className="mt-2 flex items-baseline gap-2">
            <span className="text-3xl font-extrabold text-slate-900 tracking-tight">
              ${totalBalance.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
            </span>
            <span className="text-xs font-medium text-slate-500">USD</span>
          </div>
          <div className="mt-3 flex items-center gap-1.5 text-xs text-emerald-600 font-medium">
            <Shield className="w-3.5 h-3.5" />
            <span>FDIC-Insured Multi-Tier Ledger</span>
          </div>
        </div>

        <div className="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-sm">
          <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Active Accounts</p>
          <div className="mt-2 flex items-baseline gap-2">
            <span className="text-3xl font-extrabold text-slate-900 tracking-tight">{accounts.length}</span>
            <span className="text-xs font-medium text-slate-500">Accounts registered</span>
          </div>
          <div className="mt-3 text-xs text-slate-400">All accounts currently in ACTIVE status</div>
        </div>

        <div className="bg-gradient-to-br from-slate-900 to-slate-800 text-white p-6 rounded-2xl shadow-md shadow-slate-900/10 flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between text-xs text-slate-400 mb-1">
              <span>Instant P2P Network</span>
              <Sparkles className="w-4 h-4 text-emerald-400" />
            </div>
            <p className="text-sm font-semibold text-slate-200">Zero-Fee Peer Transfers</p>
            <p className="text-xs text-slate-400 mt-1">Direct account-to-account ACID fund transfers.</p>
          </div>
          <Link
            to="/transfers"
            className="mt-4 inline-flex items-center gap-1.5 text-xs font-semibold text-emerald-400 hover:text-emerald-300 transition-colors"
          >
            <span>Initiate Transfer</span>
            <ArrowUpRight className="w-3.5 h-3.5" />
          </Link>
        </div>
      </div>

      {/* Account Cards Grid */}
      <div>
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-bold text-slate-900 tracking-tight">Your Accounts</h2>
          <span className="text-xs text-slate-500">{accounts.length} total accounts</span>
        </div>

        {loading ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {[1, 2].map((i) => (
              <div key={i} className="h-48 bg-white rounded-2xl border border-slate-200 p-6 animate-pulse" />
            ))}
          </div>
        ) : accounts.length === 0 ? (
          <div className="bg-white rounded-2xl border border-dashed border-slate-300 p-12 text-center">
            <CreditCard className="w-10 h-10 text-slate-300 mx-auto mb-3" />
            <h3 className="text-base font-semibold text-slate-800">No bank accounts opened yet</h3>
            <p className="text-xs text-slate-500 mt-1 max-w-sm mx-auto">
              Open your first savings or checking account with an optional initial deposit to get started.
            </p>
            <button
              onClick={() => setIsModalOpen(true)}
              className="mt-4 inline-flex items-center gap-2 px-4 py-2 bg-slate-900 text-white text-xs font-semibold rounded-xl hover:bg-slate-800"
            >
              <Plus className="w-3.5 h-3.5" />
              Open an Account
            </button>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {accounts.map((account) => (
              <div
                key={account.id}
                className="group bg-white rounded-2xl border border-slate-200/80 p-6 hover:shadow-lg hover:border-slate-300 transition-all flex flex-col justify-between"
              >
                <div>
                  <div className="flex items-start justify-between">
                    <div>
                      <span className="text-[11px] font-bold uppercase tracking-wider text-slate-400">
                        {account.account_type || account.accountType}
                      </span>
                      <p className="text-sm font-mono text-slate-700 tracking-wider mt-0.5 font-semibold">
                        {account.account_number || account.accountNumber}
                      </p>
                    </div>
                    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200/60">
                      <span className="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
                      {account.status}
                    </span>
                  </div>

                  <div className="mt-6">
                    <p className="text-xs text-slate-400 font-medium">Available Balance</p>
                    <div className="flex items-baseline gap-1 mt-1">
                      <span className="text-2xl font-black text-slate-900 tracking-tight">
                        ${parseFloat(account.balance).toLocaleString('en-US', {
                          minimumFractionDigits: 2,
                          maximumFractionDigits: 2,
                        })}
                      </span>
                      <span className="text-xs font-semibold text-slate-500">{account.currency}</span>
                    </div>
                  </div>
                </div>

                <div className="mt-6 pt-4 border-t border-slate-100 flex items-center justify-between">
                  <Link
                    to={`/accounts/${account.id}`}
                    className="text-xs font-semibold text-slate-900 hover:text-emerald-600 flex items-center gap-1 transition-colors"
                  >
                    <span>Manage Account</span>
                    <ArrowUpRight className="w-3.5 h-3.5" />
                  </Link>

                  <Link
                    to={`/accounts/${account.id}/transactions`}
                    className="text-xs text-slate-400 hover:text-slate-600 transition-colors"
                  >
                    View History
                  </Link>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Open Account Modal */}
      <Modal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        title="Open New Bank Account"
        subtitle="Create an institutional depository account."
      >
        <form onSubmit={handleCreateAccount} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
              Account Product Type
            </label>
            <select
              value={accountType}
              onChange={(e) => setAccountType(e.target.value)}
              className="w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-slate-900 text-slate-800"
            >
              <option value="SAVINGS">High-Yield Savings (SAVINGS)</option>
              <option value="CURRENT">Institutional Current / Checking (CURRENT)</option>
              <option value="FIXED_DEPOSIT">Term Fixed Deposit (FIXED_DEPOSIT)</option>
            </select>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
              Opening Deposit ($ USD)
            </label>
            <input
              type="number"
              step="0.01"
              min="0"
              value={initialDeposit}
              onChange={(e) => setInitialDeposit(e.target.value)}
              placeholder="0.00"
              className="w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-slate-900 text-slate-800"
            />
            <p className="text-[11px] text-slate-400 mt-1">Optional. Can be 0.00 or higher.</p>
          </div>

          <div className="pt-4 flex items-center justify-end gap-3 border-t border-slate-100">
            <button
              type="button"
              onClick={() => setIsModalOpen(false)}
              className="px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-100 rounded-xl"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={modalLoading}
              className="px-4 py-2 bg-slate-900 hover:bg-slate-800 text-white text-xs font-semibold rounded-xl shadow-md disabled:opacity-50"
            >
              {modalLoading ? 'Opening...' : 'Confirm Account Creation'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
