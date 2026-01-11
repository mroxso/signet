import React, { useState, useEffect } from 'react';
import styles from './KeysPanel.module.css';

interface CreateKeyFormProps {
  creating: boolean;
  onSubmit: (data: { keyName: string; passphrase?: string; nsec?: string }) => Promise<boolean>;
  onCancel: () => void;
}

export function CreateKeyForm({ creating, onSubmit, onCancel }: CreateKeyFormProps) {
  const [keyName, setKeyName] = useState('');
  const [passphrase, setPassphrase] = useState('');
  const [nsec, setNsec] = useState('');
  const [createMode, setCreateMode] = useState<'generate' | 'import'>('generate');

  // Clear sensitive data (passphrase, nsec) on unmount
  useEffect(() => {
    return () => {
      setPassphrase('');
      setNsec('');
    };
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const success = await onSubmit({
      keyName: keyName.trim(),
      passphrase: passphrase.trim() || undefined,
      nsec: createMode === 'import' ? nsec.trim() : undefined,
    });

    if (success) {
      setKeyName('');
      setPassphrase('');
      setNsec('');
      onCancel();
    }
  };

  return (
    <form className={styles.createForm} onSubmit={handleSubmit}>
      <div className={styles.formGroup}>
        <label className={styles.label} htmlFor="keyName">Key Name</label>
        <input
          id="keyName"
          type="text"
          value={keyName}
          onChange={(e) => setKeyName(e.target.value)}
          placeholder="e.g., main-key"
          className={styles.input}
          required
        />
      </div>

      <div className={styles.modeSelector}>
        <button
          type="button"
          className={`${styles.modeButton} ${createMode === 'generate' ? styles.active : ''}`}
          onClick={() => setCreateMode('generate')}
        >
          Generate New
        </button>
        <button
          type="button"
          className={`${styles.modeButton} ${createMode === 'import' ? styles.active : ''}`}
          onClick={() => setCreateMode('import')}
        >
          Import Existing
        </button>
      </div>

      {createMode === 'import' && (
        <div className={styles.formGroup}>
          <label className={styles.label} htmlFor="nsec">Private Key (nsec)</label>
          <input
            id="nsec"
            type="password"
            value={nsec}
            onChange={(e) => setNsec(e.target.value)}
            placeholder="nsec1..."
            className={styles.input}
            required
          />
        </div>
      )}

      <div className={styles.formGroup}>
        <label className={styles.label} htmlFor="passphrase">Encryption Passphrase (optional)</label>
        <input
          id="passphrase"
          type="password"
          value={passphrase}
          onChange={(e) => setPassphrase(e.target.value)}
          placeholder="Leave empty for unencrypted storage"
          className={styles.input}
          aria-describedby="passphrase-hint"
        />
        <span id="passphrase-hint" className={styles.hint}>
          Keys without a passphrase are stored in plain text and auto-unlock on startup
        </span>
      </div>

      <button type="submit" className={styles.submitButton} disabled={creating}>
        {creating ? 'Creating...' : createMode === 'generate' ? 'Generate Key' : 'Import Key'}
      </button>
    </form>
  );
}
