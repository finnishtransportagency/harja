(ns harja.palvelin.raportointi.raportit.tyomaapaivakirja.muut-huomiot
  "Työmaapäiväkirja -näkymän muut huomiot"
  (:require
   [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi]]
   [harja.domain.ely :as ely]
   [harja.domain.tierekisteri :as tr-domain]
   [clojure.string :as str]
   [harja.pvm :as pvm]
   [taoensso.timbre :as log]
   [harja.palvelin.raportointi.raportit.tyomaapaivakirja.yhteiset :as yhteiset]))

(defn muut-huomiot []
  (let [onko-huomioita? false]
    (into ()
      [[:jakaja true]
       
       (if onko-huomioita?
         (yhteiset/body-teksti "Huomio")
         (yhteiset/placeholder-ei-tietoja "Ei muita huomioita"))
       
       [:jakaja true]

       (yhteiset/sektio-otsikko "Muut huomiot")])))
