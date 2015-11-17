(ns harja.palvelin.palvelut.maksuerat
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [harja.kyselyt.maksuerat :as q]
            [harja.palvelin.integraatiot.sampo.sampo-komponentti :as sampo]
            [harja.kyselyt.konversio :as konversio]
            [harja.domain.roolit :as roolit]))

(def aseta-kustannussuunnitelman-tila-xf
  (map #(assoc-in % [:kustannussuunnitelma :tila] (keyword (:tila (:kustannussuunnitelma %))))))

(def aseta-tyyppi-ja-tila-xf
  (map #(-> %
            (assoc-in [:maksuera :tyyppi] (keyword (:tyyppi (:maksuera %))))
            (assoc-in [:maksuera :tila] (keyword (:tila (:maksuera %)))))))

(def maksuera-xf
  (comp (map konversio/alaviiva->rakenne)
        aseta-kustannussuunnitelman-tila-xf
        aseta-tyyppi-ja-tila-xf))

(defn hae-maksueran-ja-kustannussuunnitelman-tilat [db maksueranumero]
  (let [tilat (q/hae-maksueran-ja-kustannussuunnitelman-tilat db maksueranumero)
        muunnetut-tilat (into []
                              maksuera-xf
                              tilat)]
    (assoc (first muunnetut-tilat) :numero maksueranumero)))

(defn laheta-maksuera-sampoon
  [sampo db _ maksueranumero]
  (assert (not (nil? maksueranumero)) " maksueranumero ei saa olla nil.")
  (log/debug "Lähetetään maksuera Sampoon, jonka numero on: " maksueranumero)
  (let [tulos (sampo/laheta-maksuera-sampoon sampo maksueranumero)
        tilat (hae-maksueran-ja-kustannussuunnitelman-tilat db maksueranumero)]
    (log/debug "Maksueran (numero: " maksueranumero " lähetyksen tulos:" tulos)
    (log/debug "Maksuerän tilat" tilat)
    tilat))

(comment
  ;; Maksuerä rivi esimerkki:
  {:toimenpideinstanssi_nimi "Oulu Sorateiden hoito TP 2014-2019",
   :numero 40,
   :toimenpideinstanssi_loppupvm #inst "2019-09-29T21:00:00.000-00:00",
   :maksuera_tyyppi "muu",
   :sopimus_sampoid "2H16339/01",
   :maksuera_lahetetty nil,
   :maksuera_tila nil,
   :kustannussuunnitelma_tila nil,
   :kustannussuunnitelma_summa 1M,
   :toimenpideinstanssi_alkupvm #inst "2014-09-30T21:00:00.000-00:00",
   :maksuera_nimi "Oulu Sorateiden hoito TP ME 2014-2019",
   :kustannussuunnitelma_lahetetty nil,
   :toimenpideinstanssi_id 6,
   :maksuera_summa 0M})

(defn hae-urakan-maksuerat
  "Palvelu, joka palauttaa urakan maksuerät."
  [db user urakka-id]
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (log/debug "Haetaan maksuerät urakalle: " urakka-id)
  (let [tulos (into []
                    maksuera-xf
                    (q/hae-urakan-maksuerat db urakka-id))]
    (log/info "TULOS: " (pr-str tulos))
    tulos))

(defn laheta-maksuerat-sampoon
  "Palvelu, joka lähettää annetut maksuerät Sampoon. Ei vaadi erillisoikeuksia."
  [sampo db user maksueranumerot]
  (mapv (fn [maksueranumero]
          (laheta-maksuera-sampoon sampo db user maksueranumero))
        maksueranumerot))


(defrecord Maksuerat []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :hae-urakan-maksuerat (fn [user urakka-id]
                                              (hae-urakan-maksuerat (:db this) user urakka-id)))

    (julkaise-palvelu (:http-palvelin this)
                      :laheta-maksuerat-sampoon (fn [user maksueranumerot]
                                                  (laheta-maksuerat-sampoon (:sampo this) (:db this) user maksueranumerot)))
    this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :hae-urakan-maksuerat)
    (poista-palvelu (:http-palvelin this) :laheta-maksuerat-sampoon)
    this))
