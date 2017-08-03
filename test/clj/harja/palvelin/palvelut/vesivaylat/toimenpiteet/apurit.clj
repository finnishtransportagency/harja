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
            [clojure.string :as str]))

(defn hae-kokonaishintaiset-toimenpide-idt []
  (set (map :id
            (q-map "SELECT id FROM reimari_toimenpide
                    WHERE hintatyyppi = 'kokonaishintainen'"))))

(defn hae-toimenpiteiden-kiintio-idt [toimenpide-idt]
  (set (keep :kiintio-id (q-map (str "SELECT \"kiintio-id\" FROM reimari_toimenpide
                    WHERE id IN (" (str/join ", " toimenpide-idt) ")")))))

(defn hae-toimenpiteiden-hintaryhma-idt [toimenpide-idt]
  (set (keep :hinnoittelu-id (q-map (str "SELECT \"hinnoittelu-id\" FROM vv_hinnoittelu_toimenpide
                                          WHERE \"toimenpide-id\" IN ( " (str/join ", " toimenpide-idt) ")"
                                         " AND \"hinnoittelu-id\" IN (SELECT id FROM vv_hinnoittelu WHERE hintaryhma IS TRUE AND poistettu IS NOT TRUE)
                                           AND poistettu IS NOT TRUE")))))

(defn hae-toimenpiteiden-omien-hinnoittelujen-idt [toimenpide-idt]
  (set (keep :hinnoittelu-id (q-map (str "SELECT \"hinnoittelu-id\" FROM vv_hinnoittelu_toimenpide
                                          WHERE \"toimenpide-id\" IN ( " (str/join ", " toimenpide-idt) ")"
                                         " AND \"hinnoittelu-id\" IN (SELECT id FROM vv_hinnoittelu WHERE hintaryhma IS FALSE AND poistettu IS NOT FALSE)
                                           AND poistettu IS NOT TRUE")))))

(defn hae-yksikkohintaiset-toimenpide-idt []
  (set (map :id
            (q-map "SELECT id FROM reimari_toimenpide
                    WHERE hintatyyppi = 'yksikkohintainen'"))))

(defn hae-toimenpiteiden-tyyppi [idt]
  (set (map :hintatyyppi
            (q-map "SELECT hintatyyppi FROM reimari_toimenpide
                    WHERE id IN (" (str/join ", " idt) ");"))))
