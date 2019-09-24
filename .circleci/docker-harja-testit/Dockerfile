FROM circleci/clojure:openjdk-11-lein

USER root

RUN echo "deb http://apt.postgresql.org/pub/repos/apt/ stretch-pgdg main" >> /etc/apt/sources.list.d/pgdg.list
RUN wget --quiet -O - https://www.postgresql.org/media/keys/ACCC4CF8.asc | sudo apt-key add -
RUN apt-get update && apt-get install -y postgresql-9.5

RUN wget https://download.java.net/java/GA/jdk11/9/GPL/openjdk-11.0.2_linux-x64_bin.tar.gz
RUN rm -rf /usr/local/openjdk-11
RUN if [ "$(sha256sum openjdk-11.0.2_linux-x64_bin.tar.gz )" != "99be79935354f5c0df1ad293620ea36d13f48ec3ea870c838f20c504c9668b57  openjdk-11.0.2_linux-x64_bin.tar.gz" ]; then exit 1; fi;
RUN tar -C /usr/local/ -zxf openjdk-11.0.2_linux-x64_bin.tar.gz

ENV JAVA_HOME="/usr/local/jdk-11.0.2"
ENV PATH="${JAVA_HOME}/bin:${PATH}"

USER circleci

RUN mkdir ~/opt
RUN wget -O ~/apache-maven-3.6.2-bin.tar.gz http://mirror.netinch.com/pub/apache/maven/maven-3/3.6.2/binaries/apache-maven-3.6.2-bin.tar.gz
RUN if [ "$(sha512sum ~/apache-maven-3.6.2-bin.tar.gz )" != "d941423d115cd021514bfd06c453658b1b3e39e6240969caf4315ab7119a77299713f14b620fb2571a264f8dff2473d8af3cb47b05acf0036fc2553199a5c1ee  /home/circleci/apache-maven-3.6.2-bin.tar.gz" ]; then exit 1; fi;
RUN tar -C ~/opt/ -zxf ~/apache-maven-3.6.2-bin.tar.gz
ENV PATH="/home/circleci/opt/apache-maven-3.6.2/bin:${PATH}"
RUN rm ~/apache-maven-3.6.2-bin.tar.gz
RUN wget -O ~/phantomjs-2.1.1-linux-x86_64.tar.bz2 https://bitbucket.org/ariya/phantomjs/downloads/phantomjs-2.1.1-linux-x86_64.tar.bz2
RUN if [ "$(sha512sum ~/phantomjs-2.1.1-linux-x86_64.tar.bz2 )" != "039b62ecf2fa4196357e129727871a53b046948e17b21900c78a1af4d0b523e41b9d4137e7454b0a638333d7fc27e65d14f4af8c665e982a504c12f828525419  /home/circleci/phantomjs-2.1.1-linux-x86_64.tar.bz2" ]; then exit 1; fi;
RUN tar -C ~/opt/ -jxf ~/phantomjs-2.1.1-linux-x86_64.tar.bz2
ENV PATH="/home/circleci/opt/phantomjs-2.1.1-linux-x86_64/bin:${PATH}"
RUN rm ~/phantomjs-2.1.1-linux-x86_64.tar.bz2

RUN cd /tmp; git clone https://github.com/finnishtransportagency/harja.git;
RUN cd /tmp/harja/; lein deps;
RUN cd /tmp/harja/; lein with-profile test deps;
RUN cd /tmp/harja/tietokanta; (mvn flyway:info 2> /dev/null || true)

EXPOSE 3000
COPY sisus.bash /tmp/
ENTRYPOINT ["bash", "/tmp/sisus.bash"]
 CMD ["help", "develop"]
