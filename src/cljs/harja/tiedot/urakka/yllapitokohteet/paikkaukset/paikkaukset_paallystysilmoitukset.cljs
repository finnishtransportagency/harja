(ns harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paallystysilmoitukset
  (:require [tuck.core :as tuck]))

(defrecord HaePotPaikkaukset [])

(extend-protocol tuck/Event

  HaePotPaikkaukset
  (process-event [_ app]
    app))
