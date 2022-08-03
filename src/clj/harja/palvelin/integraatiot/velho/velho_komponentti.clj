(ns harja.palvelin.integraatiot.velho.velho-komponentti
  (:require [harja.palvelin.integraatiot.velho.pot-lahetys :as pot-lahetys]
            [harja.palvelin.integraatiot.velho.varusteet :as varusteet]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.palvelin.tyokalut.lukot :as lukko]
            [harja.pvm :as pvm]
            [harja.tyokalut.yleiset :as yleiset]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]))

(defprotocol PaallystysilmoituksenLahetys
  (laheta-kohde [this urakka-id kohde-id]))

(defprotocol VarustetoteumaHaku
  (tuo-uudet-varustetoteumat-velhosta [this])
  (paivita-mhu-urakka-oidt-velhosta [this]))

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

(defn hae-varustetoteumat-velhosta [integraatioloki db asetukset]
  (lukko/yrita-ajaa-lukon-kanssa
    db
    "varustetoteuma-haku"
    #(suorita-ja-kirjaa-alku-loppu-ajat
       (fn [] (varusteet/tuo-uudet-varustetoteumat-velhosta integraatioloki db asetukset))
       "tuo-uudet-varustetoteumat-velhosta")))

(defn tee-varustetoteuma-haku-tehtava-fn [{:keys [db asetukset integraatioloki]} suoritusaika]
  (if suoritusaika
    (do
      (log/debug "Ajastetaan varustetoteumien haku Tievelhosta tehtäväksi joka päivä kello: " (-> asetukset :velho :varustetoteuma-suoritusaika))
      (ajastettu-tehtava/ajasta-paivittain
        suoritusaika
        (do
          (log/info "ajasta-paivittain :: varustetoteumien haku :: Alkaa " (pvm/nyt))
          (fn [_] (hae-varustetoteumat-velhosta integraatioloki db asetukset)))))
    (fn [_])))

(defrecord Velho [asetukset]
  component/Lifecycle
  (start [this]
    (let [suoritusaika  (:varuste-haku-suoritusaika asetukset)]
      (log/info (str "Käynnistetään varustetoteuma-haku-komponentti. Suoritusaika: " suoritusaika))
      (assoc this :varustetoteuma-haku-tehtava (tee-varustetoteuma-haku-tehtava-fn this suoritusaika)))
    this)
  (stop [this]
    (log/info "Sammutetaan varustetoteuma-haku-komponentti.")
    (:varustetoteuma-haku-tehtava this)
    this)

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