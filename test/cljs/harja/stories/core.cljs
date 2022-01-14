(ns harja.stories.core
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.paikannus :as paikannus]
            [harja-laadunseuranta.main :as main]
            [harja-laadunseuranta.tiedot.sovellus :as sovellus]
            [harja-laadunseuranta.tiedot.comms :as comms]
            [harja-laadunseuranta.tiedot.asetukset.asetukset :as asetukset]
            [harja-laadunseuranta.tiedot.tr-haku :as tr-haku]
            [harja-laadunseuranta.tiedot.ilmoitukset :as ilmoitukset]
            [harja-laadunseuranta.utils :as utils]
            [harja-laadunseuranta.tiedot.puhe :as puhe]
            [harja-laadunseuranta.tiedot.tarkastusajon-luonti :as tarkastusajon-luonti]
            [cljs.core.async :as async :refer [<!]]
            [harja-laadunseuranta.tiedot.reitintallennus :as reitintallennus]
            [clojure.string :as str]
            [harja-laadunseuranta.ui.yleiset.dom :as dom]
            [harja-laadunseuranta.asiakas.tapahtumat :as tapahtumat]
            [cljs-time.local :as l])
  (:require-macros [reagent.ratom :refer [run!]]
                   [cljs.core.async.macros :refer [go]]
                   [harja-laadunseuranta.macros :refer [after-delay]]))

(defn render []
  (reagent/render-component [main/main] (.getElementById js/document "app")))

(defn main []
  [:div])

