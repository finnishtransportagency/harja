(ns harja.palvelin.palvelut.kulut.valikatselmus-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.kulut.valikatselmukset :as valikatselmukset]
            [harja.domain.kulut.valikatselmus :as valikatselmus]
            [harja.pvm :as pvm])
  (:import (clojure.lang ExceptionInfo)))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :db-replica (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :valikatselmus (component/using
                                         (valikatselmukset/->Valikatselmukset)
                                         [:http-palvelin :db :db-replica])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

;; käytettävä urakka oulun-maanteiden-hoitourakan-2019-2024-id
(deftest tee-tavoitehinnan-oikaisu-test
  (let [urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
        vastaus (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2020)]
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tee-tavoitehinnan-oikaisu
                                  +kayttaja-jvh+
                                  {:urakka-id urakka-id
                                   :otsikko "Oikaisu"
                                   :summa 9001
                                   :selite "Maailmanloppu tuli, kesti vähän oletettua kauempaa"}))]
    (is (some? vastaus))
    (is (= (::valikatselmus/summa vastaus) 9001M))))

(deftest oikaisun-teko-epaonnistuu-alkuvuodesta
  (let [urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
        virheellinen-vastaus (try
                               (with-redefs [pvm/nyt #(pvm/luo-pvm 2020 5 20)]
                                 (kutsu-palvelua (:http-palvelin jarjestelma)
                                                 :tee-tavoitehinnan-oikaisu
                                                 +kayttaja-jvh+
                                                 {:urakka-id urakka-id
                                                  :otsikko "Oikaisu"
                                                  :summa 1000
                                                  :selite "Juhannusmenot hidasti"}))
                               (catch Exception e e))]
    (is (= ExceptionInfo (type virheellinen-vastaus)))
    ;; Muista muuttaa päivämäärät, kun ne varmistuvat.
    (is (= (:viesti (:virheet (:data virheellinen-vastaus)))) "Tavoitehinnan oikaisuja saa tehdä ainoastaan aikavälillä 1.9 - 31.12.")))

(deftest virheellisen-oikaisun-teko-epaonnistuu
  (let [urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id]
    (is (thrown? Exception (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2020)]
                             (kutsu-palvelua (:http-palvelin jarjestelma)
                                             :tee-tavoitehinnan-oikaisu
                                             +kayttaja-jvh+
                                             {:urakka-id urakka-id
                                              :otsikko "Oikaisu"
                                              :summa "Kolmesataa"
                                              :selite "Maailmanloppu tuli, kesti vähän oletettua kauempaa"}))))))