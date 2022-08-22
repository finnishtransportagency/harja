FROM httpd:2.4

COPY ./my-httpd.conf /usr/local/apache2/conf/httpd.conf
ADD ./luo-tapahtuma.sh /usr/local/apache2/cgi-bin/luo-tapahtuma.sh
RUN mkdir /usr/local/apache2/tapahtumat; \
    chmod 777 /usr/local/apache2/tapahtumat;

RUN chmod +x /usr/local/apache2/cgi-bin/luo-tapahtuma.sh

RUN rm /usr/local/apache2/bin/suexec