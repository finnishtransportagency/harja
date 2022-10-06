(ns harja.kyselyt.geometriapaivitykset
  "Geometriapäivityksiin liittyvät tietokantakyselyt"
  (:require [taoensso.timbre :as log]
            [jeesql.core :refer [defqueries]]
            [harja.pvm :as pvm]
            [clj-time.core :as t]
            [clj-time.coerce :as time-coerce]
            [harja.kyselyt.geometriaaineistot :as geometria-aineistot]
            [harja.domain.geometriaaineistot :as ga]))

(defqueries "harja/kyselyt/geometriapaivitykset.sql"
            {:positional? true})

(defn pitaako-paivittaa? [db paivitystunnus]
  (let [aineisto (geometria-aineistot/hae-voimassaoleva-geometria-aineisto db paivitystunnus)
        paivityksen-tiedot (first (harja.kyselyt.geometriapaivitykset/hae-paivitys db paivitystunnus))
        viimeisin-paivitys (:viimeisin_paivitys paivityksen-tiedot)
        seuraava-paivitys (:seuraava_paivitys paivityksen-tiedot)
        aja-paikallinen-paivitys (:kayta_paikallista_tiedostoa paivityksen-tiedot)
        ei-ajeta (:ei_ajeta paivityksen-tiedot)]

       (if ei-ajeta
         (log/warn (format "Geometriapäivitystä %s ei ajeta lainkaan. Päivitä geometriapaivitys-taulun tiedot, jos päivitys täytyy ajaa." paivitystunnus))
         (do
           (log/debug (format "Geometriapäivitys: %s on päivitetty viimeksi: %s. Seuraava päivitysajankohta: %s." paivitystunnus viimeisin-paivitys seuraava-paivitys))
           ;; Jos aineiston seuraava päivitysajankohta on määrittelemättä tai jos se on menneisyydessä, päivitä aineisto.
           ;; Päivitä geometria-aineisto-taulussa määritellyt geometriat riippumatta päivitysmäärityksistä myös silloin, kun niitä ei ole vielä kertaakaan päivitetty voimassaolon aikana.
           (when (or (nil? seuraava-paivitys)
                     (pvm/ennen?
                       (time-coerce/from-sql-time seuraava-paivitys)
                       (t/now))
                     (and
                       aineisto
                       (::ga/voimassaolo-alkaa aineisto)
                       (::ga/voimassaolo-paattyy aineisto)
                       (not (pvm/valissa?
                              (t/now)
                              (time-coerce/from-sql-time (::ga/voimassaolo-alkaa aineisto))
                              (time-coerce/from-sql-time (::ga/voimassaolo-paattyy aineisto))
                              false)))
                     )
                 (if aja-paikallinen-paivitys
                   :paikallinen
                   :palvelimelta))))))

(defn harjan-verkon-pvm [db]
  (or (hae-karttapvm db) (pvm/nyt)))
