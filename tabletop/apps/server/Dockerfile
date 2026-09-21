# Build in due fasi: l'immagine finale non contiene il compilatore ne' le sorgenti.
FROM node:22-alpine AS build
WORKDIR /app
RUN corepack enable
COPY pnpm-workspace.yaml package.json tsconfig.base.json tsconfig.json ./
COPY packages/shared/package.json packages/shared/
COPY packages/data-access/package.json packages/data-access/
COPY apps/server/package.json apps/server/
RUN pnpm install --frozen-lockfile
COPY packages ./packages
COPY apps/server ./apps/server
RUN pnpm build

FROM node:22-alpine AS run
WORKDIR /app
ENV NODE_ENV=production
# Utente non privilegiato: il server sta su un PC di casa, dentro la rete di famiglia.
RUN addgroup -S tavolo && adduser -S tavolo -G tavolo
# dist/ contiene ora anche le risorse non TypeScript (schema.sql), copiate dallo
# script di build: senza, il server si ferma all'avvio dentro il container.
COPY --from=build --chown=tavolo:tavolo /app/apps/server/dist ./dist
COPY --from=build --chown=tavolo:tavolo /app/node_modules ./node_modules
COPY --from=build --chown=tavolo:tavolo /app/packages ./packages
USER tavolo
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/salute || exit 1
CMD ["node", "dist/index.js"]
