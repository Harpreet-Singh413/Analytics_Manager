import React, { createContext, useContext, useState, useCallback } from 'react';
import { CheckCircle2, AlertTriangle, XCircle, Info, X, Copy, Check } from 'lucide-react';

const ToastContext = createContext(null);

export const ToastProvider = ({ children }) => {
  const [toasts, setToasts] = useState([]);

  const addToast = useCallback((toast) => {
    const id = Math.random().toString(36).substring(2, 9);
    const newToast = { id, ...toast };

    setToasts((prev) => [...prev, newToast]);

    const duration = toast.duration || 4500;
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, duration);
  }, []);

  const removeToast = useCallback((id) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const success = useCallback((title, msg, extra = {}) => {
    addToast({ type: 'success', title, message: msg, ...extra });
  }, [addToast]);

  const error = useCallback((title, msg, extra = {}) => {
    addToast({ type: 'error', title, message: msg, ...extra });
  }, [addToast]);

  const warning = useCallback((title, msg, extra = {}) => {
    addToast({ type: 'warning', title, message: msg, ...extra });
  }, [addToast]);

  const info = useCallback((title, msg, extra = {}) => {
    addToast({ type: 'info', title, message: msg, ...extra });
  }, [addToast]);

  return (
    <ToastContext.Provider value={{ addToast, removeToast, success, error, warning, info }}>
      {children}
      <div className="fixed bottom-4 right-4 z-50 flex flex-col gap-2 max-w-md w-full px-4 pointer-events-none">
        {toasts.map((toast) => (
          <ToastItem key={toast.id} toast={toast} onDismiss={() => removeToast(toast.id)} />
        ))}
      </div>
    </ToastContext.Provider>
  );
};

export const useToast = () => {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used within a ToastProvider');
  }
  return context;
};

const ToastItem = ({ toast, onDismiss }) => {
  const [copied, setCopied] = useState(false);

  const copyEventId = (e) => {
    e.stopPropagation();
    if (toast.eventId) {
      navigator.clipboard.writeText(toast.eventId);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const icons = {
    success: <CheckCircle2 className="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" />,
    error: <XCircle className="w-5 h-5 text-rose-400 shrink-0 mt-0.5" />,
    warning: <AlertTriangle className="w-5 h-5 text-amber-400 shrink-0 mt-0.5" />,
    info: <Info className="w-5 h-5 text-sky-400 shrink-0 mt-0.5" />,
  };

  const borders = {
    success: 'border-emerald-500/30 bg-emerald-950/40 text-emerald-200',
    error: 'border-rose-500/30 bg-rose-950/40 text-rose-200',
    warning: 'border-amber-500/30 bg-amber-950/40 text-amber-200',
    info: 'border-sky-500/30 bg-sky-950/40 text-sky-200',
  };

  return (
    <div
      className={`pointer-events-auto flex items-start gap-3 p-3.5 rounded-lg border backdrop-blur-md shadow-2xl transition-all duration-300 animate-in slide-in-from-right-5 ${
        borders[toast.type] || borders.info
      }`}
    >
      {icons[toast.type] || icons.info}
      <div className="flex-1 min-w-0">
        <div className="flex items-center justify-between gap-2">
          <p className="font-semibold text-sm text-slate-100">{toast.title}</p>
          {toast.status && (
            <span className="text-[11px] font-mono px-1.5 py-0.5 rounded bg-slate-800 text-slate-300 border border-slate-700">
              HTTP {toast.status}
            </span>
          )}
        </div>
        {toast.message && (
          <p className="text-xs text-slate-300 mt-1 leading-relaxed break-words">{toast.message}</p>
        )}
        {toast.eventId && (
          <div className="mt-2 flex items-center gap-1.5 bg-slate-900/80 px-2 py-1 rounded border border-slate-800 text-[11px] font-mono text-slate-400">
            <span className="truncate">ID: {toast.eventId}</span>
            <button
              onClick={copyEventId}
              className="ml-auto text-slate-400 hover:text-slate-200 transition-colors p-0.5"
              title="Copy eventId"
            >
              {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
            </button>
          </div>
        )}
      </div>
      <button
        onClick={onDismiss}
        className="text-slate-400 hover:text-slate-200 transition-colors p-1 -mr-1 -mt-1 rounded"
      >
        <X className="w-4 h-4" />
      </button>
    </div>
  );
};
