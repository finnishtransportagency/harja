(ns harja.palvelin.integraatiot.sonja.sahkoposti
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.integraatiopisteet.jms :as jms]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.sonja.sahkoposti.sanomat :as sanomat]
            [harja.kyselyt.integraatiot :as q]
            [harja.tyokalut.xml :as xml]
            [taoensso.timbre :as log]))

(defprotocol Sahkoposti
  (rekisteroi-kuuntelija! [this kuuntelija-fn]
                          "Rekisteröi funktion, joka vastaanottaa sähköpostiviestit.")
  (laheta-viesti! [this lahettaja vastaanottaja otsikko sisalto]))

(defn- lokittaja [{il :integraatioloki db :db} nimi]
  (integraatioloki/lokittaja il db "sonja" nimi))

(defn- tee-vastaanottokuuntelija [{:keys [db sonja] :as this} sahkoposti-sisaan-jono sahkoposti-sisaan-kuittausjono kuuntelijat]
  (jms/kuuntele-ja-kuittaa (lokittaja this "sahkoposti-vastaanotto") sonja
                           sahkoposti-sisaan-jono sahkoposti-sisaan-kuittausjono
                           sanomat/lue-sahkoposti sanomat/kirjoita-kuittaus
                           #(try
                              (doseq [kuuntelija @kuuntelijat]
                                (kuuntelija %))
                              (sanomat/kuittaus % nil)
                              (catch Exception e
                                (sanomat/kuittaus % [(.getMessage e)])))))

(defn- tee-lahetyksen-kuittauskuuntelija [{:keys [db sonja] :as this} sahkoposti-ulos-kuittausjono]
  (let [integraatio (q/integraation-id db "sonja" "sahkoposti-lahetys")]
    (jms/kuittausjonokuuntelija (lokittaja this "sahkoposti-lahetys") (:sonja this) sahkoposti-ulos-kuittausjono
                                sanomat/lue-kuittaus :viesti-id :onnistunut
                                (fn [viesti viesti-id onnistunut]
                                  (q/kuittaa-integraatiotapahtuma! db onnistunut "" integraatio viesti-id)))))

(defrecord SonjaSahkoposti [jonot kuuntelijat]
  component/Lifecycle
  (start [{sonja :sonja :as this}]
    (assoc this
           :saapuva (tee-vastaanottokuuntelija this (:sahkoposti-sisaan-jono jonot) (:sahkoposti-sisaan-kuittausjono jonot) kuuntelijat)
           :lahteva (tee-lahetyksen-kuittauskuuntelija this (:sahkoposti-ulos-kuittausjono jonot))
           :jms-lahettaja (jms/jonolahettaja (lokittaja this "sahkoposti-lahetys")
                                             sonja
                                             (:sahkoposti-ulos-jono jonot))))

  (stop [this]
    ((:saapuva this))
    ((:lahteva this))
    (dissoc this :saapuva :lahteva))

  Sahkoposti
  (rekisteroi-kuuntelija! [this kuuntelija-fn]
    (swap! kuuntelijat conj kuuntelija-fn)
    #(swap! kuuntelijat disj kuuntelija-fn))

  (laheta-viesti! [{lahettaja :jms-lahettaja} lahettaja vastaanottaja otsikko sisalto]
    (let [viesti-id (str (java.util.UUID/randomUUID))
          sahkoposti (sanomat/sahkoposti viesti-id lahettaja vastaanottaja otsikko sisalto)
          viesti (xml/tee-xml-sanoma sahkoposti)]
      (lahettaja viesti viesti-id))))

(defn luo-sahkoposti [jonot]
  (->SonjaSahkoposti jonot (atom #{})))



