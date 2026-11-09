import React, { useState, useEffect } from 'react';
import { ArrowLeftRight, ShieldCheck, CheckCircle2 } from 'lucide-react';
import api from '../api/client';
import { ToastAlert } from '../components/ToastAlert';

export const Transfers = () => {
  const [accounts, setAccounts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  const [sourceAccountId, setSourceAccountId] = useState('');
  const [targetAccountNumber, setTargetAccountNumber] = useState('');
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');

  const [error, setError] = useState('');
  const [successResponse, setSuccessResponse] = useState(null);

  const fetchAccounts = async () => {
    try {
      setLoading(true);
      const res = await api.get('/accounts');
      setAccounts(res.data);
      if (res.data.length > 0) {
        setSourceAccountId(res.data[0].id);
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load your accounts.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAccounts();
  }, []);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setError('');
    setSuccessResponse(null);

    try {
      const res = await api.post('/transfers', {
        sourceAccountId,
        targetAccountNumber: targetAccountNumber.trim(),
        amount: parseFloat(amount),
        description: description || 'P2P Fund Transfer',
      });
      setSuccessResponse(res.data);
      setAmount('');
      setDescription('');
      setTargetAccountNumber('');
      await fetchAccounts();
    } catch (err) {
      setError(err.response?.data?.message || 'Transfer failed.');
    } finally {
      setSubmitting(false);
    }
  };

  const selectedAccount = accounts.find((a) => a.id === sourceAccountId);

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Peer-to-Peer Fund Transfer</h1>
        <p className="text-xs text-slate-500 mt-1">
          Instant, deadlock-free transfer directly into any client account number.
        </p>
      </div>

      {error && <ToastAlert type="error" message={error} onClose={() => setError('')} />}

      {/* Success Notification Card */}
      {successResponse && (
        <div className="bg-emerald-50 border border-emerald-200/80 rounded-2xl p-6 text-emerald-900 shadow-sm space-y-3">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-5 h-5 text-emerald-600" />
            <span className="font-bold text-sm">Transfer Successfully Executed</span>
          </div>
          <div className="grid grid-cols-2 gap-4 text-xs pt-2 border-t border-emerald-200/60 font-mono">
            <div>
              <span className="text-emerald-700 block text-[10px] uppercase font-sans">Reference Code</span>
              <span>{successResponse.reference_code}</span>
            </div>
            <div>
              <span className="text-emerald-700 block text-[10px] uppercase font-sans">Amount Transferred</span>
              <span className="font-bold text-emerald-800">${parseFloat(successResponse.amount).toFixed(2)} USD</span>
            </div>
            <div>
              <span className="text-emerald-700 block text-[10px] uppercase font-sans">From Account</span>
              <span>{successResponse.source_account_number}</span>
            </div>
            <div>
              <span className="text-emerald-700 block text-[10px] uppercase font-sans">To Account</span>
              <span>{successResponse.target_account_number}</span>
            </div>
          </div>
        </div>
      )}

      {/* Transfer Form Card */}
      <div className="bg-white rounded-2xl border border-slate-200/80 p-8 shadow-sm">
        <form onSubmit={handleSubmit} className="space-y-5">
          {/* Source Account Selector */}
          <div>
            <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
              Select Source Account
            </label>
            {loading ? (
              <div className="h-10 bg-slate-100 rounded-xl animate-pulse"></div>
            ) : accounts.length === 0 ? (
              <p className="text-xs text-red-500">No active accounts found to transfer from.</p>
            ) : (
              <select
                value={sourceAccountId}
                onChange={(e) => setSourceAccountId(e.target.value)}
                className="w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-slate-900 text-slate-800"
              >
                {accounts.map((acc) => (
                  <option key={acc.id} value={acc.id}>
                    {acc.account_type || acc.accountType} (#{acc.account_number || acc.accountNumber}) — Balance: $
                    {parseFloat(acc.balance).toFixed(2)} {acc.currency}
                  </option>
                ))}
              </select>
            )}

            {selectedAccount && (
              <p className="text-[11px] text-slate-400 mt-1">
                Available to transfer: ${parseFloat(selectedAccount.balance).toFixed(2)}
              </p>
            )}
          </div>

          {/* Recipient Account Number */}
          <div>
            <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
              Recipient Account Number
            </label>
            <input
              type="text"
              required
              value={targetAccountNumber}
              onChange={(e) => setTargetAccountNumber(e.target.value)}
              placeholder="e.g. 1000045823"
              className="w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-sm font-mono focus:outline-none focus:ring-2 focus:ring-slate-900 text-slate-800"
            />
            <p className="text-[11px] text-slate-400 mt-1">
              Enter the recipient's 10-digit account number.
            </p>
          </div>

          {/* Transfer Amount */}
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
              className="w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-slate-900 text-slate-800"
            />
          </div>

          {/* Description */}
          <div>
            <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
              Transfer Memo / Purpose
            </label>
            <input
              type="text"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="e.g. Invoice payment #402"
              className="w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-slate-900 text-slate-800"
            />
          </div>

          {/* Submit Button */}
          <div className="pt-4 border-t border-slate-100 flex items-center justify-between">
            <div className="flex items-center gap-1.5 text-xs text-slate-400">
              <ShieldCheck className="w-4 h-4 text-emerald-600" />
              <span>Two-phase pessimistic locking</span>
            </div>

            <button
              type="submit"
              disabled={submitting || accounts.length === 0}
              className="px-6 py-2.5 bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-semibold rounded-xl shadow-md disabled:opacity-50 flex items-center gap-2"
            >
              {submitting ? (
                <div className="w-4 h-4 border-2 border-white/20 border-t-white rounded-full animate-spin"></div>
              ) : (
                <>
                  <ArrowLeftRight className="w-4 h-4" />
                  <span>Execute Transfer</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
