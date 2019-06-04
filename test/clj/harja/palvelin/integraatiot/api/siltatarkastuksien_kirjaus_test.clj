(ns harja.palvelin.integraatiot.api.siltatarkastuksien-kirjaus-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [harja.palvelin.integraatiot.api.tyokalut.json :as json-tyokalut]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.api.siltatarkastukset :as api-siltatarkastukset]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.liitteet :as liitteet])
  (:import (java.util Date)))

(def kayttaja "destia")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :liitteiden-hallinta (component/using (liitteet/->Liitteet nil) [:db])
    :api-siltatarkastukset (component/using
                             (api-siltatarkastukset/->Siltatarkastukset)
                             [:http-palvelin :db :integraatioloki :liitteiden-hallinta])))

(use-fixtures :once jarjestelma-fixture)

(defn hae-siltatunnukset []
  (q (str "SELECT siltatunnus FROM silta;")))

(deftest tallenna-siltatarkastus
  (let [ulkoinen-id 12345
        siltatunnus (ffirst (hae-siltatunnukset))
        tarkastusaika "2014-01-30T12:00:00Z"
        tarkastaja-etunimi "Martti"
        tarkastaja-sukunimi "Ahtisaari"
        odotettu-kohdetulos (fn [kohde]
                              (let [tulos [(case kohde
                                              ; Alusrakenne
                                              1 "-" 2 "A" 3 "A"
                                              ; Päällysrakenne
                                              4 "B" 5 "A" 6 "A" 7 "A" 8 "A" 9 "B C" 10 "A"
                                              ; Varusteet ja laitteet
                                              11 "A" 12 "A" 13 "A" 14 "A" 15 "A" 16 "A" 17 "A" 18 "A" 19 "D"
                                              ; Siltapaikan rakenteet
                                              20 "A" 21 "A" 22 "A" 23 "A" 24 "A")]]
                              (keep #(when (not= % \space ) (str %)) (first tulos))))
        odotettu-kohteen-lisatieto (fn [kohde]
                                     (case kohde
                                       4 "Kansi likainen" 9 "Saumat lohkeilleet" 19 "Korjattava" nil))
        vastaus-lisays (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/tarkastus/siltatarkastus"] kayttaja portti
                                                (-> "test/resurssit/api/siltatarkastus.json"
                                                    slurp
                                                    (.replace "__ID__" (str ulkoinen-id))
                                                    (.replace "__ETUNIMI__" tarkastaja-etunimi)
                                                    (.replace "__SUKUNIMI__" tarkastaja-sukunimi)
                                                    (.replace "__SILTATUNNUS__" (str siltatunnus))
                                                    (.replace "__TARKASTUSAIKA__" tarkastusaika)))]
    (log/debug "Vastaus: " vastaus-lisays)
    (is (= 200 (:status vastaus-lisays)))
    (let [siltatarkastus-kannassa (first (q (str "SELECT id, ulkoinen_id, tarkastaja, tarkastusaika FROM siltatarkastus WHERE ulkoinen_id = '" ulkoinen-id "';")))
          siltatarkastus-kannassa-id (first siltatarkastus-kannassa)]
      (is (not (nil? siltatarkastus-kannassa)))
      (is (= (nth siltatarkastus-kannassa 1) (str ulkoinen-id)))
      (is (= (nth siltatarkastus-kannassa 2) (str tarkastaja-etunimi " " tarkastaja-sukunimi)))
      (log/debug (str "TARKASTUS KANNASSA " siltatarkastus-kannassa-id))

      (let [kohteet-kannassa (q (str "SELECT kohde, tulos::char[], lisatieto FROM siltatarkastuskohde WHERE siltatarkastus = " siltatarkastus-kannassa-id ";"))]
        (log/debug (str "KOHTEET KANNASSA " kohteet-kannassa))
        (is (= (count kohteet-kannassa) 24))
        (doseq [kohde kohteet-kannassa]
          (is (= (konv/pgarray->vector (second kohde)) (odotettu-kohdetulos (first kohde)))))
        (doseq [kohde kohteet-kannassa]
          (is (= (nth kohde 2) (odotettu-kohteen-lisatieto (first kohde)))))))))

(deftest yrita-tallentaa-virheellinen-siltatarkastus-ilman-kaikkia-kohteita
  (let [ulkoinen-id 666
        siltatunnus (ffirst (hae-siltatunnukset))
        tarkastusaika "2004-01-30T12:00:00Z"
        tarkastaja-etunimi "Simo"
        tarkastaja-sukunimi "Siili"
        vastaus-lisays (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/tarkastus/siltatarkastus"] kayttaja portti
                                                (-> "test/resurssit/api/virheellinen_siltatarkastus_ei_kaikkia_kohteita.json"
                                                    slurp
                                                    (.replace "__ID__" (str ulkoinen-id))
                                                    (.replace "__ETUNIMI__" tarkastaja-etunimi)
                                                    (.replace "__SUKUNIMI__" tarkastaja-sukunimi)
                                                    (.replace "__SILTATUNNUS__" (str siltatunnus))
                                                    (.replace "__TARKASTUSAIKA__" tarkastusaika)))]
    (is (not= 200 (:status vastaus-lisays)))))

(deftest yrita-tallentaa-siltatarkastus-olemattomalle-sillalle
  (let [ulkoinen-id 999
        siltatunnus 1
        tarkastusaika "2004-01-30T12:00:00Z"
        tarkastaja-etunimi "Martti"
        tarkastaja-sukunimi "Ahtisaari"
        vastaus-lisays (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/tarkastus/siltatarkastus"] kayttaja portti
                                                (-> "test/resurssit/api/siltatarkastus.json"
                                                    slurp
                                                    (.replace "__ID__" (str ulkoinen-id))
                                                    (.replace "__ETUNIMI__" tarkastaja-etunimi)
                                                    (.replace "__SUKUNIMI__" tarkastaja-sukunimi)
                                                    (.replace "__SILTATUNNUS__" (str siltatunnus))
                                                    (.replace "__TARKASTUSAIKA__" tarkastusaika)))]
    (is (not= 200 (:status vastaus-lisays)))))

(deftest paivita-siltatarkastus
  (let [ulkoinen-id 787878
        siltatunnus (first (second (hae-siltatunnukset)))
        tarkastusaika "2016-01-30T12:00:00Z"
        tarkastaja-etunimi "Siooo"
        tarkastaja-sukunimi "Silttttttarkaja"
        vastaus-lisays (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/tarkastus/siltatarkastus"] kayttaja portti
                                                (-> "test/resurssit/api/siltatarkastus.json"
                                                    slurp
                                                    (.replace "__ID__" (str ulkoinen-id))
                                                    (.replace "__ETUNIMI__" tarkastaja-etunimi)
                                                    (.replace "__SUKUNIMI__" tarkastaja-sukunimi)
                                                    (.replace "__SILTATUNNUS__" (str siltatunnus))
                                                    (.replace "__TARKASTUSAIKA__" tarkastusaika)))]
    (is (= 200 (:status vastaus-lisays)))
    (let [siltatarkastus-kannassa (first (q (str "SELECT id, ulkoinen_id, tarkastaja, tarkastusaika FROM siltatarkastus WHERE ulkoinen_id = '" ulkoinen-id "';")))]
      (is (not (nil? siltatarkastus-kannassa)))
      (is (= (nth siltatarkastus-kannassa 1) (str ulkoinen-id)))
      (is (= (nth siltatarkastus-kannassa 2) (str tarkastaja-etunimi " " tarkastaja-sukunimi))))

    ;; Nyt Simo huomaa typottaneensa oman nimensä täysin ja korjaa tilanteen
    (let [tarkastaja-etunimi "Simo"
          tarkastaja-sukunimi "Sillantarkastaja"
          vastaus-lisays (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/tarkastus/siltatarkastus"] kayttaja portti
                                                  (-> "test/resurssit/api/siltatarkastus.json"
                                                      slurp
                                                      (.replace "__ID__" (str ulkoinen-id))
                                                      (.replace "__ETUNIMI__" tarkastaja-etunimi)
                                                      (.replace "__SUKUNIMI__" tarkastaja-sukunimi)
                                                      (.replace "__SILTATUNNUS__" (str siltatunnus))
                                                      (.replace "__TARKASTUSAIKA__" tarkastusaika)))]
      (is (= 200 (:status vastaus-lisays)))
      (let [siltatarkastus-kannassa (first (q (str "SELECT id, ulkoinen_id, tarkastaja, tarkastusaika FROM siltatarkastus WHERE ulkoinen_id = '" ulkoinen-id "';")))]
        (is (not (nil? siltatarkastus-kannassa)))
        (is (= (nth siltatarkastus-kannassa 2) (str tarkastaja-etunimi " " tarkastaja-sukunimi)))))))


(deftest poista-siltatarkastus
  (let [ulkoinen-id 787879
        tarkastusaika "2016-02-01T12:00:00Z"
        siltatunnus (first (second (hae-siltatunnukset)))
        vastaus-lisays (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/tarkastus/siltatarkastus"] kayttaja portti
                                                (-> "test/resurssit/api/siltatarkastus.json"
                                                    slurp
                                                    (.replace "__ID__" (str ulkoinen-id))
                                                    (.replace "__ETUNIMI__" "Simo")
                                                    (.replace "__SUKUNIMI__" "Sillantarkastaja")
                                                    (.replace "__SILTATUNNUS__" (str siltatunnus))
                                                    (.replace "__TARKASTUSAIKA__" tarkastusaika)))
        siltatarkastus-kannassa-ennen-poistoa (first (q (str "SELECT id, ulkoinen_id, tarkastaja, tarkastusaika FROM siltatarkastus WHERE poistettu IS NOT TRUE AND ulkoinen_id = '" ulkoinen-id "';")))
        vastaus-poisto (api-tyokalut/delete-kutsu ["/api/urakat/" urakka "/tarkastus/siltatarkastus"] kayttaja portti
                                                  (-> "test/resurssit/api/siltatarkastus-poisto.json"
                                                      slurp
                                                      (.replace "__ID__" (str ulkoinen-id))
                                                      (.replace "__PVM__" (json-tyokalut/json-pvm (Date.)))))]
    (is (not (nil? siltatarkastus-kannassa-ennen-poistoa)))
    (is (= 200 (:status vastaus-poisto)))
    (let [siltatarkastus-kannassa (first (q (str "SELECT id, ulkoinen_id, tarkastaja, tarkastusaika FROM siltatarkastus WHERE poistettu IS NOT TRUE AND ulkoinen_id = '" ulkoinen-id "';")))]
      (is (nil? siltatarkastus-kannassa)))))
