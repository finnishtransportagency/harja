(ns harja.palvelin.raportointi.raportit.tyomaapaivakirja.liikenneohjaukset
  "Työmaapäiväkirja -näkymän tilapäiset liikenneohjaukset"
  (:require
   [harja.palvelin.raportointi.raportit.tyomaapaivakirja.yhteiset :as yhteiset]))

(defn liikenneohjaukset []
  (into ()
    ;; Jos tietoja ei ole, käytä: 
    ;; (yhteiset/placeholder-ei-tietoja "Ei tietoja")
    [[:jakaja true]
     (yhteiset/body-teksti "Liikenne poikki onnettomuuden takia klo 08:45 - 9:22: Vt 4 4/400/100-200. Kiertotie 847/4/0-2000. Käytetty liikenteenohjausvaunua ohjaukseen.")
     [:jakaja true]
     (yhteiset/osion-otsikko "Tilapäiset liikenteenohjaukset")]))
