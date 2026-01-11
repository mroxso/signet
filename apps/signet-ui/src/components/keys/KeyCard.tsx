import React, { useState } from 'react';
import type { KeyInfo, ConnectedApp } from '@signet/types';
import { ChevronDown, ChevronRight, Copy, QrCode, Lock, Unlock, Trash2, Users, Pencil, Shield } from 'lucide-react';
import { formatRelativeTime, toNpub } from '../../lib/formatters.js';
import { getTrustLevelInfo } from '../../lib/event-labels.js';
import { copyToClipboard as copyText } from '../../lib/clipboard.js';
import { BunkerURIModal } from '../layout/BunkerURIModal.js';
import styles from './KeysPanel.module.css';

interface KeyCardProps {
  keyInfo: KeyInfo;
  apps: ConnectedApp[];
  expanded: boolean;
  now: number;
  unlocking: string | null;  // Key name being unlocked, or null
  locking: string | null;    // Key name being locked, or null
  renaming: boolean;
  settingPassphrase: boolean;
  onToggleExpand: () => void;
  onUnlock: (passphrase: string) => Promise<boolean>;
  onLock: () => void;
  onRename: (newName: string) => Promise<boolean>;
  onSetPassphrase: (passphrase: string) => Promise<boolean>;
  onDelete: () => void;
  onShowQR: (value: string, title: string) => void;
  onClearError: () => void;
}

export function KeyCard({
  keyInfo: key,
  apps,
  expanded,
  now,
  unlocking,
  locking,
  renaming,
  settingPassphrase,
  onToggleExpand,
  onUnlock,
  onLock,
  onRename,
  onSetPassphrase,
  onDelete,
  onShowQR,
  onClearError,
}: KeyCardProps) {
  // Unlock state
  const [unlockPassphrase, setUnlockPassphrase] = useState('');

  // Rename state
  const [isRenaming, setIsRenaming] = useState(false);
  const [editName, setEditName] = useState('');

  // Set passphrase state
  const [isSettingPassphrase, setIsSettingPassphrase] = useState(false);
  const [newPassphrase, setNewPassphrase] = useState('');
  const [confirmPassphrase, setConfirmPassphrase] = useState('');

  // Clipboard feedback
  const [copiedField, setCopiedField] = useState<string | null>(null);

  // Bunker URI modal state
  const [showBunkerModal, setShowBunkerModal] = useState(false);

  const copyToClipboard = async (text: string, field: string) => {
    const success = await copyText(text);
    if (success) {
      setCopiedField(field);
      setTimeout(() => setCopiedField(null), 2000);
    }
  };

  const handleUnlock = async () => {
    if (!unlockPassphrase.trim()) return;
    const success = await onUnlock(unlockPassphrase);
    if (success) {
      setUnlockPassphrase('');
    }
  };

  const startRename = () => {
    setIsRenaming(true);
    setEditName(key.name);
    onClearError();
  };

  const cancelRename = () => {
    setIsRenaming(false);
    setEditName('');
  };

  const handleRename = async () => {
    if (!editName.trim()) return;
    const success = await onRename(editName.trim());
    if (success) {
      setIsRenaming(false);
      setEditName('');
    }
  };

  const startSetPassphrase = () => {
    setIsSettingPassphrase(true);
    setNewPassphrase('');
    setConfirmPassphrase('');
    onClearError();
  };

  const cancelSetPassphrase = () => {
    setIsSettingPassphrase(false);
    setNewPassphrase('');
    setConfirmPassphrase('');
  };

  const handleSetPassphrase = async () => {
    if (!newPassphrase.trim() || newPassphrase !== confirmPassphrase) return;
    const success = await onSetPassphrase(newPassphrase);
    if (success) {
      setIsSettingPassphrase(false);
      setNewPassphrase('');
      setConfirmPassphrase('');
    }
  };

  const handleToggleExpand = () => {
    if (!expanded) {
      // Reset local state when expanding
      setUnlockPassphrase('');
      setIsRenaming(false);
      setEditName('');
      setIsSettingPassphrase(false);
      setNewPassphrase('');
      setConfirmPassphrase('');
      onClearError();
    }
    onToggleExpand();
  };

  return (
    <div className={`${styles.keyCard} ${expanded ? styles.expanded : ''}`}>
      <button
        type="button"
        className={styles.keyHeader}
        onClick={handleToggleExpand}
        aria-expanded={expanded}
      >
        <div className={styles.keyMain}>
          <span className={`${styles.activityDot} ${
            key.status === 'online' ? styles.active :
            key.status === 'locked' ? styles.locked : ''
          }`} />
          <span className={styles.keyName}>{key.name}</span>
          <span className={`${styles.status} ${styles[key.status]}`}>
            {key.status === 'locked' && <Lock size={12} />}
            {key.status}
          </span>
        </div>
        <div className={styles.keyMeta}>
          {key.npub && (
            <span className={styles.npubPreview}>
              {key.npub.slice(0, 12)}...
            </span>
          )}
          <span className={styles.dot}>•</span>
          <span>{key.userCount} app{key.userCount !== 1 ? 's' : ''}</span>
          <span className={styles.dot}>•</span>
          <span>{key.requestCount} request{key.requestCount !== 1 ? 's' : ''}</span>
          {key.lastUsedAt && (
            <>
              <span className={styles.dot}>•</span>
              <span>{formatRelativeTime(key.lastUsedAt, now)}</span>
            </>
          )}
        </div>
        <span className={styles.expandIcon}>
          {expanded ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
        </span>
      </button>

      {expanded && (
        <div className={styles.keyDetails}>
          {key.status === 'locked' ? (
            <div className={styles.unlockSection}>
              <div className={styles.unlockHeader}>
                <Lock size={20} />
                <span>Key is Locked</span>
              </div>
              <p className={styles.unlockHint}>
                Enter passphrase to unlock and start signing
              </p>
              <div className={styles.unlockForm}>
                <input
                  type="password"
                  className={styles.input}
                  value={unlockPassphrase}
                  onChange={(e) => setUnlockPassphrase(e.target.value)}
                  placeholder="Enter passphrase"
                  aria-label="Passphrase to unlock key"
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') handleUnlock();
                  }}
                />
                <button
                  type="button"
                  className={styles.unlockButton}
                  onClick={handleUnlock}
                  disabled={unlocking === key.name || !unlockPassphrase.trim()}
                >
                  <Unlock size={16} />
                  {unlocking === key.name ? 'Unlocking...' : 'Unlock'}
                </button>
              </div>
            </div>
          ) : (
            <>
              {key.npub && (
                <div className={styles.detailSection}>
                  <span className={styles.detailLabel}>Public Key</span>
                  <div className={styles.detailRow}>
                    <code className={styles.detailValue}>{key.npub}</code>
                    <div className={styles.detailActions}>
                      <button
                        type="button"
                        className={styles.actionButton}
                        onClick={() => copyToClipboard(key.npub!, `npub-${key.name}`)}
                      >
                        <Copy size={14} />
                        {copiedField === `npub-${key.name}` ? 'Copied' : 'Copy'}
                      </button>
                      <button
                        type="button"
                        className={styles.actionButton}
                        onClick={() => onShowQR(key.npub!, 'Public Key')}
                      >
                        <QrCode size={14} />
                        QR
                      </button>
                    </div>
                  </div>
                </div>
              )}

              {key.status === 'online' && (
                <div className={styles.detailSection}>
                  <span className={styles.detailLabel}>Bunker Connection</span>
                  <div className={styles.detailRow}>
                    <code className={styles.detailValue}>
                      bunker://{key.pubkey?.slice(0, 16)}...
                    </code>
                    <div className={styles.detailActions}>
                      <button
                        type="button"
                        className={styles.actionButton}
                        onClick={() => setShowBunkerModal(true)}
                        title="Show bunker URI QR code"
                      >
                        <QrCode size={14} />
                        QR
                      </button>
                    </div>
                  </div>
                </div>
              )}

              {!key.isEncrypted && (
                <div className={styles.detailSection}>
                  <span className={styles.detailLabel}>
                    <Shield size={14} />
                    Security
                  </span>
                  {isSettingPassphrase ? (
                    <div className={styles.setPassphraseForm}>
                      <input
                        type="password"
                        className={styles.input}
                        value={newPassphrase}
                        onChange={(e) => setNewPassphrase(e.target.value)}
                        placeholder="New passphrase"
                        aria-label="New passphrase"
                        autoFocus
                      />
                      <input
                        type="password"
                        className={styles.input}
                        value={confirmPassphrase}
                        onChange={(e) => setConfirmPassphrase(e.target.value)}
                        placeholder="Confirm passphrase"
                        aria-label="Confirm passphrase"
                        aria-describedby={newPassphrase && confirmPassphrase && newPassphrase !== confirmPassphrase ? 'passphrase-mismatch-error' : undefined}
                        aria-invalid={newPassphrase && confirmPassphrase && newPassphrase !== confirmPassphrase ? true : undefined}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter' && newPassphrase && newPassphrase === confirmPassphrase) {
                            handleSetPassphrase();
                          }
                          if (e.key === 'Escape') {
                            cancelSetPassphrase();
                          }
                        }}
                      />
                      {newPassphrase && confirmPassphrase && newPassphrase !== confirmPassphrase && (
                        <span id="passphrase-mismatch-error" className={styles.passphraseMismatch} role="alert">Passphrases do not match</span>
                      )}
                      <div className={styles.setPassphraseActions}>
                        <button
                          type="button"
                          className={styles.saveButton}
                          onClick={handleSetPassphrase}
                          disabled={settingPassphrase || !newPassphrase.trim() || newPassphrase !== confirmPassphrase}
                        >
                          {settingPassphrase ? 'Saving...' : 'Set Passphrase'}
                        </button>
                        <button
                          type="button"
                          className={styles.cancelButton}
                          onClick={cancelSetPassphrase}
                        >
                          Cancel
                        </button>
                      </div>
                    </div>
                  ) : (
                    <div className={styles.securityWarning}>
                      <p>This key is stored unencrypted. Anyone with access to the config file can read it.</p>
                      <button
                        type="button"
                        className={styles.setPassphraseButton}
                        onClick={startSetPassphrase}
                      >
                        <Lock size={14} />
                        Set Passphrase
                      </button>
                    </div>
                  )}
                </div>
              )}
            </>
          )}

          {apps.length > 0 && (
            <div className={styles.detailSection}>
              <span className={styles.detailLabel}>
                <Users size={14} />
                Connected Apps
              </span>
              <div className={styles.appsList}>
                {apps.map(app => {
                  const trustInfo = getTrustLevelInfo(app.trustLevel);
                  const displayName = app.description || toNpub(app.userPubkey).slice(0, 12) + '...';
                  return (
                    <div key={app.id} className={styles.appItem}>
                      <span className={styles.appName}>{displayName}</span>
                      <span className={`${styles.appTrust} ${styles[app.trustLevel]}`}>
                        {trustInfo.label}
                      </span>
                      <span className={styles.appRequests}>
                        {app.requestCount} req
                      </span>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          <div className={styles.actions}>
            {isRenaming ? (
              <div className={styles.renameRow}>
                <input
                  type="text"
                  className={styles.input}
                  value={editName}
                  onChange={(e) => setEditName(e.target.value)}
                  placeholder="New key name"
                  aria-label="New key name"
                  autoFocus
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') handleRename();
                    if (e.key === 'Escape') cancelRename();
                  }}
                />
                <button
                  type="button"
                  className={styles.saveButton}
                  onClick={handleRename}
                  disabled={renaming || !editName.trim() || editName.trim() === key.name}
                >
                  {renaming ? 'Saving...' : 'Save'}
                </button>
                <button
                  type="button"
                  className={styles.cancelButton}
                  onClick={cancelRename}
                >
                  Cancel
                </button>
              </div>
            ) : (
              <>
                <button
                  type="button"
                  className={styles.renameButton}
                  onClick={startRename}
                >
                  <Pencil size={16} />
                  Rename
                </button>
                {key.status === 'online' && key.isEncrypted && (
                  <button
                    type="button"
                    className={styles.lockButton}
                    onClick={onLock}
                    disabled={locking === key.name}
                    title="Lock key to remove it from memory"
                  >
                    <Lock size={16} />
                    {locking === key.name ? 'Locking...' : 'Lock'}
                  </button>
                )}
                <button
                  type="button"
                  className={styles.deleteButton}
                  onClick={onDelete}
                >
                  <Trash2 size={16} />
                  Delete Key
                </button>
              </>
            )}
          </div>
        </div>
      )}

      {/* Bunker URI Modal */}
      <BunkerURIModal
        open={showBunkerModal}
        keyName={key.name}
        onClose={() => setShowBunkerModal(false)}
      />
    </div>
  );
}
