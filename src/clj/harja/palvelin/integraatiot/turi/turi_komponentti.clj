(ns harja.palvelin.integraatiot.turi.turi-komponentti
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.integraatiopisteet.http :as http]
            [harja.kyselyt.turvallisuuspoikkeamat :as q]
            [harja.palvelin.integraatiot.turi.turvallisuuspoikkeamasanoma :as sanoma]))

(defprotocol TurvallisuusPoikkeamanLahetys
  (laheta-turvallisuuspoikkeama [this id]))

(defn tee-lokittaja [this]
  (integraatioloki/lokittaja (:integraatioloki this) (:db this) "turi" "laheta-turvallisuuspoikkeama"))

(defn kasittele-turin-vastaus [sisalto otsikot id]
  ;; todo: tarkista onnistuiko
  (q/lokita-lahetys<! id true))

(defn laheta-turvallisuuspoikkeama-turiin [{:keys [db integraatioloki url kayttajatunnus salasana]} id]
  (let [lokittaja (integraatioloki/lokittaja integraatioloki db "turi" "laheta-turvallisuuspoikkeama")
        integraatiopiste (http/luo-integraatiopiste lokittaja {:kayttajatunnus kayttajatunnus :salasana salasana})
        vastauskasittelija (fn [sisalto otsikot] (kasittele-turin-vastaus sisalto otsikot id))
        turvallisuuspoikkeama (q/hae-urakan-turvallisuuspoikkeama db id)
        xml (sanoma/muodosta turvallisuuspoikkeama)]
    ;; todo: poikkeuskäsittely!
    (http/POST integraatiopiste url xml vastauskasittelija)))

(defrecord Turi [asetukset]
  component/Lifecycle
  (start [this]
    (log/debug "Käynnistetään TURI-komponentti")
    (let [turi (:turi asetukset)]
      (assoc this
        :url (:url turi)
        :kayttajatunnus (:kayttajatunnus turi)
        :salasana (:salasana turi))))

  (stop [this]
    this)
  TurvallisuusPoikkeamanLahetys
  (laheta-turvallisuuspoikkeama [this id]
    (laheta-turvallisuuspoikkeama-turiin this id)))
