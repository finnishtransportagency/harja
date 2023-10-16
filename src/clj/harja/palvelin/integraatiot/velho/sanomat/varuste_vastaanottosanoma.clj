(ns harja.palvelin.integraatiot.velho.sanomat.varuste-vastaanottosanoma
  (:require [taoensso.timbre :as log]
            [clj-time.format :as df]
            [harja.geo :as geo]
            [clojure.string :as str])
  (:import (org.joda.time DateTime)))

(def +liikennemerkki-tietolaji+ :tl506)

(defn aika->sql
  "Luo java.sql.Timestamp objektin org.joda.time.DateTime objektista.
   Käyttää UTC aikavyöhykettä.
   Paluttaa nil, jos saa nil."
  [^DateTime dt]
  (when dt
    (clj-time.coerce/to-sql-time dt)))

(defn velho-pvm->pvm
  "Muuttaa Velhon pvm tekstin org.joda.time.DateTime muotoon.  Paluttaa nil, jos saa nil."
  [^String teksti]
  (when teksti
    (df/parse (:date df/formatters) teksti)))

(defn varusteen-lisatieto [konversio-fn tietolaji kohde]
  (when (= (name +liikennemerkki-tietolaji+) tietolaji)
    (let [asetusnumero (get-in kohde [:ominaisuudet :toiminnalliset-ominaisuudet :asetusnumero])
          lakinumero (get-in kohde [:ominaisuudet :toiminnalliset-ominaisuudet :lakinumero])
          lisatietoja (get-in kohde [:ominaisuudet :toiminnalliset-ominaisuudet :lisatietoja])
          merkki (cond
                   (and asetusnumero (nil? lakinumero))
                   (str (konversio-fn "v/vtlm" asetusnumero kohde))

                   (and (nil? asetusnumero) lakinumero)
                   (konversio-fn "v/vtlmln" lakinumero kohde)

                   (and (nil? asetusnumero) (nil? lakinumero))
                   "VIRHE: Liikennemerkin asetusnumero ja lakinumero tyhjiä Tievelhossa"

                   (and asetusnumero lakinumero)
                   "VIRHE: Liikennemerkillä sekä asetusnumero että lakinumero Tievelhossa")]
      (if lisatietoja
        (str merkki ": " lisatietoja)
        merkki))))

(defn varusteen-toteuma [{:keys [version-voimassaolo alkaen paattyen uusin-versio ominaisuudet tekninen-tapahtuma] :as kohde}]
  (let [version-alku (:alku version-voimassaolo)
        version-loppu (:loppu version-voimassaolo)
        toimenpiteet (:toimenpiteet ominaisuudet)]
    (cond (< 1 (count toimenpiteet))
          (do
            ; Kuvittelemme, ettei ole kovin yleistä, että yhdessä
            ; varusteen versiossa on monta toimenpidettä
            (log/warn (str "Löytyi varusteversio, jolla on monta toimenpidettä: oid: " (:ulkoinen-oid kohde)
                           " version-alku: " version-alku " toimenpiteet(suodatettu): (" (str/join ", " (map #(str "\"" % "\"") toimenpiteet))
                           ") Otimme vain 1. toimenpiteen talteen."))
            (first toimenpiteet))

          (= 1 (count toimenpiteet))
          (first toimenpiteet)

          (= 0 (count toimenpiteet))
          ; Varusteiden lisäys, poisto ja muokkaus eivät ole toimenpiteitä Velhossa. Harjassa ne ovat.
          (cond (= "tekninen-tapahtuma/tt01" tekninen-tapahtuma) "Tieosoitemuutos"
                (= "tekninen-tapahtuma/tt02" tekninen-tapahtuma) "Muu tekninen toimenpide"
                (and (nil? version-voimassaolo) paattyen) "Poistettu" ; Sijaintipalvelu ei palauta versioita
                (and (nil? version-voimassaolo) (not paattyen)) "Lisätty"
                (= alkaen version-alku) "Lisätty"           ; varusteen syntymäpäivä, onnea!
                (and uusin-versio (some? version-loppu)) "Poistettu" ; uusimmalla versiolla on loppu
                :else "Päivitetty"))))

(defn velhogeo->harjageo [geo]
  (let [tyyppi (get {"MultiLineString" :multiline
                     "MultiPoint" :multipoint
                     "LineString" :line
                     "Point" :point}
                    (:type geo))]
    (when geo
      (cond
        (= :point tyyppi)
        (-> {:coordinates (:coordinates geo) :type tyyppi}
            (geo/clj->pg)
            (geo/geometry))

        (= :line tyyppi)
        (-> {:points (:coordinates geo) :type tyyppi}
            (geo/clj->pg)
            (geo/geometry))

        (= :multiline tyyppi)
        (-> {:lines (map
                      (fn [p] {:type :line :points p})
                      (:coordinates geo)) :type tyyppi}
            (geo/clj->pg)
            (geo/geometry))

        (= :multipoint tyyppi)
        (-> {:coordinates (mapv
                      (fn [p] {:type :point :coordinates p})
                      (:coordinates geo)) :type tyyppi}
            (geo/clj->pg)
            (geo/geometry))

        :else
        (assert false (str "Tuntematon geometriatyyppi Velhosta: " geo))))))
