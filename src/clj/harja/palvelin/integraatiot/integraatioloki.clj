(ns harja.palvelin.integraatiot.integraatioloki
  (:require [taoensso.timbre :as log]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.kyselyt.integraatioloki :as integraatioloki]
            [clj-time.core :refer [months ago]]
            [harja.kyselyt.konversio :as konversio]
            [com.stuartsierra.component :as component]))

(defprotocol IntegraatiolokiKirjaus
  (kirjaa-alkanut-integraatio [this jarjestelma integraation-nimi ulkoinen-id viesti])
  (kirjaa-onnistunut-integraatio [this viesti lisatietoja tapahtumaid ulkoinen-id])
  (kirjaa-epaonnistunut-integraatio [this viesti lisatietoja tapahtumaid ulkoinen-id])
  (kirjaa-jms-viesti [integraatioloki tapahtuma-id viesti-id suunta sisalto])
  (kirjaa-rest-viesti [integraatioloki tapahtuma-id suunta osoite sisaltotyyppi sisalto otsikko parametrit])
  (kirjaa-saapunut-jms-viesti [integraatioloki jarjestelma integraatio viesti-id viesti])
  (kirjaa-lahteva-jms-kuittaus [integraatioloki kuittaus tapahtuma-id onnistunut lisatietoja])
  (kirjaa-saapunut-jms-kuittaus [integraatioloki kuittaus ulkoinen-id integraatio onnistunut])
  (kirjaa-alkanut-tiedoston-haku [integraatiloki jarjestelma integraatio lahde]))

(defn tee-integraatiolokin-puhdistus-tehtava [this paivittainen-puhdistusaika]
  (if paivittainen-puhdistusaika
    (do
      (log/debug "Ajastetaan integraatiolokin puhdistus ajettavaksi joka päivä kello: " paivittainen-puhdistusaika)
      (ajastettu-tehtava/ajasta-paivittain
        paivittainen-puhdistusaika
        (fn [_]
          (let [aikarajaus (konversio/sql-timestamp (.toDate (-> 1 months ago)))]
            (log/debug "Poistetaan kaikki integraatiotapahtumat, jotka ovat alkaneet ennen:" aikarajaus)
            (integraatioloki/poista-ennen-paivamaaraa-kirjatut-tapahtumat! (:db this) aikarajaus)))))
    (fn [] ())))

(defn tee-jms-lokiviesti [suunta sisalto otsikko]
  {:suunta        suunta
   :sisaltotyyppi "application/xml"
   :siirtotyyppi  "JMS"
   :sisalto       sisalto
   :otsikko       (when otsikko (str otsikko))
   :parametrit    nil})

(defn tee-rest-lokiviesti [suunta osoite sisaltotyyppi sisalto otsikko parametrit]
  {:suunta        suunta
   :sisaltotyyppi sisaltotyyppi
   :siirtotyyppi  "HTTP"
   :osoite        osoite
   :sisalto       (str sisalto)
   :otsikko       (when otsikko (str otsikko))
   :parametrit    parametrit})

(defn tee-tiedoston-hakuviesti [osoite]
  {:suunta        "sisään"
   :sisaltotyyppi nil
   :siirtotyyppi  nil
   :osoite        osoite
   :sisalto       nil
   :otsikko       nil
   :parametrit    nil})

(defn kirjaa-viesti [db tapahtumaid {:keys [osoite suunta sisaltotyyppi siirtotyyppi sisalto otsikko parametrit]}]
  (integraatioloki/luo-integraatioviesti<! db tapahtumaid osoite suunta sisaltotyyppi siirtotyyppi sisalto otsikko parametrit))

(defn luo-alkanut-integraatio [db jarjestelma nimi ulkoinen-id viesti]
  (let [tapahtumaid (:id (integraatioloki/luo-integraatiotapahtuma<! db jarjestelma nimi ulkoinen-id))]
    (when viesti
      (kirjaa-viesti db tapahtumaid viesti))
    tapahtumaid))

(defn kirjaa-paattynyt-integraatio [db viesti lisatietoja onnistunut tapahtuma-id ulkoinen-id]
  (let [kasitellyn-tapahtuman-id
        (if tapahtuma-id
          (do
            (integraatioloki/merkitse-integraatiotapahtuma-paattyneeksi! db onnistunut lisatietoja tapahtuma-id)
            tapahtuma-id)
          (:id (integraatioloki/merkitse-integraatiotapahtuma-paattyneeksi-ulkoisella-idlla<! db onnistunut lisatietoja ulkoinen-id)))]
    (when (and viesti kasitellyn-tapahtuman-id)
      (kirjaa-viesti db kasitellyn-tapahtuman-id viesti))))

(defn lokita-jms-viesti [db tapahtuma-id viesti-id suunta sisalto]
  (let [otsikko {:message-id viesti-id}
        lokiviesti (tee-jms-lokiviesti suunta sisalto otsikko)]
    (kirjaa-viesti db tapahtuma-id lokiviesti)
    (integraatioloki/aseta-ulkoinen-id-integraatiotapahtumalle! db viesti-id tapahtuma-id)))

(defn lokita-rest-viesti [db tapahtuma-id suunta osoite sisaltotyyppi sisalto otsikko parametrit]
  (let [lokiviesti (tee-rest-lokiviesti suunta osoite sisaltotyyppi sisalto otsikko parametrit)]
    (kirjaa-viesti db tapahtuma-id lokiviesti)))

(defn lokita-saapunut-jms-viesti [integraatioloki jarjestelma integraatio viesti-id viesti]
  (let [otsikko {:message-id viesti-id}
        lokiviesti (tee-jms-lokiviesti "sisään" viesti otsikko)]
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

(defn lokita-alkanut-tiedoston-haku [integraatiloki jarjestelma integraatio lahde]
  (kirjaa-alkanut-integraatio integraatiloki jarjestelma integraatio nil (tee-tiedoston-hakuviesti lahde)))

(defrecord Integraatioloki [paivittainen-puhdistusaika]
  component/Lifecycle
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
  (kirjaa-jms-viesti [this tapahtuma-id viesti-id suunta sisalto] (lokita-jms-viesti (:db this) tapahtuma-id viesti-id suunta sisalto))
  (kirjaa-rest-viesti [this tapahtuma-id suunta osoite sisaltotyyppi sisalto otsikko parametrit] (lokita-rest-viesti (:db this) tapahtuma-id suunta osoite sisaltotyyppi sisalto otsikko parametrit))
  (kirjaa-saapunut-jms-viesti [this jarjestelma integraatio viesti-id viesti] (lokita-saapunut-jms-viesti this jarjestelma integraatio viesti-id viesti))
  (kirjaa-lahteva-jms-kuittaus [this kuittaus tapahtuma-id onnistunut lisatietoja] (lokita-lahteva-jms-kuittaus this kuittaus tapahtuma-id onnistunut lisatietoja))
  (kirjaa-saapunut-jms-kuittaus [this kuittaus ulkoinen-id integraatio onnistunut] (lokita-saapunut-jms-kuittaus this kuittaus ulkoinen-id integraatio onnistunut))
  (kirjaa-alkanut-tiedoston-haku [this jarjestelma integraatio lahde] (lokita-alkanut-tiedoston-haku this jarjestelma integraatio lahde)))

(defn lokittaja [integraatioloki db jarjestelma integraation-nimi]
  (fn [operaatio & argumentit]
    (let [integraatio (integraatioloki/hae-integraation-id db jarjestelma integraation-nimi)]
      (case operaatio
        :alkanut
        (let [[ulkoinen-id viesti] argumentit]
          (kirjaa-alkanut-integraatio integraatioloki jarjestelma integraation-nimi ulkoinen-id viesti))

        :onnistunut
        (let [[viesti lisatietoja tapahtumaid ulkoinen-id] argumentit]
          (kirjaa-onnistunut-integraatio integraatioloki viesti lisatietoja tapahtumaid ulkoinen-id))

        :epaonnistunut
        (let [[viesti lisatietoja tapahtumaid ulkoinen-id] argumentit]
          (kirjaa-epaonnistunut-integraatio integraatioloki viesti lisatietoja tapahtumaid ulkoinen-id))

        :saapunut-jms-kuittaus
        (let [[ulkoinen-id kuittaus onnistunut] argumentit]
          (kirjaa-saapunut-jms-kuittaus integraatioloki kuittaus ulkoinen-id integraatio onnistunut))

        :saapunut-jms-viesti
        (let [[viesti-id viesti] argumentit]
          (kirjaa-saapunut-jms-viesti integraatioloki jarjestelma integraation-nimi viesti-id viesti))

        :lahteva-jms-kuittaus
        (let [[kuittaus tapahtuma-id onnistunut lisatietoja] argumentit]
          (kirjaa-lahteva-jms-kuittaus integraatioloki kuittaus tapahtuma-id onnistunut lisatietoja))

        :jms-viesti
        (let [[tapahtuma-id viesti-id suunta sisalto]argumentit]
          (kirjaa-jms-viesti integraatioloki tapahtuma-id viesti-id suunta sisalto))))))