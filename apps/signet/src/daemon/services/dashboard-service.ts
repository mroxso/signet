import type { DashboardStats, ActivityEntry } from '@signet/types';
import type { StoredKey } from '../../config/types.js';
import { appRepository, logRepository, requestRepository } from '../repositories/index.js';

export interface DashboardServiceConfig {
    allKeys: Record<string, StoredKey>;
    getActiveKeyCount: () => number;
}

export interface DashboardData {
    stats: DashboardStats;
    activity: ActivityEntry[];
    hourlyActivity: Array<{ hour: number; type: string; count: number }>;
}

export class DashboardService {
    private readonly config: DashboardServiceConfig;

    constructor(config: DashboardServiceConfig) {
        this.config = config;
    }

    /**
     * Get just the dashboard stats (without activity or hourly data)
     * Used for emitting stats:updated events
     */
    async getStats(): Promise<DashboardStats> {
        const totalKeys = Object.keys(this.config.allKeys).length;
        const activeKeys = this.config.getActiveKeyCount();

        const yesterday = new Date();
        yesterday.setDate(yesterday.getDate() - 1);

        const [connectedApps, pendingRequests, recentActivity24h] = await Promise.all([
            appRepository.countActive(),
            requestRepository.countPending(),
            logRepository.countSince(yesterday),
        ]);

        return {
            totalKeys,
            activeKeys,
            connectedApps,
            pendingRequests,
            recentActivity24h,
        };
    }

    async getDashboardData(): Promise<DashboardData> {
        const totalKeys = Object.keys(this.config.allKeys).length;
        const activeKeys = this.config.getActiveKeyCount();

        const yesterday = new Date();
        yesterday.setDate(yesterday.getDate() - 1);

        // Run all independent queries in parallel (5 queries -> 1 round trip)
        const [
            connectedApps,
            pendingRequests,
            recentActivity24h,
            hourlyActivity,
            recentLogs,
        ] = await Promise.all([
            appRepository.countActive(),
            requestRepository.countPending(),
            logRepository.countSince(yesterday),
            logRepository.getHourlyActivityRaw(),
            logRepository.findRecent(5),
        ]);

        const activity = recentLogs.map(log => logRepository.toActivityEntry(log));

        return {
            stats: {
                totalKeys,
                activeKeys,
                connectedApps,
                pendingRequests,
                recentActivity24h,
            },
            activity,
            hourlyActivity,
        };
    }
}

// Singleton instance for global access
let dashboardServiceInstance: DashboardService | null = null;

export function getDashboardService(): DashboardService {
    if (!dashboardServiceInstance) {
        throw new Error('DashboardService not initialized. Call setDashboardService() first.');
    }
    return dashboardServiceInstance;
}

export function setDashboardService(service: DashboardService): void {
    dashboardServiceInstance = service;
}
