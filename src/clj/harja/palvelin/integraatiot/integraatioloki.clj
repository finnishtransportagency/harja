(ns harja.palvelin.integraatiot.integraatioloki
  (:require [taoensso.timbre :as log]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.kyselyt.integraatioloki :as integraatiloki]
            [joda-time :as j]))

(defprotocol IntegraatiolokiKirjaus
  (kirjaa-alkanut-integraatio [this jarjestelma integraation-nimi ulkoinen-id viesti])
  (kirjaa-onnistunut-integraatio [this viesti lisatietoja tapahtumaid ulkoinen-id])
  (kirjaa-epaonnistunut-integraatio [this viesti lisatietoja tapahtumaid ulkoinen-id]))

(defn tee-integraatiolokin-puhdistus-tehtava [this paivittainen-puhdistusaika]
  (if paivittainen-puhdistusaika
    (do
      (log/debug "Ajastetaan integraatiolokin puhdistus ajettavaksi joka päivä kello: " paivittainen-puhdistusaika)
      (ajastettu-tehtava/ajasta-paivittain
        paivittainen-puhdistusaika
        (fn [_]
          #_(integraatiloki/poista-ennen-paivamaaraa-kirjatut-tapahtumat! (:db this) (minus now (months 10))

                  ;;todo: toteuta
                  ))))
    (fn [] ())))

(defn kirjaa-viesti [db tapahtumaid {:keys [suunta sisaltotyyppi siirtotyyppi sisalto otsikko parametrit]}]
  (integraatiloki/luo-integraatioviesti<! db tapahtumaid suunta sisaltotyyppi siirtotyyppi sisalto otsikko parametrit))

(defn luo-alkanut-integraatio [db jarjestelma nimi ulkoinen-id viesti]
  (let [tapahtumaid (:id (integraatiloki/luo-integraatiotapahtuma<! db jarjestelma nimi ulkoinen-id))]
    (when viesti
      (kirjaa-viesti db tapahtumaid viesti))
    tapahtumaid))

(defn kirjaa-paattynyt-integraatio [db viesti lisatietoja onnistunut tapahtumaid ulkoinen-id]
  (let [kasitellyn-tapahtuman-id
        (if tapahtumaid
          (do
            (integraatiloki/merkitse-integraatiotapahtuma-paattyneeksi! db onnistunut lisatietoja tapahtumaid)
            tapahtumaid)
          (:id (integraatiloki/merkitse-integraatiotapahtuma-paattyneeksi-ulkoisella-idlla<! db onnistunut lisatietoja ulkoinen-id)))]
    (when (and viesti kasitellyn-tapahtuman-id)
      (kirjaa-viesti db kasitellyn-tapahtuman-id viesti))))

(defrecord Integraatioloki [paivittainen-puhdistusaika]
  com.stuartsierra.component/Lifecycle
  (start [this]
    (assoc this :integraatiolokin-puhdistus-tehtava (tee-integraatiolokin-puhdistus-tehtava this paivittainen-puhdistusaika)))
  (stop [this]
    (let [poista-integraatiolokin-puhdistus (:integraatiolokin-puhdistus-tehtava this)]
      (poista-integraatiolokin-puhdistus))
    this)

  IntegraatiolokiKirjaus
  (kirjaa-alkanut-integraatio [this jarjestelma integraation-nimi ulkoinen-id viesti] (luo-alkanut-integraatio (:db this) jarjestelma integraation-nimi ulkoinen-id viesti))
  (kirjaa-onnistunut-integraatio [this viesti lisatietoja tapahtumaid ulkoinen-id] (kirjaa-paattynyt-integraatio (:db this) viesti lisatietoja true tapahtumaid ulkoinen-id))
  (kirjaa-epaonnistunut-integraatio [this viesti lisatietoja tapahtumaid ulkoinen-id] (kirjaa-paattynyt-integraatio (:db this) viesti lisatietoja false tapahtumaid ulkoinen-id)))