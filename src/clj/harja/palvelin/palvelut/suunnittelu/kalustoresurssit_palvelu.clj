(ns harja.palvelin.palvelut.suunnittelu.kalustoresurssit-palvelu
  "MHU26-urakoiden suunnittelun kalustoresurssien endpointit."
  (:require [com.stuartsierra.component :as component]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.kalustoresurssit :as kalustoresurssit]
            [harja.kyselyt.kalustoresurssit :as kalustoresurssit-q]
            [slingshot.slingshot :refer [throw+]]))

(defn hae-urakan-kalustoresurssit [db kayttaja {:keys [urakka-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-tehtava-ja-maaraluettelo kayttaja urakka-id)
  (kalustoresurssit-q/hae-urakan-kalustoresurssit db {:urakka-id urakka-id}))

(defn tallenna-urakan-kalustoresurssit
  "Tallentaa urakan kalustoresurssit hoitoluokkaryhmittäin ja palauttaa tallennetut tiedot."
  [db kayttaja {:keys [urakka-id kalustoresurssit] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-tehtava-ja-maaraluettelo kayttaja urakka-id)
  (jdbc/with-db-transaction [db db]
    (doseq [{:keys [hoitoluokkaryhma maara]} kalustoresurssit]
      (when-not (kalustoresurssit/validi-hoitoluokkaryhma? hoitoluokkaryhma)
        (throw+ {:type :virheellinen-hoitoluokkaryhma
                 :virheet [{:koodi "ERROR"
                            :viesti (str "Tuntematon hoitoluokkaryhmä: " hoitoluokkaryhma)}]}))
      (kalustoresurssit-q/tallenna-kalustoresurssi<! db {:urakka-id urakka-id
                                                         :hoitoluokkaryhma hoitoluokkaryhma
                                                         :maara maara
                                                         :kayttaja-id (:id kayttaja)}))
    (kalustoresurssit-q/hae-urakan-kalustoresurssit db {:urakka-id urakka-id})))

(defrecord Kalustoresurssit []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
      :hae-urakan-kalustoresurssit
      (fn [user tiedot]
        (hae-urakan-kalustoresurssit (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
      :tallenna-urakan-kalustoresurssit
      (fn [user tiedot]
        (tallenna-urakan-kalustoresurssit (:db this) user tiedot)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
      :hae-urakan-kalustoresurssit
      :tallenna-urakan-kalustoresurssit)
    this))
