(ns harja.tiedot.hallinta.yhteydenpito
  (:require [reagent.core :refer [atom]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tiedot.navigaatio :as nav]
            [clojure.string :as str])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(def nakymassa? (atom false))

(def vastaanottajat (reaction (when nakymassa?)
                              [{:sahkoposti "testiseppo@example.com"}
                               {:sahkoposti "testijorma@example.com"}]))

(defn mailto-bcc-linkki [vastaanottajat]
  (str "mailto:?bcc="
       (str/join "," (map :sahkoposti vastaanottajat))))