import React, { useState, useEffect } from 'react';
import { NavLink } from 'react-router-dom';
import { Activity, Play, BarChart3, Clock, ExternalLink, ShieldCheck, AlertCircle, RefreshCw } from 'lucide-react';
import { getHealth } from '../../api/client';

export const Navbar = () => {
  const [health, setHealth] = useState({ status: 'CHECKING', components: {} });
  const [isChecking, setIsChecking] = useState(false);

  const checkHealth = async () => {
    setIsChecking(true);
    try {
      const data = await getHealth();
      setHealth(data);
    } catch {
      setHealth({ status: 'DOWN', components: {} });
    } finally {
      setIsChecking(false);
    }
  };

  useEffect(() => {
    checkHealth();
    const interval = setInterval(checkHealth, 10000);
    return () => clearInterval(interval);
  }, []);

  const navLinks = [
    { to: '/simulator', label: 'Simulator', icon: Play },
    { to: '/dashboard', label: 'Dashboard', icon: BarChart3 },
    { to: '/events', label: 'Event History', icon: Clock },
  ];

  const isUp = health.status === 'UP';

  return (
    <header className="sticky top-0 z-40 w-full border-b border-slate-800 bg-slate-950/80 backdrop-blur-md">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
        {/* Brand / Logo */}
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-brand-600 to-indigo-500 flex items-center justify-center shadow-lg shadow-brand-500/20 ring-1 ring-brand-400/30">
            <Activity className="w-5 h-5 text-white animate-pulse" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="font-bold text-base tracking-tight text-white">Analytics Manager</span>
              <span className="text-[10px] uppercase font-semibold tracking-wider px-1.5 py-0.5 rounded bg-brand-950 text-brand-300 border border-brand-800/60">
                Kafka • KRaft
              </span>
            </div>
            <p className="text-[11px] text-slate-400">Event-Driven Ingestion & Pre-Aggregates</p>
          </div>
        </div>

        {/* Navigation Tabs */}
        <nav className="hidden md:flex items-center gap-1 bg-slate-900/60 p-1 rounded-xl border border-slate-800">
          {navLinks.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                `flex items-center gap-2 px-3.5 py-1.5 rounded-lg text-xs font-medium transition-all duration-200 ${
                  isActive
                    ? 'bg-brand-600 text-white shadow-md shadow-brand-600/30 font-semibold'
                    : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60'
                }`
              }
            >
              <Icon className="w-4 h-4" />
              <span>{label}</span>
            </NavLink>
          ))}
        </nav>

        {/* Status & Observability Links */}
        <div className="flex items-center gap-3">
          {/* Health Pill */}
          <div
            className={`flex items-center gap-2 px-2.5 py-1 rounded-full text-xs font-mono border transition-colors cursor-pointer group relative ${
              isUp
                ? 'bg-emerald-950/40 border-emerald-500/30 text-emerald-300'
                : 'bg-rose-950/40 border-rose-500/30 text-rose-300'
            }`}
            onClick={checkHealth}
            title="Click to recheck health"
          >
            <span
              className={`w-2 h-2 rounded-full ${
                isUp ? 'bg-emerald-400 animate-ping' : 'bg-rose-400'
              }`}
            />
            <span className="font-semibold text-[11px]">
              {health.status === 'CHECKING' ? 'CONNECTING...' : isUp ? 'BACKEND UP' : 'BACKEND DOWN'}
            </span>
            <RefreshCw
              className={`w-3 h-3 opacity-0 group-hover:opacity-100 transition-opacity ${
                isChecking ? 'animate-spin' : ''
              }`}
            />

            {/* Health details tooltip on hover */}
            {health.components && Object.keys(health.components).length > 0 && (
              <div className="absolute right-0 top-full mt-2 hidden group-hover:block w-48 p-2 rounded-lg bg-slate-900 border border-slate-800 shadow-xl text-[11px] text-slate-300 z-50">
                <div className="font-semibold text-slate-200 border-b border-slate-800 pb-1 mb-1">
                  Health Components:
                </div>
                {Object.entries(health.components).map(([k, v]) => (
                  <div key={k} className="flex justify-between py-0.5">
                    <span className="capitalize">{k}:</span>
                    <span className={v.status === 'UP' ? 'text-emerald-400' : 'text-rose-400 font-bold'}>
                      {v.status}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* External Observability Links */}
          <div className="hidden sm:flex items-center gap-1.5 border-l border-slate-800 pl-3">
            <a
              href="http://localhost:3000"
              target="_blank"
              rel="noreferrer"
              className="flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-medium text-slate-400 hover:text-amber-400 hover:bg-slate-900 border border-transparent hover:border-amber-500/30 transition-all"
              title="Open Grafana Dashboard (Port 3000)"
            >
              <span>Grafana</span>
              <ExternalLink className="w-3 h-3" />
            </a>
            <a
              href="http://localhost:9090"
              target="_blank"
              rel="noreferrer"
              className="flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-medium text-slate-400 hover:text-rose-400 hover:bg-slate-900 border border-transparent hover:border-rose-500/30 transition-all"
              title="Open Prometheus (Port 9090)"
            >
              <span>Prometheus</span>
              <ExternalLink className="w-3 h-3" />
            </a>
          </div>
        </div>
      </div>

      {/* Mobile nav */}
      <div className="md:hidden border-t border-slate-800/80 bg-slate-950 px-4 py-2 flex items-center justify-around">
        {navLinks.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) =>
              `flex flex-col items-center gap-1 py-1 px-3 rounded-lg text-[10px] font-medium ${
                isActive ? 'text-brand-400 font-bold' : 'text-slate-400'
              }`
            }
          >
            <Icon className="w-4 h-4" />
            <span>{label}</span>
          </NavLink>
        ))}
      </div>
    </header>
  );
};
