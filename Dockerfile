FROM node:boron
WORKDIR /usr/src/harja-gh-pages
COPY . .
EXPOSE 8080
RUN npm install
RUN npm run build
CMD  [ "npm", "run", "serve" ]

