import type { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import type { AppService } from '../../services/index.js';
import { emitCurrentStats } from '../../services/index.js';
import type { TrustLevel } from '@signet/types';
import type { PreHandlerAuthCsrf } from '../types.js';
import { sendError } from '../../lib/route-errors.js';

export interface AppsRouteConfig {
    appService: AppService;
}

export function registerAppsRoutes(
    fastify: FastifyInstance,
    config: AppsRouteConfig,
    preHandler: PreHandlerAuthCsrf
): void {
    // List all connected apps (GET - no CSRF needed)
    fastify.get('/apps', { preHandler: preHandler.auth }, async (_request: FastifyRequest, reply: FastifyReply) => {
        const apps = await config.appService.listApps();
        return reply.send({ apps });
    });

    // Revoke app access (POST - needs CSRF)
    fastify.post('/apps/:id/revoke', { preHandler: [...preHandler.auth, ...preHandler.csrf] }, async (request: FastifyRequest, reply: FastifyReply) => {
        const params = request.params as { id: string };
        const appId = Number(params.id);

        if (!Number.isFinite(appId)) {
            return reply.code(400).send({ error: 'Invalid app ID' });
        }

        try {
            await config.appService.revokeApp(appId);

            // Emit stats update (app count changed)
            await emitCurrentStats();

            return reply.send({ ok: true });
        } catch (error) {
            return sendError(reply, error);
        }
    });

    // Update app (description and/or trust level) (PATCH - needs CSRF)
    fastify.patch('/apps/:id', { preHandler: [...preHandler.auth, ...preHandler.csrf] }, async (request: FastifyRequest, reply: FastifyReply) => {
        const params = request.params as { id: string };
        const appId = Number(params.id);

        if (!Number.isFinite(appId)) {
            return reply.code(400).send({ error: 'Invalid app ID' });
        }

        const body = request.body as { description?: string; trustLevel?: TrustLevel };
        const description = body?.description?.trim();
        const trustLevel = body?.trustLevel;

        if (!description && !trustLevel) {
            return reply.code(400).send({ error: 'Nothing to update' });
        }

        try {
            if (description) {
                await config.appService.updateDescription(appId, description);
            }
            if (trustLevel) {
                await config.appService.updateTrustLevel(appId, trustLevel);
            }
            return reply.send({ ok: true });
        } catch (error) {
            return sendError(reply, error);
        }
    });
}
