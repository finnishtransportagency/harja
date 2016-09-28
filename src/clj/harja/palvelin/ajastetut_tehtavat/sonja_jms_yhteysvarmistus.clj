(ns harja.palvelin.ajastetut-tehtavat.sonja-jms-yhteysvarmistus
  "Tekee ajastetun yhteysvarmistuksen API:n"
  (:require [chime :refer [chime-ch]]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clj-time.periodic :refer [periodic-seq]]
            [taoensso.timbre :as log]
            [chime :refer [chime-at]]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]))






(defn tarkista-jms-yhteys [db integraatioloki sonja jono]

  )

(defn tee-jms-yhteysvarmistus-tehtava [{:keys [db integraatioloki sonja]} minuutit jono]
  (when (and minuutit url)
    (log/debug (format "Varmistetaan Sonjan JMS jonoihin yhteys %s minuutin välein." minuutit))
    (ajastettu-tehtava/ajasta-minuutin-valein
      minuutit
      (fn [_] (tarkista-jms-yhteys db integraatioloki sonja jono)))))

(defrecord SonjaJmsYhteysvarmistus [ajovali-minuutteina jono]
  component/Lifecycle
  (start [this]
    (assoc this :jms-varmistus (tee-jms-yhteysvarmistus-tehtava this ajovali-minuutteina jono)))
  (stop [this]
    ((get this :jms-varmistus))
    this))
