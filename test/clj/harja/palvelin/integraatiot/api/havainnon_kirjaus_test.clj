(ns harja.palvelin.integraatiot.api.havainnon-kirjaus-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.http-palvelin :as http-palvelin]
            [harja.palvelin.komponentit.todennus :as todennus]
            [harja.palvelin.komponentit.tapahtumat :as tapahtumat]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.komponentit.liitteet :as liitteet]
            [com.stuartsierra.component :as component]
            [org.httpkit.client :as http]
            [taoensso.timbre :as log]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [harja.palvelin.integraatiot.api.tyokalut.json :as json-tyokalut]
            [harja.palvelin.integraatiot.api.havainnot :as api-havainnot]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [cheshire.core :as cheshire])
  (:import (java.util Date)
           (java.text SimpleDateFormat)))

(def portti nil)
(def kayttaja "yit-rakennus")
(def urakka nil)

(defn jarjestelma-fixture [testit]
  (alter-var-root #'portti (fn [_] (arvo-vapaa-portti)))
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (apply tietokanta/luo-tietokanta testitietokanta)
                        :klusterin-tapahtumat (component/using
                                                (tapahtumat/luo-tapahtumat)
                                                [:db])

                        :todennus (component/using
                                    (todennus/http-todennus)
                                    [:db :klusterin-tapahtumat])
                        :http-palvelin (component/using
                                         (http-palvelin/luo-http-palvelin portti true)
                                         [:todennus])
                        :integraatioloki (component/using
                                           (integraatioloki/->Integraatioloki nil)
                                           [:db])
                        :liitteiden-hallinta (component/using
                                               (liitteet/->Liitteet)
                                               [:db])
                        :api-havainnot (component/using
                                         (api-havainnot/->Havainnot)
                                         [:http-palvelin :db :liitteiden-hallinta :integraatioloki])))))

  (alter-var-root #'urakka
                  (fn [_]
                    (ffirst (q (str "SELECT id FROM urakka WHERE urakoitsija=(SELECT organisaatio FROM kayttaja WHERE kayttajanimi='" kayttaja "') "
                                    " AND tyyppi='hoito'::urakkatyyppi")))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once jarjestelma-fixture)

(deftest tallenna-tiestotarkastus
  (is true))

(deftest tallenna-havainto
  (let [havainnot-kannassa-ennen-pyyntoa (ffirst (q (str "SELECT COUNT(*) FROM havainto;")))
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/havainto"] kayttaja portti
                                         (-> "test/resurssit/api/havainto.json"
                                             slurp))
        encoodattu-havainto (cheshire/decode (:body vastaus) true)]

    (is (= 200 (:status vastaus)))

    (let [havainnot-kannassa-pyynnon-jalkeen (ffirst (q (str "SELECT COUNT(*) FROM havainto;")))
          liite-id  (ffirst (q (str "SELECT id FROM liite WHERE nimi = 'testihavainto36934853.png';")))
          havainto-id (ffirst (q (str "SELECT id FROM havainto WHERE kohde = 'testikohde36934853';")))
          kommentti-id (ffirst (q (str "SELECT id FROM kommentti WHERE kommentti = 'Testikommentti323353435';")))]
      (log/debug "liite-id: " liite-id)
      (log/debug "havainto-id: " havainto-id)
      (log/debug "kommentti-id: " kommentti-id)

      (is (= (+ havainnot-kannassa-ennen-pyyntoa 1) havainnot-kannassa-pyynnon-jalkeen))
      (is (number? liite-id))
      (is (number? havainto-id))
      (is (number? kommentti-id))

      (u (str "DELETE FROM havainto_liite WHERE havainto = " havainto-id ";"))
      (u (str "DELETE FROM havainto_kommentti WHERE havainto = " havainto-id ";"))
      (u (str "DELETE FROM kommentti WHERE kommentti = 'Testikommentti323353435';"))
      (u (str "DELETE FROM liite WHERE id = " liite-id ";"))
      (u (str "DELETE FROM havainto WHERE kuvaus = 'testihavainto36934853';")))))


(deftest tallenna-havainto-virheellisella-liitteella
  (let [vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/havainto"] kayttaja portti
                                         (-> "test/resurssit/api/havainto-virheellinen-liite.json"
                                             slurp))]
    (is (= 400 (:status vastaus)))))