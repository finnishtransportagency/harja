(ns harja.tiedot.urakka.laskutusyhteenveto
  "Tämä nimiavaruus hallinnoi urakan laskutusyhteenvedon tietoja."
  (:require [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log]]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

(defn hae-laskutusyhteenvedon-tiedot [tiedot]
  (log "hae-laskutusyhteenvedon-tiedot tiedot, tiedot" (pr-str tiedot))
  (k/post! :hae-laskutusyhteenvedon-tiedot tiedot))
