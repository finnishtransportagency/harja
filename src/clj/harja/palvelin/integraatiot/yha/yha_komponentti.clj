(ns harja.palvelin.integraatiot.yha.yha-komponentti
  (:require [com.stuartsierra.component :as component]
            [hiccup.core :refer [html]]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]))

(defprotocol YllapidonUrakoidenHallinta
  (hae-urakat [this tunniste nimi vuosi])
  (hae-kohteet [this urakka-id])
  (laheta-kohde [this kohde-id]))

(defn kasittele-vastaus [body headers]
  )

(defn hae-urakat-yhasta [integraatioloki db url tunniste nimi vuosi]
  (let [url (str url "/urakkahaku")]
    (log/debug (format "Haetaan YHA:sta urakata (tunniste: %s, nimi: %s & vuosi: %s)" tunniste nimi vuosi))
    (integraatiotapahtuma/suorita-integraatio
      db integraatioloki "yha" "hae-urakat"
      (fn [konteksti]
        (let [parametrit {"tunniste" tunniste
                          "nimi" nimi
                          "vuosi" vuosi}
              http-asetukset {:metodi :GET
                              :url url
                              :parametrit parametrit}
              {body :body headers :headers}
              (integraatiotapahtuma/laheta konteksti :http http-asetukset)]
          (kasittele-vastaus body headers))))))

(defn hae-urakan-kohteet-yhasta [integraatioloki db url urakka-id]
  ;; todo: toteuta
  )

(defn laheta-kohde-yhan [integraatioloki db url kohde-id]
  ;; todo: toteuta
  )

(defrecord Yha [asetukset]
  component/Lifecycle
  (start [this])
  (stop [this])

  YllapidonUrakoidenHallinta

  (hae-urakat [this tunniste nimi vuosi]
    (hae-urakat-yhasta (:integraatioloki this) (:db this) (:url (:yha asetukset)) tunniste nimi vuosi))
  (hae-kohteet [this urakka-id]
    (hae-urakan-kohteet-yhasta (:integraatioloki this) (:db this) (:url (:yha asetukset)) urakka-id))
  (laheta-kohde [this kohde-id]
    (laheta-kohde-yhan (:integraatioloki this) (:db this) (:url (:yha asetukset)) kohde-id)))

