(ns harja.palvelin.raportointi.excel
  "Harja raporttielementtien vienti Excel muotoon"
  (:require [taoensso.timbre :as log]))

(defmulti muodosta-excel
  "Muodostaa Excel data annetulle raporttielementille.
  Dispatch tyypin mukaan (vektorin 1. elementti)."
  (fn [elementti]
    (assert (and (vector? elementti)
                 (> (count elementti) 1)
                 (keyword? (first elementti)))
            (str "Raporttielementin on oltava vektori, jonka 1. elementti on tyyppi "
                 "ja muut sen sisältöä, sain: " (pr-str elementti)))
    (first elementti)))

(defmethod muodosta-excel :taulukko [[_ optiot sarakkeet data]]
  (concat
   [(with-meta
      (mapv :otsikko sarakkeet)
      {:background :blue
       :font {:color :white}})]
   (map #(if (map? %)
           (:rivi %)
           %) data)))

(defmethod muodosta-excel :raportti [[_ raportin-tunnistetiedot & sisalto]]
  {:nimi (:nimi raportin-tunnistetiedot)
   :rivit (mapcat muodosta-excel sisalto)})

(defmethod muodosta-excel :default [elementti]
  (log/debug "Excel ei tue elementtiä: " elementti)
  nil)
