## Knowledge Hub Explorer Dockerfile

ARG NODE_VERSION=18
FROM node:${NODE_VERSION}-alpine

ARG KHUB_DIR=/app
ARG FUSEKI_ENDPOINT
ARG FUSEKI_SERVICE

WORKDIR ${KHUB_DIR}
COPY ./app .

RUN printf \
    "window.env = { \
        \"REACT_APP_FUSEKI_ENDPOINT\": \"$FUSEKI_ENDPOINT\", \
        \"REACT_APP_FUSEKI_SERVICE\": \"$FUSEKI_SERVICE\" \
    }" \
    > "./env.js"

RUN npm install serve

EXPOSE 3000

CMD [ "npx", "serve" ]