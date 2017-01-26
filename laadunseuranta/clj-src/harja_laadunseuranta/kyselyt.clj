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
  ;; TODO KÄYTÄ lue-pgobject kunhan mergeytyy developpiin.
  ;; ks. https://github.com/finnishtransportagency/harja/commit/ca012d9fbc6a58fd1444716665796a94a855a1cf
  (let [[tie osa et ajr d :as arvot]
        (and osoite
             (str/starts-with? osoite "(")
             (str/ends-with? osoite ")")
             (str/split (str/replace osoite #"\(|\)" "") #","))]
    {:tie (Integer/parseInt tie)
     :aosa (Integer/parseInt osa)
     :aet (Integer/parseInt et)
     :ajorata (Integer/parseInt ajr)
     :etaisyys-gps-pisteesta (Float/parseFloat d)}))

(defn- kasittele-kantamerkinta [db merkinta]
  (let [jatkuvat-vakiohavaintoidt (into #{} (map :id (hae-jatkuvat-vakiohavainto-idt db)))
        geometria (when (:sijainti merkinta)
                    (.getGeometry (:sijainti merkinta)))
        havainnot (when (:havainnot merkinta)
                    (seq (.getArray (:havainnot merkinta))))]
    (as-> merkinta merkinta
          (assoc merkinta :laheiset-tr-osoitteet
                          (mapv lue-laheinen-osoiterivi
                                (.getArray (:laheiset-tr-osoitteet merkinta))))
          (assoc merkinta :tr-osoite (first (sort-by :etaisyys-gps-pisteesta
                                                     (:laheiset-tr-osoitteet merkinta))))
          ;; Jaetaan havainnot pistemäisiin ja jatkuviin
          (assoc merkinta :sijainti [(.x geometria) (.y geometria)]
                          :jatkuvat-havainnot (suodata-jatkuvat-havainnot havainnot jatkuvat-vakiohavaintoidt)
                          :pistemainen-havainto (suodata-pistemainen-havainto havainnot jatkuvat-vakiohavaintoidt))
          (dissoc merkinta :havainnot))))

(defn hae-reitin-merkinnat-tieosoitteilla
  "Hakee reittimerkinnät ja niiden projisoidun tieverkon osoitteet.
   Jokaisella merkinnällä on tieto sijainnin lähimmistä TR-osoitteista
   avaimessa :laheiset-tr-osoitteet. Lähin TR-osoite löytyy avaimesta :tr-osoite.

   Lähimmäksi valittu TR-osoite on suurella todennäköisyydellä se tie, johon
   ajon GPS-piste liittyy. Kuitenkin esimerkiksi ramppien alkuosa saattaa osua
   moottoritielle, jolloin reittiä analysoitaessa täytyy käyttää läheisiä TR-osoitteita
   apuna ja päätellä todellinen ajettu reitti (jatkettiinko motarilla vai siirryttiinkö rampille)."
  [db args]
  (mapv (partial kasittele-kantamerkinta db) (hae-reitin-merkinnat-tieosoitteilla-raw db args)))

(defn hae-vakiohavaintoavaimet [db]
  (into {} (mapv (fn [r] [(keyword (:avain r)) (:id r)])
                 (hae-vakiohavaintojen-avaimet db))))
