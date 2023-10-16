(ns harja.palvelin.integraatiot.velho.velho-komponentti
  (:require [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.velho.pot-lahetys :as pot-lahetys]
            [harja.palvelin.integraatiot.velho.varusteet :as varusteet]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.palvelin.tyokalut.lukot :as lukko]
            [harja.pvm :as pvm]))

(defprotocol PaallystysilmoituksenLahetys
  (laheta-kohde [this urakka-id kohde-id]))

(defprotocol VarustetoteumaHaku
  (hae-urakan-varustetoteumat [this tiedot])
  (tuo-velho-nimikkeisto [this])
  (hae-varusteen-historia [this tiedot]))

(defn suorita-ja-kirjaa-alku-loppu-ajat
  [funktio tunniste]
  (let [aloitusaika-ms (System/currentTimeMillis)]
    (log/info tunniste "suoritus alkoi")
    (let [onnistui? (try
                      (funktio)
                      (catch Throwable t (log/error "Virhe suoritettaessa" tunniste "Throwable:" t) false))]
      (log/info (str tunniste " suoritus päättyi "
                     (when-not onnistui? "epäonnistuneesti") ; Suoritus voi onnistua kokonaan, osittain tai ei ollenkaan.
                     ". Kesto: "
                     (float (/ (- (System/currentTimeMillis) aloitusaika-ms) 1000)) " sekuntia")))))

(defn tee-varustetoteuma-haku-tehtava-fn [{:keys [db] :as this} suoritusaika]
  (log/debug "Ajastetaan varustetoteumien tuonti Tievelhosta tehtäväksi joka päivä kello: " suoritusaika)
  (ajastettu-tehtava/ajasta-paivittain
    suoritusaika
    (fn [_]
      (lukko/yrita-ajaa-lukon-kanssa
        db
        "tuo-uudet-varustetoteumat-ja-urakat-velhosta"
        #(do
           (log/info "ajasta-paivittain :: varustetoteumien tuonti :: Alkaa " (pvm/nyt))
           (paivita-mhu-urakka-oidt-velhosta this)
           (tuo-uudet-varustetoteumat-velhosta this))))))

(defn tee-velho-nimikkeisto-tuonti-tehtava [{:keys [db :as this]} suoritusaika]
  (ajastettu-tehtava/ajasta-paivittain
    suoritusaika
    (fn [_]
      (lukko/yrita-ajaa-lukon-kanssa db
        "tuo-velhon-nimikkeisto"
        #(tuo-velho-nimikkeisto this)))))

(defrecord Velho [asetukset]
  component/Lifecycle
  (start [this]
    (let [varustetuonti-suoritusaika (:varuste-tuonti-suoritusaika asetukset)
          nimikkeistotuonti-suoritusaika (:varuste-tuonti-suoritusaika asetukset)]
      (log/info (str "Käynnistetään tuo-uudet-varustetoteumat-velhosta -komponentti. Suoritusaika: " varustetuonti-suoritusaika))
      (-> this
        (assoc :varustetoteuma-tuonti-tehtava (tee-varustetoteuma-haku-tehtava-fn this varustetuonti-suoritusaika))
        (assoc :velho-nimikkeisto-tuonti-tehtava (tee-velho-nimikkeisto-tuonti-tehtava this nimikkeistotuonti-suoritusaika)))))
  (stop [this]
    (log/info "Sammutetaan tuo-uudet-varustetoteumat-velhosta -komponentti.")
    (when-let [varustetoteuma-tuonti-tehtava-cancel (:varustetoteuma-tuonti-tehtava this)]
      (varustetoteuma-tuonti-tehtava-cancel))
    (dissoc this :varustetoteuma-tuonti-tehtava))

  PaallystysilmoituksenLahetys
  (laheta-kohde [this urakka-id kohde-id]
    (pot-lahetys/laheta-kohde-velhoon (:integraatioloki this) (:db this) asetukset urakka-id kohde-id))

  VarustetoteumaHaku
  (hae-urakan-varustetoteumat [this tiedot]
    (varusteet/hae-urakan-varustetoteumat (:integraatioloki this) (:db this) asetukset tiedot))
  (hae-varusteen-historia [this tiedot]
    (varusteet/hae-varusteen-historia (:integraatioloki this) (:db this) asetukset tiedot))
  (tuo-velho-nimikkeisto [this]
    (varusteet/tuo-velho-nimikkeisto this)))
