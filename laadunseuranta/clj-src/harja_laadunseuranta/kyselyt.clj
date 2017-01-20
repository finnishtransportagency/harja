(ns harja-laadunseuranta.kyselyt
  (:require [jeesql.core :refer [defqueries]]
            [harja-laadunseuranta.tietokanta :as tietokanta]
            [taoensso.timbre :as log]))

(defqueries "harja_laadunseuranta/kyselyt/kyselyt.sql")

(defn- suodata-jatkuvat-havainnot [havainnot jatkuvat-vakiohavainto-idt]
  (filterv jatkuvat-vakiohavainto-idt havainnot))

(defn- suodata-pistemainen-havainto [havainnot jatkuvat-vakiohavainto-idt]
  (first (filterv (comp not jatkuvat-vakiohavainto-idt) havainnot)))

(defn- kasittele-kantamerkinta [merkinta db]
  (let [jatkuvat-vakiohavaintoidt (into #{} (map :id (hae-jatkuvat-vakiohavainto-idt db)))
        geometria (when (:sijainti merkinta)
                    (.getGeometry (:sijainti merkinta)))
        havainnot (when (:havainnot merkinta)
                    (seq (.getArray (:havainnot merkinta))))]
    (-> merkinta
        ;; Jaetaan havainnot pistemäisiin ja jatkuviin
        (assoc :sijainti [(.x geometria) (.y geometria)]
               :jatkuvat-havainnot (suodata-jatkuvat-havainnot havainnot jatkuvat-vakiohavaintoidt)
               :pistemainen-havainto (suodata-pistemainen-havainto havainnot jatkuvat-vakiohavaintoidt))
        (dissoc :havainnot))))

(defn hae-reitin-merkinnat-tieosoitteilla
  "Hakee reittimerkinnät ja niiden projisoidun tieverkon osoitteet."
  [db args]
  (mapv #(kasittele-kantamerkinta % db) (hae-reitin-merkinnat-tieosoitteilla-raw db args)))

(defn hae-vakiohavaintoavaimet [db]
  (into {} (mapv (fn [r] [(keyword (:avain r)) (:id r)])
                 (hae-vakiohavaintojen-avaimet db))))

(defn hae-vakiohavaintoidt [db]
  (into {} (mapv (fn [r] [(:id r) (keyword (:avain r))])
                 (hae-vakiohavaintojen-avaimet db))))
