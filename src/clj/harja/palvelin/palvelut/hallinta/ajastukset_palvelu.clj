(ns harja.palvelin.palvelut.hallinta.ajastukset-palvelu
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.ajastetut-tehtavat.kustannusarvioiden-toteumat :as kustannusarvioidut-toteumat]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.integraatiot.sampo.kasittely.maksuerat :as kasittely-maksuerat]
            [harja.palvelin.integraatiot.sampo.kasittely.urakat :as kasittely-urakat]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.pvm :as pvm]
            [clj-time.coerce :as c]
            [taoensso.timbre :as log]
            [harja.kyselyt.maksuerat :as maksuerat-kyselyt]
            [harja.kyselyt.kustannussuunnitelmat :as kustannussuunnitelma-kyselyt]))


(defn- aja-kustannusarviot-toteumiksi
  "Kustannusarvioitu_tyo tauluun tallennetaan budjetoidut kustannukset. Niistä osa generoituu kuukauden vaihteessa
  aina toteutuneeksi kustannukseksi. Tämä prosessi pyörii joka yö. Jos ei malteta odottaa, että yöllinen ajo
  tapahtuu, niin tätä kutsumalla sama prosessi voidaan käynnistää heti (esim gc ympäristöissä tai lokaalitestauksessa"
  [db kayttaja]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-toteumatyokalu kayttaja)
  (log/debug "aja-kustannusarviot-toteumiksi käynnistetty!")
  (let [;; ajopäivän täytyy olla aina kuukauden ensimmäinen, joten otetaan ensi kuun ensimmäinen päivä defaultiksi
        kuukauden-viimeinen (pvm/kuukauden-viimeinen-paiva (pvm/nyt))
        kuukauden-ensimmainen (pvm/ajan-muokkaus kuukauden-viimeinen true 1 :paiva)
        sql-kuukauden-ensimmainen (c/to-sql-time kuukauden-ensimmainen)
        _ (kustannusarvioidut-toteumat/siirra-kustannukset db sql-kuukauden-ensimmainen)]
    "OK"))

(defn aja-kasittele-maksuerat
  "Maksuerätaulun tietojen päivittäminen hoidon urakoille.
   Tätä kutsutaan normaalisti sampointegraation yhteydessä, jota ei tapahdu lokaalisti.
   Nyt sen voi tarvittaessa voidaan kutsua myös manuaalisesti hallinta-paneelista."
  [db kayttaja]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-toteumatyokalu kayttaja)
  (log/debug "aja-kasittele-maksuerat käynnistetty!")
  (let [;; Normaalisti tämä kutsutaan sampointegraation yhteydessä ja sille ei ole tarvetta
        _ (kasittely-maksuerat/perusta-maksuerat-hoidon-urakoille db)
        ;; Lokaalisti on mahdollista, että puutteellisten tietojen takia on luotu maksueriä, joita ei ole linkitetty kustannussuuunnitelmiin.
        ;; Joten pakotetaan linkitys tässä ajossa.
        maksuerat-ilman-kustannussuunnitelmaa (maksuerat-kyselyt/maksuearat-ilman-kustannussuunnitelmaa db)
        _ (doseq [rivi maksuerat-ilman-kustannussuunnitelmaa]
            (kustannussuunnitelma-kyselyt/luo-kustannussuunnitelma<! db (:numero rivi)))]
    "OK"))

(defrecord AjastuksetHallinta []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]
    (julkaise-palvelu http-palvelin :aja-kustannusarviot-toteumiksi
      (fn [kayttaja _tiedot]
        (aja-kustannusarviot-toteumiksi db kayttaja)))
    (julkaise-palvelu http-palvelin :aja-kasittele-maksuerat
      (fn [kayttaja _tiedot]
        (aja-kasittele-maksuerat db kayttaja)))

    this)
  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :aja-kustannusarviot-toteumiksi
      :aja-kasittele-maksuerat)
    this))
