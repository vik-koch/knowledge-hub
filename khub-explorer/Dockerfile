## Knowledge Hub Explorer Dockerfile

ARG NODE_VERSION=18
FROM node:${NODE_VERSION}-alpine

ARG KHUB_DIR=/app
ARG FUSEKI_ENDPOINT
ARG FUSEKI_SERVICE
ARG CONFLUENCE_ENDPOINT
ARG CONFLUENCE_TOKEN

WORKDIR ${KHUB_DIR}
COPY ./app .

RUN printf \
    "{ \
        \"FUSEKI_ENDPOINT\": \"$FUSEKI_ENDPOINT\", \
        \"FUSEKI_SERVICE\": \"$FUSEKI_SERVICE\", \
        \"CONFLUENCE_TOKEN\": \"$CONFLUENCE_TOKEN\" \
    }" \
    > "./config.json"

RUN sed -i 's/"proxy": ""/"proxy": "$CONFLUENCE_ENDPOINT"/' ./package.json

RUN npm ci
RUN npm run build

EXPOSE 3000

CMD [ "npm", "start" ]