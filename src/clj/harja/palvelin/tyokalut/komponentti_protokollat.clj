(ns harja.palvelin.tyokalut.komponentti-protokollat
  (:require [com.stuartsierra.component :as component]
            [clojure.spec.alpha :as s]))

(s/def ::tiedot any?)
(s/def ::kaikki-ok? boolean?)
(s/def ::status (s/keys :req [::tiedot ::kaikki-ok?]))

(defprotocol IRestart
  (restart [this system-component])
  (reload [this component]))

(defprotocol IStatus
  (-status [this] "Palauttaa komponentin statuksen. Palautetun arvon pitäisi olla validi ::status"))

(defn status [komponentti]
  {:pre [(satisfies? component/Lifecycle komponentti)
         (satisfies? IStatus komponentti)]
   :post [(s/valid? ::status %)]}
  (-status komponentti))

(defn status-ok? [komponentti]
  {:pre [(satisfies? component/Lifecycle komponentti)
         (satisfies? IStatus komponentti)]
   :post [(s/valid? ::kaikki-ok? %)]}
  (::kaikki-ok? (status komponentti)))