FROM circleci/clojure:openjdk-11-lein

USER root

RUN apt-get update && apt-get install -y --no-install-recommends postgresql-client maven
# RUN apt-get install -y --no-install-recommends sudo zip git gettext-base && \
#   (cd tietokanta && mvn flyway:migrate && sh devdb_testidata.sh --localhost)
# Cleanup
RUN apt-get -y autoremove && \
    apt-get -y autoclean

USER circleci
RUN git clone --recurse-submodules https://github.com/finnishtransportagency/harja.git
RUN cd harja && lein deps && cd tietokanta && (mvn flyway:info > /dev/null 2>&1 || true)
RUN wget https://bitbucket.org/ariya/phantomjs/downloads/phantomjs-2.1.1-linux-x86_64.tar.bz2 && tar -C /home/circleci -jxf phantomjs-2.1.1-linux-x86_64.tar.bz2
ENV PATH="/home/circleci/phantomjs-2.1.1-linux-x86_64/bin:${PATH}"
EXPOSE 3000
COPY sisus.bash /tmp/
ENTRYPOINT ["bash", "/tmp/sisus.bash"]
 CMD ["develop", "help"]
