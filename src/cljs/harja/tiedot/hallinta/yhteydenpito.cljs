(ns harja.tiedot.hallinta.yhteydenpito
  (:require [reagent.core :refer [atom]]
            [harja.loki :refer [log tarkkaile!]]
            [cljs.core.async :refer [<! >! chan]]
            [clojure.string :as str]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction<!]]))

(def nakymassa? (atom false))

(defn- hae-yhteydenpidon-vastaanottajat []
  (log "Haetaan mailiosoitteet")
  (k/post! :yhteydenpito-vastaanottajat nil))

(def vastaanottajat (reaction<! [nakymassa? @nakymassa?]
                                {:nil-kun-haku-kaynnissa? true}
                                (when nakymassa?
                                  (hae-yhteydenpidon-vastaanottajat))))

(defn mailto-bcc-linkki [vastaanottajat]
  (let [sahkopostit (->> (keep :sahkoposti vastaanottajat)
                         (filter #(str/includes? % "@")))]
    (str "mailto:?bcc="
         (str/join ";" sahkopostit))))