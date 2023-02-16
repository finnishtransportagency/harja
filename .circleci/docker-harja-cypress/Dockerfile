FROM solita/harja-testit

USER root
RUN curl -fsSL https://deb.nodesource.com/setup_16.x | bash -
RUN apt-get install -y nodejs
RUN npm install npm -g
RUN npm cache clean -f
RUN npm install -g n
RUN n stable

RUN wget -q -O - https://dl-ssl.google.com/linux/linux_signing_key.pub | apt-key add - && \
    echo "deb http://dl.google.com/linux/chrome/deb/ stable main" > /etc/apt/sources.list.d/google.list && \
    apt-get update && \
    apt-get install -y  \
    dbus-x11 \
    google-chrome-stable \
    libgtk2.0-0 \
    libnotify-dev \
    libgconf-2-4 \
    libnss3 \
    libxss1 \
    libasound2 \
    xvfb \
    postgresql-client

RUN apt-get -y autoremove && \
    apt-get -y autoclean && \
    rm -rf /var/lib/apt/lists/*

USER circleci
RUN mkdir -p /tmp/cypress-run
RUN cd /tmp/cypress-run
RUN npm i cypress@^9.1.1
RUN npx cypress verify
