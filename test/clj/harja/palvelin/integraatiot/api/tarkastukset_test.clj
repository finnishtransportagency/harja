(ns harja.palvelin.integraatiot.api.tarkastukset-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.tarkastukset :as api-tarkastukset]
            [harja.palvelin.integraatiot.api.tyokalut.json :as json-tyokalut]
            [com.stuartsierra.component :as component]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [harja.palvelin.komponentit.liitteet :as liitteet])
  (:import (java.util Date)))

(def kayttaja "fastroi")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :liitteiden-hallinta (component/using (liitteet/->Liitteet) [:db])
    :api-pistetoteuma (component/using
                        (api-tarkastukset/->Tarkastukset)
                        [:http-palvelin :db :integraatioloki :liitteiden-hallinta])))

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
    (let [tark (first (q (str "SELECT t.tyyppi, t.havainnot, stm.kiinteys, l.nimi "
                              "  FROM tarkastus t "
                              "       JOIN soratiemittaus stm ON stm.tarkastus=t.id "
                              "       JOIN tarkastus_liite hl ON t.id = hl.tarkastus "
                              "       JOIN liite l ON hl.liite = l.id"
                              " WHERE t.ulkoinen_id = " id
                              "   AND t.luoja = (SELECT id FROM kayttaja WHERE kayttajanimi='" kayttaja "')")))]
      (is (= tark ["soratie" "jotain outoa" 3 "soratietarkastus.jpg"]) (str "Tarkastuksen data tallentunut ok " id)))))

(deftest tallenna-virheellinen-soratietarkastus
  (let [vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/tarkastus/soratietarkastus"] kayttaja portti
                                         (-> "test/resurssit/api/soratietarkastus-virhe.json"
                                             slurp))]
    (is (= 400 (:status vastaus)))
    (is (= "invalidi-json" (some-> vastaus :body json/read-str
                                   (get "virheet") first (get "virhe") (get "koodi"))))))

(deftest tallenna-talvihoitotarkastus
  (let [pvm (Date.)
        id (hae-vapaa-tarkastus-ulkoinen-id)

        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/tarkastus/talvihoitotarkastus"] kayttaja portti
                                         (-> "test/resurssit/api/talvihoitotarkastus.json"
                                             slurp
                                             (.replace "__PVM__" (json-tyokalut/json-pvm pvm))
                                             (.replace "__ID__" (str id))))]

    (is (= 200 (:status vastaus)))
    (is (str/blank? (slurp (:body vastaus))))

    ;; varmistetaan että tarkastus löytyy tietokannasta
    (let [tark (first (q (str "SELECT t.tyyppi, t.havainnot, thm.lumimaara, l.nimi "
                              "  FROM tarkastus t "
                              "       JOIN talvihoitomittaus thm ON thm.tarkastus=t.id "
                              "       JOIN tarkastus_liite hl ON t.id = hl.tarkastus "
                              "       JOIN liite l ON hl.liite = l.id"
                              " WHERE t.ulkoinen_id = " id
                              "   AND t.luoja = (SELECT id FROM kayttaja WHERE kayttajanimi='" kayttaja "')")))]
      (is (= tark ["talvihoito" "jotain talvisen outoa" 15.00M "talvihoitotarkastus.jpg"]) (str "Tarkastuksen data tallentunut ok " id)))))
    
                               
