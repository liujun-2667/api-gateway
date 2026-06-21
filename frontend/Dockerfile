FROM node:20-alpine AS builder
WORKDIR /app

COPY package*.json ./
COPY .npmrc ./
RUN npm install

COPY . .
RUN npx ng build --configuration=production

FROM nginx:alpine AS runtime

COPY --from=builder /app/dist/frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
