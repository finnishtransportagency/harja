(ns harja.tiedot.urakka.siltatarkastukset
  "Tämä nimiavaruus hallinnoi urakan siltatarkastuksien tietoja."
  (:require [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defn hae-urakan-sillat [urakka-id]
  (k/post! :hae-urakan-sillat urakka-id))

(defn hae-sillan-tarkastukset [silta-id]
  (k/post! :hae-sillan-tarkastukset silta-id))

(defn hae-siltatarkastuksen-kohteet [siltatarkastus-id]
  (k/post! :hae-siltatarkastuksen-kohteet siltatarkastus-id))
