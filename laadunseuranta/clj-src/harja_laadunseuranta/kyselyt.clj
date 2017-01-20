(ns harja-laadunseuranta.kyselyt
  (:require [jeesql.core :refer [defqueries]]
            [harja-laadunseuranta.tietokanta :as tietokanta]
            [taoensso.timbre :as log]))

(defqueries "harja_laadunseuranta/kyselyt/kyselyt.sql")

(def db tietokanta/db)

(def jatkuvat-vakiohavainto-idt (delay (into #{} (map :id (hae-jatkuvat-vakiohavainto-idt @db)))))

(defn- suodata-jatkuvat-havainnot [havainnot]
  (filterv @jatkuvat-vakiohavainto-idt havainnot))

(defn- suodata-pistemainen-havainto [havainnot]
  (first (filterv (comp not @jatkuvat-vakiohavainto-idt) havainnot)))

(defn- kasittele-kantamerkinta [merkinta]
  (let [geometria (when (:sijainti merkinta)
                    (.getGeometry (:sijainti merkinta)))
        havainnot (when (:havainnot merkinta)
                    (seq (.getArray (:havainnot merkinta))))]
    (-> merkinta
        ;; Jaetaan havainnot pistemäisiin ja jatkuviin
        (assoc :sijainti [(.x geometria) (.y geometria)]
               :jatkuvat-havainnot (suodata-jatkuvat-havainnot havainnot)
               :pistemainen-havainto (suodata-pistemainen-havainto havainnot))
        (dissoc :havainnot))))

(defn hae-reitin-merkinnat-tieosoitteilla
  "Hakee reittimerkinnät ja niiden projisoidun tieverkon osoitteet."
  [db args]
  (mapv kasittele-kantamerkinta (hae-reitin-merkinnat-tieosoitteilla-raw db args)))

(defn hae-vakiohavaintojen-kuvaukset [db]
  (into {} (mapv (fn [r] [(keyword (:avain r)) (:nimi r)])
                 (hae-pistemaiset-vakiohavainnot db))))

(defn hae-vakiohavaintoavaimet [db]
  (into {} (mapv (fn [r] [(keyword (:avain r)) (:id r)])
                 (hae-vakiohavaintojen-avaimet db))))

(defn hae-vakiohavaintoidt [db]
  (into {} (mapv (fn [r] [(:id r) (keyword (:avain r))])
                 (hae-vakiohavaintojen-avaimet db))))
