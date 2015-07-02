(ns harja.palvelin.integraatiot.sampo
  (:require [taoensso.timbre :as log]
            [hiccup.core :refer [html]]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.sampo.vienti :as vienti]))

(defprotocol Maksueralahetys
  (laheta-maksuera-sampoon [this numero]))

(defn tee-sonja-kuittauskuuntelija [this kuittausjono-ulos]
  (log/debug "Käynnistetään Sonja kuittauskuuntelija kuuntelemaan jonoa: " kuittausjono-ulos)
  (sonja/kuuntele (:sonja this) kuittausjono-ulos
                  (fn [viesti]
                    (vienti/kasittele-kuittaus (:db this) viesti))))

(defn tee-paivittainen-lahetys-tehtava [this paivittainen-lahetysaika lahetysjono-ulos]
  (if paivittainen-lahetysaika
    (do
      (log/debug "Ajastetaan maksuerien ja kustannussuunnitelmien lähetys ajettavaksi joka päivä kello: " paivittainen-lahetysaika)
      (ajastettu-tehtava/ajasta-paivittain
        paivittainen-lahetysaika
        (fn [_] (vienti/aja-paivittainen-lahetys (:sonja this) (:db this) lahetysjono-ulos))))
    (fn [] ())))

(defrecord Sampo [lahetysjono-ulos kuittausjono-ulos paivittainen-lahetysaika]
  com.stuartsierra.component/Lifecycle
  (start [this]
    (assoc this :sonja-kuittauskuuntelija (tee-sonja-kuittauskuuntelija this kuittausjono-ulos)
                :paivittainen-lahetys-tehtava (tee-paivittainen-lahetys-tehtava this paivittainen-lahetysaika lahetysjono-ulos)))
  (stop [this]
    (let [poista-kuuntelija (:sonja-kuittauskuuntelija this)
          poista-paivittainen-lahetys-tehtava (:paivittainen-lahetys-tehtava this)]
      (poista-kuuntelija)
      (poista-paivittainen-lahetys-tehtava))
    this)

  Maksueralahetys
  (laheta-maksuera-sampoon [this numero]
    (let [maksueran-lahetys (vienti/laheta-maksuera (:sonja this) (:db this) lahetysjono-ulos numero)
          kustannussuunnitelman-lahetys (vienti/laheta-kustannussuunitelma (:sonja this) (:db this) lahetysjono-ulos numero)]
      {:maksuera             maksueran-lahetys
       :kustannussuunnitelma kustannussuunnitelman-lahetys})))
