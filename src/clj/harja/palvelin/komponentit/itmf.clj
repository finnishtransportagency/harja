(ns harja.palvelin.komponentit.itmf
  (:require [clojure.string :as clj-str]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.jms-clientit.sonic :as sonic]
            [harja.palvelin.integraatiot.jms-clientit.apache-classic :as activemq]
            [harja.palvelin.tyokalut.komponentti-protokollat :as kp]
            [harja.palvelin.asetukset :refer [ominaisuus-kaytossa?]]
            [harja.palvelin.integraatiot.jms :as jms]))

(defrecord FeikkiITMF []
  component/Lifecycle
  (start [this] this)
  (stop [this] this)

  jms/JMSClientYhdista
  (-yhdista! [this yhdistamisen-tila])
  (-sammuta-yhteys! [this yhdistamisen-tila])

  jms/JMS
  (kuuntele! [this jonon-nimi kuuntelija-fn jarjestelma]
    (log/warn "Feikki ITMF, aloita muka kuuntelu jonossa: " jonon-nimi)
    (constantly nil))
  (kuuntele! [this jonon-nimi kuuntelija-fn]
    (jms/kuuntele! this jonon-nimi kuuntelija-fn nil))
  (laheta [this jonon-nimi viesti otsikot jarjestelma]
    (log/warn "Feikki ITMF, lähetä muka viesti jonoon: " jonon-nimi)
    (str "ID:" (System/currentTimeMillis)))
  (laheta [this jonon-nimi viesti otsikot]
    (jms/laheta this jonon-nimi viesti otsikot nil))
  (laheta [this jonon-nimi viesti]
    (jms/laheta this jonon-nimi viesti nil nil))
  (sammuta-lahettaja [this jonon-nimi jarjestelma]
    (log/warn "Feikki ITMF samuttaa muka viesti jonon: " jonon-nimi))
  (sammuta-lahettaja [this jonon-nimi]
    (jms/sammuta-lahettaja this jonon-nimi (jms/oletusjarjestelmanimi jonon-nimi)))
  (kasky [this kaskyn-tiedot]
    (log/warn "Feikki ITMF sai käskyn"))
  kp/IStatus
  (-status [this]
    (log/warn "Feikki ITMF tila")
    {::kp/kaikki-ok? true
     ::kp/tiedot true}))

(defn luo-oikea-itmf [asetukset]
  (cond
    (not (ominaisuus-kaytossa? :itmf)) (->FeikkiITMF)
    (= (:tyyppi asetukset) :sonicmq) (sonic/->Sonic "itmf" asetukset)
    (= (:tyyppi asetukset) :activemq) (activemq/->ApacheClassic "itmf" asetukset)))

(defn luo-itmf [asetukset]
  (if (and asetukset (not (clj-str/blank? (:url asetukset))))
    (luo-oikea-itmf asetukset)
    (jms/luo-feikki-jms)))
