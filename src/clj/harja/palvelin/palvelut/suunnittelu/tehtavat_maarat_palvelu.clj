(ns harja.palvelin.palvelut.suunnittelu.tehtavat-maarat-palvelu
  (:require [com.stuartsierra.component :as component]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.tehtavat-maarat-kyselyt :as tehtavat-maarat-kyselyt]
            [clojure.pprint :as pprint]
            [harja.pvm :as pvm]))

(defn tallenna-tehtavat-ja-maarat
  "Tallennetaan sopimuksen tehtävät ja määrät.
   Palautetaan tallennetut tehtävät ja määrät."
  [db kayttaja {:keys [urakka-id] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-tehtava-ja-maaraluettelo kayttaja urakka-id)
  (jdbc/with-db-transaction [db db]
    (let [hk-alkuvuosi (pvm/vuosi (first (:valittu-hoitokausi tiedot)))
          _ (tehtavat-maarat-kyselyt/tallenna-tarjouksen-tehtavat-ja-maarat db urakka-id (:id kayttaja) hk-alkuvuosi (:tehtavat tiedot))
          ;; Haetaan tallennetut tiedot
          tehtavat-ja-maarat (tehtavat-maarat-kyselyt/hae-tehtavat-ja-maarat db urakka-id hk-alkuvuosi)]
      tehtavat-ja-maarat)))

(defn hae-tehtavat-ja-maarat [db kayttaja {:keys [urakka-id] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-tehtava-ja-maaraluettelo kayttaja urakka-id)
  (jdbc/with-db-transaction [db db]
    (println "hae-tehtavat-ja-maarat :: tiedot" (pr-str tiedot))
    (tehtavat-maarat-kyselyt/hae-tehtavat-ja-maarat db urakka-id (pvm/vuosi (first (:valittu-hoitokausi tiedot))))))

(defrecord TehtavatJaMaarat []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
      :hae-tehtavat-ja-maarat
      (fn [user tiedot]
        (hae-tehtavat-ja-maarat (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
      :tallenna-tehtavat-ja-maarat
      (fn [user tiedot]
        (tallenna-tehtavat-ja-maarat (:db this) user tiedot)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
      :hae-tehtavat-ja-maarat)
    this))
