(ns harja.tiedot.hallinta.valitavoitteet
  "Hallinnoi valtakunnallisten välitavoitteiden tietoja"
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log tarkkaile!]]
            [harja.pvm :as pvm]
            [cljs-time.core :as time]
            [harja.atom :refer [paivita!]]
            [cljs-time.core :as t]
            [harja.pvm :as pvm])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(def nakymassa? (atom false))

(def tavoitteet (atom nil))