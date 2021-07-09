(ns harja.palvelin.ajastetut-tehtavat.sonja-jms-yhteysvarmistus
  "Tekee ajastetun yhteysvarmistuksen API:n"
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clj-time.periodic :refer [periodic-seq]]
            [taoensso.timbre :as log]
            [harja.palvelin.tyokalut.tapahtuma-apurit :as tapahtuma-apurit]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.jms :as jms]
            [harja.palvelin.tyokalut.lukot :as lukot]))

(def sonja-kanava "sonjaping")
(def lukon-vanhenemisaika 300)

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

(defn tarkista-jms-yhteys [db integraatioloki sonja jono]
  (lukot/yrita-ajaa-lukon-kanssa
    db
    "sonja-jms-yhteysvarmistus"
    (fn []
      (let [lahteva-viesti "ping"
            lokiviesti (integraatioloki/tee-jms-lokiviesti "ulos" lahteva-viesti nil jono)
            tapahtuma-id (integraatioloki/kirjaa-alkanut-integraatio integraatioloki "sonja" "ping" nil lokiviesti)
            viestit (atom [])
            lopeta-kuuntelu-fn! (tapahtuma-apurit/tapahtuman-kuuntelija! sonja-kanava (fn [{viesti :payload}] (swap! viestit conj viesti)))]

        (jms/laheta sonja jono lahteva-viesti)

        (when (odota-viestin-saapumista integraatioloki tapahtuma-id #(= 1 (count @viestit)) 100000)
          (let [saapunut-viesti (first @viestit)
                lokiviesti (integraatioloki/tee-jms-lokiviesti "sisään" saapunut-viesti nil jono)]
            (if (= lahteva-viesti saapunut-viesti)
              (integraatioloki/kirjaa-onnistunut-integraatio integraatioloki lokiviesti "Yhteyskokeilu onnistunut" tapahtuma-id nil)
              (let [virheviesti (format "Yhteyskokeilu Sonjan JMS-jonoon (%s) ei palauttanut oletettua vastausta. Vastaus: %s." jono saapunut-viesti)]
                (log/error virheviesti)
                (integraatioloki/kirjaa-epaonnistunut-integraatio integraatioloki lokiviesti virheviesti tapahtuma-id nil)))))
        (when lopeta-kuuntelu-fn!
          (lopeta-kuuntelu-fn!))))
    lukon-vanhenemisaika))

(defn tee-jms-yhteysvarmistus-tehtava [{:keys [db integraatioloki sonja]} minuutit jono]
  (when (and minuutit jono)
    (log/debug (format "Varmistetaan Sonjan JMS jonoihin yhteys %s minuutin välein." minuutit))
    {:sonja-lopeta! (jms/kuuntele! sonja jono #(tapahtuma-apurit/julkaise-tapahtuma sonja-kanava (.getText %)))
     :ajastus-lopeta! (ajastettu-tehtava/ajasta-minuutin-valein
                        minuutit 34                         ;; ajastus alkaa pyöriä 34 sekunnin kuluttua käynnistyksestä
                        (fn [_] (tarkista-jms-yhteys db integraatioloki sonja jono)))}))

(defrecord SonjaJmsYhteysvarmistus [ajovali-minuutteina jono]
  component/Lifecycle
  (start [this]
    (assoc this ::lopetus-fns (tee-jms-yhteysvarmistus-tehtava this ajovali-minuutteina jono)))
  (stop [this]
    (let [{:keys [sonja-lopeta! ajastus-lopeta!]} (get this ::lopetus-fns)]
      (when sonja-lopeta! (sonja-lopeta!))
      (when ajastus-lopeta! (ajastus-lopeta!)))
    this))
