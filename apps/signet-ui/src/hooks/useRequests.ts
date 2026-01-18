import { useState, useCallback, useEffect, useMemo } from 'react';
import type { PendingRequest, PendingRequestWire, DisplayRequest, RequestMeta, TrustLevel, RequestFilter } from '@signet/types';
import { apiGet, apiPost, apiDelete } from '../lib/api-client.js';
import { buildErrorMessage, formatRelativeTime, toNpub } from '../lib/formatters.js';
import { useSSESubscription } from '../contexts/ServerEventsContext.js';
import type { ServerEvent } from './useServerEvents.js';
import { useRequestFilters, type SortBy } from './useRequestFilters.js';
import { useRequestSelection } from './useRequestSelection.js';

const REQUEST_LIMIT = 10;

// Re-export SortBy for backward compatibility
export type { SortBy } from './useRequestFilters.js';

interface UseRequestsResult {
  requests: DisplayRequest[];
  loading: boolean;
  loadingMore: boolean;
  error: string | null;
  hasMore: boolean;
  filter: RequestFilter;
  setFilter: (filter: RequestFilter) => void;
  passwords: Record<string, string>;
  setPassword: (id: string, password: string) => void;
  meta: Record<string, RequestMeta>;
  approve: (id: string, trustLevel?: TrustLevel, alwaysAllow?: boolean, allowKind?: number, appName?: string) => Promise<void>;
  deny: (id: string) => Promise<void>;
  loadMore: () => Promise<void>;
  refresh: () => Promise<void>;
  // Search and sort
  searchQuery: string;
  setSearchQuery: (query: string) => void;
  sortBy: SortBy;
  setSortBy: (sort: SortBy) => void;
  // Bulk selection
  selectionMode: boolean;
  toggleSelectionMode: () => void;
  selectedIds: Set<string>;
  toggleSelection: (id: string) => void;
  selectAll: () => void;
  deselectAll: () => void;
  bulkApprove: (trustLevel?: TrustLevel) => Promise<{ approved: number; failed: number }>;
  bulkApproving: boolean;
}

export function useRequests(): UseRequestsResult {
  // Core data state
  const [requests, setRequests] = useState<PendingRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(true);
  const [offset, setOffset] = useState(0);

  // Password and meta state (sensitive data)
  const [passwords, setPasswords] = useState<Record<string, string>>({});
  const [meta, setMeta] = useState<Record<string, RequestMeta>>({});

  // Bulk operation state
  const [bulkApproving, setBulkApproving] = useState(false);

  // Current time for TTL calculations
  const [now, setNow] = useState(() => Date.now());

  // Compose filter and selection hooks
  const filters = useRequestFilters();
  const selection = useRequestSelection();

  // Update now every second
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);

  // Data fetching
  const fetchRequests = useCallback(async (status: RequestFilter, offsetVal: number, append: boolean) => {
    const response = await apiGet<{ requests?: PendingRequestWire[] }>(
      `/requests?limit=${REQUEST_LIMIT}&status=${status}&offset=${offsetVal}`
    );

    const list = Array.isArray(response.requests)
      ? response.requests.map((request) => ({
          ...request,
          requiresPassword: Boolean(request.requiresPassword)
        }))
      : [];

    if (append) {
      setRequests(prev => [...prev, ...list]);
    } else {
      setRequests(list);
      setOffset(REQUEST_LIMIT);
    }

    setHasMore(list.length === REQUEST_LIMIT);
  }, []);

  const refresh = useCallback(async () => {
    try {
      await fetchRequests(filters.filter, 0, false);
      setError(null);
    } catch (err) {
      setError(buildErrorMessage(err, 'Unable to refresh requests'));
    }
  }, [filters.filter, fetchRequests]);

  const loadMore = useCallback(async () => {
    if (loadingMore || !hasMore) return;
    setLoadingMore(true);
    try {
      await fetchRequests(filters.filter, offset, true);
      setOffset(prev => prev + REQUEST_LIMIT);
    } catch (err) {
      console.error('Failed to load more requests:', err);
    } finally {
      setLoadingMore(false);
    }
  }, [loadingMore, hasMore, filters.filter, offset, fetchRequests]);

  // Initial load and filter change
  useEffect(() => {
    let cancelled = false;
    setOffset(0);
    setHasMore(true);
    setLoading(true);

    const load = async () => {
      try {
        await fetchRequests(filters.filter, 0, false);
        if (!cancelled) setError(null);
      } catch (err) {
        if (!cancelled) setError(buildErrorMessage(err, 'Unable to load requests'));
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    load();

    return () => {
      cancelled = true;
    };
  }, [filters.filter, fetchRequests]);

  // Subscribe to SSE events for real-time updates
  const handleSSEEvent = useCallback((event: ServerEvent) => {
    // Refresh data on reconnection to ensure consistency
    if (event.type === 'reconnected') {
      refresh();
      return;
    }

    // Refresh when requests are created, approved, denied, or auto-approved to update the list
    if (event.type === 'request:created' || event.type === 'request:approved' || event.type === 'request:denied' || event.type === 'request:auto_approved') {
      refresh();
    }
  }, [refresh]);

  useSSESubscription(handleSSEEvent);

  // Clean up passwords and meta when requests change
  useEffect(() => {
    setPasswords(prev => {
      const next: Record<string, string> = {};
      for (const request of requests) {
        if (request.requiresPassword && prev[request.id]) {
          next[request.id] = prev[request.id];
        }
      }
      return next;
    });

    setMeta(prev => {
      const next: Record<string, RequestMeta> = {};
      for (const request of requests) {
        const details = prev[request.id];
        if (details && details.state !== 'success') {
          next[request.id] = details;
        }
      }
      return next;
    });
  }, [requests]);

  // Clear sensitive data (passwords) on unmount
  useEffect(() => {
    return () => {
      setPasswords({});
    };
  }, []);

  const setPassword = useCallback((id: string, password: string) => {
    setPasswords(prev => ({ ...prev, [id]: password }));
  }, []);

  // Approval and denial operations
  const approve = useCallback(async (id: string, trustLevel?: TrustLevel, alwaysAllow?: boolean, allowKind?: number, appName?: string) => {
    const request = requests.find(r => r.id === id);
    const requiresPassword = request?.requiresPassword ?? false;
    const password = passwords[id]?.trim() ?? '';

    if (requiresPassword && !password) {
      setMeta(prev => ({
        ...prev,
        [id]: { state: 'error', message: 'Password required to authorize this request' }
      }));
      return;
    }

    setMeta(prev => ({ ...prev, [id]: { state: 'approving' } }));

    try {
      const payload: { password?: string; trustLevel?: TrustLevel; alwaysAllow?: boolean; allowKind?: number; appName?: string } = {};
      if (requiresPassword) {
        payload.password = password;
      }
      if (trustLevel) {
        payload.trustLevel = trustLevel;
      }
      if (alwaysAllow) {
        payload.alwaysAllow = alwaysAllow;
      }
      if (allowKind !== undefined) {
        payload.allowKind = allowKind;
      }
      if (appName) {
        payload.appName = appName;
      }
      const result = await apiPost<{ ok?: boolean; error?: string }>(`/requests/${id}`, payload);

      if (!result?.ok) {
        throw new Error(result?.error ?? 'Authorization failed');
      }

      setMeta(prev => ({ ...prev, [id]: { state: 'success', message: 'Approved' } }));
      setPasswords(prev => {
        const next = { ...prev };
        delete next[id];
        return next;
      });

      await refresh();
    } catch (err) {
      setMeta(prev => ({
        ...prev,
        [id]: { state: 'error', message: buildErrorMessage(err, 'Authorization failed') }
      }));
    }
  }, [requests, passwords, refresh]);

  const deny = useCallback(async (id: string) => {
    setMeta(prev => ({ ...prev, [id]: { state: 'approving' } }));

    try {
      const result = await apiDelete<{ ok?: boolean; error?: string }>(`/requests/${id}`);

      if (!result?.ok) {
        throw new Error(result?.error ?? 'Denial failed');
      }

      setMeta(prev => ({ ...prev, [id]: { state: 'success', message: 'Denied' } }));

      await refresh();
    } catch (err) {
      setMeta(prev => ({
        ...prev,
        [id]: { state: 'error', message: buildErrorMessage(err, 'Denial failed') }
      }));
    }
  }, [refresh]);

  // Decorate requests with computed fields
  const decoratedRequests: DisplayRequest[] = useMemo(() => {
    return requests.map(request => {
      const expires = Date.parse(request.expiresAt);
      const ttl = Number.isFinite(expires)
        ? Math.max(0, Math.round((expires - now) / 1000))
        : Math.max(0, request.ttlSeconds);

      let state: DisplayRequest['state'];
      if (request.allowed === true) {
        state = 'approved';
      } else if (request.allowed === false) {
        state = 'denied';
      } else if (ttl === 0) {
        state = 'expired';
      } else {
        state = 'pending';
      }

      return {
        ...request,
        ttl,
        npub: toNpub(request.remotePubkey),
        createdLabel: formatRelativeTime(request.createdAt, now),
        state,
        approvedAt: request.processedAt ?? undefined
      };
    });
  }, [requests, now]);

  // Apply filters and sorting
  const sortedRequests = useMemo(() => {
    return filters.applyFilters(decoratedRequests);
  }, [decoratedRequests, filters]);

  // Bulk approval
  const bulkApprove = useCallback(async (trustLevel?: TrustLevel): Promise<{ approved: number; failed: number }> => {
    if (selection.selectedIds.size === 0) return { approved: 0, failed: 0 };

    const toApprove = Array.from(selection.selectedIds);
    const needPassword = toApprove.filter(id => {
      const req = requests.find(r => r.id === id);
      return req?.requiresPassword && (!passwords[id] || !passwords[id].trim());
    });

    if (needPassword.length > 0) {
      setError(`${needPassword.length} selected request(s) require a password`);
      return { approved: 0, failed: needPassword.length };
    }

    setBulkApproving(true);
    let approved = 0;
    let failed = 0;

    for (const id of toApprove) {
      try {
        await approve(id, trustLevel);
        approved++;
      } catch {
        failed++;
      }
    }

    setBulkApproving(false);
    selection.clearSelection();

    return { approved, failed };
  }, [selection, requests, passwords, approve]);

  // Wrapper for selectAll that uses current sorted requests
  const selectAll = useCallback(() => {
    selection.selectAll(sortedRequests);
  }, [selection, sortedRequests]);

  return {
    requests: sortedRequests,
    loading,
    loadingMore,
    error,
    hasMore,
    filter: filters.filter,
    setFilter: filters.setFilter,
    passwords,
    setPassword,
    meta,
    approve,
    deny,
    loadMore,
    refresh,
    searchQuery: filters.searchQuery,
    setSearchQuery: filters.setSearchQuery,
    sortBy: filters.sortBy,
    setSortBy: filters.setSortBy,
    selectionMode: selection.selectionMode,
    toggleSelectionMode: selection.toggleSelectionMode,
    selectedIds: selection.selectedIds,
    toggleSelection: selection.toggleSelection,
    selectAll,
    deselectAll: selection.deselectAll,
    bulkApprove,
    bulkApproving,
  };
}
