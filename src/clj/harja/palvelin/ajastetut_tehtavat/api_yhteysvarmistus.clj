(ns harja.palvelin.ajastetut-tehtavat.api-yhteysvarmistus
  "Tekee ajastetun yhteysvarmistuksen API:n"
  (:require [chime :refer [chime-ch]]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clj-time.periodic :refer [periodic-seq]]
            [taoensso.timbre :as log]
            [chime :refer [chime-at]]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]))


(defn kasittele-vastaus [url body]
  (when (not (= "{\"ilmoitukset\":\"pong\"}" body))
    (log/error (format "Harja API:n yhteys ei toimi URL:ssa (%s). Palvelu ei palauttanut oletettua vastausta. Vastaus: %s." url body))))

(defn tarkista-api-yhteys [db integraatioloki url kayttajatunnus salasana]
  (integraatiotapahtuma/suorita-integraatio
    db integraatioloki "api" "ping-ulos"
    (fn [konteksti]
      (try
        (let [http-asetukset {:metodi :GET
                              :url url
                              ;; Huom. lokaalisti testatessa täytyy asettaa OAM_REMOTE_USER otsikoihin järjestelmätunnuksen kanssa
                              ;; :otsikot {"OAM_REMOTE_USER" "***"}
                              :kayttajatunnus kayttajatunnus
                              :salasana salasana
                              }
              {body :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset)]
          (kasittele-vastaus url body))
        (catch Exception e
          (log/error e (format "Harja API:n yhteys ei toimi URL:ssa (%s)" url)))))))

(defn tee-api-varmistus-tehtava [{:keys [db integraatioloki]} minuutit url kayttajatunnus salasana]
  (when (and minuutit url)
    (log/debug (format "Varmistetaan API:n yhteys %s minuutin välein." minuutit))
    (ajastettu-tehtava/ajasta-minuutin-valein
      minuutit
      (fn [_] (tarkista-api-yhteys db integraatioloki url kayttajatunnus salasana)))))

(defrecord ApiVarmistus [ajovali-minuutteina url kayttajatunnus salasana]
  component/Lifecycle
  (start [this]
    (assoc this :api-varmistus (tee-api-varmistus-tehtava this ajovali-minuutteina url kayttajatunnus salasana)))
  (stop [this]
    (let [lopeta (get this :api-varmistus)]
      (when lopeta (lopeta)))
    this))
