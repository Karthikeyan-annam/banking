import React, { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { ArrowLeft, ArrowDownLeft, ArrowUpRight, ArrowLeftRight, ShieldCheck, History, Landmark } from 'lucide-react';
import api from '../api/client';
import { Modal } from '../components/Modal';
import { ToastAlert } from '../components/ToastAlert';

export const AccountDetail = () => {
  const { id } = useParams();
  const [account, setAccount] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  // Modals
  const [activeModal, setActiveModal] = useState(null); // 'DEPOSIT' | 'WITHDRAW' | 'TRANSFER'
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  const [targetAccountNumber, setTargetAccountNumber] = useState('');
  const [actionLoading, setActionLoading] = useState(false);

  const fetchAccount = async () => {
    try {
      setLoading(true);
      setError('');
      const res = await api.get(`/accounts/${id}`);
      setAccount(res.data);
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to retrieve account details.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAccount();
  }, [id]);

  const resetForm = () => {
    setAmount('');
    setDescription('');
    setTargetAccountNumber('');
    setActiveModal(null);
  };

  const handleDeposit = async (e) => {
    e.preventDefault();
    setActionLoading(true);
    setError('');

    try {
      await api.post(`/accounts/${id}/deposit`, {
        amount: parseFloat(amount),
        description: description || 'Fund Deposit',
      });
      setSuccess(`Successfully credited $${amount} to account.`);
      resetForm();
      await fetchAccount();
    } catch (err) {
      setError(err.response?.data?.message || 'Deposit failed.');
    } finally {
      setActionLoading(false);
    }
  };

  const handleWithdraw = async (e) => {
    e.preventDefault();
    setActionLoading(true);
    setError('');

    try {
      await api.post(`/accounts/${id}/withdraw`, {
        amount: parseFloat(amount),
        description: description || 'Cash Withdrawal',
      });
      setSuccess(`Successfully withdrew $${amount} from account.`);
      resetForm();
      await fetchAccount();
    } catch (err) {
      setError(err.response?.data?.message || 'Withdrawal rejected (e.g. insufficient funds).');
    } finally {
      setActionLoading(false);
    }
  };

  const handleTransfer = async (e) => {
    e.preventDefault();
    setActionLoading(true);
    setError('');

    try {
      await api.post('/transfers', {
        sourceAccountId: id,
        targetAccountNumber: targetAccountNumber.trim(),
        amount: parseFloat(amount),
        description: description || 'P2P Fund Transfer',
      });
      setSuccess(`Successfully transferred $${amount} to Account #${targetAccountNumber}.`);
      resetForm();
      await fetchAccount();
    } catch (err) {
      setError(err.response?.data?.message || 'Transfer failed.');
    } finally {
      setActionLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="w-8 h-8 border-4 border-slate-900 border-t-emerald-500 rounded-full animate-spin"></div>
      </div>
    );
  }

  if (!account) {
    return (
      <div className="bg-white rounded-2xl p-8 border border-slate-200 text-center">
        <p className="text-sm text-slate-600 mb-4">Account not found or access restricted.</p>
        <Link to="/dashboard" className="text-xs font-semibold text-emerald-600">
          Return to Dashboard
        </Link>
      </div>
    );
  }

  return (
    <div className="max-w-5xl mx-auto space-y-6">
      {/* Top Breadcrumb */}
      <div>
        <Link
          to="/dashboard"
          className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-500 hover:text-slate-900 transition-colors"
        >
          <ArrowLeft className="w-3.5 h-3.5" />
          <span>Back to Portfolio</span>
        </Link>
      </div>

      {/* Alerts */}
      {error && <ToastAlert type="error" message={error} onClose={() => setError('')} />}
      {success && <ToastAlert type="success" message={success} onClose={() => setSuccess('')} />}

      {/* Hero Account Overview Card */}
      <div className="bg-white rounded-2xl border border-slate-200/80 p-8 shadow-sm">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-6 pb-6 border-b border-slate-100">
          <div>
            <div className="flex items-center gap-2">
              <span className="text-xs font-bold uppercase tracking-wider text-slate-400">
                {account.account_type || account.accountType}
              </span>
              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200/60">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
                {account.status}
              </span>
            </div>
            <p className="text-lg font-mono font-semibold text-slate-800 mt-1">
              {account.account_number || account.accountNumber}
            </p>
          </div>

          <Link
            to={`/accounts/${id}/transactions`}
            className="inline-flex items-center gap-2 px-4 py-2 text-xs font-semibold rounded-xl border border-slate-200 text-slate-700 hover:bg-slate-50"
          >
            <History className="w-4 h-4 text-slate-400" />
            <span>View Full Ledger</span>
          </Link>
        </div>

        {/* Balance Display */}
        <div className="pt-6">
          <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">Current Ledger Balance</p>
          <div className="flex items-baseline gap-2 mt-2">
            <span className="text-4xl font-extrabold text-slate-900 tracking-tight">
              ${parseFloat(account.balance).toLocaleString('en-US', {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2,
              })}
            </span>
            <span className="text-sm font-semibold text-slate-500">{account.currency}</span>
          </div>
        </div>

        {/* Action Buttons */}
        <div className="mt-8 grid grid-cols-1 sm:grid-cols-3 gap-3">
          <button
            onClick={() => setActiveModal('DEPOSIT')}
            className="flex items-center justify-center gap-2 p-3.5 rounded-xl bg-slate-900 text-white text-xs font-semibold hover:bg-slate-800 shadow-sm transition-all"
          >
            <ArrowDownLeft className="w-4 h-4 text-emerald-400" />
            <span>Deposit Funds</span>
          </button>

          <button
            onClick={() => setActiveModal('WITHDRAW')}
            className="flex items-center justify-center gap-2 p-3.5 rounded-xl bg-white border border-slate-200 text-slate-800 text-xs font-semibold hover:bg-slate-50 shadow-sm transition-all"
          >
            <ArrowUpRight className="w-4 h-4 text-red-500" />
            <span>Withdraw Cash</span>
          </button>

          <button
            onClick={() => setActiveModal('TRANSFER')}
            className="flex items-center justify-center gap-2 p-3.5 rounded-xl bg-emerald-600 text-white text-xs font-semibold hover:bg-emerald-700 shadow-sm transition-all"
          >
            <ArrowLeftRight className="w-4 h-4" />
            <span>Peer-to-Peer Transfer</span>
          </button>
        </div>
      </div>

      {/* Security & Audit Metadata Box */}
      <div className="bg-slate-50 rounded-2xl border border-slate-200/80 p-6 flex items-center justify-between text-xs text-slate-500">
        <div className="flex items-center gap-2">
          <ShieldCheck className="w-4 h-4 text-emerald-600" />
          <span>Row-level pessimistic locking & ACID atomicity active on this ledger</span>
        </div>
        <div>
          <span>Account ID: {account.id}</span>
        </div>
      </div>

      {/* Deposit Modal */}
      <Modal
        isOpen={activeModal === 'DEPOSIT'}
        onClose={resetForm}
        title="Deposit Funds"
        subtitle={`Crediting account ${account.account_number || account.accountNumber}`}
      >
        <form onSubmit={handleDeposit} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
              Deposit Amount ($ USD)
            </label>
            <input
              type="number"
              step="0.01"
              min="0.01"
              required
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="0.00"
              className="w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-slate-900"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
              Memo / Reference
            </label>
            <input
              type="text"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="e.g. Payroll deposit"
              className="w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-slate-900"
            />
          </div>

          <div className="pt-4 flex items-center justify-end gap-3 border-t border-slate-100">
            <button
              type="button"
              onClick={resetForm}
              className="px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-100 rounded-xl"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={actionLoading}
              className="px-4 py-2 bg-slate-900 text-white text-xs font-semibold rounded-xl hover:bg-slate-800 disabled:opacity-50"
            >
              {actionLoading ? 'Processing...' : 'Confirm Deposit'}
            </button>
          </div>
        </form>
      </Modal>

      {/* Withdraw Modal */}
      <Modal
        isOpen={activeModal === 'WITHDRAW'}
        onClose={resetForm}
        title="Withdraw Funds"
        subtitle={`Debiting account ${account.account_number || account.accountNumber}`}
      >
        <form onSubmit={handleWithdraw} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
              Withdrawal Amount ($ USD)
            </label>
            <input
              type="number"
              step="0.01"
              min="0.01"
              required
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="0.00"
              className="w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-slate-900"
            />
            <p className="text-[11px] text-slate-400 mt-1">
              Current Available: ${parseFloat(account.balance).toFixed(2)}
            </p>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
              Memo
            </label>
            <input
              type="text"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="e.g. ATM withdrawal"
              className="w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-slate-900"
            />
          </div>

          <div className="pt-4 flex items-center justify-end gap-3 border-t border-slate-100">
            <button
              type="button"
              onClick={resetForm}
              className="px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-100 rounded-xl"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={actionLoading}
              className="px-4 py-2 bg-slate-900 text-white text-xs font-semibold rounded-xl hover:bg-slate-800 disabled:opacity-50"
            >
              {actionLoading ? 'Processing...' : 'Confirm Withdrawal'}
            </button>
          </div>
        </form>
      </Modal>

      {/* Transfer Modal */}
      <Modal
        isOpen={activeModal === 'TRANSFER'}
        onClose={resetForm}
        title="P2P Fund Transfer"
        subtitle={`Instant transfer from Account #${account.account_number || account.accountNumber}`}
      >
        <form onSubmit={handleTransfer} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
              Recipient Account Number (10 Digits)
            </label>
            <input
              type="text"
              required
              value={targetAccountNumber}
              onChange={(e) => setTargetAccountNumber(e.target.value)}
              placeholder="e.g. 1000045823"
              className="w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-sm font-mono focus:outline-none focus:ring-2 focus:ring-slate-900"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
              Transfer Amount ($ USD)
            </label>
            <input
              type="number"
              step="0.01"
              min="0.01"
              required
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="0.00"
              className="w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-slate-900"
            />
            <p className="text-[11px] text-slate-400 mt-1">
              Available to transfer: ${parseFloat(account.balance).toFixed(2)}
            </p>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
              Transfer Reference / Note
            </label>
            <input
              type="text"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="e.g. Rent share"
              className="w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-slate-900"
            />
          </div>

          <div className="pt-4 flex items-center justify-end gap-3 border-t border-slate-100">
            <button
              type="button"
              onClick={resetForm}
              className="px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-100 rounded-xl"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={actionLoading}
              className="px-4 py-2 bg-emerald-600 text-white text-xs font-semibold rounded-xl hover:bg-emerald-700 disabled:opacity-50"
            >
              {actionLoading ? 'Executing Transfer...' : 'Execute ACID Transfer'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
