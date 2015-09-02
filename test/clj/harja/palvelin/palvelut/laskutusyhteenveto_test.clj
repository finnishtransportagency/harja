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
                  :hk_alkupvm         (java.sql.Date. 114 9 1)
                  :hk_loppupvm        (java.sql.Date. 115 8 30)
                  :aikavali_alkupvm   (java.sql.Date. 115 6 1)
                   :aikavali_loppupvm (java.sql.Date. 115 6 30)}
                 tiedot (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :hae-laskutusyhteenvedon-tiedot
                                        +kayttaja-jvh+
                                        payload)
                 _ (log/debug "tiedot" tiedot)
                 ;                 tiedot-talvihoito (first (filter #(= (:nimi %) "Talvihoito") tiedot))
                 ;_ (log/debug "tiedot th" tiedot-talvihoito)
                 odotetut [{:yht_laskutettu_ind_korotettuna 103.9, :kht_laskutettu 31500.0, :kht_laskutetaan_ind_korotettuna 3706.5, :yht_laskutettu_ind_korotus 3.9, :kht_laskutetaan 3500.0, :kht_laskutettu_ind_korotus 1466.5, :kht_laskutetaan_ind_korotus 206.5, :yht_laskutetaan 20.0, :yht_laskutetaan_ind_korotettuna 21.18, :nimi "Talvihoito", :yht_laskutettu 100.0, :kht_laskutettu_ind_korotettuna 32966.5, :yht_laskutetaan_ind_korotus 1.18}
                           {:yht_laskutettu_ind_korotettuna 519.5, :kht_laskutettu 0.0, :kht_laskutetaan_ind_korotettuna nil, :yht_laskutettu_ind_korotus 19.5, :kht_laskutetaan nil, :kht_laskutettu_ind_korotus 0.0, :kht_laskutetaan_ind_korotus nil, :yht_laskutetaan nil, :yht_laskutetaan_ind_korotettuna nil, :nimi "Liikenneympäristön hoito", :yht_laskutettu 500.0, :kht_laskutettu_ind_korotettuna 0.0, :yht_laskutetaan_ind_korotus nil}
                           {:yht_laskutettu_ind_korotettuna 0.0, :kht_laskutettu 90000.0, :kht_laskutetaan_ind_korotettuna 10590.0, :yht_laskutettu_ind_korotus 0.0, :kht_laskutetaan 10000.0, :kht_laskutettu_ind_korotus 4190.0, :kht_laskutetaan_ind_korotus 590.0, :yht_laskutetaan nil, :yht_laskutetaan_ind_korotettuna nil, :nimi "Soratien hoito", :yht_laskutettu 0.0, :kht_laskutettu_ind_korotettuna 94190.0, :yht_laskutetaan_ind_korotus nil}]

                       ;                  odotettu-talvihoito (first (filter #(= (:nimi %) "Talvihoito") odotetut))
                  ;_ (log/debug "talvihoito" odotettu-talvihoito)
                  ]
      (is (= tiedot odotetut) "laskutusyhteenvedon-tiedot"))))





