FROM openjdk:17

COPY server/target/scala-*/*.sh.bat ./

ENV CACHE_PATH=/etc/app/cache
RUN mkdir -p "$CACHE_PATH"

CMD exec ./*.sh.bat

HEALTHCHECK --interval=15s --timeout=3s --start-period=10s \
  CMD curl -Ssf -- http://localhost:${SERVER_PORT:-8080}/health || exit 1
