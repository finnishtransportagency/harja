(ns harja.palvelin.palvelut.maksuerat
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [harja.kyselyt.maksuerat :as q]
            [harja.palvelin.integraatiot.sampo.sampo-komponentti :as sampo]
            [harja.kyselyt.konversio :as konversio]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.domain.oikeudet :as oikeudet]))

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
  (assert (number? maksueranumero) " maksueranumeron oltava numero.")
  (log/debug "Lähetetään maksuera Sampoon, jonka numero on: " maksueranumero)
  (sampo/laheta-maksuera-sampoon sampo maksueranumero)
  (let [tilat (hae-maksueran-ja-kustannussuunnitelman-tilat db maksueranumero)]
    (log/debug "Maksuerän tilat" tilat)
    tilat))

(defn hae-urakan-maksuerat
  "Palvelu, joka palauttaa urakan maksuerät."
  [db user urakka-id]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laskutus-maksuerat user urakka-id)
  (log/debug "Haetaan maksuerät urakalle: " urakka-id)
  (let [summat (into {}
                     (map (juxt :tpi_id identity))
                     (q/hae-urakan-maksueran-summat db urakka-id))
        maksuerat (into []
                        (comp maksuera-xf
                              (map (fn [maksuera]
                                     (let [tpi (get-in maksuera [:toimenpideinstanssi :id])
                                           tyyppi (get-in maksuera [:maksuera :tyyppi])]
                                       (assoc-in maksuera
                                                 [:maksuera :summa]
                                                 (get-in summat [tpi tyyppi]))))))
                        (q/hae-maksuerat-urakassa db urakka-id))]
    maksuerat))

(defn laheta-maksuerat-sampoon
  "Palvelu, joka lähettää annetut maksuerät Sampoon."
  [db user {:keys [sampo maksueranumerot urakka-id]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laskutus-maksuerat user urakka-id)
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
                      :laheta-maksuerat-sampoon (fn [user {:keys [maksueranumerot urakka-id]}]
                                                  (laheta-maksuerat-sampoon (:db this)
                                                                            user
                                                                            {:sampo (:sampo this)
                                                                             :maksueranumerot maksueranumerot
                                                                             :urakka-id urakka-id})))
    this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :hae-urakan-maksuerat)
    (poista-palvelu (:http-palvelin this) :laheta-maksuerat-sampoon)
    this))
