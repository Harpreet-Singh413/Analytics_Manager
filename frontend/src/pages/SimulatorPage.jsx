import React, { useState, useRef } from 'react';
import { 
  Send, Zap, RefreshCw, AlertOctagon, Sparkles, CheckCircle2, 
  XCircle, Clock, Server, Layers, HelpCircle, ArrowRight
} from 'lucide-react';
import { ingestEvent, ingestBatchEvents } from '../api/client';
import { useToast } from '../components/common/Toast';

const EVENT_TYPES = [
  { value: 'PAGE_VIEW', label: 'PAGE_VIEW', color: 'bg-blue-500/10 text-blue-400 border-blue-500/20' },
  { value: 'LOGIN', label: 'LOGIN', color: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' },
  { value: 'PRODUCT_VIEW', label: 'PRODUCT_VIEW', color: 'bg-purple-500/10 text-purple-400 border-purple-500/20' },
  { value: 'SEARCH', label: 'SEARCH', color: 'bg-amber-500/10 text-amber-400 border-amber-500/20' },
  { value: 'PURCHASE', label: 'PURCHASE', color: 'bg-rose-500/10 text-rose-400 border-rose-500/20' },
  { value: 'ERROR', label: 'ERROR', color: 'bg-red-500/10 text-red-400 border-red-500/20' },
];

const SOURCES = ['WEB', 'MOBILE', 'BACKEND'];

const RANDOM_CLIENTS = [
  'client-web-us-east', 'client-ios-premium', 'client-android-eu',
  'client-partner-api', 'client-checkout-svc', 'client-mobile-apac'
];

const RANDOM_PAGES = [
  '/home', '/pricing', '/products/laptops', '/checkout/step-1',
  '/dashboard/analytics', '/settings/profile', '/docs/getting-started'
];

export const SimulatorPage = () => {
  const toast = useToast();

  // Form State
  const [clientId, setClientId] = useState('client-web-us-east');
  const [eventType, setEventType] = useState('PAGE_VIEW');
  const [source, setSource] = useState('WEB');
  const [page, setPage] = useState('/products/laptops');
  const [productId, setProductId] = useState('prod-9872');
  const [occurredAt, setOccurredAt] = useState(new Date().toISOString().slice(0, 19));
  const [metadataJson, setMetadataJson] = useState('{\n  "category": "electronics",\n  "price": 1299.99\n}');
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Last Sent Event (for Duplicate Test)
  const [lastSentEvent, setLastSentEvent] = useState(null);

  // Burst Test State
  const [burstCount, setBurstCount] = useState(50);
  const [burstDelayMs, setBurstDelayMs] = useState(10);
  const [isBursting, setIsBursting] = useState(false);
  const burstAbortRef = useRef(false);
  const [burstStats, setBurstStats] = useState({
    sent: 0,
    accepted: 0,
    rejected: 0,
    failed: 0,
    latencyMs: 0,
  });

  // Helper to generate a random client
  const randomizeClient = () => {
    const random = RANDOM_CLIENTS[Math.floor(Math.random() * RANDOM_CLIENTS.length)];
    const unique = `${random}-${Math.floor(Math.random() * 900 + 100)}`;
    setClientId(unique);
  };

  // Helper to construct payload
  const buildPayload = (customEventId = null) => {
    let metadata = {};
    try {
      if (metadataJson.trim()) {
        metadata = JSON.parse(metadataJson);
      }
    } catch {
      metadata = { raw: metadataJson };
    }

    const payload = {
      eventType,
      clientId: clientId.trim(),
      source,
      occurredAt: occurredAt ? new Date(occurredAt).toISOString() : new Date().toISOString(),
      metadata,
    };

    if (customEventId) {
      payload.eventId = customEventId;
    }

    if (eventType === 'PAGE_VIEW' || eventType === 'SEARCH' || page.trim()) {
      payload.page = page.trim();
    }
    if (eventType === 'PRODUCT_VIEW' || eventType === 'PURCHASE' || productId.trim()) {
      payload.productId = productId.trim();
    }

    return payload;
  };

  // 1. Single Event Submit
  const handleSendSingle = async (e) => {
    if (e) e.preventDefault();
    setIsSubmitting(true);

    try {
      const payload = buildPayload();
      const startTime = performance.now();
      const res = await ingestEvent(payload);
      const elapsed = Math.round(performance.now() - startTime);

      setLastSentEvent({
        ...payload,
        eventId: res.data.eventId,
      });

      toast.success(
        'Event Accepted by Broker',
        `Acknowledged in ${elapsed}ms via Kafka producer`,
        { eventId: res.data.eventId, status: res.status }
      );
    } catch (err) {
      const problem = err.problemDetail;
      const status = err.response?.status || 500;
      toast.error(
        problem?.title || 'Ingestion Failed',
        problem?.detail || err.message,
        { status }
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  // 2. Resend Exact Same Event (Idempotency & Duplicate Test)
  const handleResendDuplicate = async () => {
    if (!lastSentEvent) return;
    setIsSubmitting(true);

    try {
      const startTime = performance.now();
      const res = await ingestEvent(lastSentEvent);
      const elapsed = Math.round(performance.now() - startTime);

      toast.warning(
        'Duplicate Event Sent (Idempotency Demo)',
        `Published with same eventId. Consumer will execute INSERT IGNORE (0 rows affected) and skip aggregates. (${elapsed}ms)`,
        { eventId: lastSentEvent.eventId, status: res.status }
      );
    } catch (err) {
      toast.error('Resend Failed', err.message);
    } finally {
      setIsSubmitting(false);
    }
  };

  // 3. Send Poison Pill / Malformed Event (DLQ Test)
  const handleSendPoisonPill = async () => {
    setIsSubmitting(true);
    try {
      // Missing mandatory fields or invalid UUID to trigger 400/422
      const invalidPayload = {
        eventId: 'invalid-non-uuid-string-12345',
        eventType: 'UNKNOWN_TYPE_123',
        clientId: '', // Empty client ID violates @NotBlank
        occurredAt: 'not-a-timestamp',
      };

      await ingestEvent(invalidPayload);
    } catch (err) {
      const status = err.response?.status || 400;
      toast.info(
        'Validation Rejected Malformed Event',
        `Correctly rejected with HTTP ${status} ProblemDetail before reaching Kafka`,
        { status }
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  // 4. Send Burst (Load Generation to observe Kafka Lag)
  const handleStartBurst = async () => {
    setIsBursting(true);
    burstAbortRef.current = false;
    setBurstStats({ sent: 0, accepted: 0, rejected: 0, failed: 0, latencyMs: 0 });

    const total = burstCount;
    const types = ['PAGE_VIEW', 'PRODUCT_VIEW', 'LOGIN', 'PURCHASE', 'SEARCH', 'ERROR'];

    for (let i = 0; i < total; i++) {
      if (burstAbortRef.current) break;

      const randomType = types[Math.floor(Math.random() * types.length)];
      const randomClient = RANDOM_CLIENTS[Math.floor(Math.random() * RANDOM_CLIENTS.length)];
      const randomPage = RANDOM_PAGES[Math.floor(Math.random() * RANDOM_PAGES.length)];

      const item = {
        eventType: randomType,
        clientId: `${randomClient}-${Math.floor(Math.random() * 50 + 1)}`,
        source: SOURCES[Math.floor(Math.random() * SOURCES.length)],
        occurredAt: new Date().toISOString(),
        page: randomPage,
        productId: randomType === 'PRODUCT_VIEW' || randomType === 'PURCHASE' ? `prod-${Math.floor(Math.random() * 500)}` : undefined,
        metadata: {
          burstIndex: i + 1,
          batchTotal: total,
        },
      };

      const start = performance.now();
      try {
        const res = await ingestEvent(item);
        const elapsed = Math.round(performance.now() - start);

        if (i === 0) {
          setLastSentEvent({ ...item, eventId: res.data.eventId });
        }

        setBurstStats((prev) => ({
          ...prev,
          sent: prev.sent + 1,
          accepted: prev.accepted + 1,
          latencyMs: elapsed,
        }));
      } catch (err) {
        const status = err.response?.status || 500;
        setBurstStats((prev) => ({
          ...prev,
          sent: prev.sent + 1,
          rejected: status === 400 || status === 422 ? prev.rejected + 1 : prev.rejected,
          failed: status >= 500 ? prev.failed + 1 : prev.failed,
        }));
      }

      if (burstDelayMs > 0 && i < total - 1) {
        await new Promise((resolve) => setTimeout(resolve, burstDelayMs));
      }
    }

    setIsBursting(false);
    toast.success(
      'Burst Completed',
      `Sent ${total} events into the ingestion pipeline. Switch to Dashboard or Grafana to observe consumer drain!`
    );
  };

  const handleStopBurst = () => {
    burstAbortRef.current = true;
    setIsBursting(false);
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2">
          <Zap className="w-6 h-6 text-brand-400" />
          Event Simulator
        </h1>
        <p className="text-sm text-slate-400 mt-1">
          Produce high-throughput events, simulate burst traffic, and test Kafka consumer idempotency and DLQ recovery.
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
        {/* Left Column: Single Event Ingestion Form (7 cols) */}
        <div className="lg:col-span-7 glass-panel rounded-2xl p-6 space-y-6">
          <div className="flex items-center justify-between border-b border-slate-800 pb-4">
            <div>
              <h2 className="text-base font-semibold text-slate-200">Single Event Generator</h2>
              <p className="text-xs text-slate-400">Posts to <code className="text-brand-300 font-mono">POST /api/events</code></p>
            </div>
            <span className="text-[11px] font-mono bg-brand-950 text-brand-300 px-2 py-0.5 rounded border border-brand-800/60">
              acks=all • sync
            </span>
          </div>

          <form onSubmit={handleSendSingle} className="space-y-4">
            {/* Event Type Grid */}
            <div>
              <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">
                Event Type
              </label>
              <div className="grid grid-cols-3 gap-2">
                {EVENT_TYPES.map((t) => (
                  <button
                    key={t.value}
                    type="button"
                    onClick={() => setEventType(t.value)}
                    className={`py-2 px-3 rounded-lg text-xs font-mono font-medium border text-center transition-all ${
                      eventType === t.value
                        ? 'border-brand-500 bg-brand-600/20 text-brand-200 shadow-md shadow-brand-500/10'
                        : 'border-slate-800 bg-slate-900/60 text-slate-400 hover:border-slate-700 hover:text-slate-200'
                    }`}
                  >
                    {t.label}
                  </button>
                ))}
              </div>
            </div>

            {/* Client ID with Randomize */}
            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label className="text-xs font-semibold text-slate-300 uppercase tracking-wider">
                  Client ID <span className="text-rose-400">*</span>
                </label>
                <button
                  type="button"
                  onClick={randomizeClient}
                  className="flex items-center gap-1 text-[11px] text-brand-400 hover:text-brand-300 font-medium transition-colors"
                >
                  <Sparkles className="w-3 h-3" />
                  Randomize
                </button>
              </div>
              <input
                type="text"
                value={clientId}
                onChange={(e) => setClientId(e.target.value)}
                required
                className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-200 font-mono focus:outline-none focus:ring-1 focus:ring-brand-500 focus:border-brand-500"
                placeholder="e.g. client-mobile-01"
              />
              <p className="text-[11px] text-slate-500 mt-1">Acts as the Kafka partition key for strict per-client ordering.</p>
            </div>

            {/* Source & OccurredAt */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-1.5">
                  Source
                </label>
                <select
                  value={source}
                  onChange={(e) => setSource(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none focus:ring-1 focus:ring-brand-500"
                >
                  {SOURCES.map((s) => (
                    <option key={s} value={s}>{s}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-1.5">
                  Occurred At (UTC)
                </label>
                <input
                  type="datetime-local"
                  step="1"
                  value={occurredAt}
                  onChange={(e) => setOccurredAt(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-200 font-mono focus:outline-none focus:ring-1 focus:ring-brand-500"
                />
              </div>
            </div>

            {/* Dynamic Context Fields */}
            {(eventType === 'PAGE_VIEW' || eventType === 'SEARCH') && (
              <div className="animate-in fade-in duration-200">
                <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-1.5">
                  Page Path {eventType === 'PAGE_VIEW' && <span className="text-rose-400">*</span>}
                </label>
                <input
                  type="text"
                  value={page}
                  onChange={(e) => setPage(e.target.value)}
                  required={eventType === 'PAGE_VIEW'}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-200 font-mono focus:outline-none focus:ring-1 focus:ring-brand-500"
                  placeholder="/pricing"
                />
              </div>
            )}

            {(eventType === 'PRODUCT_VIEW' || eventType === 'PURCHASE') && (
              <div className="animate-in fade-in duration-200">
                <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-1.5">
                  Product ID {eventType === 'PRODUCT_VIEW' && <span className="text-rose-400">*</span>}
                </label>
                <input
                  type="text"
                  value={productId}
                  onChange={(e) => setProductId(e.target.value)}
                  required={eventType === 'PRODUCT_VIEW'}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-200 font-mono focus:outline-none focus:ring-1 focus:ring-brand-500"
                  placeholder="prod-1092"
                />
              </div>
            )}

            {/* Metadata JSON */}
            <div>
              <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-1.5">
                Metadata (JSON Envelope)
              </label>
              <textarea
                rows={3}
                value={metadataJson}
                onChange={(e) => setMetadataJson(e.target.value)}
                className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-xs font-mono text-slate-300 focus:outline-none focus:ring-1 focus:ring-brand-500"
              />
            </div>

            {/* Send Button */}
            <button
              type="submit"
              disabled={isSubmitting}
              className="w-full py-2.5 px-4 rounded-xl bg-brand-600 hover:bg-brand-500 disabled:opacity-50 text-white font-medium text-sm flex items-center justify-center gap-2 shadow-lg shadow-brand-600/30 transition-all cursor-pointer"
            >
              <Send className="w-4 h-4" />
              <span>{isSubmitting ? 'Publishing to Kafka...' : 'Publish Event (202 Accepted)'}</span>
            </button>
          </form>
        </div>

        {/* Right Column: Burst Generator & Idempotency Testing (5 cols) */}
        <div className="lg:col-span-5 space-y-6">
          {/* Burst Load Generator Card */}
          <div className="glass-panel rounded-2xl p-6 space-y-5">
            <div className="flex items-center justify-between border-b border-slate-800 pb-3">
              <div>
                <h3 className="text-base font-semibold text-slate-200 flex items-center gap-2">
                  <Layers className="w-4 h-4 text-amber-400" />
                  Burst Load Generator
                </h3>
                <p className="text-xs text-slate-400">Generate backpressure to observe Kafka lag</p>
              </div>
            </div>

            {/* Sliders */}
            <div className="space-y-4">
              <div>
                <div className="flex justify-between text-xs font-medium text-slate-300 mb-1">
                  <span>Batch Volume</span>
                  <span className="font-mono text-brand-400 font-bold">{burstCount} events</span>
                </div>
                <input
                  type="range"
                  min="10"
                  max="500"
                  step="10"
                  value={burstCount}
                  disabled={isBursting}
                  onChange={(e) => setBurstCount(Number(e.target.value))}
                  className="w-full accent-brand-500 cursor-pointer"
                />
              </div>

              <div>
                <div className="flex justify-between text-xs font-medium text-slate-300 mb-1">
                  <span>Inter-Event Delay</span>
                  <span className="font-mono text-amber-400 font-bold">{burstDelayMs} ms</span>
                </div>
                <input
                  type="range"
                  min="0"
                  max="100"
                  step="5"
                  value={burstDelayMs}
                  disabled={isBursting}
                  onChange={(e) => setBurstDelayMs(Number(e.target.value))}
                  className="w-full accent-amber-500 cursor-pointer"
                />
                <span className="text-[10px] text-slate-500">0ms creates maximum consumer lag spike</span>
              </div>
            </div>

            {/* Burst Action Button */}
            {!isBursting ? (
              <button
                type="button"
                onClick={handleStartBurst}
                className="w-full py-2.5 px-4 rounded-xl bg-amber-600 hover:bg-amber-500 text-white font-medium text-sm flex items-center justify-center gap-2 shadow-lg shadow-amber-600/20 transition-all cursor-pointer"
              >
                <Zap className="w-4 h-4" />
                <span>Launch {burstCount} Event Burst</span>
              </button>
            ) : (
              <button
                type="button"
                onClick={handleStopBurst}
                className="w-full py-2.5 px-4 rounded-xl bg-rose-600 hover:bg-rose-500 text-white font-medium text-sm flex items-center justify-center gap-2 transition-all cursor-pointer"
              >
                <AlertOctagon className="w-4 h-4" />
                <span>Stop Burst</span>
              </button>
            )}

            {/* Live Burst Stats */}
            <div className="grid grid-cols-4 gap-2 pt-2 border-t border-slate-800 text-center">
              <div className="bg-slate-950/60 p-2 rounded-lg border border-slate-800/80">
                <div className="text-[10px] text-slate-500">SENT</div>
                <div className="text-sm font-bold font-mono text-slate-200">{burstStats.sent}</div>
              </div>
              <div className="bg-slate-950/60 p-2 rounded-lg border border-slate-800/80">
                <div className="text-[10px] text-emerald-500">202 ACK</div>
                <div className="text-sm font-bold font-mono text-emerald-400">{burstStats.accepted}</div>
              </div>
              <div className="bg-slate-950/60 p-2 rounded-lg border border-slate-800/80">
                <div className="text-[10px] text-amber-500">4xx REJ</div>
                <div className="text-sm font-bold font-mono text-amber-400">{burstStats.rejected}</div>
              </div>
              <div className="bg-slate-950/60 p-2 rounded-lg border border-slate-800/80">
                <div className="text-[10px] text-sky-500">LATENCY</div>
                <div className="text-sm font-bold font-mono text-sky-400">{burstStats.latencyMs}ms</div>
              </div>
            </div>
          </div>

          {/* Idempotency & Fault Tolerance Demos Card */}
          <div className="glass-panel rounded-2xl p-6 space-y-4">
            <h3 className="text-base font-semibold text-slate-200 flex items-center gap-2">
              <Server className="w-4 h-4 text-indigo-400" />
              Idempotency & Resilience Demos
            </h3>
            <p className="text-xs text-slate-400">
              Trigger exact scenarios specified in the PRD interview defense.
            </p>

            <div className="space-y-3 pt-2">
              {/* Duplicate Event Demo */}
              <button
                type="button"
                onClick={handleResendDuplicate}
                disabled={!lastSentEvent || isSubmitting}
                className="w-full p-3 rounded-xl bg-slate-900 border border-slate-800 hover:border-slate-700 disabled:opacity-40 text-left transition-all flex items-start gap-3 cursor-pointer"
              >
                <RefreshCw className="w-5 h-5 text-purple-400 shrink-0 mt-0.5" />
                <div className="min-w-0">
                  <div className="text-xs font-semibold text-slate-200">
                    Resend Last Event (Duplicate Demo)
                  </div>
                  <div className="text-[11px] text-slate-400 mt-0.5 truncate">
                    {lastSentEvent ? `Re-sends eventId: ${lastSentEvent.eventId}` : 'Publish an event first to enable'}
                  </div>
                </div>
              </button>

              {/* Poison Pill Demo */}
              <button
                type="button"
                onClick={handleSendPoisonPill}
                disabled={isSubmitting}
                className="w-full p-3 rounded-xl bg-slate-900 border border-slate-800 hover:border-slate-700 disabled:opacity-40 text-left transition-all flex items-start gap-3 cursor-pointer"
              >
                <AlertOctagon className="w-5 h-5 text-rose-400 shrink-0 mt-0.5" />
                <div>
                  <div className="text-xs font-semibold text-slate-200">
                    Send Invalid Payload (Validation & DLQ Demo)
                  </div>
                  <div className="text-[11px] text-slate-400 mt-0.5">
                    Sends invalid payload; verifies RFC 7807 400 rejection or DLQ isolation
                  </div>
                </div>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
