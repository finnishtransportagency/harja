(ns harja.palvelin.komponentit.uudelleen-kaynnistaja
  (:require [com.stuartsierra.component :as component]))

(defrecord UudelleenKaynnistaja []
  component/Lifecycle
  (start [this]

    this)
  (stop [this]
    this))
