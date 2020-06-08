(ns harja.palvelin.komponentit.uudelleen-kaynnistaja
  (:require [com.stuartsierra.component :as component]))

(defrecord UudelleenKaynnistaja []
  component/Lifecycle
  (start [{:keys [komponenttien-tila] :as this}]

    this)
  (stop [this]
    this))
