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
    (is (= (count kohteet-kannassa) 24))))))

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
        tarkastaja-etunimi "Simo"
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
      (is (= (nth siltatarkastus-kannassa 1) (str ulkoinen-id)))
      (is (= (nth siltatarkastus-kannassa 2) (str tarkastaja-etunimi " " tarkastaja-sukunimi))))

  ;; Nyt Simo huomaa virheen tarkastuksessa ja yrittää vierittää vastuun Antti Ahtaajalle
  (let [tarkastaja-etunimi "Antti"
        tarkastaja-sukunimi "Ahtaaja"
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