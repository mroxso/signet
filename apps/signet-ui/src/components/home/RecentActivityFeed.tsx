import React from 'react';
import type { ActivityEntry } from '@signet/types';
import { getMethodLabelPastTense } from '@signet/types';
import { Clock, ChevronRight, Check, X, Activity } from 'lucide-react';
import { formatTimeAgo } from '../../lib/formatters.js';
import styles from './HomeView.module.css';

interface RecentActivityFeedProps {
  activity: ActivityEntry[];
  showAutoApproved: boolean;
  onToggleShowAutoApproved: () => void;
  onNavigateToActivity: () => void;
}

export function RecentActivityFeed({
  activity,
  showAutoApproved,
  onToggleShowAutoApproved,
  onNavigateToActivity,
}: RecentActivityFeedProps) {
  const filteredActivity = showAutoApproved
    ? activity
    : activity.filter(entry => !entry.autoApproved);
  const recentActivity = filteredActivity.slice(0, 5);

  const getActivityIcon = (type: string) => {
    if (type === 'approval') return <Check size={14} className={styles.activityIconApproved} />;
    if (type === 'denial') return <X size={14} className={styles.activityIconDenied} />;
    return <Clock size={14} className={styles.activityIconPending} />;
  };

  return (
    <section className={styles.section}>
      <div className={styles.sectionHeader}>
        <h2 className={styles.sectionTitle}>Recent</h2>
        <label className={styles.filterToggle}>
          <input
            type="checkbox"
            className={styles.visuallyHidden}
            checked={showAutoApproved}
            onChange={onToggleShowAutoApproved}
          />
          <span className={styles.checkbox} aria-hidden="true" />
          <span>Show auto</span>
        </label>
      </div>
      {recentActivity.length === 0 ? (
        <div className={styles.emptyState}>
          <span className={styles.emptyIcon}><Activity size={18} /></span>
          <p>{activity.length === 0 ? 'No recent activity' : 'No manual approvals'}</p>
        </div>
      ) : (
        <div className={styles.listCard}>
          {recentActivity.map((entry) => (
            <div key={entry.id} className={styles.activityItem}>
              <div className={styles.activityRow}>
                {getActivityIcon(entry.type)}
                <span className={styles.activityAppName}>
                  {entry.appName || 'Unknown'}
                  {entry.keyName && <span className={styles.activityKeyName}> • {entry.keyName}</span>}
                </span>
                <span className={styles.statusBadge}>
                  {entry.autoApproved ? (
                    <span className={styles.badgeAuto}>Auto Approved</span>
                  ) : entry.type === 'approval' ? (
                    <span className={styles.badgeApproved}>Approved</span>
                  ) : entry.type === 'denial' ? (
                    <span className={styles.badgeDenied}>Denied</span>
                  ) : null}
                </span>
              </div>
              <div className={styles.activityRow}>
                <span className={styles.activityMethod}>
                  {entry.method ? getMethodLabelPastTense(entry.method, entry.eventKind) : entry.type}
                  {' • '}
                  {formatTimeAgo(entry.timestamp)}
                </span>
              </div>
            </div>
          ))}
          <button type="button" className={styles.viewAllButton} onClick={onNavigateToActivity}>
            View all
            <ChevronRight size={14} />
          </button>
        </div>
      )}
    </section>
  );
}
