(ns harja.palvelin.palvelut.toimenpidekoodit-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.konversio :as konv]
            [clojure.set :as set]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :toimenpidekoodit (component/using
                                        (->Toimenpidekoodit)
                                        [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

;; Varmuuden vuoksi yhdeksi ototetuksi arvoksi vielä tämä kovakoodattuna,
;; ettei maskaudu sellainen bugi, joka johtuisi ao. SQL:ään kohdistuvasta muutoksesta.
(def odotetut-tasojen-lkmt
  {1 6
   2 25
   3 50
   4 360})

(defn- tason-lukumaarat-samat [palvelusta kannasta kovakoodattu taso]
  (= (count (get palvelusta taso)) kannasta (get kovakoodattu taso)))

(deftest toimenpidekoodien-haku-toimii
  (let [[[_ taso1-lkm] [_ taso2-lkm] [_ taso3-lkm] [_ taso4-lkm]] (q "select taso, count(*) from toimenpidekoodi\nWHERE piilota IS NOT TRUE\nGROUP BY taso ORDER BY taso")
        koodit-tasoittain (group-by
                            :taso
                            (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :hae-toimenpidekoodit +kayttaja-jvh+))
        odotetut-avaimet-tasot-1-2-3 #{:id
                                       :nimi
                                       :koodi
                                       :emo
                                       :taso
                                       :luoja
                                       :poistettu}
        odotetut-avaimet-taso-4 (set/union odotetut-avaimet-tasot-1-2-3
                                           #{:voimassaolon-alkuvuosi
                                             :voimassaolon-loppuvuosi
                                             :yksikko
                                             :jarjestys
                                             :hinnoittelu
                                             :tehtavaryhma})]

    (is (tason-lukumaarat-samat koodit-tasoittain taso1-lkm odotetut-tasojen-lkmt 1) "Tason 1 koodien lukumäärä")
    (is (tason-lukumaarat-samat koodit-tasoittain taso2-lkm odotetut-tasojen-lkmt 2) "Tason 2 koodien lukumäärä")
    (is (tason-lukumaarat-samat koodit-tasoittain taso3-lkm odotetut-tasojen-lkmt 3) "Tason 3 koodien lukumäärä")
    (is (tason-lukumaarat-samat koodit-tasoittain taso4-lkm odotetut-tasojen-lkmt 4) "Tason 4 koodien lukumäärä")

    (is (every?
          #(contains? (first (get koodit-tasoittain 1)) %)
          odotetut-avaimet-tasot-1-2-3) "Löytyy oikeat avaimet tasolta 1")
    (is (every?
          #(contains? (first (get koodit-tasoittain 2)) %)
          odotetut-avaimet-tasot-1-2-3) "Löytyy oikeat avaimet tasolta 2")
    (is (every?
          #(contains? (first (get koodit-tasoittain 3)) %)
          odotetut-avaimet-tasot-1-2-3) "Löytyy oikeat avaimet tasolta 3")
    (is (every?
          #(contains? (first (get koodit-tasoittain 4)) %)
          odotetut-avaimet-taso-4) "Löytyy oikeat avaimet tasolta 4")))
