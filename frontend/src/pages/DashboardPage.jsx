import React, { useState, useEffect, useCallback } from 'react';
import { 
  BarChart3, TrendingUp, Users, AlertTriangle, Layers, 
  RefreshCw, Clock, ArrowUpRight, CheckCircle2, XCircle
} from 'lucide-react';
import { 
  AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, 
  ResponsiveContainer, BarChart, Bar, Cell 
} from 'recharts';
import { 
  getAnalyticsSummary, getEventsByType, getEventsOverTime, 
  getTopPages, getEvents 
} from '../api/client';
import { useToast } from '../components/common/Toast';

const EVENT_TYPE_COLORS = {
  PAGE_VIEW: '#3b82f6',
  LOGIN: '#10b981',
  PRODUCT_VIEW: '#a855f7',
  SEARCH: '#f59e0b',
  PURCHASE: '#f43f5e',
  ERROR: '#ef4444',
};

export const DashboardPage = () => {
  const toast = useToast();

  const [summary, setSummary] = useState({
    totalEvents: 0,
    eventsLastMinute: 0,
    activeClientsLast5Min: 0,
    failedEventsCount: 0,
  });
  const [eventsByType, setEventsByType] = useState([]);
  const [eventsOverTime, setEventsOverTime] = useState([]);
  const [topPages, setTopPages] = useState([]);
  const [recentEvents, setRecentEvents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [lastUpdated, setLastUpdated] = useState(new Date());
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [refreshInterval, setRefreshInterval] = useState(5000);

  const fetchDashboardData = useCallback(async () => {
    try {
      const [sumData, byTypeData, overTimeData, topPagesData, eventsData] = await Promise.all([
        getAnalyticsSummary().catch(() => ({ totalEvents: 0, eventsLastMinute: 0, activeClientsLast5Min: 0, failedEventsCount: 0 })),
        getEventsByType().catch(() => []),
        getEventsOverTime().catch(() => []),
        getTopPages().catch(() => []),
        getEvents({ page: 0, size: 10, sortBy: 'occurredAt', sortDirection: 'desc' }).catch(() => ({ content: [] })),
      ]);

      setSummary(sumData);
      setEventsByType(byTypeData);
      
      // Format time series for chart (e.g. HH:mm)
      const formattedOverTime = (overTimeData || []).map((d) => ({
        ...d,
        time: new Date(d.bucketStart).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      }));
      setEventsOverTime(formattedOverTime);

      setTopPages(topPagesData || []);
      setRecentEvents(eventsData.content || []);
      setLastUpdated(new Date());
    } catch (err) {
      console.error('Failed to load dashboard data:', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchDashboardData();
  }, [fetchDashboardData]);

  useEffect(() => {
    if (!autoRefresh) return;
    const timer = setInterval(() => {
      fetchDashboardData();
    }, refreshInterval);
    return () => clearInterval(timer);
  }, [autoRefresh, refreshInterval, fetchDashboardData]);

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
      {/* Header and Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2">
            <BarChart3 className="w-6 h-6 text-brand-400" />
            Analytics Dashboard
          </h1>
          <p className="text-sm text-slate-400 mt-0.5">
            Real-time pre-aggregated metrics computed asynchronously from the Kafka consumer stream.
          </p>
        </div>

        {/* Controls */}
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2 bg-slate-900 border border-slate-800 rounded-lg px-3 py-1.5 text-xs text-slate-300">
            <span className="text-[11px] text-slate-400">Updated:</span>
            <span className="font-mono text-slate-200">
              {lastUpdated.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
            </span>
          </div>

          <button
            onClick={() => setAutoRefresh(!autoRefresh)}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium border transition-colors ${
              autoRefresh 
                ? 'bg-emerald-950/40 border-emerald-500/30 text-emerald-300' 
                : 'bg-slate-900 border-slate-800 text-slate-400'
            }`}
          >
            Auto-refresh: {autoRefresh ? 'ON' : 'OFF'}
          </button>

          <button
            onClick={fetchDashboardData}
            className="p-1.5 rounded-lg bg-slate-900 border border-slate-800 hover:border-slate-700 text-slate-300 hover:text-white transition-colors"
            title="Refresh now"
          >
            <RefreshCw className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Row 1: KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Total Events */}
        <div className="glass-panel p-5 rounded-2xl relative overflow-hidden group">
          <div className="flex justify-between items-start">
            <div>
              <p className="text-xs font-medium text-slate-400 uppercase tracking-wider">Total Processed Events</p>
              <h3 className="text-2xl font-bold text-white font-mono mt-1">
                {Number(summary.totalEvents || 0).toLocaleString()}
              </h3>
            </div>
            <div className="p-2.5 rounded-xl bg-blue-500/10 border border-blue-500/20 text-blue-400">
              <Layers className="w-5 h-5" />
            </div>
          </div>
          <p className="text-[11px] text-slate-500 mt-3 flex items-center gap-1">
            <span className="text-emerald-400 font-medium">Idempotent</span> • Verified via MySQL unique index
          </p>
        </div>

        {/* Velocity / Last Minute */}
        <div className="glass-panel p-5 rounded-2xl relative overflow-hidden group">
          <div className="flex justify-between items-start">
            <div>
              <p className="text-xs font-medium text-slate-400 uppercase tracking-wider">Velocity (Last Minute)</p>
              <h3 className="text-2xl font-bold text-white font-mono mt-1">
                {Number(summary.eventsLastMinute || 0).toLocaleString()} <span className="text-xs font-normal text-slate-400">events</span>
              </h3>
            </div>
            <div className="p-2.5 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-400">
              <TrendingUp className="w-5 h-5" />
            </div>
          </div>
          <p className="text-[11px] text-slate-500 mt-3">
            Pre-aggregated in <code className="text-slate-400 font-mono">event_agg_minute</code>
          </p>
        </div>

        {/* Active Clients */}
        <div className="glass-panel p-5 rounded-2xl relative overflow-hidden group">
          <div className="flex justify-between items-start">
            <div>
              <p className="text-xs font-medium text-slate-400 uppercase tracking-wider">Active Clients (5 min)</p>
              <h3 className="text-2xl font-bold text-white font-mono mt-1">
                {Number(summary.activeClientsLast5Min || 0).toLocaleString()}
              </h3>
            </div>
            <div className="p-2.5 rounded-xl bg-purple-500/10 border border-purple-500/20 text-purple-400">
              <Users className="w-5 h-5" />
            </div>
          </div>
          <p className="text-[11px] text-slate-500 mt-3">
            Tracked in <code className="text-slate-400 font-mono">client_stats</code>
          </p>
        </div>

        {/* Failed Events / DLQ */}
        <div className="glass-panel p-5 rounded-2xl relative overflow-hidden group">
          <div className="flex justify-between items-start">
            <div>
              <p className="text-xs font-medium text-slate-400 uppercase tracking-wider">Dead-Letter Queue</p>
              <h3 className={`text-2xl font-bold font-mono mt-1 ${summary.failedEventsCount > 0 ? 'text-rose-400' : 'text-slate-200'}`}>
                {Number(summary.failedEventsCount || 0).toLocaleString()}
              </h3>
            </div>
            <div className={`p-2.5 rounded-xl border ${summary.failedEventsCount > 0 ? 'bg-rose-500/10 border-rose-500/20 text-rose-400' : 'bg-slate-800 border-slate-700 text-slate-400'}`}>
              <AlertTriangle className="w-5 h-5" />
            </div>
          </div>
          <p className="text-[11px] text-slate-500 mt-3">
            Stored in <code className="text-slate-400 font-mono">failed_events</code>
          </p>
        </div>
      </div>

      {/* Row 2: Charts (Throughput & By Type) */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Events Over Time Chart (7 cols) */}
        <div className="lg:col-span-7 glass-panel rounded-2xl p-6">
          <div className="flex items-center justify-between mb-6">
            <div>
              <h2 className="text-base font-semibold text-slate-200">Throughput Over Time</h2>
              <p className="text-xs text-slate-400">Minute-by-minute event volume (Last 30 Minutes)</p>
            </div>
            <span className="text-xs font-mono text-brand-400 bg-brand-950 px-2 py-0.5 rounded border border-brand-800/60">
              event_agg_minute
            </span>
          </div>

          <div className="h-64 w-full">
            {eventsOverTime.length > 0 ? (
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={eventsOverTime} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                  <defs>
                    <linearGradient id="colorCount" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#6366f1" stopOpacity={0.4}/>
                      <stop offset="95%" stopColor="#6366f1" stopOpacity={0}/>
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                  <XAxis dataKey="time" stroke="#64748b" fontSize={11} />
                  <YAxis stroke="#64748b" fontSize={11} />
                  <Tooltip 
                    contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', borderRadius: '0.75rem', fontSize: '12px' }}
                    itemStyle={{ color: '#e2e8f0' }}
                  />
                  <Area type="monotone" dataKey="count" stroke="#818cf8" strokeWidth={2} fillOpacity={1} fill="url(#colorCount)" />
                </AreaChart>
              </ResponsiveContainer>
            ) : (
              <div className="h-full flex items-center justify-center text-xs text-slate-500">
                No aggregation data yet. Use the Simulator to generate events!
              </div>
            )}
          </div>
        </div>

        {/* Events By Type Bar Chart (5 cols) */}
        <div className="lg:col-span-5 glass-panel rounded-2xl p-6">
          <div className="flex items-center justify-between mb-6">
            <div>
              <h2 className="text-base font-semibold text-slate-200">Events by Type</h2>
              <p className="text-xs text-slate-400">Distribution across business domains</p>
            </div>
          </div>

          <div className="h-64 w-full">
            {eventsByType.length > 0 ? (
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={eventsByType} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                  <XAxis dataKey="eventType" stroke="#64748b" fontSize={10} tickFormatter={(v) => v.slice(0, 7)} />
                  <YAxis stroke="#64748b" fontSize={11} />
                  <Tooltip 
                    contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', borderRadius: '0.75rem', fontSize: '12px' }}
                    itemStyle={{ color: '#e2e8f0' }}
                  />
                  <Bar dataKey="count" radius={[4, 4, 0, 0]}>
                    {eventsByType.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={EVENT_TYPE_COLORS[entry.eventType] || '#6366f1'} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            ) : (
              <div className="h-full flex items-center justify-center text-xs text-slate-500">
                No event records found.
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Row 3: Tables (Top Pages & Recent Events) */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Top 5 Pages (4 cols) */}
        <div className="lg:col-span-4 glass-panel rounded-2xl p-6">
          <h2 className="text-base font-semibold text-slate-200 mb-1">Top Visited Pages</h2>
          <p className="text-xs text-slate-400 mb-4">Upserted in real time via <code className="font-mono text-slate-300">page_stats</code></p>

          <div className="space-y-3">
            {topPages.length > 0 ? (
              topPages.slice(0, 5).map((page, idx) => (
                <div key={page.page} className="flex items-center justify-between p-2.5 rounded-xl bg-slate-950/60 border border-slate-800">
                  <div className="flex items-center gap-2.5 min-w-0">
                    <span className="w-5 h-5 rounded-full bg-slate-800 flex items-center justify-center text-[10px] font-bold text-slate-400">
                      {idx + 1}
                    </span>
                    <span className="text-xs font-mono text-slate-300 truncate">{page.page}</span>
                  </div>
                  <span className="text-xs font-mono font-bold text-brand-400 ml-2">
                    {Number(page.views).toLocaleString()} views
                  </span>
                </div>
              ))
            ) : (
              <div className="py-8 text-center text-xs text-slate-500">No page view data recorded.</div>
            )}
          </div>
        </div>

        {/* Recent Events Table (8 cols) */}
        <div className="lg:col-span-8 glass-panel rounded-2xl p-6">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h2 className="text-base font-semibold text-slate-200">Recent Ingested Events</h2>
              <p className="text-xs text-slate-400">Latest 10 committed events</p>
            </div>
            <a href="#/events" className="text-xs text-brand-400 hover:text-brand-300 font-medium flex items-center gap-1">
              View all history <ArrowUpRight className="w-3.5 h-3.5" />
            </a>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="border-b border-slate-800 text-slate-400 uppercase tracking-wider font-semibold">
                  <th className="pb-3 font-medium">Event ID</th>
                  <th className="pb-3 font-medium">Type</th>
                  <th className="pb-3 font-medium">Client</th>
                  <th className="pb-3 font-medium">Occurred</th>
                  <th className="pb-3 font-medium text-right">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60 font-mono text-slate-300">
                {recentEvents.length > 0 ? (
                  recentEvents.map((ev) => (
                    <tr key={ev.eventId} className="hover:bg-slate-800/30 transition-colors">
                      <td className="py-2.5 pr-2 font-mono text-slate-400 truncate max-w-[120px]" title={ev.eventId}>
                        {ev.eventId.substring(0, 8)}...
                      </td>
                      <td className="py-2.5 pr-2">
                        <span className={`px-2 py-0.5 rounded text-[10px] font-semibold ${
                          EVENT_TYPE_COLORS[ev.eventType] ? 'bg-slate-800' : 'bg-slate-800'
                        }`} style={{ color: EVENT_TYPE_COLORS[ev.eventType] || '#94a3b8' }}>
                          {ev.eventType}
                        </span>
                      </td>
                      <td className="py-2.5 pr-2 truncate max-w-[120px] text-slate-300">
                        {ev.clientId}
                      </td>
                      <td className="py-2.5 pr-2 text-slate-400 text-[11px]">
                        {new Date(ev.occurredAt).toLocaleTimeString()}
                      </td>
                      <td className="py-2.5 text-right">
                        <span className="inline-flex items-center gap-1 text-[10px] font-bold px-2 py-0.5 rounded-full bg-emerald-950/60 text-emerald-400 border border-emerald-500/30">
                          <CheckCircle2 className="w-3 h-3" />
                          PROCESSED
                        </span>
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={5} className="py-8 text-center text-slate-500 font-sans">
                      No events currently in database.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
};
