import React from 'react';
import type { RelayStatusResponse } from '@signet/types';
import { X, CheckCircle, XCircle } from 'lucide-react';
import { formatRelativeTime } from '../../lib/formatters.js';
import styles from './RelayDetailModal.module.css';

interface RelayDetailModalProps {
  open: boolean;
  onClose: () => void;
  relayStatus: RelayStatusResponse | null;
}

export function RelayDetailModal({ open, onClose, relayStatus }: RelayDetailModalProps) {
  const now = Date.now();

  const handleBackdropClick = (e: React.MouseEvent) => {
    if (e.target === e.currentTarget) {
      onClose();
    }
  };

  if (!open || !relayStatus) return null;

  return (
    <div className={styles.backdrop} onClick={handleBackdropClick} role="presentation">
      <div className={styles.modal} role="dialog" aria-modal="true" aria-labelledby="relay-modal-title">
        <div className={styles.header}>
          <h2 id="relay-modal-title" className={styles.title}>Relay Status</h2>
          <button type="button" className={styles.closeButton} onClick={onClose} aria-label="Close">
            <X size={20} />
          </button>
        </div>

        <div className={styles.summary}>
          {relayStatus.connected} of {relayStatus.total} relays connected
        </div>

        <div className={styles.relayList}>
          {relayStatus.relays.map((relay) => (
            <div key={relay.url} className={styles.relayItem}>
              <div className={styles.relayInfo}>
                <span className={styles.relayUrl}>{relay.url}</span>
                <span className={styles.relayTime}>
                  {relay.connected
                    ? relay.lastConnected
                      ? `Connected ${formatRelativeTime(relay.lastConnected, now)}`
                      : 'Connected'
                    : relay.lastDisconnected
                      ? `Disconnected ${formatRelativeTime(relay.lastDisconnected, now)}`
                      : 'Disconnected'}
                </span>
              </div>
              <div className={`${styles.relayStatus} ${relay.connected ? styles.connected : styles.disconnected}`}>
                {relay.connected ? <CheckCircle size={18} /> : <XCircle size={18} />}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
