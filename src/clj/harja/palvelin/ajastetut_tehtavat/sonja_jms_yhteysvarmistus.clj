(ns harja.palvelin.ajastetut-tehtavat.sonja-jms-yhteysvarmistus
  "Tekee ajastetun yhteysvarmistuksen API:n"
  (:require [chime :refer [chime-ch]]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clj-time.periodic :refer [periodic-seq]]
            [taoensso.timbre :as log]
            [chime :refer [chime-at]]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.komponentit.sonja :as sonja]))

(def viestit (atom []))

(defn odota-viestin-saapumista [integraatioloki tapahtuma-id ehto-fn max-aika]
  (loop [max-ts (+ max-aika (System/currentTimeMillis))]
    (if (> (System/currentTimeMillis) max-ts)
      (let [virheviesti (format "Yhteys Sonjan JMS-jonoihin on poikki. Yhteyskokeilu ei mennyt läpi: %s millisekunnissa" max-aika)]
        (log/error virheviesti)
        (integraatioloki/kirjaa-epaonnistunut-integraatio integraatioloki nil virheviesti tapahtuma-id nil)
        false)
      (if (ehto-fn)
        true
        (recur max-ts)))))

(defn tarkista-jms-yhteys [integraatioloki sonja jono]
  (let [lahteva-viesti "ping"
        lokiviesti (integraatioloki/tee-jms-lokiviesti "ulos" lahteva-viesti nil)
        tapahtuma-id (integraatioloki/kirjaa-alkanut-integraatio integraatioloki "sonja" "ping" nil lokiviesti)]
    (sonja/laheta sonja jono lahteva-viesti)
    (when (odota-viestin-saapumista integraatioloki tapahtuma-id #(= 1 (count @viestit)) 100000)
      (let [saapunut-viesti (first @viestit)
            lokiviesti (integraatioloki/tee-jms-lokiviesti "sisään" saapunut-viesti nil)]
        (if (= lahteva-viesti saapunut-viesti)
          (integraatioloki/kirjaa-onnistunut-integraatio integraatioloki lokiviesti "Yhteyskokeilu onnistunut" tapahtuma-id nil)
          (let [virheviesti (format "Yhteyskokeilu Sonjan JMS-jonoon (%s) ei palauttanut oletettua vastausta. Vastaus: %s." jono saapunut-viesti)]
            (log/error virheviesti)
            (integraatioloki/kirjaa-epaonnistunut-integraatio integraatioloki lokiviesti virheviesti tapahtuma-id nil)))))
    (reset! viestit [])))

(defn tee-jms-yhteysvarmistus-tehtava [{:keys [integraatioloki sonja]} minuutit jono]
  (when (and minuutit jono)
    (log/debug (format "Varmistetaan Sonjan JMS jonoihin yhteys %s minuutin välein." minuutit))
    (sonja/kuuntele sonja jono #(swap! viestit conj (.getText %)))
    (ajastettu-tehtava/ajasta-minuutin-valein
      minuutit
      (fn [_] (tarkista-jms-yhteys integraatioloki sonja jono)))))

(defrecord SonjaJmsYhteysvarmistus [ajovali-minuutteina jono]
  component/Lifecycle
  (start [this]
    (assoc this :jms-varmistus (tee-jms-yhteysvarmistus-tehtava this ajovali-minuutteina jono)))
  (stop [this]
    (let [lopeta (get this :jms-varmistus)]
      (when lopeta (lopeta)))
    this))
