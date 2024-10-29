(ns harja.palvelin.palvelut.hallinta.lupaukset-palvelu
  (:require [com.stuartsierra.component :as component]
            [harja.kyselyt.lupaus-kyselyt :as lupaus-kyselyt]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [taoensso.timbre :as log]))

(defn- hae-lupausten-linkitykset [db kayttaja]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-lupaukset kayttaja)
  {:puuttuvat-urakat (lupaus-kyselyt/hae-puuttuvat-urakka-linkitykset db)})


(defn- hae-rivin-tunnistin-selitteet [db kayttaja]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-lupaukset kayttaja)
  {:rivin-tunnistin-selitteet (lupaus-kyselyt/hae-rivin-tunnistin-selitteet db)})

(defn- hae-kategorian-urakat [db kayttaja {:keys [kategoria]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-lupaukset kayttaja)
  (log/debug "hae-kategorian-urakat :: kategoria" kategoria)
  {:kategorian-urakat (lupaus-kyselyt/hae-kategorian-urakat db {:rivin-tunnistin-selite (:rivin-tunnistin-selite kategoria)
                                                                :urakan-alkuvuosi (:urakan-alkuvuosi kategoria)})})

(defn- hae-urakan-lupaukset [db kayttaja {:keys [urakka-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-lupaukset kayttaja)
  (log/debug "hae-urakan-lupaukset :: urakka-id " urakka-id)
  {:urakan-lupaukset (lupaus-kyselyt/hae-urakan-lupaukset db {:urakka-id urakka-id})})

(defrecord LupauksetHallinta []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}] 
    (julkaise-palvelu http-palvelin :hae-lupausten-linkitykset
      (fn [kayttaja _tiedot]
        (hae-lupausten-linkitykset db kayttaja)))
    (julkaise-palvelu http-palvelin :hae-rivin-tunnistin-selitteet
      (fn [kayttaja _tiedot]
        (hae-rivin-tunnistin-selitteet db kayttaja)))
    (julkaise-palvelu http-palvelin :hae-kategorian-urakat
      (fn [kayttaja tiedot]
        (hae-kategorian-urakat db kayttaja tiedot)))
    (julkaise-palvelu http-palvelin :hae-urakan-lupaukset
      (fn [kayttaja tiedot]
        (hae-urakan-lupaukset db kayttaja tiedot)))
    this)
  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :hae-lupausten-linkitykset
      :hae-rivin-tunnistin-selitteet
      :hae-kategorian-urakat
      :hae-urakan-lupaukset)
    this))