(ns harja.palvelin.palvelut.yllapitokohteet.paikkauskohteet
  (:require [com.stuartsierra.component :as component]
            [harja.domain.oikeudet :as oikeudet]
            [harja.geo :as geo]
            [harja.kyselyt.paikkaus :as q]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [taoensso.timbre :as log]))



(defn paikkauskohteet [db user {:keys [vastuuyksikko tila aikavali tyomenetelmat urakka-id] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-paikkaukset-toteumat user (:urakka-id tiedot))
  (let [_ (println "paikkauskohteet :: tiedot" (pr-str tiedot))
        urakan-paikkauskohteet (q/paikkauskohteet-urakalle db {:urakka-id urakka-id})
        _ (println "urakan-paikkauskohteet alkup: " (pr-str urakan-paikkauskohteet))
        urakan-paikkauskohteet (map (fn [p]
                                      (-> p
                                          (assoc :sijainti (geo/pg->clj (:geometria p)))
                                          (dissoc :geometria))
                                      ) urakan-paikkauskohteet)
        _ (println "urakan-paikkauskohteet: " (pr-str urakan-paikkauskohteet))
        _ (println "urakan-paikkauskohteet geometria siivottu: " (pr-str urakan-paikkauskohteet))]
    urakan-paikkauskohteet))

(defrecord Paikkauskohteet []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          ;email (:sonja-sahkoposti this)
          db (:db this)]
      (julkaise-palvelu http :paikkauskohteet-urakalle
                        (fn [user tiedot]
                          (paikkauskohteet db user tiedot)))
      this))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :paikkauskohteet-urakalle)
    this))
