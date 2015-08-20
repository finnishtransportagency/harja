(ns harja.palvelin.integraatiot.api.tarkastukset-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.tarkastukset :as api-tarkastukset]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.http-palvelin :as http-palvelin]
            [harja.palvelin.komponentit.todennus :as todennus]
            [harja.palvelin.komponentit.tapahtumat :as tapahtumat]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.api.tyokalut.json :as json-tyokalut]
            [com.stuartsierra.component :as component]
            [org.httpkit.client :as http]
            [clojure.data.json :as json]
            [taoensso.timbre :as log]
            [clojure.string :as str]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut])
  (:import (java.util Date)
           (java.text SimpleDateFormat)))

(def kayttaja "fastroi")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea kayttaja
                                           :api-pistetoteuma (component/using
                                                              (api-tarkastukset/->Tarkastukset)
                                                              [:http-palvelin :db :integraatioloki])))

(use-fixtures :once jarjestelma-fixture)

(deftest tallenna-tiestotarkastus
  (is true))

(defn hae-vapaa-tarkastus-ulkoinen-id []
  (let [id (rand-int 10000)
        vastaus (q (str "SELECT * FROM tarkastus t WHERE t.ulkoinen_id = " id ";"))]
  (if (empty? vastaus) id (recur))))

(deftest tallenna-soratietarkastus
  (let [pvm (Date.)
        id (hae-vapaa-tarkastus-ulkoinen-id)

        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/tarkastus/soratietarkastus"] kayttaja portti
                           (-> "test/resurssit/api/soratietarkastus.json"
                               slurp
                               (.replace "__PVM__" (json-tyokalut/json-pvm pvm))
                               (.replace "__ID__" (str id))))]

    (is (= 200 (:status vastaus)))
    (is (str/blank? (slurp (:body vastaus))))

    ;; varmistetaan että tarkastus löytyy tietokannasta
    (let [tark (first (q (str "SELECT t.tyyppi, h.kuvaus, stm.kiinteys "
                              "  FROM tarkastus t "
                              "       JOIN havainto h ON t.havainto=h.id "
                              "       JOIN soratiemittaus stm ON stm.tarkastus=t.id "
                              " WHERE t.ulkoinen_id = " id
                              "   AND t.luoja = (SELECT id FROM kayttaja WHERE kayttajanimi='" kayttaja "')")))]
      (is (= tark ["soratie" "jotain outoa" 3]) (str "Tarkastuksen data tallentunut ok " id)))

    (let [t-id (ffirst (q (str "SELECT id FROM tarkastus"
                               " WHERE ulkoinen_id=" id
                               "   AND luoja = (SELECT id FROM kayttaja WHERE kayttajanimi='" kayttaja "')")))
          h-id (ffirst (q (str "SELECT havainto FROM tarkastus WHERE id=" t-id)))]
      (u (str "DELETE FROM soratiemittaus WHERE tarkastus=" t-id))
      (u (str "DELETE FROM tarkastus WHERE id=" t-id))
      (u (str "DELETE FROM havainto WHERE id=" h-id)))))


;; Kommentoitu pois JSON-validoinnin korjauksen ajaksi
#_(deftest tallenna-virheellinen-soratietarkastus
  (let [vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/tarkastus/soratietarkastus"]  kayttaja portti
                           (-> "test/resurssit/api/soratietarkastus-virhe.json"
                               slurp))]
    (is (= 400 (:status vastaus)))
    (is (= "invalidi-json" (some-> vastaus :body json/read-str
                                   (get "virheet") first (get "virhe") (get "koodi"))))))


(deftest tallenna-talvihoitotarkastus
  (let [pvm (Date.)
        id (hae-vapaa-tarkastus-ulkoinen-id)

        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/tarkastus/talvihoitotarkastus"]  kayttaja portti
                           (-> "test/resurssit/api/talvihoitotarkastus.json"
                               slurp
                               (.replace "__PVM__" (json-tyokalut/json-pvm pvm))
                               (.replace "__ID__" (str id))))]

    (is (= 200 (:status vastaus)))
    (is (str/blank? (slurp (:body vastaus))))

    ;; varmistetaan että tarkastus löytyy tietokannasta
    (let [tark (first (q (str "SELECT t.tyyppi, h.kuvaus, thm.lumimaara "
                              "  FROM tarkastus t "
                              "       JOIN havainto h ON t.havainto=h.id "
                              "       JOIN talvihoitomittaus thm ON thm.tarkastus=t.id "
                              " WHERE t.ulkoinen_id = " id
                              "   AND t.luoja = (SELECT id FROM kayttaja WHERE kayttajanimi='" kayttaja "')")))]
      (is (= tark ["talvihoito" "jotain talvisen outoa" 15.00M]) (str "Tarkastuksen data tallentunut ok " id)))

    (let [t-id (ffirst (q (str "SELECT id FROM tarkastus"
                               " WHERE ulkoinen_id=" id
                               "   AND luoja = (SELECT id FROM kayttaja WHERE kayttajanimi='" kayttaja "')")))
          h-id (ffirst (q (str "SELECT havainto FROM tarkastus WHERE id=" t-id)))]
      (u (str "DELETE FROM talvihoitomittaus WHERE tarkastus=" t-id))
      (u (str "DELETE FROM tarkastus WHERE id=" t-id))
      (u (str "DELETE FROM havainto WHERE id=" h-id)))))
    
                               
