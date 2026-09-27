FROM eclipse-temurin:21-jre

WORKDIR /opt/worldgate
ARG VIAPROXY_VERSION=3.4.13
ENV VIAPROXY_VERSION=${VIAPROXY_VERSION}
ENV JAVA_TOOL_OPTIONS="-XX:+UseG1GC -XX:MaxRAMPercentage=75"

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl ca-certificates \
 && rm -rf /var/lib/apt/lists/* \
 && curl -fsSL "https://github.com/ViaVersion/ViaProxy/releases/download/ViaProxy-${VIAPROXY_VERSION}/ViaProxy-${VIAPROXY_VERSION}.jar" -o /opt/worldgate/ViaProxy.jar \
 && mkdir -p /opt/worldgate/plugins /opt/worldgate/run

RUN curl -fsSL "https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest/downloads/viaproxy" \
    -o /opt/worldgate/plugins/Geyser-ViaProxy.jar

COPY start.sh /opt/worldgate/start.sh
RUN chmod +x /opt/worldgate/start.sh

EXPOSE 25568/tcp
EXPOSE 19132/udp

ENTRYPOINT ["/opt/worldgate/start.sh"]
