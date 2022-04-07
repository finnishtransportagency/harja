(ns harja.palvelin.integraatiot.velho.velho-komponentti
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.velho.pot-lahetys :as pot-lahetys]
            [harja.palvelin.integraatiot.velho.varusteet :as varusteet]
            [harja.pvm :as pvm]))

(defprotocol PaallystysilmoituksenLahetys
  (laheta-kohde [this urakka-id kohde-id]))

(defprotocol VarustetoteumaHaku
  (tuo-uudet-varustetoteumat-velhosta [this])
  (paivita-mhu-urakka-oidt-velhosta [this]))

(defn suorita-ja-kirjaa-alku-loppu-ajat
  [funktio tunniste]
  (let [aloitusaika-ms (System/currentTimeMillis)]
    (log/info tunniste "suoritus alkoi")
    (try
      (funktio)
      (catch Throwable t (log/error "Virhe suoritettaessa" tunniste "Throwable:" t)))
    (log/info (str tunniste " suoritus päättyi. Kesto: "
                   (float (/ (- (System/currentTimeMillis) aloitusaika-ms) 1000)) " sekuntia"))))

(defrecord Velho [asetukset]
  component/Lifecycle
  (start [this] this)
  (stop [this] this)

  PaallystysilmoituksenLahetys
  (laheta-kohde [this urakka-id kohde-id]
    (pot-lahetys/laheta-kohde-velhoon (:integraatioloki this) (:db this) asetukset urakka-id kohde-id))

  VarustetoteumaHaku
  (tuo-uudet-varustetoteumat-velhosta [this]
    (suorita-ja-kirjaa-alku-loppu-ajat
      #(varusteet/tuo-uudet-varustetoteumat-velhosta (:integraatioloki this) (:db this) asetukset)
      "tuo-uudet-varustetoteumat-velhosta"))
  (paivita-mhu-urakka-oidt-velhosta [this]
    (suorita-ja-kirjaa-alku-loppu-ajat
      #(varusteet/paivita-mhu-urakka-oidt-velhosta (:integraatioloki this) (:db this) asetukset)
      "paivita-mhu-urakka-oidt-velhosta")))