FROM lolhens/sbt-graal:21.2.0-java11 as builder

COPY . .
ARG CI_VERSION
RUN sbt assembly
RUN cp "$(find server/target/scala-* -type f -name '*.sh.bat')" /tmp/app

FROM openjdk:16

COPY --from=builder /tmp/app .

HEALTHCHECK --interval=15s --timeout=3s --start-period=10s \
  CMD curl -Ssf -- http://localhost:${SERVER_PORT:-8080}/health || exit 1

CMD exec ./app
