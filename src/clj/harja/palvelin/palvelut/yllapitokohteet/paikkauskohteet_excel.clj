(ns harja.palvelin.palvelut.yllapitokohteet.paikkauskohteet-excel
  "Luetaan paikkauskohteet excelistä tiedot ulos"
  (:require [dk.ative.docjure.spreadsheet :as xls]))

(defn erottele-paikkauskohteet [workbook]
  (let [sivu (first (xls/sheet-seq workbook)) ;; Käsitellään excelin ensimmäinen sivu tai tabi
        ;; Esimerkki excelissä paikkauskohteet alkavat vasta viidenneltä riviltä.
        ;; Me emme voi olla tästä kuitenkaan ihan varmoja, niin luetaan varalta kaikki data excelistä ulos
        raaka-data (xls/select-columns {:A :nro, :B :nimi :C :tie :D :ajorata :E :aosa :F :aet :G :losa
                                        :H :let :I :pituus :J :alkupvm :K :loppupvm :L :tyomenetelma
                                        :M :yksikko :N :suunniteltu-hinta :O :lisatiedot}
                                       sivu)
        ;; Säästetään vain ne rivit, joille on annettu tarpeeksi data
        paikkauskohteet (keep
                          (fn [rivi]
                            (when (and (:nimi rivi)
                                       (:tyomenetelma rivi)
                                       (:yksikko rivi)
                                       (:suunniteltu-hinta rivi)
                                       (number? (:suunniteltu-hinta rivi)))
                              rivi))
                          raaka-data)]
    paikkauskohteet))