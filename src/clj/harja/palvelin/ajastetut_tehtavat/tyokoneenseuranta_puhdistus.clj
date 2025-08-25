(ns harja.palvelin.ajastetut-tehtavat.tyokoneenseuranta-puhdistus
  (:require [harja.kyselyt.tyokoneseuranta :as tks]
            [com.stuartsierra.component :as component]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]))

(defn poista-vanhat-tyokonesijainnit [db]
  (tks/poista-vanhentuneet-havainnot! db))

(defrecord TyokoneenseurantaPuhdistus []
  component/Lifecycle
  (start [this]
    (assoc this
      ::poista-ajastus
      (ajastettu-tehtava/ajasta-minuutin-valein
        15 34 ;; alkaa pyöriä 34s käynnistyksestä
        (fn [_] (poista-vanhat-tyokonesijainnit (:db this))))))
  (stop [this]
    ((::poista-ajastus this))
    this))
