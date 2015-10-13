(ns harja.palvelin.integraatiot.api.siltatarkastuksien-kirjaus-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.api.reittitoteuma :as api-reittitoteuma]
            [harja.palvelin.integraatiot.api.varustetoteuma :as api-varustetoteuma]
            [harja.palvelin.integraatiot.api.siltatarkastukset :as api-siltatarkastukset]))

(def kayttaja "fastroi")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea kayttaja
                                           :api-siltatarkastukset (component/using
                                                               (api-siltatarkastukset/->Siltatarkastukset)
                                                               [:http-palvelin :db :integraatioloki])))

(use-fixtures :once jarjestelma-fixture)

(defn hae-siltanumerot []
  (q (str "SELECT siltanro FROM silta;")))

(deftest tallenna-siltatarkastus
  (let [ulkoinen-id 12345
        siltanumero (ffirst (hae-siltanumerot))
        tarkastusaika "2014-01-30T12:00:00Z"
        tarkastaja-etunimi "Martti"
        tarkastaja-sukunimi "Ahtisaari"
        odotettu-kohdetulos (fn [kohde]
                              (case kohde
                                ; Alusrakenne
                                1 "A"
                                2 "A"
                                3 "A"
                                ; Päällysrakenne
                                4 "B"
                                5 "A"
                                6 "A"
                                7 "A"
                                8 "A"
                                9 "C"
                                10 "A"
                                ; Varusteet ja laitteet
                                11 "A"
                                12 "A"
                                13 "A"
                                14 "A"
                                15 "A"
                                16 "A"
                                17 "A"
                                18 "A"
                                19 "D"
                                ; Siltapaikan rakenteet
                                20 "A"
                                21 "A"
                                22 "A"
                                23 "A"
                                24 "A")
                              )
        odotettu-kohteen-lisatieto (fn [kohde]
                                     (case kohde
                                       4 "Kansi likainen"
                                       9 "Saumat lohkeilleet"
                                       19 "Korjattava"
                                       nil))
        vastaus-lisays (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/tarkastus/siltatarkastus"] kayttaja portti
                                                (-> "test/resurssit/api/siltatarkastus.json"
                                                    slurp
                                                    (.replace "__ID__" (str ulkoinen-id))
                                                    (.replace "__ETUNIMI__" tarkastaja-etunimi)
                                                    (.replace "__SUKUNIMI__" tarkastaja-sukunimi)
                                                    (.replace "__SILTANUMERO__" (str siltanumero))
                                                    (.replace "__TARKASTUSAIKA__" tarkastusaika)))]
    (is (= 200 (:status vastaus-lisays)))
    (let [siltatarkastus-kannassa (first (q (str "SELECT id, ulkoinen_id, tarkastaja, tarkastusaika FROM siltatarkastus WHERE ulkoinen_id = '" ulkoinen-id "';")))
          siltatarkastus-kannassa-id (first siltatarkastus-kannassa)]
    (is (not (nil? siltatarkastus-kannassa)))
    (is (= (nth siltatarkastus-kannassa 1) (str ulkoinen-id)))
    (is (= (nth siltatarkastus-kannassa 2) (str tarkastaja-etunimi " " tarkastaja-sukunimi)))

    (let [kohteet-kannassa (q (str "SELECT kohde, tulos, lisatieto FROM siltatarkastuskohde WHERE siltatarkastus = " siltatarkastus-kannassa-id ";"))]
    (is (= (count kohteet-kannassa) 24))
    (doseq [kohde kohteet-kannassa]
      (is (= (second kohde) (odotettu-kohdetulos (first kohde)))))
    (doseq [kohde kohteet-kannassa]
      (is (= (nth kohde 2) (odotettu-kohteen-lisatieto (first kohde)))))))))

(deftest yrita-tallentaa-virheellinen-siltatarkastus-ilman-kaikkia-kohteita
  (let [ulkoinen-id 666
        siltanumero (ffirst (hae-siltanumerot))
        tarkastusaika "2004-01-30T12:00:00Z"
        tarkastaja-etunimi "Martti"
        tarkastaja-sukunimi "Ahtisaari"
        vastaus-lisays (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/tarkastus/siltatarkastus"] kayttaja portti
                                                (-> "test/resurssit/api/virheellinen_siltatarkastus_ei_kaikkia_kohteita.json"
                                                    slurp
                                                    (.replace "__ID__" (str ulkoinen-id))
                                                    (.replace "__ETUNIMI__" tarkastaja-etunimi)
                                                    (.replace "__SUKUNIMI__" tarkastaja-sukunimi)
                                                    (.replace "__SILTANUMERO__" (str siltanumero))
                                                    (.replace "__TARKASTUSAIKA__" tarkastusaika)))]
    (is (not= 200 (:status vastaus-lisays)))))

(deftest paivita-siltatarkastus
  (let [ulkoinen-id 787878
        siltanumero (first (second (hae-siltanumerot)))
        tarkastusaika "2016-01-30T12:00:00Z"
        tarkastaja-etunimi "Siooo"
        tarkastaja-sukunimi "Silttttttarkaja"
        vastaus-lisays (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/tarkastus/siltatarkastus"] kayttaja portti
                                                (-> "test/resurssit/api/siltatarkastus.json"
                                                    slurp
                                                    (.replace "__ID__" (str ulkoinen-id))
                                                    (.replace "__ETUNIMI__" tarkastaja-etunimi)
                                                    (.replace "__SUKUNIMI__" tarkastaja-sukunimi)
                                                    (.replace "__SILTANUMERO__" (str siltanumero))
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
                                                    (.replace "__SILTANUMERO__" (str siltanumero))
                                                    (.replace "__TARKASTUSAIKA__" tarkastusaika)))]
    (is (= 200 (:status vastaus-lisays)))
    (let [siltatarkastus-kannassa (first (q (str "SELECT id, ulkoinen_id, tarkastaja, tarkastusaika FROM siltatarkastus WHERE ulkoinen_id = '" ulkoinen-id "';")))]
      (is (not (nil? siltatarkastus-kannassa)))
      (is (= (nth siltatarkastus-kannassa 2) (str tarkastaja-etunimi " " tarkastaja-sukunimi)))))))