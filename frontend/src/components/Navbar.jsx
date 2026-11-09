import React from 'react';
import { Bell, Shield } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

export const Navbar = () => {
  const { user } = useAuth();

  return (
    <header className="h-16 bg-white border-b border-slate-200/80 px-8 flex items-center justify-between sticky top-0 z-10 shadow-sm/50">
      <div className="flex items-center gap-2">
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-emerald-50 text-emerald-700 border border-emerald-200/60">
          <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
          Core Banking Network Active
        </span>
      </div>

      <div className="flex items-center gap-4">
        <div className="flex items-center gap-2 text-xs text-slate-500 bg-slate-100/80 px-3 py-1.5 rounded-md border border-slate-200">
          <Shield className="w-3.5 h-3.5 text-navy-800" />
          <span>Stateless 256-bit Session</span>
        </div>

        <div className="h-4 w-px bg-slate-200"></div>

        <div className="text-right">
          <p className="text-xs font-semibold text-slate-800">{user?.email}</p>
          <p className="text-[10px] text-slate-400">Authenticated Client</p>
        </div>
      </div>
    </header>
  );
};
