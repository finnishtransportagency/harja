(ns harja.palvelin.palvelut.hallinta.ajastukset-palvelu
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.ajastetut-tehtavat.kustannusarvioiden-toteumat :as kustannusarvioidut-toteumat]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.pvm :as pvm]
            [taoensso.timbre :as log]))


(defn- aja-kustannusarviot-toteumiksi
  "Kustannusarvioitu_tyo tauluun tallennetaan budjetoidut kustannukset. Niistä osa generoituu kuukauden vaihteessa
  aina toteutuneeksi kustannukseksi. Tämä prosessi pyörii joka yö. Jos ei malteta odottaa, että yöllinen ajo
  tapahtuu, niin tätä kutsumalla sama prosessi voidaan käynnistää heti (esim gc ympäristöissä tai lokaalitestauksessa"
  [db kayttaja]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-toteumatyokalu kayttaja)
  (log/debug "aja-kustannusarviot-toteumiksi käynnistetty!")
  (kustannusarvioidut-toteumat/siirra-kustannukset db (pvm/nyt)))

(defrecord AjastuksetHallinta []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]
    (julkaise-palvelu http-palvelin :aja-kustannusarviot-toteumiksi
      (fn [kayttaja _tiedot]
        (aja-kustannusarviot-toteumiksi db kayttaja)))

    this)
  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :aja-kustannusarviot-toteumiksi)
    this))
