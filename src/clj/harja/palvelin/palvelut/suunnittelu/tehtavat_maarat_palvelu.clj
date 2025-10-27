(ns harja.palvelin.palvelut.suunnittelu.tehtavat-maarat-palvelu
  (:require [com.stuartsierra.component :as component]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.tehtavat-maarat-kyselyt :as tehtavat-maarat-kyselyt]
            [clojure.pprint :as pprint]))

(defn hae-tehtavat-ja-maarat [db kayttaja {:keys [urakka-id] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-tehtava-ja-maaraluettelo kayttaja urakka-id)
  (jdbc/with-db-transaction [db db]
    (let [lista (tehtavat-maarat-kyselyt/hae-maaramitattavat-tehtavat db)
          _ (println "Tehtavat ja maarat :: " (pprint/pprint lista))]
      lista)))

(defrecord TehtavatJaMaarat []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
      :hae-tehtavat-ja-maarat
      (fn [user tiedot]
        (hae-tehtavat-ja-maarat (:db this) user tiedot)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
      :hae-tehtavat-ja-maarat)
    this))
