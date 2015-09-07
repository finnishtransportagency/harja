(ns harja.palvelin.palvelut.laskutusyhteenveto_test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.raportit :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.konversio :as konv]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (apply tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :hae-laskutusyhteenvedon-tiedot (component/using
                                                               (->Raportit)
                                                               [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      tietokanta-fixture
                      (compose-fixtures jarjestelma-fixture urakkatieto-fixture)))

(deftest laskutusyhteenvedon-tietojen-haku
  (testing "laskutusyhteenvedon-tietojen-haku"
    (let [payload {:urakka-id          @oulun-alueurakan-2014-2019-id
                   :hk-alkupvm         (java.sql.Date. 114 9 1)
                   :hk-loppupvm        (java.sql.Date. 115 8 30)
                   :aikavali-alkupvm   (java.sql.Date. 115 6 1)
                    :aikavali-loppupvm (java.sql.Date. 115 6 30)}
          haetut-tiedot (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :hae-laskutusyhteenvedon-tiedot
                                        +kayttaja-jvh+
                                        payload)
          _ (log/debug "tiedot" haetut-tiedot)
          odotetut [{:yht_laskutettu_ind_korotettuna 103.9, :suolasakot_laskutetaan 0.0, :sakot_laskutetaan_ind_korotettuna 0.0, :kht_laskutettu 31500.0, :kht_laskutetaan_ind_korotettuna 3706.5, :yht_laskutettu_ind_korotus 3.9, :kht_laskutetaan 3500.0, :sakot_laskutettu_ind_korotettuna 103.9, :kht_laskutettu_ind_korotus 1466.5, :sakot_laskutetaan 0.0, :kht_laskutetaan_ind_korotus 206.5, :yht_laskutetaan 2020.0, :suolasakot_laskutettu_ind_korotettuna 0.0, :suolasakot_laskutetaan_ind_korotus 0.0, :yht_laskutetaan_ind_korotettuna 2139.18, :nimi "Talvihoito", :yht_laskutettu 100.0, :sakot_laskutetaan_ind_korotus 0.0, :suolasakot_laskutettu 0.0, :suolasakot_laskutetaan_ind_korotettuna 0.0, :tuotekoodi "23100", :sakot_laskutettu_ind_korotus 3.9, :sakot_laskutettu 100.0, :kht_laskutettu_ind_korotettuna 32966.5, :suolasakot_laskutettu_ind_korotus 0.0, :yht_laskutetaan_ind_korotus 119.18}
                    {:yht_laskutettu_ind_korotettuna 519.5, :suolasakot_laskutetaan 0.0, :sakot_laskutetaan_ind_korotettuna 700.0, :kht_laskutettu 0.0, :kht_laskutetaan_ind_korotettuna 0.0, :yht_laskutettu_ind_korotus 19.5, :kht_laskutetaan 0.0, :sakot_laskutettu_ind_korotettuna 526.0, :kht_laskutettu_ind_korotus 0.0, :sakot_laskutetaan 700.0, :kht_laskutetaan_ind_korotus 0.0, :yht_laskutetaan 0.0, :suolasakot_laskutettu_ind_korotettuna 0.0, :suolasakot_laskutetaan_ind_korotus 0.0, :yht_laskutetaan_ind_korotettuna 0.0, :nimi "Liikenneympäristön hoito", :yht_laskutettu 500.0, :sakot_laskutetaan_ind_korotus 0.0, :suolasakot_laskutettu 0.0, :suolasakot_laskutetaan_ind_korotettuna 0.0, :tuotekoodi "23110", :sakot_laskutettu_ind_korotus 26.0, :sakot_laskutettu 500.0, :kht_laskutettu_ind_korotettuna 0.0, :suolasakot_laskutettu_ind_korotus 0.0, :yht_laskutetaan_ind_korotus 0.0} {:yht_laskutettu_ind_korotettuna 0.0, :suolasakot_laskutetaan 0.0, :sakot_laskutetaan_ind_korotettuna 0.0, :kht_laskutettu 90000.0, :kht_laskutetaan_ind_korotettuna 10590.0, :yht_laskutettu_ind_korotus 0.0, :kht_laskutetaan 10000.0, :sakot_laskutettu_ind_korotettuna 0.0, :kht_laskutettu_ind_korotus 4190.0, :sakot_laskutetaan 0.0, :kht_laskutetaan_ind_korotus 590.0, :yht_laskutetaan 0.0, :suolasakot_laskutettu_ind_korotettuna 0.0, :suolasakot_laskutetaan_ind_korotus 0.0, :yht_laskutetaan_ind_korotettuna 0.0, :nimi "Soratien hoito", :yht_laskutettu 0.0, :sakot_laskutetaan_ind_korotus 0.0, :suolasakot_laskutettu 0.0, :suolasakot_laskutetaan_ind_korotettuna 0.0, :tuotekoodi "23120", :sakot_laskutettu_ind_korotus 0.0, :sakot_laskutettu 0.0, :kht_laskutettu_ind_korotettuna 94190.0, :suolasakot_laskutettu_ind_korotus 0.0, :yht_laskutetaan_ind_korotus 0.0}]

          ]

      (is (= haetut-tiedot odotetut) "laskutusyhteenvedon-tiedot"))))





