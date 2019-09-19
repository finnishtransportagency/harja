FROM centos:7.6.1810

RUN yum -y install https://download.postgresql.org/pub/repos/yum/reporpms/EL-7-x86_64/pgdg-redhat-repo-latest.noarch.rpm
RUN yum -y install postgresql95 wget bzip2 git which

RUN wget https://download.java.net/java/GA/jdk11/9/GPL/openjdk-11.0.2_linux-x64_bin.tar.gz
RUN if [ "$(sha256sum openjdk-11.0.2_linux-x64_bin.tar.gz )" != "99be79935354f5c0df1ad293620ea36d13f48ec3ea870c838f20c504c9668b57  openjdk-11.0.2_linux-x64_bin.tar.gz" ]; then exit 1; fi;
RUN tar -C /usr/local/ -zxf openjdk-11.0.2_linux-x64_bin.tar.gz

ENV JAVA_HOME="/usr/local/jdk-11.0.2"
ENV PATH="${JAVA_HOME}/bin:${PATH}"

RUN wget -O /usr/local/bin/lein https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein
RUN if [ "$(sha512sum /usr/local/bin/lein )" != "184713b69b545ffb48a0d714100fb5da0f26444299b6bd1b3791d2cd90fd9515b270b9f8087c701314c65126eefd66638c71c65628db8c8436af537a01265091  /usr/local/bin/lein" ]; then exit 1; fi;
RUN chmod a+x /usr/local/bin/lein
RUN lein
RUN wget -O /usr/local/apache-maven-3.6.2-bin.tar.gz http://mirror.netinch.com/pub/apache/maven/maven-3/3.6.2/binaries/apache-maven-3.6.2-bin.tar.gz
RUN if [ "$(sha512sum /usr/local/apache-maven-3.6.2-bin.tar.gz )" != "d941423d115cd021514bfd06c453658b1b3e39e6240969caf4315ab7119a77299713f14b620fb2571a264f8dff2473d8af3cb47b05acf0036fc2553199a5c1ee  /usr/local/apache-maven-3.6.2-bin.tar.gz" ]; then exit 1; fi;
RUN tar -C /usr/local -zxf /usr/local/apache-maven-3.6.2-bin.tar.gz
RUN ln -s /usr/local/apache-maven-3.6.2/bin/mvn /usr/local/bin/mvn
RUN rm /usr/local/apache-maven-3.6.2-bin.tar.gz
RUN wget -O /usr/local/phantomjs-2.1.1-linux-x86_64.tar.bz2 https://bitbucket.org/ariya/phantomjs/downloads/phantomjs-2.1.1-linux-x86_64.tar.bz2
RUN tar -C /usr/local/ -jxf /usr/local/phantomjs-2.1.1-linux-x86_64.tar.bz2
RUN ln -s /usr/local/phantomjs-2.1.1-linux-x86_64/bin/phantomjs /usr/local/bin/phantomjs
RUN rm /usr/local/phantomjs-2.1.1-linux-x86_64.tar.bz2

RUN cd /tmp; git clone https://github.com/finnishtransportagency/harja.git;
RUN cd /tmp/harja/; lein deps;

EXPOSE 3000
COPY sisus.bash /tmp/
ENTRYPOINT ["bash", "/tmp/sisus.bash"]
 CMD ["help", "develop"]
