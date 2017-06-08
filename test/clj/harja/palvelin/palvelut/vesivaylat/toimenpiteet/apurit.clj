(ns harja.palvelin.palvelut.vesivaylat.toimenpiteet.apurit
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja
             [pvm :as pvm]
             [testi :refer :all]]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.yllapito-toteumat :refer :all]
            [harja.tyokalut.functor :refer [fmap]]
            [taoensso.timbre :as log]
            [clojure.string :as str]
            [harja.palvelin.palvelut.vesivaylat.toimenpiteet.yksikkohintaiset :as yks]))

(defn hae-kokonaishintaiset-toimenpide-idt []
  (set (map :id
            (q-map "SELECT id FROM reimari_toimenpide
                    WHERE hintatyyppi = 'kokonaishintainen'"))))

(defn hae-yksikkohintaiset-toimenpide-idt []
  (set (map :id
            (q-map "SELECT id FROM reimari_toimenpide
                    WHERE hintatyyppi = 'yksikkohintainen'"))))

(defn hae-toimenpiteiden-tyyppi [idt]
  (set (map :hintatyyppi
            (q-map "SELECT hintatyyppi FROM reimari_toimenpide
                    WHERE id IN (" (str/join ", " idt) ");"))))
