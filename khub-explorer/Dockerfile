## Knowledge Hub Explorer Dockerfile

ARG NODE_VERSION=18
FROM node:${NODE_VERSION}-alpine

ARG KHUB_DIR=/app
ARG FUSEKI_ENDPOINT
ARG FUSEKI_SERVICE
ARG CONFLUENCE_ENDPOINT
ARG CONFLUENCE_EMAIL
ARG CONFLUENCE_TOKEN
ARG LOGGING_ENDPOINT

WORKDIR ${KHUB_DIR}
COPY ./app .

RUN apk add --no-cache util-linux

RUN PERSONAL_UUID=$(uuidgen --sha1 --namespace @dns --name $CONFLUENCE_EMAIL) \
    && echo \
    "{ \
        \"FUSEKI_ENDPOINT\": \"$FUSEKI_ENDPOINT\", \
        \"FUSEKI_SERVICE\": \"$FUSEKI_SERVICE\", \
        \"CONFLUENCE_EMAIL\": \"$CONFLUENCE_EMAIL\", \
        \"CONFLUENCE_TOKEN\": \"$CONFLUENCE_TOKEN\", \
        \"LOGGING_ENDPOINT\": \"$LOGGING_ENDPOINT\", \
        \"PERSONAL_UUID\": \"$PERSONAL_UUID\" \
    }" \
    > "./public/config.json"

RUN sed -i "s~\"proxy\": \"\"~\"proxy\": \"$CONFLUENCE_ENDPOINT\"~" "./package.json"

RUN npm ci

EXPOSE 3000

CMD [ "npm", "start" ]