(ns harja.palvelin.raportointi.raportit.tyomaapaivakirja.keliolosuhteet
  "Työmaapäiväkirja -näkymän poikkeukselliset keliolosuhteet"
  (:require
   [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi]]
   [harja.domain.ely :as ely]
   [harja.domain.tierekisteri :as tr-domain]
   [clojure.string :as str]
   [harja.pvm :as pvm]
   [taoensso.timbre :as log]
   [harja.palvelin.raportointi.raportit.tyomaapaivakirja.yhteiset :as yhteiset]))



(defn poikkeukselliset-keliolosuhteet-taulukko []
  (into ()
    [(yhteiset/taulukko nil)
     [:otsikko-heading "Omat havainnot"]
     [:otsikko-heading "Poikkeukselliset paikalliset keliolosuhteet"]]))
