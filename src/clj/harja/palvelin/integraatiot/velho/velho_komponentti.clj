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
    "tuo-uudet-varustetoteumat-velhosta"
    #(suorita-ja-kirjaa-alku-loppu-ajat
       (fn [] (varusteet/tuo-uudet-varustetoteumat-velhosta integraatioloki db asetukset))
       "tuo-uudet-varustetoteumat-velhosta")))

(defn tee-varustetoteuma-haku-tehtava-fn [{:keys [db asetukset integraatioloki]} suoritusaika]
  (if suoritusaika
    (do
      (log/debug "Ajastetaan varustetoteumien tuonti Tievelhosta tehtäväksi joka päivä kello: " (-> asetukset :velho :varustetoteuma-suoritusaika))
      (ajastettu-tehtava/ajasta-paivittain
        suoritusaika
        (do
          (log/info "ajasta-paivittain :: varustetoteumien tuonti :: Alkaa " (pvm/nyt))
          (fn [_] (hae-varustetoteumat-velhosta integraatioloki db asetukset)))))
    (constantly nil)))

(defrecord Velho [asetukset]
  component/Lifecycle
  (start [this]
    (let [suoritusaika (:varuste-tuonti-suoritusaika asetukset)]
      (log/info (str "Käynnistetään tuo-uudet-varustetoteumat-velhosta -komponentti. Suoritusaika: " suoritusaika))
      (assoc this :varustetoteuma-tuonti-tehtava (tee-varustetoteuma-haku-tehtava-fn this suoritusaika))))
  (stop [this]
    (log/info "Sammutetaan tuo-uudet-varustetoteumat-velhosta -komponentti.")
    (let [varustetoteuma-tuonti-tehtava-cancel (:varustetoteuma-tuonti-tehtava this)]
      (varustetoteuma-tuonti-tehtava-cancel))
    (dissoc this :varustetoteuma-tuonti-tehtava))

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

;; Esimerkki miten testiajojen suorittaminen onnistuu
(comment 
  (def j harja.palvelin.main/harja-jarjestelma)
  (def asetukset (get-in j [:velho-integraatio :asetukset]))
  (varusteet/paivita-mhu-urakka-oidt-velhosta (:integraatioloki j) (:db j) asetukset)
  (varusteet/tuo-uudet-varustetoteumat-velhosta (:integraatioloki j) (:db j) asetukset))