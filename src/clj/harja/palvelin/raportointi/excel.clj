(ns harja.palvelin.raportointi.excel
  "Harja raporttielementtien vienti Excel muotoon"
  (:require [taoensso.timbre :as log]
            [dk.ative.docjure.spreadsheet :as excel])
  (:import (org.apache.poi.ss.util CellReference)))

(defmulti muodosta-excel
  "Muodostaa Excel data annetulle raporttielementille.
  Dispatch tyypin mukaan (vektorin 1. elementti)."
  (fn [elementti workbook]
    (assert (and (vector? elementti)
                 (> (count elementti) 1)
                 (keyword? (first elementti)))
            (str "Raporttielementin on oltava vektori, jonka 1. elementti on tyyppi "
                 "ja muut sen sisältöä, sain: " (pr-str elementti)))
    (first elementti)))


(defn solu [rivi-nro sarake-nro]
  (.formatAsString (CellReference. rivi-nro sarake-nro)))

(defmulti aseta-kaava!
  (fn [[tyyppi &_] _ _ _]
    tyyppi))

(defmethod aseta-kaava! :summa-vasen [[_ alkusarake-nro] cell rivi-nro sarake-nro]
  (.setCellFormula cell
                   (str "SUM("
                        (solu rivi-nro alkusarake-nro)
                        ":"
                        (solu rivi-nro (dec sarake-nro))
                        ")")))

(defmethod muodosta-excel :taulukko [[_ optiot sarakkeet data] workbook]
  (try
    (let [sheet (excel/add-sheet! workbook (:otsikko optiot))
          sarake-tyyli (excel/create-cell-style! workbook {:background :blue
                                                           :font {:color :white}})
          otsikko-rivi (.createRow sheet 0)]
      ;; Luodaan otsikot saraketyylillä
      (dorun
       (map-indexed
        (fn [sarake-nro {:keys [otsikko] :as sarake}]
          (log/info "saraketta taulukkoon " sarake)
          (let [cell (.createCell otsikko-rivi sarake-nro)]
            (excel/set-cell! cell otsikko)
            (excel/set-cell-style! cell sarake-tyyli)))
        sarakkeet))

      (dorun
       (map-indexed
        (fn [rivi-nro rivi]
          (let [rivi-nro (inc rivi-nro)
                [data optiot] (if (map? rivi)
                                [(:rivi rivi) rivi]
                                [rivi {}])
                row (.createRow sheet rivi-nro)]
            (dorun
             (map-indexed
              (fn [sarake-nro sarake]
                (let [cell (.createCell row sarake-nro)
                      lihavoi? (:lihavoi? optiot)
                      tyyli (excel/create-cell-style! workbook
                                                      {:font {:bold lihavoi?}})]

                  (if-let [kaava (:excel sarake)]
                    (aseta-kaava! kaava cell rivi-nro sarake-nro)
                    (excel/set-cell! cell (nth data sarake-nro)))
                  (excel/set-cell-style! cell tyyli)))
              sarakkeet))))
        data)))
    (catch Throwable t
      (log/error t "Virhe Excel muodostamisessa"))))

(defmethod muodosta-excel :raportti [[_ raportin-tunnistetiedot & sisalto] workbook]
  (doseq [elementti sisalto]
    (muodosta-excel elementti workbook))
  (:nimi raportin-tunnistetiedot))

(defmethod muodosta-excel :default [elementti workbook]
  (log/debug "Excel ei tue elementtiä: " elementti)
  nil)
