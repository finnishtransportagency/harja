(ns harja.palvelin.tyokalut.excel-tyokalut
  ;; Tänne voi laittaa mm yksittäisten raporttien funktioita
  
  (:require [taoensso.timbre :as log]
            [harja.fmt :as fmt]
            [dk.ative.docjure.spreadsheet :as excel]
            [harja.domain.raportointi :refer [tee-solu]]
            [harja.palvelin.raportointi.excel :as excel-raportointi])
  (:import (org.apache.poi.ss.util CellRangeAddress)))

(defmethod excel-raportointi/muodosta-excel :tyomaa-laskutusyhteenveto-yhteensa [[_ kyseessa-kk-vali? hoitokausi laskutettu laskutetaan laskutettu-str laskutetaan-str] workbook]
  ;; Muodostaa työmaakokouksen laskutusyhteenvedolle "Laskutus yhteensä" -yhteenvedon 
  ;; Näihin tulee Hoitokauden & Valitun kuukauden otsikot joiden alle arvot annettujen parametrien perusteella

  (let [aiempi-sheet (last (excel/sheet-seq workbook))
        [sheet rivi-nro] [aiempi-sheet (+ 2 (.getLastRowNum aiempi-sheet))]

        tyyli-tiedot {:font {:color :black :size 12 :name "Aria"}}
        tyyli-normaali (excel/create-cell-style! workbook tyyli-tiedot)
        tyyli-otsikko (excel/create-cell-style! workbook (assoc-in tyyli-tiedot [:font :bold] true))

        rivi (.createRow sheet rivi-nro)
        rivin-solu (.createCell rivi 0)
        solu-laskutettu (.createCell rivi 1)
        solu-laskutetaan (.createCell rivi 2)]

    (tee-solu rivin-solu (str "Laskutus yhteensä " hoitokausi) tyyli-otsikko)
    (tee-solu solu-laskutettu laskutettu-str tyyli-otsikko)
    (when kyseessa-kk-vali?
      (tee-solu solu-laskutetaan laskutetaan-str tyyli-otsikko))

    (let [rivi-nro (+ 1 rivi-nro)
          rivi (.createRow sheet rivi-nro)
          solu-laskutettu (.createCell rivi 1)
          solu-laskutetaan (.createCell rivi 2)]
      (tee-solu solu-laskutettu (str (fmt/euro laskutettu)) tyyli-normaali)
      (when kyseessa-kk-vali?
        (tee-solu solu-laskutetaan (str (fmt/euro laskutetaan)) tyyli-normaali))
      
      (dotimes [i 2]
        (.autoSizeColumn sheet i)))))


(defn liikenneyhteenveto-arvo-str [arvot tyyppi avain]
  (str (avain (get arvot tyyppi))))

(defmethod excel-raportointi/muodosta-excel :liikenneyhteenveto [[_ sarakkeiden-arvot] workbook]
  ;; Luodaan tehdyn taulukon loppuun yhteenveto liikennetapahtumista
  (try
    (let [aiempi-sheet (last (excel/sheet-seq workbook))
          [sheet nolla] [aiempi-sheet (+ 2 (.getLastRowNum aiempi-sheet))]

          ;; Rivit sekä ensimmäiset sarakkeet
          sarakkeen-ensimmainen-solu {:toimenpiteet "Toimenpiteet"
                                      :palvelumuoto "Palvelumuoto"}
          ;; Yhteenvetosarakkeet
          sarakkeen_nimet {:toimenpiteet {:sulutukset-ylos "Sulutukset ylös: "
                                          :sulutukset-alas "Sulutukset alas: "
                                          :sillan-avaukset "Sillan avaukset: "
                                          :tyhjennykset "Tyhjennykset: "
                                          :yhteensa "Yhteensä: "}
                           :palvelumuoto {:paikallispalvelu "Paikallispalvelu: "
                                          :kaukopalvelu "Kaukopalvelu: "
                                          :itsepalvelu "Itsepalvelu: "
                                          :muu "Muu: "
                                          :yhteensa "Yhteensä: "}}

          raportin-tiedot-tyyli (excel/create-cell-style! workbook {:font {:color :black
                                                                           :size 12
                                                                           :bold true
                                                                           :name "Aria"}})
          nolla (+ 2 nolla)]

      (doall
        ;; Käydään läpi annettujen parametrien (yhteenveto) avaimet
        (for [x (keys sarakkeiden-arvot)]

          (let [rivi-indeksi (.indexOf (keys sarakkeen-ensimmainen-solu) x)
                nolla (+ nolla rivi-indeksi)
                rivi (.createRow sheet nolla)
                tyyli-normaali (excel/create-cell-style! workbook {:font {:color :black
                                                                          :size 12
                                                                          :bold false
                                                                          :name "Aria"}})
                ensimmainen-sarake (.createCell rivi 0)]

            ;; Tehdään uusi rivi ja ensimmäinen sarake
            (excel/set-cell! ensimmainen-sarake (str (x sarakkeen-ensimmainen-solu)))
            (excel/set-cell-style! ensimmainen-sarake tyyli-normaali)

            ;; Loput sarakkeet
            (doseq [y (get sarakkeiden-arvot x)]

              (let [nimi (liikenneyhteenveto-arvo-str sarakkeen_nimet x (first y))
                    arvo (liikenneyhteenveto-arvo-str sarakkeiden-arvot x (first y))
                    ;; Sarake indeksi (mille sarakkeelle data laitetaan)
                    ;; Avaimet on indeksijärjestyksessä
                    indeksi (inc (.indexOf (keys (x sarakkeen_nimet)) (first y)))
                    solu-nro (dec indeksi)
                    solu (.createCell rivi solu-nro)]

                (excel/set-cell! solu (str nimi arvo))
                (excel/set-cell-style! solu raportin-tiedot-tyyli)

                ;; Korjattu välitys
                (.autoSizeColumn sheet solu-nro)))))))

    (catch Throwable t
      (log/error t "Virhe Excel muodostamisessa (liikenneyhteenveto)"))))
