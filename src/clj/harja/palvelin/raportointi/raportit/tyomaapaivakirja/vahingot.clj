(ns harja.palvelin.raportointi.raportit.tyomaapaivakirja.vahingot
  "Työmaapäiväkirja -näkymän vahingot ja onnettomuudet"
  (:require
   [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi]]
   [harja.domain.ely :as ely]
   [harja.domain.tierekisteri :as tr-domain]
   [clojure.string :as str]
   [harja.pvm :as pvm]
   [taoensso.timbre :as log]
   [harja.palvelin.raportointi.raportit.tyomaapaivakirja.yhteiset :as yhteiset]))

(defn vahingot []
  (into ()
    [[:jakaja true]
     [:teksti "Vahinko: Rekka törmännyt keskikaiteeseen Vt 4 4/400/100-200, Kaide vaurioitunut 100 metriä"]
     [:jakaja true]]))
