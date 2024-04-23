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
            [harja.kyselyt.mpu-kustannukset :as q]))


(defn hae-paikkaus-kustannukset [db kayttaja {:keys [urakka-id aikavali] :as _tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-paikkaukset-toteumat kayttaja urakka-id)
  (let [parametrit {:alkuaika (when
                                (and
                                  (some? aikavali)
                                  (first aikavali))
                                (konversio/sql-date (first aikavali)))
                    :loppuaika (when
                                 (and
                                   (some? aikavali)
                                   (second aikavali))
                                 (konversio/sql-date (second aikavali)))
                    :urakka-id urakka-id}
        vastaus (q/hae-paikkaus-kustannukset db parametrit)]
    vastaus))


(defn tallenna-mpu-kustannus
  [db {:keys [id] :as kayttaja} {:keys [urakka-id] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-paikkaukset-toteumat kayttaja urakka-id)

  (println "\n tallenna-mpu-kustannus, tiedot: " tiedot))


(defrecord MPUKustannukset []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]
    ;; Haku
    (julkaise-palvelu http-palvelin
      :hae-paikkaus-kustannukset (fn [user tiedot] (hae-paikkaus-kustannukset db user tiedot)))
    ;; Tallennus
    (julkaise-palvelu http-palvelin
      :tallenna-mpu-kustannus (fn [user tiedot] (tallenna-mpu-kustannus db user tiedot)))
    this)

  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :hae-tyomenetelmat
      :tallenna-mpu-kustannus)
    this))
