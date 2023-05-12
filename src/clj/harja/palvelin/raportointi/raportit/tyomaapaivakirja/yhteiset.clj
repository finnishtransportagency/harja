(ns harja.palvelin.raportointi.raportit.tyomaapaivakirja.yhteiset
  "Työmaapäiväkirja raportin yhteiset funktiot"
  (:require
   [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi]]
   [harja.domain.ely :as ely]
   [harja.domain.tierekisteri :as tr-domain]
   [clojure.string :as str]
   [harja.pvm :as pvm]
   [taoensso.timbre :as log]))

(defn taulukon-rivi
  [test lihavoi?]
  (let []
    (rivi
      [:varillinen-teksti {:arvo "AaAa"}]
      [:varillinen-teksti {:arvo (str test) :lihavoi? lihavoi?}])))
