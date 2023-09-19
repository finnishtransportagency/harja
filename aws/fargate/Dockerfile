# -- Run-time vaihe --
# Käytetään Eclipse Temurin 11 (OpenJDK) imagea
# Pohjalla Ubuntu Jammy (Ubuntu 22.04 LTS)
FROM eclipse-temurin:18.0.2.1_1-jre-jammy

# Lisää labeleita AWS ECR:ää varten
LABEL description="Harja app"
LABEL version="1.0"

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

COPY ./aws/fargate/entrypoint.sh entrypoint.sh

# Kopioi uberjar pipelinesta
# Tässä oleteetaan, että uberjar on jo buildattu ja se on hakemistossa "target"
COPY ./target/harja-0.0.1-SNAPSHOT-standalone.jar harja.jar

# Kopioi asetusten templaatti
# Asetuksien parametrit ja salaisuudet asetetaan AWS Fargate palvelun kautta ympäristömuuttujina
COPY ./aws/fargate/asetukset.edn asetukset.edn

# Harja-app tarkastaa tällä hetkellä jotkin asetetut ENV muuttujat .harja_env tiedoston mukaisesti
# harja.palvelin.asetukset/tarkista-ymparisto! -funktiolla
COPY ./aws/fargate/.harja_env .harja_env


# Aseta ENV muuttujat (default-arvot)
# ENV-muuttujien varsinaiset arvot tuodaan ulkopuolelta containeriin.

ENV HARJA_JAVA_AGENT=""

# Käytä lisäksi -XshowSettings:all, jos haluat nähdä mitä asetuksia käytetään
ENV HARJA_JVM_OPTS="-Xmx6g"

#ENV HARJA_LIBS=""

# Aseta portti
EXPOSE 3000

# Aseta entrypoint,
ENTRYPOINT ["./entrypoint.sh"]
