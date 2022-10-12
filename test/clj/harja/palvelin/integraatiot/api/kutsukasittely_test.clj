(ns harja.palvelin.integraatiot.api.kutsukasittely-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.xml-skeemat :as xml-skeemat]
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
                                                 :headers {"oam_remote_user" "yit-rakennus", "origin" "http://localhost:3000"}}
                                                json-skeemat/laatupoikkeaman-kirjaus
                                                json-skeemat/kirjausvastaus
                                                (fn [_]))]
    (is (= 400 (:status vastaus)))
    (is (= {"Content-Type" "application/json" "Access-Control-Allow-Origin" "http://localhost:3000" "Vary" "Origin"} (:headers vastaus)) "CORS-headerit on lisätty palautuvan virhesanoman headereihin.")
    (is (.contains (:body vastaus) "invalidi-json"))))

(deftest huomaa-kutsu-jossa-vaara-content-type
         (let [kutsun-data (IOUtils/toInputStream "{\"asdfasdfa\":234}")
               vastaus (kutsukasittely/kasittele-kutsu
                         (:db jarjestelma)
                         (:integraatioloki jarjestelma)
                         "hae-urakka"
                         {:body kutsun-data
                          :request-method :post
                          :headers {"oam_remote_user" "yit-rakennus", "content-type" "application/x-www-form-urlencoded" "origin" "http://localhost:3000"}}
                         json-skeemat/laatupoikkeaman-kirjaus
                         json-skeemat/kirjausvastaus
                         (fn [_]))]
              (is (= 415 (:status vastaus)))
              (is (= {"Content-Type" "text/plain" "Access-Control-Allow-Origin" "http://localhost:3000" "Vary" "Origin"} (:headers vastaus)) "CORS-headerit on lisätty palautuvan virhesanoman headereihin.")
              (is (.contains (:body vastaus) "kutsu lomakedatan content-typellä"))))

(deftest huomaa-kutsu-jossa-vaara-content-type2
  (let [kutsun-data (IOUtils/toInputStream "{\"asdfasdfa\":234}")
        vastaus (kutsukasittely/kasittele-sampo-kutsu
                  (:db jarjestelma)
                  (:integraatioloki jarjestelma)
                  "/sampo/api/harja"
                  {:body kutsun-data
                   :request-method :post
                   :headers {"oam_remote_user" "yit-rakennus", "content-type" "application/x-www-form-urlencoded" "origin" "http://localhost:3000"}}
                  xml-skeemat/+sampo-kutsu+
                  (fn [_])
                  "sampo")]
    (is (= 415 (:status vastaus)))
    (is (= {"Content-Type" "text/plain" "Access-Control-Allow-Origin" "http://localhost:3000" "Vary" "Origin"} (:headers vastaus)) "CORS-headerit on lisätty palautuvan virhesanoman headereihin.")
    (is (.contains (:body vastaus) "Error: Wrong content type. Please use"))))

(deftest huomaa-kutsu-jossa-tuntematon-kayttaja
         (let [kutsun-data (IOUtils/toInputStream "{\"asdfasdfa\":234}")
               vastaus (kutsukasittely/kasittele-kutsu
                         (:db jarjestelma)
                         (:integraatioloki jarjestelma)
                         "hae-urakka"
                         {:body kutsun-data
                          :request-method :get
                          :headers {"oam_remote_user" "tuntematon", "origin" "http://localhost:3000"}}
                         json-skeemat/laatupoikkeaman-kirjaus
                         json-skeemat/kirjausvastaus
                         (fn [_]))]
              (is (= 403 (:status vastaus)))
              (is (= "http://localhost:3000" (get (:headers vastaus) "Access-Control-Allow-Origin")) "CORS-headerit palautuvat.")
              (is (.contains (:body vastaus) "tuntematon-kayttaja"))))

(deftest testaa-response-headerien-lisaaminen
         (is (= {"Content-Type" "text/plain" "Access-Control-Allow-Origin" "*" "Vary" "Origin"} (kutsukasittely/lisaa-request-headerit-cors {"Content-Type" "text/plain"} nil)) "Palauta asterix, jos Origin on nil.")
         (is (= {"Content-Type" "text/plain" "Access-Control-Allow-Origin" "*" "Vary" "Origin"} (kutsukasittely/lisaa-request-headerit-cors {"Content-Type" "text/plain"} "")) "Palauta asterix, jos Origin on tyhjä.")
         (is (= {"Content-Type" "text/plain" "Access-Control-Allow-Origin" "http://localhost:3000" "Vary" "Origin"} (kutsukasittely/lisaa-request-headerit-cors {"Content-Type" "text/plain"} "http://localhost:3000")) "Palauta Origin, jos Origin on annettu.")
         (is (= {"Content-Type" "application/xml" "Access-Control-Allow-Origin" "*" "Vary" "Origin"} (kutsukasittely/lisaa-request-headerit true nil)) "Palauta xml. Palauta asterix, jos Origin on nil.")
         (is (= {"Content-Type" "application/json" "Access-Control-Allow-Origin" "*" "Vary" "Origin"} (kutsukasittely/lisaa-request-headerit false nil)) "Palauta json. Palauta asterix, jos Origin on nil.")
         (is (= {"Content-Type" "application/xml" "Access-Control-Allow-Origin" "*" "Vary" "Origin"} (kutsukasittely/lisaa-request-headerit true "")) "Palauta xml. Palauta asterix, jos Origin on tyhjä.")
         (is (= {"Content-Type" "application/json" "Access-Control-Allow-Origin" "*" "Vary" "Origin"} (kutsukasittely/lisaa-request-headerit false "")) "Palauta json. Palauta asterix, jos Origin on tyhjä.")
         (is (= {"Content-Type" "application/xml" "Access-Control-Allow-Origin" "http://localhost:3000" "Vary" "Origin"} (kutsukasittely/lisaa-request-headerit true "http://localhost:3000")) "Palauta xml. Palauta Origin, jos Origin on annettu.")
         (is (= {"Content-Type" "application/json" "Access-Control-Allow-Origin" "http://localhost:3000" "Vary" "Origin"} (kutsukasittely/lisaa-request-headerit false "http://localhost:3000")) "Palauta json. Palauta Origin, jos Origin on annettu."))


(deftest testaa-tee-virhevastaus
         (let [vastaus (kutsukasittely/tee-virhevastaus "Voivoi" "Nyt ei onnistunu!" "http://localhost:3000")]
              (is (= "Voivoi" (:status vastaus)) "Status palautuu oikein")
              (is (= {"Content-Type" "application/json", "Access-Control-Allow-Origin" "http://localhost:3000", "Vary" "Origin"} (:headers vastaus)) "Headerit palautuvat oikein")))
