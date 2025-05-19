(ns harja.palvelin.palvelut.muutos.muutos-palvelu
  (:require [com.stuartsierra.component :as component]
            [harja.kyselyt
             [muutos-kyselyt :as muutos-kyselyt]]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [clojure.java.jdbc :as jdbc]
            [taoensso.timbre :as log]))

(defn tallenna-muutos [db {:keys [urakka-id] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id))



(defn hae-urakan-muutostiedot
  [db user {:keys [urakka-id hoitokauden-alkuvuosi] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (log/debug "hae-urakan-muutostiedot: " tiedot)
  (let [vastaus (mapv
                  (fn [rivi]
                    (-> rivi
                      (update :kustannusvaikutukset #(konv/jsonb->clojuremap %))
                      (update :tehtavat_ja_maarat #(konv/jsonb->clojuremap %))
                      (update :liitteet #(konv/jsonb->clojuremap %))))
                  (muutos-kyselyt/hae-urakan-hoitovuoden-muutostiedot db {:urakka urakka-id
                                                                          :hoitokauden_alkuvuosi hoitokauden-alkuvuosi}))]
    (log/debug "Haetut muutostiedot: " vastaus)
    vastaus))

(defrecord Muutos [asetukset]
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
      :hae-urakan-muutostiedot
      (fn [user tiedot]
        (hae-urakan-muutostiedot (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
      :tallenna-muutos
      (fn [user tiedot]
        (tallenna-muutos (:db this) user tiedot)))

    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
      :hae-urakan-muutostiedot
      :tallenna-muutos)
    this))
