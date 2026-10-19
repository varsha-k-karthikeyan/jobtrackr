# JobTrackr frontend image: build the Angular app with Node, serve it with nginx.
# Build from this folder:  docker build -t jobtrackr-frontend .
# Run:  docker run -p 4200:80 -e BACKEND_URL=http://backend:8080 jobtrackr-frontend

# ---------- 1. Build: Node 24 (Angular 22 needs ^22.22.3, ^24.15.0 or >=26) ----------
FROM node:24-alpine AS build
WORKDIR /workspace

# Dependencies first, in their own layer; npm ci installs exactly what package-lock.json says
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
# The default configuration is production: hashed file names, budgets enforced, apiBaseUrl = /api/v1
RUN npx ng build --configuration production

# ---------- 2. Serve: nginx ----------
FROM nginx:1.30-alpine

# Templates in /etc/nginx/templates/ go through envsubst at start-up, so the backend address is a run-time
# setting (BACKEND_URL), not baked into the image. Only that variable is substituted, so nginx's own
# $uri, $host... stay untouched.
ENV BACKEND_URL=http://backend:8080
ENV NGINX_ENVSUBST_FILTER=^BACKEND_URL$
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /workspace/dist/jobtrackr-frontend/browser/ /usr/share/nginx/html/

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -q -O /dev/null http://localhost/ || exit 1
