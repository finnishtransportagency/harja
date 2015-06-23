(ns harja.tiedot.urakka.paallystys
  "Tämä nimiavaruus hallinnoi urakan päällystystietoja."
  (:require [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm]
            [harja.ui.protokollat :refer [Haku hae]])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defn hae-paallystyskohteet [urakka-id sopimus-id]
  (k/post! :urakan-paallystyskohteet {:urakka-id urakka-id
                                          :sopimus-id sopimus-id}))

(defn hae-paallystystoteumat [urakka-id sopimus-id]
  (k/post! :urakan-paallystystoteumat {:urakka-id urakka-id
                                      :sopimus-id sopimus-id}))