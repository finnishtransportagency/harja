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


(defn kasittele-vastaus [url body headers]
  (println "body" body)
  (println "headers" headers)
  (when (not (= "{\"ilmoitukset\":\"pong\"}" body))
    (log/error (format "Harja API:n yhteys ei toimi URL:ssa (%s). Palvelu ei palauttanut oletettua vastausta. Vastaus: %s." url body))))

(defn tarkista-api-yhteys [db integraatioloki url kayttajatunnus salasana]
  (integraatiotapahtuma/suorita-integraatio
    db integraatioloki "api" "ping-ulos"
    (fn [konteksti]
      (try
        ;; Huom. lokaalisti testatessa täytyy asettaa OAM_REMOTE_USER otsikoihin järjestelmätunnuksen kanssa
        (let [http-asetukset {:metodi :GET
                              :url url
                              :otsikot {"OAM_REMOTE_USER" "yit-rakennus"}
                              ;; :kayttajatunnus kayttajatunnus
                              ;; :salasana salasana
                              }
              {body :body headers :headers} (integraatiotapahtuma/laheta konteksti :http http-asetukset)]
          (kasittele-vastaus url body headers))
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
    ((get this :api-varmistus))
    this))
