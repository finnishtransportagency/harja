FROM solita/harjadb:postgis-3.1

RUN yum install -y git

WORKDIR /var/lib/pgsql

COPY dbn-kaynnistys.sh ./dbn-kaynnistys.sh
COPY julkaise-tapahtuma.sh ./julkaise-tapahtuma.sh
COPY odota-tapahtumaa.sh ./odota-tapahtumaa.sh
COPY lue-arvot-ymparistomuuttujiin.sh ./lue-arvot-ymparistomuuttujiin.sh
RUN chmod +x dbn-kaynnistys.sh
RUN chmod +x julkaise-tapahtuma.sh
RUN chmod +x odota-tapahtumaa.sh
RUN chmod +x lue-arvot-ymparistomuuttujiin.sh

USER postgres
