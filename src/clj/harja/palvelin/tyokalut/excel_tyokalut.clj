(ns harja.palvelin.tyokalut.excel-tyokalut
  ;; Tänne voi laittaa mm yksittäisten raporttien funktioita
  
  (:require [harja.fmt :as fmt]
            [dk.ative.docjure.spreadsheet :as excel]
            [harja.domain.raportointi :refer [tee-solu]]
            [harja.palvelin.raportointi.excel :as excel-raportointi]))

(defmethod excel-raportointi/muodosta-excel :tyomaa-laskutusyhteenveto-yhteensa [[_ hoitokausi laskutettu laskutetaan laskutettu-str laskutetaan-str] workbook]
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
    (tee-solu solu-laskutetaan laskutetaan-str tyyli-otsikko)

    (let [rivi-nro (+ 1 rivi-nro)
          rivi (.createRow sheet rivi-nro)
          solu-laskutettu (.createCell rivi 1)
          solu-laskutetaan (.createCell rivi 2)]
      (tee-solu solu-laskutettu (str (fmt/euro laskutettu)) tyyli-normaali)
      (tee-solu solu-laskutetaan (str (fmt/euro laskutetaan)) tyyli-normaali))))
