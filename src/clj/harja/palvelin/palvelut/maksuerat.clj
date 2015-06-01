(ns harja.palvelin.palvelut.maksuerat
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [harja.kyselyt.maksuerat :as q]
            [harja.palvelin.integraatiot.sampo :as sampo]
            [harja.kyselyt.konversio :as konversio]
            [harja.palvelin.oikeudet :as oikeudet]))

(declare hae-urakan-maksuerat)
(declare laheta-maksuerat-sampoon)

(defrecord Maksuerat []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :hae-urakan-maksuerat (fn [user urakka-id]
                                              (hae-urakan-maksuerat (:db this) user urakka-id)))

    (julkaise-palvelu (:http-palvelin this)
                      :laheta-maksuerat-sampoon (fn [user maksueranumerot]
                                                  (laheta-maksuerat-sampoon (:sampo this) user maksueranumerot)))
    this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :hae-urakan-maksuerat)
    (poista-palvelu (:http-palvelin this) :laheta-maksuerat-sampoon)
    this))

(def aseta-kustannussuunnitelman-summa-xf
  (map
    #(case (:tyyppi %)
      "kokonaishintainen" (if-let [summa (:summa (:kokonaishintaisettyot %))]
                            (assoc % :kustannussuunnitelma-summa (double summa))
                            (assoc % :kustannussuunnitelma-summa 0))
      "yksikkohintainen" (if-let [summa (:summa (:yksikkohintaisettyot %))]
                           (assoc % :kustannussuunnitelma-summa (double summa))
                           (assoc % :kustannussuunnitelma-summa 0))
      (assoc % :kustannussuunnitelma-summa 1))))

(def aseta-tyyppi-ja-tila-xf
  (map #(do (log/debug "Rivi on:" %)
            (assoc % :tyyppi (keyword (:tyyppi %))
                     :tila (keyword (:tila %))))))

(def maksuera-xf
  (comp (map konversio/alaviiva->rakenne)
        aseta-kustannussuunnitelman-summa-xf
        aseta-tyyppi-ja-tila-xf))

(defn hae-urakan-maksuerat
  "Palvelu, joka palauttaa urakan maksuerät."
  [db user urakka-id]
  (oikeudet/vaadi-lukuoikeus-urakkaan user urakka-id)
  (log/debug "Haetaan maksuerät urakalle: " urakka-id)
  (into []
        maksuera-xf
        (q/hae-urakan-maksuerat db urakka-id)))

(defn laheta-maksuera-sampoon
  [sampo user maksueranumero]
  (log/debug "Lähetetään maksuera Sampoon, jonka numero on: " maksueranumero)
  (sampo/laheta-maksuera-sampoon sampo maksueranumero))

(defn laheta-maksuerat-sampoon
  "Palvelu, joka lähettää annetut maksuerät Sampoon. Ei vaadi erillisoikeuksia."
  [sampo user maksueranumerot]
  (into {}
        (mapv (fn [maksueranumero]
                [maksueranumero
                 (let [tulos (laheta-maksuera-sampoon sampo user maksueranumero)]
                   (log/debug "Maksueran (numero: " maksueranumero " lähetyksen tulos:" tulos)
                   tulos)])
              maksueranumerot)))

