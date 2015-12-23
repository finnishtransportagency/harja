(ns harja.tiedot.urakka.aikataulu
  "Ylläpidon urakoiden aikataulu"
  (:require [reagent.core :refer [atom]]
            [harja.loki :refer [log logt tarkkaile!]]
            [cljs.core.async :refer [<!]]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.loki :refer [log]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.navigaatio :as nav])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(defonce aikataulu-nakymassa? (atom false))

(defn hae-aikataulut [urakka-id sopimus-id]
  (k/post! :hae-aikataulut {:urakka-id  urakka-id
                            :sopimus-id sopimus-id}))

(def aikataulurivit (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
                                 [valittu-sopimus-id _] @u/valittu-sopimusnumero
                                 nakymassa? @aikataulu-nakymassa?]
                                (when (and valittu-urakka-id valittu-sopimus-id nakymassa?)
                                  (hae-aikataulut valittu-urakka-id valittu-sopimus-id))))