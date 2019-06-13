(ns harja.palvelin.integraatiot.sampo.sampo-komponentti
  (:require [taoensso.timbre :as log]
            [hiccup.core :refer [html]]
            [com.stuartsierra.component :as component]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.sampo.tuonti :as tuonti]
            [harja.palvelin.integraatiot.sampo.vienti :as vienti]
            [harja.kyselyt.maksuerat :as q-maksuerat]
            [harja.kyselyt.urakat :as q-urakat]
            [harja.palvelin.integraatiot.sampo.kasittely.maksuerat :as maksuerat]
            [harja.palvelin.integraatiot.sampo.kasittely.kustannussuunnitelmat :as kustannussuunnitelmat]))

(defprotocol Maksueralahetys
  (laheta-maksuera-sampoon [this numero]))

(defn tee-sonja-viestikuuntelija [{:keys [db integraatioloki sonja]} lahetysjono-sisaan kuittausjono-sisaan]
  (log/debug "Käynnistetään Sampon Sonja viestikuuntelija kuuntelemaan jonoa: " lahetysjono-sisaan)
  (sonja/kuuntele! sonja lahetysjono-sisaan
                  (fn [viesti]
                    (tuonti/kasittele-viesti sonja integraatioloki db kuittausjono-sisaan viesti))))

(defn tee-sonja-kuittauskuuntelija [{:keys [db integraatioloki sonja]} kuittausjono-ulos]
  (log/debug "Käynnistetään Sampon Sonja kuittauskuuntelija kuuntelemaan jonoa: " kuittausjono-ulos)
  (sonja/kuuntele! sonja kuittausjono-ulos
                  (fn [viesti]
                    (vienti/kasittele-kuittaus integraatioloki db viesti kuittausjono-ulos))))

(defn tee-paivittainen-lahetys-tehtava [{:keys [db integraatioloki sonja]} paivittainen-lahetysaika lahetysjono-ulos]
  (if paivittainen-lahetysaika
    (do
      (log/debug "Ajastetaan maksuerien ja kustannussuunnitelmien lähetys ajettavaksi joka päivä kello: "
                 paivittainen-lahetysaika)
      (ajastettu-tehtava/ajasta-paivittain
        paivittainen-lahetysaika
        (fn [_] (vienti/aja-paivittainen-lahetys sonja integraatioloki db lahetysjono-ulos))))
    (constantly nil)))

(defrecord Sampo [lahetysjono-sisaan kuittausjono-sisaan lahetysjono-ulos kuittausjono-ulos paivittainen-lahetysaika]
  component/Lifecycle
  (start [this]
    (log/debug "Käynnistetään Sampo-komponentti")
    (assoc this :sonja-viestikuuntelija (tee-sonja-viestikuuntelija this lahetysjono-sisaan kuittausjono-sisaan)
                :sonja-kuittauskuuntelija (tee-sonja-kuittauskuuntelija this kuittausjono-ulos)
                :paivittainen-lahetys-tehtava (tee-paivittainen-lahetys-tehtava this paivittainen-lahetysaika
                                                                                lahetysjono-ulos)))
  (stop [this]
    (let [poista-viestikuuntelija (:sonja-viestikuuntelija this)
          poista-kuittauskuuntelija (:sonja-kuittauskuuntelija this)
          poista-paivittainen-lahetys-tehtava (:paivittainen-lahetys-tehtava this)]
      (poista-viestikuuntelija)
      (poista-kuittauskuuntelija)
      (poista-paivittainen-lahetys-tehtava))
    this)

  Maksueralahetys
  (laheta-maksuera-sampoon [{:keys [sonja db integraatioloki]} numero]
    (let [urakkaid (q-maksuerat/hae-maksueran-urakka db numero)
          summat (q-maksuerat/hae-urakan-maksueran-summat db urakkaid)
          maksueran-lahetys (maksuerat/laheta-maksuera sonja integraatioloki db lahetysjono-ulos numero summat)
          kustannussuunnitelman-lahetys (kustannussuunnitelmat/laheta-kustannussuunitelma sonja
                                                                                          integraatioloki
                                                                                          db
                                                                                          lahetysjono-ulos
                                                                                          numero)]
      {:maksuera             maksueran-lahetys
       :kustannussuunnitelma kustannussuunnitelman-lahetys})))
