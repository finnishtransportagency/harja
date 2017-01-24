(ns harja-laadunseuranta.kyselyt
  (:require [jeesql.core :refer [defqueries]]
            [harja-laadunseuranta.tietokanta :as tietokanta]
            [taoensso.timbre :as log]
            [clojure.string :as str]))

(defqueries "harja_laadunseuranta/kyselyt/kyselyt.sql")

(defn- suodata-jatkuvat-havainnot [havainnot jatkuvat-vakiohavainto-idt]
  (filterv jatkuvat-vakiohavainto-idt havainnot))

(defn- suodata-pistemainen-havainto [havainnot jatkuvat-vakiohavainto-idt]
  (first (filterv (comp not jatkuvat-vakiohavainto-idt) havainnot)))

(defn- lue-laheinen-osoiterivi [osoite]
  "Lukee sprocin palauttaman laheinen_osoiterivi arvon tekstimuodosta, jossa on tie, osa, etaisyys,
  ajorata ja d. \"(18637,1,204,1,8.13394232930644)\""
  [osoite]
  (let [[tie osa et ajr d :as arvot]
        (and osoite
             (str/starts-with? osoite "(")
             (str/ends-with? osoite ")")
             (str/split (str/replace osoite #"\(|\)" "") #","))]
    ;; TODO Testaa miten toimii jos sproc ei löydä mitään.
    {:tie (Integer/parseInt tie)
     :aosa (Integer/parseInt osa)
     :aet (Float/parseFloat et)
     :ajorata (Integer/parseInt ajr)
     :etaisyys-gps-pisteesta (Float/parseFloat d)}))

(defn- kasittele-kantamerkinta [merkinta db]
  (let [jatkuvat-vakiohavaintoidt (into #{} (map :id (hae-jatkuvat-vakiohavainto-idt db)))
        geometria (when (:sijainti merkinta)
                    (.getGeometry (:sijainti merkinta)))
        havainnot (when (:havainnot merkinta)
                    (seq (.getArray (:havainnot merkinta))))]
    (-> merkinta
        (assoc :laheiset-tr-osoitteet (mapv lue-laheinen-osoiterivi
                                           (.getArray (:laheiset-tr-osoitteet merkinta))))
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
