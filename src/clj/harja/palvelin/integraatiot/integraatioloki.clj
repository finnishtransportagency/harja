(ns harja.palvelin.integraatiot.integraatioloki
  (:require [taoensso.timbre :as log]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.kyselyt.integraatioloki :as integraatiloki]
            [clj-time.core :refer [months ago]]
            [harja.kyselyt.konversio :as konversio]))

(defprotocol IntegraatiolokiKirjaus
  (kirjaa-alkanut-integraatio [this jarjestelma integraation-nimi ulkoinen-id viesti])
  (kirjaa-onnistunut-integraatio [this viesti lisatietoja tapahtumaid ulkoinen-id])
  (kirjaa-epaonnistunut-integraatio [this viesti lisatietoja tapahtumaid ulkoinen-id])
  (kirjaa-jms-viesti [integraatioloki jarjestelma integraatio viesti-id suunta sisalto])
  (kirjaa-lahteva-jms-kuittaus [integraatioloki kuittaus tapahtuma-id onnistunut lisatietoja])
  (kirjaa-saapunut-jms-kuittaus [integraatioloki kuittaus ulkoinen-id integraatio onnistunut]))

(defn tee-integraatiolokin-puhdistus-tehtava [this paivittainen-puhdistusaika]
  (if paivittainen-puhdistusaika
    (do
      (log/debug "Ajastetaan integraatiolokin puhdistus ajettavaksi joka päivä kello: " paivittainen-puhdistusaika)
      (ajastettu-tehtava/ajasta-paivittain
        paivittainen-puhdistusaika
        (fn [_]
          (let [aikarajaus (konversio/sql-timestamp (.toDate (-> 1 months ago)))]
            (log/debug "Poistetaan kaikki integraatiotapahtumat, jotka ovat alkaneet ennen:" aikarajaus)
            (integraatiloki/poista-ennen-paivamaaraa-kirjatut-tapahtumat! (:db this) aikarajaus)))))
    (fn [] ())))

(defn tee-jms-lokiviesti [suunta sisalto otsikko]
  {:suunta        suunta
   :sisaltotyyppi "application/xml"
   :siirtotyyppi  "JMS"
   :sisalto       sisalto
   :otsikko       (when otsikko (str otsikko))
   :parametrit    nil})

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

(defn lokita-jms-viesti [integraatioloki jarjestelma integraatio viesti-id suunta sisalto]
  (let [otsikko {:message-id viesti-id}
        lokiviesti (tee-jms-lokiviesti suunta sisalto otsikko)]
    (kirjaa-alkanut-integraatio integraatioloki jarjestelma integraatio viesti-id lokiviesti)))

(defn lokita-lahteva-jms-kuittaus [integraatioloki kuittaus tapahtuma-id onnistunut lisatietoja]
  (let [lokiviesti (tee-jms-lokiviesti "ulos" kuittaus nil)]
    (if onnistunut
      (kirjaa-onnistunut-integraatio integraatioloki lokiviesti lisatietoja tapahtuma-id nil)
      (kirjaa-epaonnistunut-integraatio integraatioloki lokiviesti lisatietoja tapahtuma-id nil))))

(defn lokita-saapunut-jms-kuittaus [integraatioloki kuittaus ulkoinen-id integraatio onnistunut]
  (log/debug "Kirjataan saapunut kuittaus " kuittaus ", " ulkoinen-id ", " integraatio ", " onnistunut)
  (let [lokiviesti (tee-jms-lokiviesti "sisään" kuittaus nil)]
    (if onnistunut
      (kirjaa-onnistunut-integraatio integraatioloki lokiviesti nil nil ulkoinen-id)
      (kirjaa-epaonnistunut-integraatio integraatioloki lokiviesti nil nil ulkoinen-id))))

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
  (kirjaa-epaonnistunut-integraatio [this viesti lisatietoja tapahtumaid ulkoinen-id] (kirjaa-paattynyt-integraatio (:db this) viesti lisatietoja false tapahtumaid ulkoinen-id))
  (kirjaa-jms-viesti [this jarjestelma integraatio viesti-id suunta sisalto] (lokita-jms-viesti this jarjestelma integraatio viesti-id suunta sisalto))
  (kirjaa-lahteva-jms-kuittaus [this kuittaus tapahtuma-id onnistunut lisatietoja] (lokita-lahteva-jms-kuittaus this kuittaus tapahtuma-id onnistunut lisatietoja))
  (kirjaa-saapunut-jms-kuittaus [this kuittaus ulkoinen-id integraatio onnistunut] (lokita-saapunut-jms-kuittaus this kuittaus ulkoinen-id integraatio onnistunut)))