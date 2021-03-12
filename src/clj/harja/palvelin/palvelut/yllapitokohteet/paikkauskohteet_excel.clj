(ns harja.palvelin.palvelut.yllapitokohteet.paikkauskohteet-excel
  "Luetaan paikkauskohteet excelistä tiedot ulos"
  (:require [dk.ative.docjure.spreadsheet :as xls]))

(defn erottele-paikkauskohteet [workbook]
  (let [sivu (first (xls/sheet-seq workbook))               ;; Käsitellään excelin ensimmäinen sivu tai tabi
        ;; Esimerkki excelissä paikkauskohteet alkavat vasta viidenneltä riviltä.
        ;; Me emme voi olla tästä kuitenkaan ihan varmoja, niin luetaan varalta kaikki data excelistä ulos
        raaka-data (xls/select-columns {:A :nro, :B :nimi :C :tie :D :ajorata :E :aosa :F :aet :G :losa
                                        :H :let :I :pituus :J :alkupvm :K :loppupvm :L :tyomenetelma
                                        :M :suunniteltu-maara :N :yksikko :O :suunniteltu-hinta :P :lisatiedot}
                                       sivu)
        ;; TODO: Katsotaan tarviiko tätä, riippuu miten halutaan hoitaa virheiden hallinta
        ;; Säästetään vain ne rivit, joille on annettu tarpeeksi data
        ;; Paitsi että ei, jotta voidaan antaa validointivirheet
        #_#_paikkauskohteet (keep
                          (fn [rivi]
                            (when (and (:nimi rivi)
                                       (:tyomenetelma rivi)
                                       (:yksikko rivi)
                                       (:suunniteltu-hinta rivi)
                                       (number? (:suunniteltu-hinta rivi)))
                              rivi))
                          raaka-data)

        ;; Tämä toimii nykyisellä excel-pohjalla toistaiseksi.
        ;; Katsotaan, millä rivillä otsikkorivi on, oletuksena että sieltä löytyy ainakin "Nro." ja "kohde" otsikot.
        ;; Ja otetaan otsikon jälkeiset rivit, joissa on nimi. Päästetään tässä vaiheessa myös selvästi virheelliset
        ;; rivit läpi, jotta voidaan palauttaa validaatiovirheet.
        otsikko-idx (first (keep-indexed (fn [idx rivi] (when (and (= "Nro." (:nro rivi)) (= "Kohde" (:nimi rivi))) idx)) raaka-data))
        paikkauskohteet (remove #(nil? (:nimi %)) (subvec raaka-data (inc otsikko-idx)))]
    paikkauskohteet))