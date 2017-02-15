(ns harja.tiedot.hallinta.yhteydenpito
  (:require [reagent.core :refer [atom]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tiedot.navigaatio :as nav]
            [cljs.core.async :refer [<! >! chan]]
            [clojure.string :as str]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]))

(def nakymassa? (atom false))

(defn- hae-yhteydenpidon-vastaanottajat []
  (log "Haetaan mailiosoitteet")
  (k/post! :yhteydenpito-vastaanottajat nil))

(def vastaanottajat (atom nil))

(run! (let [nakymassa? @nakymassa?]
        (reset! vastaanottajat nil)
        (when nakymassa?
          (go (let [vastaus (<! (hae-yhteydenpidon-vastaanottajat))]
                (reset! vastaanottajat vastaus))))))

(add-watch vastaanottajat ::debug (fn [_ _ old new]
                                    (.log js/console "asd: " (pr-str old) " => " (pr-str new))))

(defn mailto-bcc-linkki [vastaanottajat]
  (str "mailto:?bcc="
       (str/join "," (keep :sahkoposti vastaanottajat))))