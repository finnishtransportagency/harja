# -- Run-time vaihe --
# Käytetään Eclipse Temurin 17 LTS (OpenJDK) imagea (https://endoflife.date/java)
# Pohjalla Ubuntu Jammy (Ubuntu 22.04 LTS)
FROM eclipse-temurin:17.0.10_7-jre-jammy

# Lisää labeleita AWS ECR:ää varten
LABEL description="Harja app"
LABEL version="1.0"

# https://github.com/aws/amazon-ecs-agent/issues/3594
# Lisää /tmp volumena, jotta voidaan käyttää sitä kiertotienä ECS Agent ongelmaan.
# Korjaa ohjeen mukaan 0755 luvat
# https://docs.aws.amazon.com/AmazonECS/latest/developerguide/bind-mounts.html#specify-volume-config
RUN mkdir -p /tmp && chmod 1777 /tmp
VOLUME ["/tmp"]

# Aseta aikavyöhyke
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive TZ=Europe/Helsinki apt-get -y install tzdata

# Asenna apurit
RUN apt-get update && apt-get install -y \
    bash \
    curl \
    jq \
    nano \
    unzip \
    vim \
    telnet \
    && rm -rf /var/lib/apt/lists/*


# Asenna AWS CLI v2
RUN curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip" && \
 unzip awscliv2.zip && \
 ./aws/install && \
 rm -rf awscliv2.zip aws

# Lataa Prometheus Java Agent
ENV PROMETHEUS_JAR_VERSION=0.20.0
# https://repo.maven.apache.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/0.20.0/jmx_prometheus_javaagent-0.20.0.jar.sha1
ENV PROMETHEUS_JAR_SHA1="7b8a98e3482cee8889698ef391b85c47a3c4ce5b"
ENV PROMETHEUS_MAVEN_REPO_URL_BASE="https://repo.maven.apache.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent"
RUN curl "$PROMETHEUS_MAVEN_REPO_URL_BASE/${PROMETHEUS_JAR_VERSION}/jmx_prometheus_javaagent-${PROMETHEUS_JAR_VERSION}.jar" \
    -o "jmx_prometheus_javaagent.jar"

# Validoi latauksen checksum
RUN if [ "$PROMETHEUS_JAR_SHA1" != "$(sha1sum jmx_prometheus_javaagent.jar | awk '{print $1}')" ]; then \
        echo "Downloaded JAR SHA1: $(sha1sum jmx_prometheus_javaagent.jar | awk '{print $1}')"; \
        echo "Prometheus jar sha1 values doesn't match! exiting."; \
        exit 1; \
    fi

# Kopioi Prometheus JMX exporter config
COPY ./aws/fargate/jmx-exporter-config.yml /


# Luo Non-root ryhmä ja käyttäjä, jotta sovellus ei pyöri roottina
ARG USERNAME=harja
ARG USER_UID=1000
ARG USER_GID=$USER_UID

RUN groupadd --gid $USER_GID $USERNAME && useradd --uid $USER_UID --gid $USER_GID -m $USERNAME

# Luo hakemisto sovellukselle ja aseta oikeudet
RUN mkdir -p /app && chown -R harja:harja /app

# Aseta default user ja workdir
USER $USERNAME
WORKDIR /app

# Kopioi entrypoint
COPY ./aws/fargate/entrypoint.sh entrypoint.sh

# Kopioi uberjar pipelinesta
# Tässä oleteetaan, että uberjar on jo buildattu ja se on hakemistossa "target"
COPY ./target/harja-0.0.1-SNAPSHOT-standalone.jar harja.jar

# Harja-app tarkastaa tällä hetkellä jotkin asetetut ENV muuttujat .harja_env tiedoston mukaisesti
# harja.palvelin.asetukset/tarkista-ymparisto! -funktiolla
COPY ./aws/fargate/.harja_env .harja_env


# Aseta ENV muuttujat (default-arvot)
# ENV-muuttujien arvoja voidaan tuoda myös ulkopuolelta containeriin infran toimesta.

# Aseta Java agenttien asetukset
# Erottele useampi agentti välilyönnillä:  "-javaagent:/jmx_prometheus_javaagent.jar=7071 -javaagent:/some-other.jar"
ENV HARJA_JAVA_AGENT_OPTS="-javaagent:/jmx_prometheus_javaagent.jar=7071:/jmx-exporter-config.yml"

# Käytä lisäksi -XshowSettings:all, jos haluat nähdä mitä asetuksia käytetään
# Erotele optiot välilyönnillä: "-Xms256m -Xmx2g"
ENV HARJA_JVM_OPTS="-Xmx6g"

#ENV HARJA_LIBS=""

# Aseta portit
EXPOSE 3000 7071

# Aseta entrypoint,
ENTRYPOINT ["./entrypoint.sh"]
