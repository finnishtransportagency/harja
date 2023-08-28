# Ohjeet imagen buildaamiseen ja manuaaliseen ajamiseen työkoneella:
# 1. Build: Aja ./build-image.sh
# 2. Testaa toimintaa lokaalisti:
#    docker run -it --network host \
#     -v /absoluuttinen/polku/harja-repoon/harja/cypress:/cypress ghcr.io/finnishtransportagency/harja_cypress:latest \
#    bash -c "echo '{}' > cypress.json && cypress run --browser chrome -c baseUrl='http://localhost:3000'"
# 2.1. Kopioi cypress.json config  kontin sisälle
# 2.2. aja /root/node_modules/cypress/bin/cypress run --browser chrome
# 3. Testien pitäisi mennä läpi

# Lue ohjeet Dockerfilejen käyttöön Github Actions container jobeissa:
# https://docs.github.com/en/actions/creating-actions/dockerfile-support-for-github-actions

# Käytetään pohjana virallista nodejs Debian imagea.
# Tämänhetkinen Debianin stable-release on versio: 12 (bookworm)
# GH Actions: "It's recommended to use Docker images based on the Debian operating system."

FROM node:18-bookworm-slim

# Yhdistä image harjan repositoryyn
LABEL org.opencontainers.image.source https://github.com/finnishtransportagency/harja

# NPM pakettien versiot Docker build argumentteina
ARG NPM_CYPRESS_VERSION
ARG NPM_TRANSITJS_VERSION

# "Docker actions must be run by the default Docker user (root)."
USER root


###  Asenna Cypressin riippuuvudet ja muut riippuvuudet ###
RUN apt-get update && \
    apt-get install --no-install-recommends -y \
    libgtk2.0-0 \
    libgtk-3-0 \
    libnotify-dev \
    libgconf-2-4 \
    libgbm-dev \
    libnss3 \
    libxss1 \
    libasound2 \
    libxtst6 \
    procps \
    xauth \
    xvfb \
      \
      \
   && apt-get install -y \
    postgresql-client \
    fonts-liberation \
    libcurl4 \
    libcurl3-gnutls \
    libcurl3-nss \
    xdg-utils \
    wget \
    curl \
    nano \
    vim-tiny \
      \
      \
    # Siivoa
    && rm -rf /var/lib/apt/lists/* \
    && apt-get clean


# Asenna libappindicator3-1 - ei sisälly Debian 11:aan
#RUN wget --no-verbose /usr/src/libappindicator3-1_0.4.92-7_amd64.deb "http://ftp.us.debian.org/debian/pool/main/liba/libappindicator/libappindicator3-1_0.4.92-7_amd64.deb" && \
#  dpkg -i /usr/src/libappindicator3-1_0.4.92-7_amd64.deb ; \
#  apt-get install -f -y && \
#  rm -f /usr/src/libappindicator3-1_0.4.92-7_amd64.deb

# Asenna uusin Chrome selain
# Apt-get update täytyy ajaa ennen chromen asennusta, jotta chromen asennuksen yhteydessä löytyy tarvittavat depsut
ADD  "https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb" /usr/src/google-chrome-stable_current_amd64.deb
RUN apt-get update &&  \
    apt-get install -f -y /usr/src/google-chrome-stable_current_amd64.deb ; \
  rm -f /usr/src/google-chrome-stable_current_amd64.deb


### Env muuttujia liittyen NPM pakettien asentamiseen ja cypressin ajamisee ###

# "fake" dbus address to prevent errors
# https://github.com/SeleniumHQ/docker-selenium/issues/87
ENV DBUS_SESSION_BUS_ADDRESS=/dev/null

# NPM asennuksia helpottavia asetuksia
# Väritys
ENV TERM=xterm
# Vähennä npm lokitusta
ENV npm_config_loglevel=warn
# Salli asentaminen, kun käyttäjä on root. GH Actions vaatii ajon root-käyttäjänä, mikäli käytetään container jobia.
ENV npm_config_unsafe_perm=true

# Avoid too many progress messages
# https://github.com/cypress-io/cypress/issues/1243
ENV CI=1 \
# Disable shared memory X11 affecting Cypress v4 and Chrome
# https://github.com/cypress-io/cypress-docker-images/issues/270
  QT_X11_NO_MITSHM=1 \
  _X11_NO_MITSHM=1 \
  _MITSHM=0 \
  # Point Cypress at the /root/cache no matter what user account is used
  # See https://on.cypress.io/caching
  CYPRESS_CACHE_FOLDER=/root/.cache/Cypress \
  # Salli viittaus globaalisti asennettuihin NPM paketteihin
  NODE_PATH=/usr/local/lib/node_modules \
  # Lisää cypress binääri suoraan PATHiin (cypressiä sen tarvitsemien depsien kanssa (transit-js) ei voi asentaa globaalisti)
  # Kierretään ongelma viittaamalla suoraan cypressiin PATH:ssa, jolloin komento on saatavilla kuten globaalisti asennettu
  # cypress.
  PATH=$PATH:/root/node_modules/cypress/bin/

### Cypressin ym. asentaminen ###
# Katso esimerkkiä esim.: https://github.com/cypress-io/cypress-docker-images/blob/master/included/12.3.0/Dockerfile

# Annetaan jokaiselle käyttäjälle lukuoikeus /root home hakemistoon, jonne cypress cachettaa.
RUN ls -la /root && chmod 755 /root

# Asenna build argumentteina annetut versiot cypress ja transitjs paketeista /root/ hakemistoon
# NPM paketit täytyy asentaa root-hakemistoon, koska / hakemistoon NPM ei halua asentaa ja WORKDIR asetusta ei saa
# build vaiheessa määritellä GH Actions docker imageille (lue: https://docs.github.com/en/actions/creating-actions/dockerfile-support-for-github-actions#workdir)
RUN cd root/ \
    && npm install "cypress@${NPM_CYPRESS_VERSION}" \
    && npm install "transit-js@${NPM_TRANSITJS_VERSION}" \
    && cypress verify \
    # Tarkista, että cypress cache ja asennus on /root hakemistossa
    && cypress cache path \
    && cypress cache list \
    && cypress info \
    && cypress version \
    # Tulosta polku mistä Node lataa tarvittavat moduulit
    && node -p 'module.paths'

# Työkalujen versiot
RUN echo  " Node version:    $(node -v) \n" \
    "NPM version:     $(npm -v) \n" \
    "Debian version:  $(cat /etc/debian_version) \n" \
    "Chrome version:  $(google-chrome --version) \n" \
    "User:          $(whoami) \n"
