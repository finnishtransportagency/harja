(ns harja.palvelin.integraatiot.turi.turi-komponentti
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.kyselyt.turvallisuuspoikkeamat :as q]
            [harja.palvelin.integraatiot.turi.turvallisuuspoikkeamasanoma :as sanoma]
            [harja.palvelin.komponentit.liitteet :as liitteet]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava])
  (:use [slingshot.slingshot :only [throw+]]))

(defprotocol TurvallisuusPoikkeamanLahetys
  (laheta-turvallisuuspoikkeama [this id]))

(defn tee-lokittaja [this]
  (integraatioloki/lokittaja (:integraatioloki this) (:db this) "turi" "laheta-turvallisuuspoikkeama"))

(defn kasittele-turin-vastaus [db id _]
  (q/lokita-lahetys<! db true id))

(defn hae-liitteet [liitteiden-hallinta db id]
  (let [liitteet (q/hae-turvallisuuspoikkeaman-liitteet db id)]
    (mapv (fn [liite] (assoc liite :sisalto (liitteet/lataa-liite liitteiden-hallinta (:id liite)))) liitteet)))

(defn hae-turvallisuuspoikkeama [liitteiden-hallinta db id]
  (let [turvallisuuspoikkeama (first (q/hae-turvallisuuspoikkeama db id))]
    (if turvallisuuspoikkeama
      (let [korjaavat-toimenpiteet (q/hae-turvallisuuspoikkeaman-korjaavat-toimenpiteet db id)
            kommentit (q/hae-turvallisuuspoikkeaman-kommentit db id)
            liitteet (hae-liitteet liitteiden-hallinta db id)]
        (assoc turvallisuuspoikkeama
          :korjaavat-toimenpiteet korjaavat-toimenpiteet
          :kommentit kommentit
          :liitteet liitteet))
      (let [virhe (format "Id:llä %s ei löydy turvallisuuspoikkeamaa" id)]
        (log/error virhe)
        (throw+ {:type :tuntematon-turvallisuuspoikkeama
                 :error virhe})))))

(defn laheta-turvallisuuspoikkeama-turiin [{:keys [db integraatioloki liitteiden-hallinta url kayttajatunnus salasana]} id]
  (when-not (empty? url)
    (log/debug (format "Lähetetään turvallisuuspoikkeama (id: %s) TURI:n" id))
    (try
      (integraatiotapahtuma/suorita-integraatio
        db integraatioloki "turi" "laheta-turvallisuuspoikkeama" nil
        (fn [konteksti]
          (->> id
               (hae-turvallisuuspoikkeama liitteiden-hallinta db)
               sanoma/muodosta
               (integraatiotapahtuma/laheta
                 konteksti :http {:metodi :POST
                                  :url url
                                  :kayttajatunnus kayttajatunnus
                                  :salasana salasana})
               (kasittele-turin-vastaus db id)))
        {:virhekasittelija (fn [_ _] (q/lokita-lahetys<! db false id))})
      (catch Throwable t
        (log/error t (format "Turvallisuuspoikkeaman (id: %s) lähetyksessä TURI:n tapahtui poikkeus" id))))))

(defn laheta-turvallisuuspoikkeamat-turiin [this]
  (let [idt (q/hae-lahettamattomat-turvallisuuspoikkeamat (:db this))]
    (doseq [id idt]
      (laheta-turvallisuuspoikkeama this id))))

(defn tee-paivittainen-lahetys-tehtava [this paivittainen-lahetysaika]
  (if paivittainen-lahetysaika
    (do
      (log/debug "Ajastetaan turvallisuuspoikkeamien lähettäminen joka päivä kello: " paivittainen-lahetysaika)
      (ajastettu-tehtava/ajasta-paivittain paivittainen-lahetysaika #(laheta-turvallisuuspoikkeamat-turiin this)))
    #()))

(defrecord Turi [asetukset]
  component/Lifecycle
  (start [this]
    (let [{url :url kayttajatunnus :kayttajatunnus salasana :salasana paivittainen-lahetysaika :paivittainen-lahetysaika} asetukset]
      (log/debug (format "Käynnistetään TURI-komponentti (URL: %s)" url))
      (assoc
        (assoc this
          :url url
          :kayttajatunnus kayttajatunnus
          :salasana salasana)
        :paivittainen-lahetys-tehtava (tee-paivittainen-lahetys-tehtava this paivittainen-lahetysaika))))

  (stop [this]
    (:paivittainen-lahetys-tehtava this)
    this)

  TurvallisuusPoikkeamanLahetys
  (laheta-turvallisuuspoikkeama [this id]
    (laheta-turvallisuuspoikkeama-turiin this id)))

