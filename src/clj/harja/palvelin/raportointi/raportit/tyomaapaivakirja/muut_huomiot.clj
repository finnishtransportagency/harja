(ns harja.palvelin.raportointi.raportit.tyomaapaivakirja.muut-huomiot
  "Työmaapäiväkirja -näkymän muut huomiot"
  (:require
   [harja.palvelin.raportointi.raportit.tyomaapaivakirja.yhteiset :as yhteiset]))

(defn muut-huomiot []
  (let [onko-huomioita? false]
    (into ()
      [[:jakaja true]
       
       (if onko-huomioita?
         (yhteiset/body-teksti "Huomio")
         (yhteiset/placeholder-ei-tietoja "Ei muita huomioita"))
       
       [:jakaja true]

       (yhteiset/osion-otsikko "Muut huomiot")])))
