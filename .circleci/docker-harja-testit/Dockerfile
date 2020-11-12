FROM circleci/clojure:openjdk-11-lein

USER root

RUN wget https://download.java.net/java/GA/jdk11/9/GPL/openjdk-11.0.2_linux-x64_bin.tar.gz
RUN rm -rf /usr/local/openjdk-11
RUN if [ "$(sha256sum openjdk-11.0.2_linux-x64_bin.tar.gz )" != "99be79935354f5c0df1ad293620ea36d13f48ec3ea870c838f20c504c9668b57  openjdk-11.0.2_linux-x64_bin.tar.gz" ]; then exit 1; fi;
RUN tar -C /usr/local/ -zxf openjdk-11.0.2_linux-x64_bin.tar.gz

ENV JAVA_HOME="/usr/local/jdk-11.0.2"
ENV PATH="${JAVA_HOME}/bin:${PATH}"

USER circleci

RUN mkdir ~/opt
RUN wget -O ~/phantomjs-2.1.1-linux-x86_64.tar.bz2 https://bitbucket.org/ariya/phantomjs/downloads/phantomjs-2.1.1-linux-x86_64.tar.bz2
RUN if [ "$(sha512sum ~/phantomjs-2.1.1-linux-x86_64.tar.bz2 )" != "039b62ecf2fa4196357e129727871a53b046948e17b21900c78a1af4d0b523e41b9d4137e7454b0a638333d7fc27e65d14f4af8c665e982a504c12f828525419  /home/circleci/phantomjs-2.1.1-linux-x86_64.tar.bz2" ]; then exit 1; fi;
RUN tar -C ~/opt/ -jxf ~/phantomjs-2.1.1-linux-x86_64.tar.bz2
ENV PATH="/home/circleci/opt/phantomjs-2.1.1-linux-x86_64/bin:${PATH}"
RUN rm ~/phantomjs-2.1.1-linux-x86_64.tar.bz2

RUN cd /tmp; git clone https://github.com/finnishtransportagency/harja.git;
RUN mkdir .harja; \
    echo aaaa > .harja/anti-csrf-token; \
    touch .harja/{mml,google-static-maps-key,turi-salasana,ava-salasana,yha-salasana,labyrintti-salasana};
RUN cd /tmp/harja/; lein deps;
RUN cd /tmp/harja/; lein with-profile test deps;

EXPOSE 3000
COPY sisus.bash /tmp/
ENTRYPOINT ["bash", "/tmp/sisus.bash"]
 CMD ["help", "develop"]
