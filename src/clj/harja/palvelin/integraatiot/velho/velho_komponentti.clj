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
    (let [onnistui? (try
                      (funktio)
                      (catch Throwable t (log/error "Virhe suoritettaessa" tunniste "Throwable:" t) false))]
      (log/info (str tunniste " suoritus päättyi "
                     (when-not onnistui? "epäonnistuneesti") ; Suoritus voi onnistua kokonaan, osittain tai ei ollenkaan.
                     ". Kesto: "
                     (float (/ (- (System/currentTimeMillis) aloitusaika-ms) 1000)) " sekuntia")))))

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
      ;; Replistä:
      ;; (def asetukset (harja.tyokalut.yleiset/prettydenty (get-in harja.palvelin.main/harja-jarjestelma [:velho-integraatio :asetukset])))
      ;; (varusteet/tuo-uudet-varustetoteumat-velhosta (:integraatioloki harja.palvelin.main/harja-jarjestelma) (:db harja.palvelin.main/harja-jarjestelma) asetukset)
      "tuo-uudet-varustetoteumat-velhosta"))
  (paivita-mhu-urakka-oidt-velhosta [this]
    (suorita-ja-kirjaa-alku-loppu-ajat
      #(varusteet/paivita-mhu-urakka-oidt-velhosta (:integraatioloki this) (:db this) asetukset)
      ;; Replistä:
      ;; (def asetukset (harja.tyokalut.yleiset/prettydenty (get-in harja.palvelin.main/harja-jarjestelma [:velho-integraatio :asetukset])))
      ;; (varusteet/paivita-mhu-urakka-oidt-velhosta (:integraatioloki harja.palvelin.main/harja-jarjestelma) (:db harja.palvelin.main/harja-jarjestelma) asetukset)
      "paivita-mhu-urakka-oidt-velhosta")))