(ns harja.palvelin.integraatiot.velho.velho-komponentti
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.velho.pot-lahetys :as pot-lahetys]
            [harja.palvelin.integraatiot.velho.varusteet :as varusteet]))

(defprotocol PaallystysilmoituksenLahetys
  (laheta-kohde [this urakka-id kohde-id]))

(defprotocol VarustetoteumaHaku
  (tuo-uudet-varustetoteumat-velhosta [this])
  (hae-mhu-urakka-oidt-velhosta [this]))

(defrecord Velho [asetukset]
  component/Lifecycle
  (start [this] this)
  (stop [this] this)

  PaallystysilmoituksenLahetys
  (laheta-kohde [this urakka-id kohde-id]
    (pot-lahetys/laheta-kohde-velhoon (:integraatioloki this) (:db this) asetukset urakka-id kohde-id))

  VarustetoteumaHaku
  (tuo-uudet-varustetoteumat-velhosta [this]
    (varusteet/tuo-uudet-varustetoteumat-velhosta (:integraatioloki this) (:db this) asetukset))
  (hae-mhu-urakka-oidt-velhosta [this]
    (varusteet/hae-mhu-urakka-oidt-velhosta (:integraatioloki this) (:db this) asetukset)))