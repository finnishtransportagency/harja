(ns harja.palvelin.raportointi.raportit.tyomaapaivakirja.vahingot
  "Työmaapäiväkirja -näkymän vahingot ja onnettomuudet"
  (:require
   [harja.palvelin.raportointi.raportit.tyomaapaivakirja.yhteiset :as yhteiset]))

(defn vahingot []
  (into ()
    ;; Jos tietoja ei ole, käytä: 
    ;; (yhteiset/placeholder-ei-tietoja "Ei vahinkoja")
    [[:jakaja true]
     (yhteiset/body-teksti "Vahinko: Rekka törmännyt keskikaiteeseen Vt 4 4/400/100-200, Kaide vaurioitunut 100 metriä")
     [:jakaja true]
     (yhteiset/sektio-otsikko "Vahingot ja onnettomuudet")]))
