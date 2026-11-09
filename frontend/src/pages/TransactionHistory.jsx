import React, { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  ArrowLeft,
  ArrowUpRight,
  ArrowDownLeft,
  Filter,
  Calendar,
  ChevronLeft,
  ChevronRight,
  RefreshCw,
  Landmark,
} from 'lucide-react';
import api from '../api/client';
import { ToastAlert } from '../components/ToastAlert';

export const TransactionHistory = () => {
  const { id } = useParams();
  const [account, setAccount] = useState(null);
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Pagination & Filtering state
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const [typeFilter, setTypeFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');

  const fetchAccount = async () => {
    try {
      const res = await api.get(`/accounts/${id}`);
      setAccount(res.data);
    } catch (err) {
      console.error(err);
    }
  };

  const fetchTransactions = async (pageIndex = 0) => {
    try {
      setLoading(true);
      setError('');
      const params = {
        page: pageIndex,
        size: 15,
        sortBy: 'createdAt',
        direction: 'DESC',
      };
      if (typeFilter) params.type = typeFilter;
      if (statusFilter) params.status = statusFilter;

      const res = await api.get(`/accounts/${id}/transactions`, { params });
      setTransactions(res.data.content || []);
      setTotalPages(res.data.totalPages || 1);
      setTotalElements(res.data.totalElements || 0);
      setPage(pageIndex);
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load transaction ledger.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAccount();
  }, [id]);

  useEffect(() => {
    fetchTransactions(0);
  }, [id, typeFilter, statusFilter]);

  const getTransactionBadge = (type) => {
    switch (type) {
      case 'DEPOSIT':
      case 'TRANSFER_IN':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200/60">
            <ArrowDownLeft className="w-3.5 h-3.5" />
            {type}
          </span>
        );
      case 'WITHDRAWAL':
      case 'TRANSFER_OUT':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-slate-100 text-slate-700 border border-slate-200">
            <ArrowUpRight className="w-3.5 h-3.5" />
            {type}
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-slate-100 text-slate-600">
            {type}
          </span>
        );
    }
  };

  const isCredit = (type) => type === 'DEPOSIT' || type === 'TRANSFER_IN';

  return (
    <div className="max-w-6xl mx-auto space-y-6">
      {/* Breadcrumb */}
      <div>
        <Link
          to={`/accounts/${id}`}
          className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-500 hover:text-slate-900 transition-colors"
        >
          <ArrowLeft className="w-3.5 h-3.5" />
          <span>Back to Account Overview</span>
        </Link>
      </div>

      {error && <ToastAlert type="error" message={error} onClose={() => setError('')} />}

      {/* Header */}
      <div className="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-400">Ledger Audit History</span>
          </div>
          <h1 className="text-xl font-bold text-slate-900">
            Account #{account?.account_number || account?.accountNumber || '...'}
          </h1>
          <p className="text-xs text-slate-500 mt-0.5">
            Immutable log of all credits, debits, and peer transfers.
          </p>
        </div>

        <button
          onClick={() => fetchTransactions(page)}
          className="inline-flex items-center gap-2 px-3 py-2 rounded-xl border border-slate-200 text-xs font-semibold text-slate-600 hover:bg-slate-50"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
          Refresh Ledger
        </button>
      </div>

      {/* Filters Toolbar */}
      <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-sm flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-2 text-xs text-slate-500 font-medium">
            <Filter className="w-3.5 h-3.5" />
            <span>Filter By:</span>
          </div>

          <select
            value={typeFilter}
            onChange={(e) => setTypeFilter(e.target.value)}
            className="px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-lg text-xs font-medium text-slate-700 focus:outline-none focus:ring-1 focus:ring-slate-900"
          >
            <option value="">All Transaction Types</option>
            <option value="DEPOSIT">DEPOSIT</option>
            <option value="WITHDRAWAL">WITHDRAWAL</option>
            <option value="TRANSFER_OUT">TRANSFER_OUT (Debit)</option>
            <option value="TRANSFER_IN">TRANSFER_IN (Credit)</option>
          </select>

          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-lg text-xs font-medium text-slate-700 focus:outline-none focus:ring-1 focus:ring-slate-900"
          >
            <option value="">All Statuses</option>
            <option value="COMPLETED">COMPLETED</option>
            <option value="PENDING">PENDING</option>
            <option value="FAILED">FAILED</option>
          </select>
        </div>

        <div className="text-xs text-slate-500">
          Showing <span className="font-semibold text-slate-800">{transactions.length}</span> of{' '}
          <span className="font-semibold text-slate-800">{totalElements}</span> entries
        </div>
      </div>

      {/* Ledger Data Table */}
      <div className="bg-white rounded-2xl border border-slate-200/80 shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 border-b border-slate-200/80 text-[11px] font-bold text-slate-500 uppercase tracking-wider">
              <tr>
                <th className="py-3.5 px-6">Timestamp (UTC)</th>
                <th className="py-3.5 px-6">Type</th>
                <th className="py-3.5 px-6">Description / Memo</th>
                <th className="py-3.5 px-6">Status</th>
                <th className="py-3.5 px-6 text-right">Amount</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loading ? (
                <tr>
                  <td colSpan="5" className="py-12 text-center text-slate-400 text-xs">
                    <div className="w-6 h-6 border-2 border-slate-900 border-t-emerald-500 rounded-full animate-spin mx-auto mb-2"></div>
                    Loading ledger data...
                  </td>
                </tr>
              ) : transactions.length === 0 ? (
                <tr>
                  <td colSpan="5" className="py-12 text-center text-slate-400 text-xs">
                    No transactions found for the selected filter criteria.
                  </td>
                </tr>
              ) : (
                transactions.map((txn) => {
                  const credit = isCredit(txn.transaction_type || txn.transactionType);
                  const txnType = txn.transaction_type || txn.transactionType;
                  return (
                    <tr key={txn.id} className="hover:bg-slate-50/60 transition-colors">
                      <td className="py-4 px-6 font-mono text-xs text-slate-500 whitespace-nowrap">
                        {txn.created_at || txn.createdAt
                          ? new Date(txn.created_at || txn.createdAt).toLocaleString()
                          : '-'}
                      </td>
                      <td className="py-4 px-6">{getTransactionBadge(txnType)}</td>
                      <td className="py-4 px-6 font-medium text-slate-700 text-xs max-w-xs truncate">
                        {txn.description || 'System Ledger Transfer'}
                        {txn.reference_id && (
                          <span className="block text-[10px] font-mono text-slate-400 truncate mt-0.5">
                            Ref: {txn.reference_id || txn.referenceId}
                          </span>
                        )}
                      </td>
                      <td className="py-4 px-6">
                        <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-semibold bg-slate-100 text-slate-700">
                          {txn.status}
                        </span>
                      </td>
                      <td
                        className={`py-4 px-6 text-right font-semibold font-mono text-sm whitespace-nowrap ${
                          credit ? 'text-emerald-600' : 'text-slate-900'
                        }`}
                      >
                        {credit ? '+' : '-'}$
                        {parseFloat(txn.amount).toLocaleString('en-US', {
                          minimumFractionDigits: 2,
                          maximumFractionDigits: 2,
                        })}
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination Footer */}
        <div className="py-3.5 px-6 bg-slate-50/60 border-t border-slate-200/80 flex items-center justify-between">
          <div className="text-xs text-slate-500">
            Page <span className="font-semibold text-slate-800">{page + 1}</span> of{' '}
            <span className="font-semibold text-slate-800">{totalPages}</span>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={() => fetchTransactions(page - 1)}
              disabled={page <= 0 || loading}
              className="p-1.5 rounded-lg border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed"
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
            <button
              onClick={() => fetchTransactions(page + 1)}
              disabled={page >= totalPages - 1 || loading}
              className="p-1.5 rounded-lg border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed"
            >
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
