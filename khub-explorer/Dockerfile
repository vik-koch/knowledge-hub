## Knowledge Hub Explorer Dockerfile

ARG NODE_VERSION=18
FROM node:${NODE_VERSION}-alpine

ARG KHUB_DIR=/app
ARG FUSEKI_ENDPOINT
ARG FUSEKI_SERVICE

WORKDIR ${KHUB_DIR}
COPY ./app .

RUN echo \
    "{ \
        \"FUSEKI_ENDPOINT\": \"$FUSEKI_ENDPOINT\", \
        \"FUSEKI_SERVICE\": \"$FUSEKI_SERVICE\" \
    }" \
    > "./config.json"

RUN npm install --global serve

EXPOSE 3000

CMD [ "npx", "serve" ]