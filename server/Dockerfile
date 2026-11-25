FROM node:22-alpine
WORKDIR /app
COPY package.json server.js ./
COPY seeds.txt* ./
ENV DATA_DIR=/data PORT=8080
EXPOSE 8080
CMD ["node", "server.js"]
