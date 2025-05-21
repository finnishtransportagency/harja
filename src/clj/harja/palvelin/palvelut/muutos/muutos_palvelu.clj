(ns harja.palvelin.palvelut.muutos.muutos-palvelu
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]
            [harja.palvelin.asetukset :refer [ominaisuus-kaytossa?]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt [muutos-kyselyt :as muutos-kyselyt]]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]))

(defn tallenna-muutos [db user {:keys [urakka-id] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id))


(defn tavoitehinnan-muutos [muutokset]
  (mapv (fn [rivi]
          (let [total (->> (:kustannusvaikutukset rivi)
                        (map :summa)
                        (reduce + 0))]
            (assoc rivi :tavoitehinnan-muutos total)))
    muutokset))

(defn hae-urakan-muutostiedot
  [db user {:keys [urakka-id valittu-hoitokausi] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (log/debug "hae-urakan-muutostiedot: " tiedot)
  (let [hoitokauden-alkuvuosi (pvm/vuosi (first valittu-hoitokausi))
        vastaus (mapv
                  (fn [rivi]
                    (-> rivi
                      (update :kustannusvaikutukset #(konv/jsonb->clojuremap %))
                      (update :tehtavat_ja_maarat #(konv/jsonb->clojuremap %))
                      (update :liitteet #(konv/jsonb->clojuremap %))))
                  (muutos-kyselyt/hae-urakan-hoitovuoden-muutostiedot db {:urakka urakka-id
                                                                          :hoitokauden_alkuvuosi hoitokauden-alkuvuosi}))
        vastaus-summien-kanssa (tavoitehinnan-muutos vastaus)]
    (log/debug "Haetut muutostiedot: " vastaus-summien-kanssa)
    vastaus-summien-kanssa))

(defrecord Muutos [asetukset]
  component/Lifecycle
  (start [this]

    (when (ominaisuus-kaytossa? :mhu-muutokset)
      (julkaise-palvelu (:http-palvelin this)
        :hae-urakan-muutostiedot
        (fn [user tiedot]
          (hae-urakan-muutostiedot
            (:db this)
            user
            tiedot))))

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
