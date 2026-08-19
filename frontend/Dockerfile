# ── build ────────────────────────────────────────────────────────────
FROM node:22-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .

# Vite inlines VITE_* variables at build time, so the Cesium token has to
# be present now — it cannot be injected when the container starts.
ARG VITE_CESIUM_ION_TOKEN=""
ENV VITE_CESIUM_ION_TOKEN=$VITE_CESIUM_ION_TOKEN

RUN npm run build

# ── runtime ──────────────────────────────────────────────────────────
# nginx serves the static bundle and proxies /api and /ws to the backend,
# which keeps everything same-origin (no CORS, and the SockJS/STOMP
# connection works without extra configuration).
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
