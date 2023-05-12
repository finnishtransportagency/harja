(ns harja.palvelin.raportointi.raportit.tyomaapaivakirja.muut-toimenpiteet
  "Työmaapäiväkirja -näkymän muut toimenpiteet taulukko"
  (:require
   [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi]]
   [harja.domain.ely :as ely]
   [harja.domain.tierekisteri :as tr-domain]
   [clojure.string :as str]
   [harja.pvm :as pvm]
   [taoensso.timbre :as log]
   [harja.palvelin.raportointi.raportit.tyomaapaivakirja.yhteiset :as yhteiset]))

(defn muut-toimenpiteet-taulukko []
  (into ()
    [(yhteiset/taulukko nil)
     [:otsikko-heading "Muut toimenpiteet"]]))
