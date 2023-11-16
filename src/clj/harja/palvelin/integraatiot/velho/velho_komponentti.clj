(ns harja.palvelin.integraatiot.velho.velho-komponentti
  (:require [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.velho.pot-lahetys :as pot-lahetys]
            [harja.palvelin.integraatiot.velho.varusteet :as varusteet]
            [harja.palvelin.integraatiot.velho.urakkatietojen-haku :as oid-haku]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.palvelin.tyokalut.lukot :as lukko]
            [harja.pvm :as pvm]))

(defprotocol PaallystysilmoituksenLahetys
  (laheta-kohde [this urakka-id kohde-id]))

(defprotocol VarustetoteumaHaku
  (hae-urakan-varustetoteumat [this tiedot])
  (tuo-velho-nimikkeisto [this])
  (hae-varusteen-historia [this tiedot]))

(defprotocol VelhoOidHaku
  (hae-velho-oidit [this]))

(defn tee-velho-nimikkeisto-tuonti-tehtava [{:keys [db] :as this} suoritusaika]
  (when suoritusaika
    (ajastettu-tehtava/ajasta-paivittain
      suoritusaika
      (fn [_]
        (lukko/yrita-ajaa-lukon-kanssa db
          "tuo-velhon-nimikkeisto"
          #(tuo-velho-nimikkeisto this))))))

(defn tee-velho-oid-tuonti-tehtava [{:keys [db] :as this} suoritusaika]
  (when suoritusaika
    (ajastettu-tehtava/ajasta-paivittain
      suoritusaika
      (fn [_]
        (lukko/yrita-ajaa-lukon-kanssa db
          "hae-velhon-oidit"
          #(hae-velho-oidit this))))))

(defrecord Velho [asetukset]
  component/Lifecycle
  (start [this]
    (let [nimikkeisto-tuonti-suoritusaika (:varuste-tuonti-suoritusaika asetukset)
          oid-tuonti-suoritusaika (:oid-tuonti-suoritusaika asetukset)]
      (-> this
        (assoc :velho-nimikkeisto-tuonti-tehtava (tee-velho-nimikkeisto-tuonti-tehtava this nimikkeisto-tuonti-suoritusaika))
        (assoc :velho-oid-tuonti-tehtava (tee-velho-oid-tuonti-tehtava this oid-tuonti-suoritusaika)))))

  (stop [this]
    (log/info "Sammutetaan tuo-uudet-varustetoteumat-velhosta -komponentti.")
    (when-let [nimikkeisto-tuonti-tehtava (:velho-nimikkeisto-tuonti-tehtava this)]
      (nimikkeisto-tuonti-tehtava))
    (when-let [oid-tuonti-tehtava (:velho-oid-tuonti-tehtava this)]
      (oid-tuonti-tehtava))
    (-> this
      (dissoc :varustetoteuma-tuonti-tehtava)
      (dissoc :velho-oid-tuonti-tehtava)
      (dissoc :velho-nimikkeisto-tuonti-tehtava)))

  PaallystysilmoituksenLahetys
  (laheta-kohde [this urakka-id kohde-id]
    (pot-lahetys/laheta-kohde-velhoon (:integraatioloki this) (:db this) asetukset urakka-id kohde-id))

  VelhoOidHaku
  (hae-velho-oidit [this]
    (oid-haku/hae-mh-urakoiden-oidit-ja-lyhytnimet (:integraatioloki this) (:db this) asetukset))

  VarustetoteumaHaku
  (hae-urakan-varustetoteumat [this tiedot]
    (varusteet/hae-urakan-varustetoteumat this tiedot))
  (hae-varusteen-historia [this tiedot]
    (varusteet/hae-varusteen-historia this tiedot))
  (tuo-velho-nimikkeisto [this]
    (varusteet/tuo-velho-nimikkeisto this)))
