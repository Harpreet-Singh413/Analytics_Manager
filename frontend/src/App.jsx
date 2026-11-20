import React from 'react';
import { HashRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ToastProvider } from './components/common/Toast';
import { Navbar } from './components/layout/Navbar';
import { SimulatorPage } from './pages/SimulatorPage';
import { DashboardPage } from './pages/DashboardPage';
import { EventHistoryPage } from './pages/EventHistoryPage';

export function App() {
  return (
    <ToastProvider>
      <HashRouter>
        <div className="min-h-screen flex flex-col bg-slate-950 text-slate-100 selection:bg-brand-500 selection:text-white">
          <Navbar />
          
          <main className="flex-1">
            <Routes>
              <Route path="/" element={<Navigate to="/dashboard" replace />} />
              <Route path="/simulator" element={<SimulatorPage />} />
              <Route path="/dashboard" element={<DashboardPage />} />
              <Route path="/events" element={<EventHistoryPage />} />
              <Route path="*" element={<Navigate to="/dashboard" replace />} />
            </Routes>
          </main>

          <footer className="border-t border-slate-800/80 py-6 text-center text-xs text-slate-500 space-y-1">
            <p>
              <strong className="text-slate-400">Analytics Manager</strong> — Event-Driven Architecture Platform
            </p>
            <p className="font-mono text-[11px] text-slate-600">
              Spring Boot 3.4 • Apache Kafka (KRaft) • MySQL 8.4 • Prometheus • Grafana
            </p>
          </footer>
        </div>
      </HashRouter>
    </ToastProvider>
  );
}

export default App;
