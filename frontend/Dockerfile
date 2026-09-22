# ---- 构建阶段 ----
FROM node:22-alpine AS build
WORKDIR /build
COPY package.json ./
# 换 npmmirror 源加速并缓存依赖
RUN npm config set registry https://registry.npmmirror.com && npm install
COPY . .
RUN npm run build

# ---- 运行阶段 ----
FROM nginx:alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
