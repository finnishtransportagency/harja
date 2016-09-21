(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.tieverkko
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clj-time.periodic :refer [periodic-seq]]
            [chime :refer [chime-at]]
            [harja.kyselyt.tieverkko :as k]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile])
  (:import (com.vividsolutions.jts.geom Coordinate LineString MultiLineString GeometryFactory)
           (com.vividsolutions.jts.geom.impl CoordinateArraySequence)
           (com.vividsolutions.jts.operation.linemerge LineSequencer)))

#_(defn paattele-alkuajorata
  "Päättelee miltä ajoradalta tien osa alkaa. Palauttaa 0 tai 1 sen mukaan kummalla
  ajoradalla osa alkaa. Alkupiste on tien alku tien kasvusuuntaan katsottuna."
  [{g0 :the_geom :as ajr0} {g1 :the_geom :as ajr1}]
  ;; Kun otetaan ensimmäiset pisteet sekä 0 että 1 ajoradoille, jompi kumpi niistä
  ;; on alkupiste.
  ;; Alkupiste on se, joka ei toistu muissa geometrian pisteissä.
  (cond
    ;; Jos jompi kumpi on nil, valitaan toinen
    (nil? ajr0)
    ajr1

    (nil? ajr1)
    ajr0

    ;; Muuten katsotaan kumman alkuosa ei esiinny
    :default
    (let [coords0 (some-> g0 .getCoordinates vec)
          coords1 (some-> g1 .getCoordinates vec)
          alku0 (first coords0)
          alku1 (first coords1)
          esiintymat (frequencies (concat coords0 coords1))]
      (cond
        (= 1 (esiintymat alku0))
        ajr0

        (= 1 (esiintymat alku1))
        ajr1

        :default
        (do (println "EI VOI SANOA, "
                     "esiintymät0: " (esiintymat alku0)
                     "; esiintymät1: " (esiintymat alku1))
            ;; Oleta nolla, vanha toiminta säilyy
            (or ajr0 ajr1))))))

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

(defn jatkuva-line-string
  "Koska tiedetään että viivan viimeinen ja seuraavan ensimmäinen piste
  ovat sama, voidaan linestringit yhdistää helposti yhdeksi."
  [[l & lines]]
  (loop [pisteet (into [] (.getCoordinates l))
         [l & lines] lines]
    (if-not l
      (LineString. (CoordinateArraySequence.
                    (into-array Coordinate pisteet))
                   (GeometryFactory.))
      (recur (into pisteet (drop 1 (.getCoordinates l)))
             lines))))

(defn- yhdista-viivat
  "Yhdistää kaksi multilinestringiä siten, että viiva alkaa ensimmäisen
  multilinestringin osalla. Jos yhdistys ei onnistu siten, että kaikki
  linestringit tulevat käytettyä, palautetaan nil."
  [g0 g1]
  (let [ls0 (line-string-seq g0)
        ls1 (line-string-seq g1)]
    (loop [result [(first ls0)]
           loppupiste (viimeinen-piste (first ls0))
           ls0 (rest ls0)
           ls1 ls1]
      (if (and (empty? ls0) (empty? ls1))
        ;; Molemmat empty, onnistui!
        (jatkuva-line-string result)

        ;; Ei vielä loppu, ota jommasta kummasta seuraava pala, joka
        ;; jatkaa loppupisteestä
        (let [seuraava-ls0 (first ls0)
              seuraava-ls1 (first ls1)]
          (cond
            ;; ls0 jatkaa geometriaa
            (and seuraava-ls0 (= loppupiste (ensimmainen-piste seuraava-ls0)))
            (recur (conj result seuraava-ls0)
                   (viimeinen-piste seuraava-ls0)
                   (rest ls0)
                   ls1)

            ;; ls1 jatkaa geometriaa
            (and seuraava-ls1 (= loppupiste (ensimmainen-piste seuraava-ls1)))
            (recur (conj result seuraava-ls1)
                   (viimeinen-piste seuraava-ls1)
                   ls0
                   (rest ls1))

            ;; Jatkoa ei löydy, tämä viiva ei onnistunut
            :default
            nil))))))

(defn- keraa-geometriat
  "Yhdistää 1-ajorataisen (ajr0) ja 2-ajorataisen halutun suunnan mukaisen osan
  viivat yhdeksi viivaksi. Osasta ei tiedetä kummalla ajoradalle se alkaa, mutta
  koko viiva on kulutettava, joten pitää yrittää molempia."
  [{g0 :the_geom :as ajr0} {g1 :the_geom :as ajr1}]
  (cond
    ;; Jos toinen on nil, valitaan suoraan toinen
    (nil? g0) g1
    (nil? g1) g0

    ;; Muuten yhdistetään viivat molemmin päin
    :default
    (or (yhdista-viivat g0 g1)
        (yhdista-viivat g1 g0))))

(def onnistui (atom 0))
(def ei-onnistu (atom 0))

(defn vie-tieosa [db tie osa osan-geometriat]
  ;; todo: Onko ok, jos rivejä missä tätä tietoa ei ole ei tuoda?

  (let [ajoradat (into {}
                       (map (juxt :ajorata identity))
                       osan-geometriat)
        oikea (keraa-geometriat (ajoradat 0) (ajoradat 1))
        vasen (keraa-geometriat (ajoradat 0) (ajoradat 2))]

    (if (nil? oikea)
      (swap! ei-onnistu inc)
      (swap! onnistui inc))
    (if (nil? oikea)
      (swap! ei-onnistu inc)
      (swap! onnistui inc))

    (k/vie-tien-osan-ajorata! db {:tie tie
                                  :osa osa
                                  :oikea (some-> oikea str)
                                  :vasen (some-> vasen str)})

    ;; Onko nämä enää tarpeellisia?
    #_(doseq [tv osan-geometriat]
      (k/vie-tieverkkotauluun! db (:osoite3 tv) (:tie tv) (:ajorata tv) (:osa tv) (:tiepiiri tv)
                               (:tr_pituus tv)
                               (.toString (:the_geom tv))))

    #_(k/paivita-tr-osan-ajoradat! db {:tie tie :osa osa})))

(defmacro prosessoi [sym items & body]
  `(loop [i# 0
          [~sym & items#] ~items]
     (when ~sym
       (try
         ~@body
         (catch Throwable t#
           (log/error t# "Virhe prosessoinnissa rivillä " i# ", tiedot: " ~sym)
           (throw (ex-info (str "Virhe rivillä " i#)
                           {:data ~sym
                            :exception t#}))))
       (recur (inc i#)
              items#))))

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
