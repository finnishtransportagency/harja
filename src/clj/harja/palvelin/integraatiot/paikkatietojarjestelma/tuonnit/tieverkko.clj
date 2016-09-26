(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.tieverkko
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clj-time.periodic :refer [periodic-seq]]
            [chime :refer [chime-at]]
            [harja.kyselyt.tieverkko :as k]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]
            [harja.geo :as geo])
  (:import (com.vividsolutions.jts.geom Coordinate LineString MultiLineString GeometryFactory)
           (com.vividsolutions.jts.geom.impl CoordinateArraySequence)
           (com.vividsolutions.jts.operation.linemerge LineSequencer)))

(defn- line-string-seq
  ([multilinestring]
   (line-string-seq 0 (.getNumGeometries multilinestring) multilinestring))
  ([i n multilinestring]
   (lazy-seq
    (cons (.getGeometryN multilinestring i)
          (when (< i (dec n))
            (line-string-seq (inc i) n multilinestring))))))

(defn- ensimmainen-piste [g]
  (let [arr (.getCoordinates g)]
    (aget arr 0)))

(defn- viimeinen-piste [g]
  (let [arr (.getCoordinates g)]
    (aget arr (dec (alength arr)))))

(defn luo-line-string [pisteet]
  (LineString. (CoordinateArraySequence.
                (into-array Coordinate pisteet))
               (GeometryFactory.)))

(defn jatkuva-line-string
  "Koska tiedetään että viivan viimeinen ja seuraavan ensimmäinen piste
  ovat sama, voidaan linestringit yhdistää helposti yhdeksi."
  [[l & lines]]
  (loop [pisteet (into [] (.getCoordinates l))
         [l & lines] lines]
    (if-not l
      (luo-line-string pisteet)
      (recur (into pisteet (drop 1 (.getCoordinates l)))
             lines))))

(defn- coord [c]
  (str "X: " (.x c) ", Y: " (.y c)))

(defn- piste [^Coordinate c]
  [(.x c) (.y c)])

(defn ota-ls-alkupisteella [alkupiste ls]
  (let [loytynyt-ls (some #(when (= alkupiste (ensimmainen-piste %))
                             %)
                          ls)]
    (if-not loytynyt-ls
      [nil ls]
      [loytynyt-ls (filter #(not= loytynyt-ls %) ls)])))

(defn- yhdista-viivat
  "Yhdistää kaksi multilinestringiä siten, että viiva alkaa ensimmäisen
  multilinestringin osalla. Jos yhdistys ei onnistu siten, että kaikki
  linestringit tulevat käytettyä, palautetaan nil."
  [g0 g1 fallback]
  (let [ls0 (line-string-seq g0)
        ls1 (line-string-seq g1)]
    (loop [result [(first ls0)]
           loppupiste (viimeinen-piste (first ls0))
           ls0 (rest ls0)
           ls1 ls1]
      #_(println "ALKU-LOPPU: " (coord (ensimmainen-piste (last result)))
               " -> " (coord (viimeinen-piste (last result))))
      (if (and (empty? ls0) (empty? ls1))
        ;; Molemmat empty, onnistui!
        ;; HUOM: fallbackin ei tarvitse olla tyhjä
        (jatkuva-line-string result)

        ;; Ei vielä loppu, ota jommasta kummasta seuraava pala, joka
        ;; jatkaa loppupisteestä
        (let [seuraava-ls0 (first ls0)
              seuraava-ls1 (first ls1)]
          (cond
            ;; ls0 jatkaa geometriaa
            (and seuraava-ls0 (= loppupiste (ensimmainen-piste seuraava-ls0)))
            (do #_(println "LS0 " (coord loppupiste) " -> " (coord (viimeinen-piste seuraava-ls0)))
                (recur (conj result seuraava-ls0)
                       (viimeinen-piste seuraava-ls0)
                       (rest ls0)
                       ls1))

            ;; ls1 jatkaa geometriaa
            (and seuraava-ls1 (= loppupiste (ensimmainen-piste seuraava-ls1)))
            (do #_(println "LS1 " (coord loppupiste) " -> " (coord (viimeinen-piste seuraava-ls1)))
                (recur (conj result seuraava-ls1)
                       (viimeinen-piste seuraava-ls1)
                       ls0
                       (rest ls1)))

            ;; Last ditch effort: kutsutaan fallbackia etsimään
            ;; jatkopala joko seuraavaan ls0 tai ls1 pätkään.
            ;; fallback on funktio joka ottaa nykyisen loppupisteen
            ;; ja seuraavat alkukohdat
            :default
            (let [fallback-ls (and fallback
                                   (fallback (piste loppupiste)
                                             (some-> seuraava-ls0 ensimmainen-piste piste)
                                             (some-> seuraava-ls1 ensimmainen-piste piste)))]
              (if fallback-ls
                (do #_(println "LSF " (coord loppupiste) " -> " (coord (viimeinen-piste fallback-ls)))
                    (recur (conj result fallback-ls)
                           (viimeinen-piste fallback-ls)
                           ls0
                           ls1))
                nil))))))))

(defn- keraa-geometriat
  "Yhdistää 1-ajorataisen (ajr0) ja 2-ajorataisen halutun suunnan mukaisen osan
  viivat yhdeksi viivaksi. Osasta ei tiedetä kummalla ajoradalle se alkaa, mutta
  koko viiva on kulutettava, joten pitää yrittää molempia."
  [{g0 :the_geom :as ajr0} {g1 :the_geom :as ajr1} fallback]
  ;(println "KERÄTÄÄN GEOM " ajr0 " JA " ajr1)
  (cond
    ;; Jos toinen on nil, valitaan suoraan toinen
    (nil? g0) (jatkuva-line-string (line-string-seq g1))
    (nil? g1) (jatkuva-line-string (line-string-seq g0))

    ;; Muuten yhdistetään viivat molemmin päin
    :default
    (or (yhdista-viivat g0 g1 fallback)
        (yhdista-viivat g1 g0 fallback))))

(defn- alkaen-pisteesta
  "Palauttaa LineString, joka alkaa annetusta [x y] pisteestä. Jos input
  linestring ei sisällä pistettä, palauttaa nil."
  [ls alkupiste]
  (let [coords (drop-while #(not= alkupiste (piste %))
                           (seq (.getCoordinates ls)))]
    (when (> (count coords) 1)
      #_(println "PALAUTETAAN LEIKATTU LS alkupisteella " alkupiste
               ", jossa coords: " coords)
      (luo-line-string coords))))

(defn luo-fallback [{g :the_geom}]
  (fn [alkupiste & _]
    (some #(alkaen-pisteesta % alkupiste)
          (line-string-seq g))))

(defn luo-siltaava-fallback [fallback]
  (fn [alkupiste p1 p2]
    (or (fallback alkupiste p1 p2)
        ;; Ultimate fallback, tehdään ylimääräinen linestring joka siltaa
        ;; tämän pätkän seuraavaan linnuntietä pitkin.
        ;; Otetaan lähempi seuraavista ls0/ls1 pätkistä ja yhdistetään siihen
        (let [et-p1 (some-> p1 (geo/etaisyys alkupiste))
              et-p2 (some-> p2 (geo/etaisyys alkupiste))]
          (println "EI fallbackia, etäisyydet: et-p1: " et-p1 "; et-p2: " et-p2)
          nil))))

(defn vie-tieosa [db tie osa osan-geometriat]
  (let [ajoradat (into {}
                       (map (juxt :ajorata identity))
                       osan-geometriat)
        oikea (or (keraa-geometriat (ajoradat 0) (ajoradat 1)
                                    (luo-fallback (ajoradat 2)))
                  (keraa-geometriat (ajoradat 0) (ajoradat 1)
                                    (luo-siltaava-fallback
                                     (luo-fallback (ajoradat 2)))))
        vasen (keraa-geometriat (ajoradat 0) (ajoradat 2)
                                (luo-fallback (ajoradat 1)))]

    (k/vie-tien-osan-ajorata! db {:tie tie :osa osa :ajorata 1 :geom (some-> oikea str)})
    (k/vie-tien-osan-ajorata! db {:tie tie :osa osa :ajorata 2 :geom (some-> vasen str)})))

(defn vie-tieverkko-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan tieosoiteverkkoa kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [db db]
        (k/tuhoa-tieverkkodata! db)
        (k/tuhoa-tien-osien-ajoradat! db)
        (shapefile/tuo-ryhmiteltyna
         shapefile "TIE"
         (fn [tien-geometriat]
           (let [tien-geometriat tien-geometriat;(filter :osoite3 tien-geometriat)
                 ]
             (let [tie (:tie (first tien-geometriat))]
               (doseq [[osa geometriat] (sort-by first (group-by :osa tien-geometriat))]
                 (vie-tieosa db tie osa geometriat)))))))

      (k/paivita-paloiteltu-tieverkko db)
      (log/debug "Tieosoiteverkon tuonti kantaan valmis."))
    (log/debug "Tieosoiteverkon tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))

;; Tuonnin testaus REPListä:
;; (def db (:db harja.palvelin.main/harja-jarjestelma))
;; (vie-tieverkko-kantaan db "file:shp/Tieosoiteverkko/PTK_tieosoiteverkko.shp")

;; Hae tietyn tien pätkät tarkasteluun:
;; (def t (harja.shp/lue-shapefile "file:shp/Tieosoiteverkko/PTK_tieosoiteverkko.shp"))
;; (def tie110 (into [] (comp (map harja.shp/feature-propertyt) (filter #(= 110 (:tie %)))) (harja.shp/featuret t)))
