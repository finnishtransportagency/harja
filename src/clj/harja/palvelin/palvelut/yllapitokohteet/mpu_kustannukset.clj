(ns harja.palvelin.palvelut.yllapitokohteet.mpu-kustannukset
  "MPU Kustannukset näkymän palvelut"
  (:require [com.stuartsierra.component :as component]
            [cheshire.core :as cheshire]
            [harja.pvm :as pvm]
            [slingshot.slingshot :refer [throw+]]
            [dk.ative.docjure.spreadsheet :as xls]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.konversio :as konversio]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.reikapaikkaukset :as q]))


(defn hae-reikapaikkaus-kustannukset [db kayttaja {:keys [urakka-id tr aikavali] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-paikkaukset-toteumat kayttaja urakka-id)
  (println "\n hae-reikapaikkaus-kustannukset -½ tiedot: " tiedot))


(defrecord MPUKustannukset []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]
    (julkaise-palvelu http-palvelin
      :hae-reikapaikkaus-kustannukset (fn [user tiedot] (hae-reikapaikkaus-kustannukset db user tiedot)))
    this)

  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :hae-tyomenetelmat)
    this))
