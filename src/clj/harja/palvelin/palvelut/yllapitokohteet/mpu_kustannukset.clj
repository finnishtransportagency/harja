(ns harja.palvelin.palvelut.yllapitokohteet.mpu-kustannukset
  "MPU Kustannukset näkymän palvelut"
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.konversio :as konversio]
            [harja.domain.oikeudet :as oikeudet]
            [clojure.string :as str]
            [harja.kyselyt.mpu-kustannukset :as q]))


(defn hae-paikkaus-kustannukset [db kayttaja {:keys [urakka-id aikavali vuosi] :as _tiedot}]
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
                    :vuosi vuosi
                    :urakka-id urakka-id}
        vastaus (q/hae-paikkaus-kustannukset db parametrit)]
    vastaus))


(defn tallenna-mpu-kustannus
  [db kayttaja {:keys [urakka-id selite kustannustyyppi summa vuosi]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-paikkaukset-toteumat kayttaja urakka-id)
  (q/tallenna-mpu-kustannus! db {:urakka-id urakka-id
                                 ;; Jos selitettä ei kirjaa, tämä menee kantaan tyhjänä stringinä ""
                                 ;; Siksi tämä iffittely, tämä asettaa kolumnin NULLiksi jos selite on tyhjä.
                                 :selite (if (str/blank? selite) nil selite)
                                 :kustannustyyppi kustannustyyppi
                                 :summa summa
                                 :vuosi vuosi
                                 :luoja (:id kayttaja)}))


(defn hae-mpu-selitteet [db kayttaja {:keys [urakka-id] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-paikkaukset-toteumat kayttaja urakka-id)
  (q/hae-mpu-selitteet db tiedot))


(defrecord MPUKustannukset []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]
    ;; Haut
    (julkaise-palvelu http-palvelin :hae-mpu-selitteet (fn [user tiedot] (hae-mpu-selitteet db user tiedot)))
    (julkaise-palvelu http-palvelin :hae-paikkaus-kustannukset (fn [user tiedot] (hae-paikkaus-kustannukset db user tiedot)))
    ;; Tallennus
    (julkaise-palvelu http-palvelin :tallenna-mpu-kustannus (fn [user tiedot] (tallenna-mpu-kustannus db user tiedot)))
    this)

  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :hae-mpu-selitteet
      :tallenna-mpu-kustannus
      :hae-paikkaus-kustannukset)
    this))
