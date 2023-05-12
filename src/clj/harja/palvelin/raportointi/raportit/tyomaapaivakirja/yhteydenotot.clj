(ns harja.palvelin.raportointi.raportit.tyomaapaivakirja.yhteydenotot
  "Työmaapäiväkirja -näkymän yhteydenotot ja palautteet"
  (:require
   [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi]]
   [harja.domain.ely :as ely]
   [harja.domain.tierekisteri :as tr-domain]
   [clojure.string :as str]
   [harja.pvm :as pvm]
   [taoensso.timbre :as log]
   [harja.palvelin.raportointi.raportit.tyomaapaivakirja.yhteiset :as yhteiset]))

(defn yhteydenotot-ja-palautteet []
  (into ()
    [[:jakaja true]
     
     [:teksti "Väyläviraston siltainsinööri haluaisi käydä silloilla x ja y tekemässä erikoistarkastuksen"]
     [:jakaja true]
     [:teksti "Kaupungin kunnossapitopäällikkö otti yhteyttä viherhoidon rajoista"]
     [:jakaja true]

     [:otsikko-heading "Yhteydenotot ja palautteet, jotka edellyttävät toimenpiteitä"]]))
