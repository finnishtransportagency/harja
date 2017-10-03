(ns harja.palvelin.integraatiot.digitraffic.ais-data
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :as kutsu]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.palvelin.palvelut.pois-kytketyt-ominaisuudet :refer [ominaisuus-kaytossa?]]
            [org.httpkit.client :as http]
            [taoensso.timbre :as log]
            [clojure.core.async :refer [go <! >! go-loop timeout close! chan]]
            [harja.palvelin.tyokalut.lukot :as lukko]
            [cheshire.core :as cheshire]))

(defn- kasittele-vastaus! [{:keys [db integraatioloki] :as deps} viesti]
  (log/debug (str "Saatiin vastauksena " (count (:features viesti)) " laivan sijainnit.")))

(defn- tee-haku! [deps url konteksti]
  (let [{body :body} (integraatiotapahtuma/laheta
                       konteksti
                       :http
                       {:metodi :GET
                        :url url})]
    (kasittele-vastaus! deps (cheshire/parse-string body true))))

(defn paivita-alusten-ais-data! [{:keys [db integraatioloki] :as deps} url]
  (let [palvelun-nimi "digitraffic"
        haun-nimi "ais-data-paivitys"]
    (lukko/yrita-ajaa-lukon-kanssa
      db
      haun-nimi
      (fn []
        (integraatiotapahtuma/suorita-integraatio
          db
          integraatioloki
          palvelun-nimi
          haun-nimi
          (fn [konteksti]
            (tee-haku! deps url konteksti)))))))

(defrecord Ais-haku [url sekunnit]
  component/Lifecycle
  (start [{:keys [db integraatioloki] :as this}]
    (if (ominaisuus-kaytossa? :ais-data)
      (do
        (log/info "Käynnistetään AIS-datan päivitys " sekunnit "s välein, urlista " url)
        (assoc this
          :lopeta-ais-data-hakeminen-fn!
          (ajastettu-tehtava/ajasta-sekunnin-valein
            sekunnit
            (fn [& _]
              (paivita-alusten-ais-data! this url)))))
      this))

  (stop [{:keys [lopeta-ais-data-hakeminen-fn!] :as this}]
    (when-let [fn lopeta-ais-data-hakeminen-fn!]
      (log/info "Lopetetaan ajastettu AIS-datan päivittäminen")
      (fn))
    this))