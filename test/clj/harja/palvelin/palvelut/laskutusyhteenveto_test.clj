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
    (let [payload {:urakka-id @oulun-alueurakan-2014-2019-id
                   :hk_alkupvm (java.sql.Date. 114 9 1)
                   :hk_loppupvm (java.sql.Date. 115 8 30)
                   :aikavali_alkupvm (java.sql.Date. 115 6 1)
                    :aikavali_loppupvm (java.sql.Date. 115 6 30)}
          tiedot (kutsu-palvelua (:http-palvelin jarjestelma)
                                     :hae-laskutusyhteenvedon-tiedot
                                     +kayttaja-jvh+
                                     payload)
          _ (log/debug "tiedot" tiedot)
          tiedot-talvihoito (first (filter #(= (:nimi  %) "Talvihoito") tiedot))
          _ (log/debug "tiedot th" tiedot-talvihoito)
          odotetut [{:nimi "Talvihoito", :kht_laskutettu_hoitokaudella_ennen_aikavalia 31500.0, :kht_laskutetaan_aikavalilla 3500.0, :yht_laskutettu_hoitokaudella_ennen_aikavalia 100.0, :yht_laskutetaan_aikavalilla 20.0}
                    {:nimi "Liikenneympäristön hoito", :kht_laskutettu_hoitokaudella_ennen_aikavalia nil, :kht_laskutetaan_aikavalilla nil, :yht_laskutettu_hoitokaudella_ennen_aikavalia 500.0, :yht_laskutetaan_aikavalilla nil}
                    {:nimi "Soratien hoito", :kht_laskutettu_hoitokaudella_ennen_aikavalia nil, :kht_laskutetaan_aikavalilla nil, :yht_laskutettu_hoitokaudella_ennen_aikavalia nil, :yht_laskutetaan_aikavalilla nil}]
          odotettu-talvihoito (first (filter #(= (:nimi  %) "Talvihoito") odotetut))
          _ (log/debug "talvihoito" odotettu-talvihoito)]
      (is (= tiedot-talvihoito odotettu-talvihoito) "laskutusyhteenvedon-tiedot"))))





