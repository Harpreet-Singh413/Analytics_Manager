import React, { useState, useEffect, useCallback } from 'react';
import { 
  Clock, Search, Filter, RefreshCw, ChevronLeft, ChevronRight, 
  ExternalLink, CheckCircle2, AlertCircle, Copy, Check, X, Eye 
} from 'lucide-react';
import { getEvents, getEventById } from '../api/client';
import { useToast } from '../components/common/Toast';

const EVENT_TYPES = ['', 'PAGE_VIEW', 'LOGIN', 'PRODUCT_VIEW', 'SEARCH', 'PURCHASE', 'ERROR'];

const EVENT_TYPE_COLORS = {
  PAGE_VIEW: 'bg-blue-500/10 text-blue-400 border-blue-500/20',
  LOGIN: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
  PRODUCT_VIEW: 'bg-purple-500/10 text-purple-400 border-purple-500/20',
  SEARCH: 'bg-amber-500/10 text-amber-400 border-amber-500/20',
  PURCHASE: 'bg-rose-500/10 text-rose-400 border-rose-500/20',
  ERROR: 'bg-red-500/10 text-red-400 border-red-500/20',
};

export const EventHistoryPage = () => {
  const toast = useToast();

  // Query / Filter state
  const [eventType, setEventType] = useState('');
  const [clientId, setClientId] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(15);
  const [sortBy, setSortBy] = useState('occurredAt');
  const [sortDirection, setSortDirection] = useState('desc');

  // Data state
  const [events, setEvents] = useState([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);

  // Detail Modal State
  const [selectedEventId, setSelectedEventId] = useState(null);
  const [eventDetail, setEventDetail] = useState(null);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [copied, setCopied] = useState(false);

  const fetchEvents = useCallback(async () => {
    setLoading(true);
    try {
      const params = {
        page,
        size,
        sortBy,
        sortDirection,
      };

      if (eventType) params.eventType = eventType;
      if (clientId.trim()) params.clientId = clientId.trim();
      if (from) params.from = new Date(from).toISOString();
      if (to) params.to = new Date(to).toISOString();

      const res = await getEvents(params);
      setEvents(res.content || []);
      setTotalPages(res.totalPages || 0);
      setTotalElements(res.totalElements || 0);
    } catch (err) {
      toast.error('Failed to load events', err.message);
    } finally {
      setLoading(false);
    }
  }, [page, size, sortBy, sortDirection, eventType, clientId, from, to]);

  useEffect(() => {
    fetchEvents();
  }, [fetchEvents]);

  // Handle Event Row Click (open modal)
  const handleOpenDetail = async (id) => {
    setSelectedEventId(id);
    setLoadingDetail(true);
    try {
      const data = await getEventById(id);
      setEventDetail(data);
    } catch (err) {
      toast.error('Failed to load event details', err.message);
    } finally {
      setLoadingDetail(false);
    }
  };

  const handleCloseDetail = () => {
    setSelectedEventId(null);
    setEventDetail(null);
  };

  const handleCopyJson = () => {
    if (eventDetail) {
      navigator.clipboard.writeText(JSON.stringify(eventDetail, null, 2));
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const handleResetFilters = () => {
    setEventType('');
    setClientId('');
    setFrom('');
    setTo('');
    setPage(0);
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2">
            <Clock className="w-6 h-6 text-brand-400" />
            Event History
          </h1>
          <p className="text-sm text-slate-400 mt-0.5">
            Query immutable events persisted to MySQL by the Kafka consumer group.
          </p>
        </div>

        <button
          onClick={fetchEvents}
          className="self-start sm:self-auto flex items-center gap-2 px-3 py-1.5 rounded-lg bg-slate-900 border border-slate-800 hover:border-slate-700 text-xs text-slate-300 hover:text-white transition-colors"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
          <span>Refresh</span>
        </button>
      </div>

      {/* Filters Bar */}
      <div className="glass-panel p-4 rounded-2xl space-y-3">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
          {/* Client ID Filter */}
          <div className="relative">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
            <input
              type="text"
              value={clientId}
              onChange={(e) => {
                setClientId(e.target.value);
                setPage(0);
              }}
              placeholder="Search Client ID..."
              className="w-full bg-slate-950 border border-slate-800 rounded-lg pl-9 pr-3 py-1.5 text-xs text-slate-200 focus:outline-none focus:ring-1 focus:ring-brand-500"
            />
          </div>

          {/* Event Type Filter */}
          <div>
            <select
              value={eventType}
              onChange={(e) => {
                setEventType(e.target.value);
                setPage(0);
              }}
              className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-1.5 text-xs text-slate-200 focus:outline-none focus:ring-1 focus:ring-brand-500"
            >
              <option value="">All Event Types</option>
              {EVENT_TYPES.filter(Boolean).map((t) => (
                <option key={t} value={t}>{t}</option>
              ))}
            </select>
          </div>

          {/* From Datetime */}
          <div>
            <input
              type="datetime-local"
              value={from}
              onChange={(e) => {
                setFrom(e.target.value);
                setPage(0);
              }}
              title="From timestamp"
              className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-1.5 text-xs text-slate-200 font-mono focus:outline-none focus:ring-1 focus:ring-brand-500"
            />
          </div>

          {/* To Datetime */}
          <div>
            <input
              type="datetime-local"
              value={to}
              onChange={(e) => {
                setTo(e.target.value);
                setPage(0);
              }}
              title="To timestamp"
              className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-1.5 text-xs text-slate-200 font-mono focus:outline-none focus:ring-1 focus:ring-brand-500"
            />
          </div>
        </div>

        {(eventType || clientId || from || to) && (
          <div className="flex justify-end pt-1">
            <button
              onClick={handleResetFilters}
              className="text-xs text-brand-400 hover:text-brand-300 underline transition-colors"
            >
              Reset Filters
            </button>
          </div>
        )}
      </div>

      {/* Events Table */}
      <div className="glass-panel rounded-2xl overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead>
              <tr className="border-b border-slate-800 bg-slate-900/40 text-slate-400 uppercase tracking-wider font-semibold">
                <th className="py-3 px-4 font-medium">Event ID</th>
                <th className="py-3 px-4 font-medium">Type</th>
                <th className="py-3 px-4 font-medium">Client ID</th>
                <th className="py-3 px-4 font-medium">Page / Product</th>
                <th className="py-3 px-4 font-medium">Occurred At (UTC)</th>
                <th className="py-3 px-4 font-medium">Partition / Offset</th>
                <th className="py-3 px-4 font-medium text-right">Status</th>
                <th className="py-3 px-4 font-medium text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60 font-mono text-slate-300">
              {loading ? (
                <tr>
                  <td colSpan={8} className="py-12 text-center text-slate-500 font-sans">
                    <RefreshCw className="w-5 h-5 animate-spin mx-auto mb-2 text-brand-400" />
                    Querying events from MySQL...
                  </td>
                </tr>
              ) : events.length > 0 ? (
                events.map((ev) => (
                  <tr 
                    key={ev.eventId} 
                    className="hover:bg-slate-800/40 transition-colors cursor-pointer group"
                    onClick={() => handleOpenDetail(ev.eventId)}
                  >
                    <td className="py-3 px-4 font-mono text-brand-300 truncate max-w-[140px]" title={ev.eventId}>
                      {ev.eventId}
                    </td>
                    <td className="py-3 px-4">
                      <span className={`px-2 py-0.5 rounded text-[10px] font-semibold border ${
                        EVENT_TYPE_COLORS[ev.eventType] || 'border-slate-700 bg-slate-800 text-slate-300'
                      }`}>
                        {ev.eventType}
                      </span>
                    </td>
                    <td className="py-3 px-4 truncate max-w-[140px] text-slate-200">
                      {ev.clientId}
                    </td>
                    <td className="py-3 px-4 text-slate-400 truncate max-w-[140px]">
                      {ev.page || ev.productId || '—'}
                    </td>
                    <td className="py-3 px-4 text-slate-400 text-[11px]">
                      {new Date(ev.occurredAt).toLocaleString()}
                    </td>
                    <td className="py-3 px-4 text-slate-500 text-[11px]">
                      P{ev.partition} : #{ev.offset}
                    </td>
                    <td className="py-3 px-4 text-right">
                      <span className="inline-flex items-center gap-1 text-[10px] font-bold px-2 py-0.5 rounded-full bg-emerald-950/60 text-emerald-400 border border-emerald-500/30">
                        <CheckCircle2 className="w-3 h-3" />
                        PROCESSED
                      </span>
                    </td>
                    <td className="py-3 px-4 text-right">
                      <button 
                        className="text-slate-400 group-hover:text-brand-300 transition-colors p-1"
                        title="View payload"
                      >
                        <Eye className="w-4 h-4" />
                      </button>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={8} className="py-12 text-center text-slate-500 font-sans">
                    No matching events found in database.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination Footer */}
        <div className="p-4 border-t border-slate-800 flex items-center justify-between text-xs text-slate-400">
          <div>
            Showing <span className="font-mono text-slate-200">{events.length}</span> of{' '}
            <span className="font-mono text-slate-200">{totalElements}</span> total events
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="p-1.5 rounded-lg border border-slate-800 bg-slate-900 disabled:opacity-40 text-slate-300 hover:text-white transition-colors cursor-pointer"
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
            <span className="font-mono text-slate-200">
              Page {page + 1} of {Math.max(1, totalPages)}
            </span>
            <button
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={page >= totalPages - 1}
              className="p-1.5 rounded-lg border border-slate-800 bg-slate-900 disabled:opacity-40 text-slate-300 hover:text-white transition-colors cursor-pointer"
            >
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        </div>
      </div>

      {/* Event Detail Modal */}
      {selectedEventId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-in fade-in duration-200">
          <div className="glass-panel w-full max-w-2xl rounded-2xl border border-slate-700 shadow-2xl p-6 space-y-4 max-h-[90vh] flex flex-col">
            {/* Modal Header */}
            <div className="flex items-center justify-between border-b border-slate-800 pb-3">
              <div>
                <h3 className="text-base font-bold text-white flex items-center gap-2">
                  <span>Event Detail</span>
                  <span className="text-xs font-mono font-normal text-brand-300 bg-brand-950 px-2 py-0.5 rounded border border-brand-800/60">
                    GET /api/events/{selectedEventId.slice(0, 8)}...
                  </span>
                </h3>
              </div>
              <button
                onClick={handleCloseDetail}
                className="text-slate-400 hover:text-white p-1 rounded-lg hover:bg-slate-800 transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Modal Content */}
            <div className="flex-1 overflow-y-auto space-y-4 pr-1">
              {loadingDetail ? (
                <div className="py-12 text-center text-xs text-slate-400">
                  <RefreshCw className="w-5 h-5 animate-spin mx-auto mb-2 text-brand-400" />
                  Fetching event envelope...
                </div>
              ) : eventDetail ? (
                <>
                  {/* Summary Grid */}
                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-xs">
                    <div className="bg-slate-950 p-2.5 rounded-xl border border-slate-800">
                      <span className="text-[10px] text-slate-500 uppercase">Status</span>
                      <div className="font-bold text-emerald-400 mt-0.5">{eventDetail.status || 'PROCESSED'}</div>
                    </div>
                    <div className="bg-slate-950 p-2.5 rounded-xl border border-slate-800">
                      <span className="text-[10px] text-slate-500 uppercase">Type</span>
                      <div className="font-bold text-brand-300 mt-0.5">{eventDetail.eventType}</div>
                    </div>
                    <div className="bg-slate-950 p-2.5 rounded-xl border border-slate-800">
                      <span className="text-[10px] text-slate-500 uppercase">Kafka Partition</span>
                      <div className="font-bold text-slate-300 mt-0.5">Partition {eventDetail.partition}</div>
                    </div>
                    <div className="bg-slate-950 p-2.5 rounded-xl border border-slate-800">
                      <span className="text-[10px] text-slate-500 uppercase">Kafka Offset</span>
                      <div className="font-bold text-slate-300 mt-0.5">#{eventDetail.offset}</div>
                    </div>
                  </div>

                  {/* Lifecycle Timestamps */}
                  <div className="bg-slate-950 p-3 rounded-xl border border-slate-800 space-y-1.5 text-xs font-mono">
                    <div className="text-[11px] font-sans font-semibold text-slate-400 uppercase tracking-wider mb-1">
                      Event Lifecycle Latency
                    </div>
                    <div className="flex justify-between text-slate-300">
                      <span>Occurred At (Client):</span>
                      <span className="text-slate-400">{eventDetail.occurredAt}</span>
                    </div>
                    <div className="flex justify-between text-slate-300">
                      <span>Received At (REST API):</span>
                      <span className="text-slate-400">{eventDetail.receivedAt}</span>
                    </div>
                    <div className="flex justify-between text-slate-300">
                      <span>Processed At (Consumer):</span>
                      <span className="text-slate-400">{eventDetail.processedAt}</span>
                    </div>
                    {eventDetail.receivedAt && eventDetail.processedAt && (
                      <div className="flex justify-between border-t border-slate-800/80 pt-1 text-emerald-400 font-bold">
                        <span>End-to-End Latency:</span>
                        <span>
                          {Math.max(0, new Date(eventDetail.processedAt) - new Date(eventDetail.receivedAt))} ms
                        </span>
                      </div>
                    )}
                  </div>

                  {/* Full JSON Envelope */}
                  <div>
                    <div className="flex items-center justify-between mb-1.5">
                      <span className="text-xs font-semibold text-slate-300 uppercase tracking-wider">
                        Raw JSON Envelope
                      </span>
                      <button
                        onClick={handleCopyJson}
                        className="flex items-center gap-1 text-[11px] text-brand-400 hover:text-brand-300 transition-colors"
                      >
                        {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                        <span>{copied ? 'Copied' : 'Copy JSON'}</span>
                      </button>
                    </div>
                    <pre className="p-3 bg-slate-950 border border-slate-800 rounded-xl text-xs font-mono text-slate-300 overflow-x-auto max-h-56">
                      {JSON.stringify(eventDetail, null, 2)}
                    </pre>
                  </div>
                </>
              ) : (
                <div className="py-8 text-center text-xs text-rose-400">
                  Event record not found or could not be loaded.
                </div>
              )}
            </div>

            {/* Modal Footer */}
            <div className="border-t border-slate-800 pt-3 flex justify-end">
              <button
                onClick={handleCloseDetail}
                className="px-4 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-xs font-medium text-slate-200 transition-colors"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
