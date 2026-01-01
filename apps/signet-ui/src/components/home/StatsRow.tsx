import React from 'react';
import type { DashboardStats, RelayStatusResponse } from '@signet/types';
import { Radio, Key, Smartphone, Clock } from 'lucide-react';
import styles from './HomeView.module.css';

interface StatsRowProps {
  stats: DashboardStats | null;
  relayStatus: RelayStatusResponse | null;
  onRelaysClick?: () => void;
  onKeysClick?: () => void;
  onAppsClick?: () => void;
  onActivityClick?: () => void;
}

export function StatsRow({
  stats,
  relayStatus,
  onRelaysClick,
  onKeysClick,
  onAppsClick,
  onActivityClick
}: StatsRowProps) {
  return (
    <section className={styles.statsSection}>
      <div className={styles.statsGrid}>
        <button
          type="button"
          className={`${styles.statCard} ${styles.statCardClickable}`}
          onClick={onKeysClick}
          aria-label="View keys"
        >
          <div className={`${styles.statIcon} ${styles.statIconKeys}`}>
            <Key size={24} />
          </div>
          <div className={styles.statContent}>
            <span className={styles.statValue}>
              {stats ? (stats.totalKeys === 0 ? '0' : `${stats.activeKeys}/${stats.totalKeys}`) : '-'}
            </span>
            <span className={styles.statLabel}>Active Keys</span>
          </div>
        </button>

        <button
          type="button"
          className={`${styles.statCard} ${styles.statCardClickable}`}
          onClick={onAppsClick}
          aria-label="View apps"
        >
          <div className={`${styles.statIcon} ${styles.statIconApps}`}>
            <Smartphone size={24} />
          </div>
          <div className={styles.statContent}>
            <span className={styles.statValue}>{stats?.connectedApps ?? '-'}</span>
            <span className={styles.statLabel}>Apps</span>
          </div>
        </button>

        <button
          type="button"
          className={`${styles.statCard} ${styles.statCardClickable}`}
          onClick={onActivityClick}
          aria-label="View activity"
        >
          <div className={`${styles.statIcon} ${styles.statIconActivity}`}>
            <Clock size={24} />
          </div>
          <div className={styles.statContent}>
            <span className={styles.statValue}>{stats?.recentActivity24h ?? '-'}</span>
            <span className={styles.statLabel}>Last 24h</span>
          </div>
        </button>

        <button
          type="button"
          className={`${styles.statCard} ${styles.statCardClickable}`}
          onClick={onRelaysClick}
          aria-label="View relay status"
        >
          <div className={`${styles.statIcon} ${styles.statIconRelays}`}>
            <Radio size={24} />
          </div>
          <div className={styles.statContent}>
            <span className={styles.statValue}>
              {relayStatus ? `${relayStatus.connected}/${relayStatus.total}` : '-'}
            </span>
            <span className={styles.statLabel}>Relays</span>
          </div>
        </button>
      </div>
    </section>
  );
}
