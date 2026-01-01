import type { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import type { KeyService } from '../../services/index.js';
import { emitCurrentStats } from '../../services/index.js';
import type { PreHandlerFull } from '../types.js';
import { sendError } from '../../lib/route-errors.js';

export interface KeysRouteConfig {
    keyService: KeyService;
}

export function registerKeysRoutes(
    fastify: FastifyInstance,
    config: KeysRouteConfig,
    preHandler: PreHandlerFull
): void {
    // List all keys (GET - no CSRF needed)
    fastify.get('/keys', { preHandler: preHandler.auth }, async (_request: FastifyRequest, reply: FastifyReply) => {
        const keys = await config.keyService.listKeys();
        return reply.send({ keys });
    });

    // Create new key (POST - needs CSRF)
    fastify.post('/keys', { preHandler: [...preHandler.rateLimit, ...preHandler.auth, ...preHandler.csrf] }, async (request: FastifyRequest, reply: FastifyReply) => {
        const body = request.body as { keyName?: string; passphrase?: string; nsec?: string };

        if (!body.keyName) {
            return reply.code(400).send({ error: 'keyName is required' });
        }

        try {
            const key = await config.keyService.createKey({
                keyName: body.keyName,
                passphrase: body.passphrase,
                nsec: body.nsec,
            });

            // Emit stats update (key count changed)
            await emitCurrentStats();

            return reply.send({ ok: true, key });
        } catch (error) {
            return sendError(reply, error);
        }
    });

    // Unlock an encrypted key (POST - needs CSRF)
    fastify.post('/keys/:keyName/unlock', { preHandler: [...preHandler.auth, ...preHandler.csrf] }, async (request: FastifyRequest, reply: FastifyReply) => {
        const { keyName } = request.params as { keyName: string };
        const { passphrase } = request.body as { passphrase?: string };

        if (!passphrase) {
            return reply.code(400).send({ error: 'passphrase is required' });
        }

        try {
            await config.keyService.unlockKey(keyName, passphrase);

            // Emit stats update (active key count changed)
            await emitCurrentStats();

            return reply.send({ ok: true });
        } catch (error) {
            return sendError(reply, error);
        }
    });

    // Set passphrase on an unencrypted key (POST - needs CSRF + rate limit)
    fastify.post('/keys/:keyName/set-passphrase', { preHandler: [...preHandler.rateLimit, ...preHandler.auth, ...preHandler.csrf] }, async (request: FastifyRequest, reply: FastifyReply) => {
        const { keyName } = request.params as { keyName: string };
        const { passphrase } = request.body as { passphrase?: string };

        if (!passphrase || !passphrase.trim()) {
            return reply.code(400).send({ error: 'passphrase is required' });
        }

        try {
            await config.keyService.setPassphrase(keyName, passphrase);
            return reply.send({ ok: true });
        } catch (error) {
            return sendError(reply, error);
        }
    });

    // Rename a key (PATCH - needs CSRF)
    fastify.patch('/keys/:keyName', { preHandler: [...preHandler.auth, ...preHandler.csrf] }, async (request: FastifyRequest, reply: FastifyReply) => {
        const { keyName } = request.params as { keyName: string };
        const { newName } = request.body as { newName?: string };

        if (!newName || !newName.trim()) {
            return reply.code(400).send({ error: 'newName is required' });
        }

        try {
            await config.keyService.renameKey(keyName, newName.trim());
            return reply.send({ ok: true });
        } catch (error) {
            return sendError(reply, error);
        }
    });

    // Delete a key (DELETE - needs CSRF)
    fastify.delete('/keys/:keyName', { preHandler: [...preHandler.rateLimit, ...preHandler.auth, ...preHandler.csrf] }, async (request: FastifyRequest, reply: FastifyReply) => {
        const { keyName } = request.params as { keyName: string };
        const { passphrase } = request.body as { passphrase?: string } ?? {};

        try {
            const result = await config.keyService.deleteKey(keyName, passphrase);

            // Emit stats update (key count and possibly app count changed)
            await emitCurrentStats();

            return reply.send({
                ok: true,
                revokedApps: result.revokedApps,
            });
        } catch (error) {
            return sendError(reply, error);
        }
    });
}
