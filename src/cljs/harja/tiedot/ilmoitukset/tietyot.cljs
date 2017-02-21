(ns harja.tiedot.ilmoitukset.tietyot
  (:require [reagent.core :refer [atom]]
            [harja.pvm :as pvm]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.urakka :as u]
            [harja.loki :refer [log tarkkaile!]]
            [cljs.core.async :refer [<!]]
            [harja.atom :refer [paivita-periodisesti] :refer-macros [reaction<! reaction-writable]]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
            [tuck.core :as t]
            [clojure.string :as str]
            [reagent.core :as r]))
