(ns harja.integraatio
  (:require [harja.palvelin.integraatiot.sampo.tyokalut :as sampo-tk]
            [harja.palvelin.integraatiot.tloik.tyokalut :as tloik-tk]
            [harja.tyokalut.env :as env]))

;; Jos haluat ajaa paikallisesti ITMF-testejä, tee seuraava:
;; 1. Käynnistä Apache Artemis broker, joka on määritelty .github/docker/docker-compose.yml tiedostoon
;;   $ cd .github/docker && docker-compose up -d --wait activemq-artemis-itmf
;; 2. Odottele, että broker-container on käynnistynyt ja healthy
;; 3. Varmista, että Harjan asetukset.edn:ssä :itmf ei ole :pois-kytketyt-ominaisuudet listalla
;; 3. Aseta REPL:lle seuraavat ympäristömuuttujat:
;;   * HARJA_ITMF_BROKER_PORT=61616
;;   * HARJA_ITMF_BROKER_AI_PORT=8161
;; 4. Käynnistä Harja REPL ja aja JMS-testit
;; Huomioi, että Apache Artemis vaatii aina käyttäjätunnuksen ja salasanan, testeissä ja lokaalikehityksessä käytetään admin/admin.
(def itmf-asetukset {:url (str "tcp://" (env/env "HARJA_ITMF_BROKER_HOST" "localhost") ":" (env/env "HARJA_ITMF_BROKER_PORT" 61626))
                     :kayttaja "admin"
                     :salasana "admin"
                     :tyyppi :activemq
                     :paivitystiheys-ms 3000})

(def integraatio-sampo-asetukset {:lahetysjono-sisaan sampo-tk/+lahetysjono-sisaan+
                                  :kuittausjono-sisaan sampo-tk/+kuittausjono-sisaan+
                                  :lahetysjono-ulos sampo-tk/+lahetysjono-ulos+
                                  :kuittausjono-ulos sampo-tk/+kuittausjono-ulos+
                                  :paivittainen-lahetysaika nil})

(def api-sahkoposti-asetukset {:suora? false
                               :sahkoposti-lahetys-url "/harja/api/sahkoposti/xml"
                               :palvelin "http://localhost:8084"
                               :vastausosoite "harja-ala-vastaa@vayla.fi"})

(def ulkoinen-sahkoposti-asetukset {:suora? true
                                  :palvelin "smtp.gmail.com"
                                  :vastausosoite "example@example.com"})

(def api-sampo-asetukset {:lahetys-url "/sampo/harja"
                          :paivittainen-lahetysaika [16 44 0]
                          :palvelin "http://localhost:8084"
                          :kayttajatunnus "harja"
                          :salasana "salakala"})
