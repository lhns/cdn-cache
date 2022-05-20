FROM lolhens/sbt-graal:22.1.0-java11 as builder

COPY . .
ARG CI_VERSION
RUN sbt assembly
RUN cp "$(find server/target/scala-* -type f -name '*.sh.bat')" /tmp/app

FROM openjdk:18

COPY --from=builder /tmp/app /opt/app

ENV CACHE_PATH=/etc/app/cache
RUN mkdir -p "$CACHE_PATH"

CMD exec /opt/app

HEALTHCHECK --interval=15s --timeout=3s --start-period=10s \
  CMD curl -Ssf -- http://localhost:${SERVER_PORT:-8080}/health || exit 1
