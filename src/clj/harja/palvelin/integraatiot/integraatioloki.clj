(ns harja.palvelin.integraatiot.integraatioloki
  (:require [taoensso.timbre :as log]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.kyselyt.integraatioloki :as integraatioloki]
            [clj-time.core :refer [weeks months ago]]
            [harja.kyselyt.konversio :as konversio]
            [com.stuartsierra.component :as component]
            [harja.fmt :as fmt])
  (:import (java.net InetAddress)))

(defprotocol IntegraatiolokiKirjaus
  (kirjaa-alkanut-integraatio [this jarjestelma integraation-nimi ulkoinen-id viesti])
  (kirjaa-onnistunut-integraatio [this viesti lisatietoja tapahtumaid ulkoinen-id])
  (kirjaa-epaonnistunut-integraatio [this viesti lisatietoja tapahtumaid ulkoinen-id])
  (kirjaa-jms-viesti [integraatioloki tapahtuma-id viesti-id suunta sisalto jono])
  (kirjaa-rest-viesti [integraatioloki tapahtuma-id suunta osoite sisaltotyyppi sisalto otsikko parametrit])
  (kirjaa-saapunut-jms-viesti [integraatioloki jarjestelma integraatio viesti-id viesti jono])
  (kirjaa-lahteva-jms-kuittaus [integraatioloki kuittaus tapahtuma-id onnistunut lisatietoja jono])
  (kirjaa-saapunut-jms-kuittaus [integraatioloki kuittaus ulkoinen-id integraatio onnistunut jono])
  (kirjaa-alkanut-tiedoston-haku [integraatiloki jarjestelma integraatio lahde]))

(defn tee-integraatiolokin-puhdistus-tehtava [this paivittainen-puhdistusaika]
  (if paivittainen-puhdistusaika
    (do
      (log/debug "Ajastetaan integraatiolokin puhdistus ajettavaksi joka päivä kello: " paivittainen-puhdistusaika)
      (ajastettu-tehtava/ajasta-paivittain
        paivittainen-puhdistusaika
        (fn [_]
          (let [aikarajaus-api (konversio/sql-timestamp (.toDate (-> 2 weeks ago)))
                aikarajaus-muut (konversio/sql-timestamp (.toDate (-> 3 months ago)))]
            (log/debug "Poistetaan API:n integraatiotapahtumat, jotka ovat alkaneet ennen:" aikarajaus-api)
            (integraatioloki/poista-ennen-paivamaaraa-kirjatut-tapahtumat! (:db this) aikarajaus-api "api")

            (log/debug "Poistetaan muut integraatiotapahtumat, jotka ovat alkaneet ennen:" aikarajaus-muut)
            (integraatioloki/poista-ennen-paivamaaraa-kirjatut-tapahtumat! (:db this) aikarajaus-muut nil)))))
    (fn [] ())))

(defn tee-jms-lokiviesti [suunta sisalto otsikko jono]
  {:suunta suunta
   :sisaltotyyppi "application/xml"
   :siirtotyyppi "JMS"
   :sisalto (if (map? sisalto) (str sisalto) sisalto)
   :osoite jono
   :otsikko (when otsikko (str otsikko))
   :parametrit nil})

(defn tee-rest-lokiviesti [suunta osoite sisaltotyyppi sisalto otsikko parametrit]
  {:suunta suunta
   :sisaltotyyppi sisaltotyyppi
   :siirtotyyppi "HTTP"
   :osoite osoite
   :sisalto (str sisalto)
   :otsikko (when otsikko (str otsikko))
   :parametrit parametrit})

(defn tee-tiedoston-hakuviesti [osoite]
  {:suunta "sisään"
   :sisaltotyyppi nil
   :siirtotyyppi nil
   :osoite osoite
   :sisalto nil
   :otsikko nil
   :parametrit nil})

(defn kirjaa-viesti [db tapahtumaid {:keys [osoite suunta sisaltotyyppi siirtotyyppi
                                            sisalto otsikko parametrit]}]
  (let [kasitteleva-palvelin (fmt/leikkaa-merkkijono 512
                                                     (.toString (InetAddress/getLocalHost)))]
    (integraatioloki/luo-integraatioviesti<!
     db tapahtumaid osoite suunta sisaltotyyppi siirtotyyppi
     sisalto otsikko parametrit kasitteleva-palvelin)))

(defn luo-alkanut-integraatio [db jarjestelma nimi ulkoinen-id viesti]
  (let [tapahtumaid (:id (integraatioloki/luo-integraatiotapahtuma<! db jarjestelma nimi ulkoinen-id))]
    (when viesti
      (kirjaa-viesti db tapahtumaid viesti))
    tapahtumaid))

(defn kirjaa-paattynyt-integraatio [db viesti lisatietoja onnistunut tapahtuma-id ulkoinen-id]
  (let [kasitellyn-tapahtuman-id
        (if tapahtuma-id
          (do
            (integraatioloki/merkitse-integraatiotapahtuma-paattyneeksi!
              db onnistunut lisatietoja tapahtuma-id)
            tapahtuma-id)
          (:id (integraatioloki/merkitse-integraatiotapahtuma-paattyneeksi-ulkoisella-idlla<!
                 db onnistunut lisatietoja ulkoinen-id)))]
    (when (and viesti kasitellyn-tapahtuman-id)
      (kirjaa-viesti db kasitellyn-tapahtuman-id viesti))))

(defn lokita-jms-viesti [db tapahtuma-id viesti-id suunta sisalto jono]
  (let [otsikko {:message-id viesti-id}
        lokiviesti (tee-jms-lokiviesti suunta sisalto otsikko jono)]
    (kirjaa-viesti db tapahtuma-id lokiviesti)
    (integraatioloki/aseta-ulkoinen-id-integraatiotapahtumalle! db viesti-id tapahtuma-id)))

(defn lokita-rest-viesti [db tapahtuma-id suunta osoite sisaltotyyppi sisalto otsikko parametrit]
  (let [lokiviesti (tee-rest-lokiviesti suunta osoite sisaltotyyppi sisalto otsikko parametrit)]
    (kirjaa-viesti db tapahtuma-id lokiviesti)))

(defn lokita-saapunut-jms-viesti [integraatioloki jarjestelma integraatio viesti-id viesti jono]
  (let [otsikko {:message-id viesti-id}
        lokiviesti (tee-jms-lokiviesti "sisään" viesti otsikko jono)]
    (kirjaa-alkanut-integraatio integraatioloki jarjestelma integraatio viesti-id lokiviesti)))

(defn lokita-lahteva-jms-kuittaus [integraatioloki kuittaus tapahtuma-id onnistunut lisatietoja jono]
  (let [lokiviesti (tee-jms-lokiviesti "ulos" kuittaus nil jono)]
    (if onnistunut
      (kirjaa-onnistunut-integraatio integraatioloki lokiviesti lisatietoja tapahtuma-id nil)
      (kirjaa-epaonnistunut-integraatio integraatioloki lokiviesti lisatietoja tapahtuma-id nil))))

(defn lokita-saapunut-jms-kuittaus [integraatioloki kuittaus ulkoinen-id integraatio onnistunut jono]
  (log/debug "Kirjataan saapunut kuittaus " kuittaus ", " ulkoinen-id ", " integraatio ", " onnistunut)
  (let [lokiviesti (tee-jms-lokiviesti "sisään" kuittaus nil jono)]
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
  (kirjaa-alkanut-integraatio [this jarjestelma integraation-nimi ulkoinen-id viesti]
    (luo-alkanut-integraatio (:db this) jarjestelma integraation-nimi ulkoinen-id viesti))
  (kirjaa-onnistunut-integraatio [this viesti lisatietoja tapahtumaid ulkoinen-id]
    (kirjaa-paattynyt-integraatio (:db this) viesti lisatietoja true tapahtumaid ulkoinen-id))
  (kirjaa-epaonnistunut-integraatio [this viesti lisatietoja tapahtumaid ulkoinen-id]
    (kirjaa-paattynyt-integraatio (:db this) viesti lisatietoja false tapahtumaid ulkoinen-id))
  (kirjaa-jms-viesti [this tapahtuma-id viesti-id suunta sisalto jono]
    (lokita-jms-viesti (:db this) tapahtuma-id viesti-id suunta sisalto jono))
  (kirjaa-rest-viesti [this tapahtuma-id suunta osoite sisaltotyyppi sisalto otsikko parametrit]
    (lokita-rest-viesti (:db this) tapahtuma-id suunta osoite sisaltotyyppi sisalto otsikko parametrit))
  (kirjaa-saapunut-jms-viesti [this jarjestelma integraatio viesti-id viesti jono]
    (lokita-saapunut-jms-viesti this jarjestelma integraatio viesti-id viesti jono))
  (kirjaa-lahteva-jms-kuittaus [this kuittaus tapahtuma-id onnistunut lisatietoja jono]
    (lokita-lahteva-jms-kuittaus this kuittaus tapahtuma-id onnistunut lisatietoja jono))
  (kirjaa-saapunut-jms-kuittaus [this kuittaus ulkoinen-id integraatio onnistunut jono]
    (lokita-saapunut-jms-kuittaus this kuittaus ulkoinen-id integraatio onnistunut jono))
  (kirjaa-alkanut-tiedoston-haku [this jarjestelma integraatio lahde]
    (lokita-alkanut-tiedoston-haku this jarjestelma integraatio lahde)))

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
        (let [[ulkoinen-id kuittaus onnistunut jono] argumentit]
          (kirjaa-saapunut-jms-kuittaus integraatioloki kuittaus ulkoinen-id integraatio onnistunut jono))

        :saapunut-jms-viesti
        (let [[viesti-id viesti jono] argumentit ]
          (kirjaa-saapunut-jms-viesti integraatioloki jarjestelma integraation-nimi viesti-id viesti jono))

        :lahteva-jms-kuittaus
        (let [[kuittaus tapahtuma-id onnistunut lisatietoja jono] argumentit]
          (kirjaa-lahteva-jms-kuittaus integraatioloki kuittaus tapahtuma-id onnistunut lisatietoja jono))

        :jms-viesti
        (let [[tapahtuma-id viesti-id suunta sisalto jono] argumentit]
          (kirjaa-jms-viesti integraatioloki tapahtuma-id viesti-id suunta sisalto jono))

        :rest-viesti
        (let [[tapahtuma-id suunta url sisaltotyyppi kutsudata otsikot parametrit] argumentit]
          (kirjaa-rest-viesti integraatioloki tapahtuma-id suunta url sisaltotyyppi kutsudata otsikot parametrit))

        :avain
        (str jarjestelma "-" integraation-nimi)))))
