(ns harja.palvelin.integraatiot.api.kutsukasittely-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :as kutsukasittely]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.integraatiot.integraatioloki :refer [->Integraatioloki] :as integraatioloki]
            [com.stuartsierra.component :as component])
  (:import (org.apache.commons.io IOUtils)))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :integraatioloki (component/using (->Integraatioloki nil) [:db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once jarjestelma-fixture)

(deftest huomaa-kutsu-jossa-epavalidia-json-dataa
  (let [
        kutsun-data (IOUtils/toInputStream "{\"asdfasdfa\":234}")
        vastaus (kutsukasittely/kasittele-kutsu (:db jarjestelma)
                                                (:integraatioloki jarjestelma)
                                                "hae-urakka"
                                                {:body kutsun-data
                                                 :request-method :post
                                                 :headers {"oam_remote_user" "yit-rakennus",}}
                                                json-skeemat/laatupoikkeaman-kirjaus
                                                json-skeemat/kirjausvastaus
                                                (fn [_]))]
    (is (= 400 (:status vastaus)))
    (is (.contains (:body vastaus) "invalidi-json"))))

(deftest huomaa-kutsu-jossa-tuntematon-kayttaja
  (let [kutsun-data (IOUtils/toInputStream "{\"asdfasdfa\":234}")
        vastaus (kutsukasittely/kasittele-kutsu
                  (:db jarjestelma)
                  (:integraatioloki jarjestelma)
                  "hae-urakka"
                  {:body kutsun-data
                   :request-method :post
                   :headers {"oam_remote_user" "tuntematon",}}
                  json-skeemat/laatupoikkeaman-kirjaus
                  json-skeemat/kirjausvastaus
                  (fn [_]))]
    (is (= 403 (:status vastaus)))
    (is (.contains (:body vastaus) "tuntematon-kayttaja"))))

(deftest testaa-cors-headerien-lisaaminen
         (is (= {"Content-Type" "text/plain" "Access-Control-Allow-Origin" "*" "Vary" "Origin"} (kutsukasittely/lisaa-cors-headerit {"Content-Type" "text/plain"} nil)) "Palauta asterix, jos Origin on nil.")
         (is (= {"Content-Type" "text/plain" "Access-Control-Allow-Origin" "*" "Vary" "Origin"} (kutsukasittely/lisaa-cors-headerit {"Content-Type" "text/plain"} "")) "Palauta asterix, jos Origin on tyhjä.")
         (is (= {"Content-Type" "text/plain" "Access-Control-Allow-Origin" "http://localhost:3000" "Vary" "Origin"} (kutsukasittely/lisaa-cors-headerit {"Content-Type" "text/plain"} "http://localhost:3000")) "Palauta Origin, jos Origin on annettu."))


