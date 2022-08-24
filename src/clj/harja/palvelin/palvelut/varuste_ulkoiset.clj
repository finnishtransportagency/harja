(ns harja.palvelin.palvelut.varuste-ulkoiset
  "Varustetoteumien backend"
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.domain.oikeudet :as oikeudet]
            [harja.geo :as geo]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.toteumat :as toteumat-q]
            [harja.palvelin.integraatiot.velho.velho-komponentti :as velho-komponentti]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.palvelin.komponentit.excel-vienti :as excel-vienti]
            [harja.palvelin.palvelut.varuste-ulkoiset-excel :as v-excel]
            [harja.pvm :as pvm]))

(defn kelvollinen-tr-filter [tie aosa aeta losa leta]
  (or
    ; kaikki kentät annettu?
    ; osien järjestys pitää olla oikein ja jos osat samat etäisyyksien pitää olla suuruusjärjestyksessä
    (and (every? some? [tie aosa aeta losa leta])
         (or
           (and (= aosa losa) (<= aeta leta))
           (and (< aosa losa))))
    ; tie, alkuosa ja alkuetäisyys annettu ja ei ole annettu loppuosaa eikä loppuetäisyyttä
    (and (every? some? [tie aosa aeta]) (every? nil? [losa leta]))
    ; tie annettu ja ei muuta
    (and tie (every? nil? [aosa aeta losa leta]))
    ; ei ole annettu mitään tr-osotteen kenttää
    (every? nil? [tie aosa aeta losa leta])))

(defn hae-urakan-uusimmat-varustetoteuma-ulkoiset
  [db user {:keys [urakka-id hoitovuosi tie aosa aeta losa leta] :as tiedot}]
  (when (nil? urakka-id) (throw (IllegalArgumentException. "urakka-id on pakollinen")))
  (when (nil? hoitovuosi) (throw (IllegalArgumentException. "hoitovuosi on pakollinen")))
  (when-not (kelvollinen-tr-filter tie aosa aeta losa leta)
    (throw (IllegalArgumentException. "tr-osoitteessa pakolliset, tie TAI tie aosa aeta TAI kaikki")))
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-varusteet user urakka-id)
  {:urakka-id urakka-id :toteumat (toteumat-q/hae-uusimmat-varustetoteuma-ulkoiset db tiedot)})

(defn hae-varustetoteumat-ulkoiset
  [db user {:keys [urakka-id ulkoinen-oid] :as tiedot}]
  (when (nil? ulkoinen-oid) (throw (IllegalArgumentException. "ulkoinen-oid on pakollinen")))
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-varusteet user urakka-id)
  (let [toteumat (toteumat-q/hae-urakan-varustetoteuma-ulkoiset db {:urakka urakka-id :ulkoinen_oid ulkoinen-oid})]
    {:urakka-id urakka-id :toteumat toteumat}))

(defn tuo-uudet-varustetoteumat-velhosta
  "Integraation kutsu selaimen avulla. Tämä on olemassa vain testausta varten."
  [velho user]
  (oikeudet/vaadi-oikeus "sido" oikeudet/urakat-kohdeluettelo-paallystyskohteet user)
  (try (velho-komponentti/tuo-uudet-varustetoteumat-velhosta velho)
       (catch Throwable t
         (log/error "Virhe Velho-varusteiden haussa: " t)
         false))
  true)

(defn hae-mhu-urakka-oidt-velhosta
  "Integraation kutsu selaimen avulla. Tämä on olemassa vain testausta varten."
  [velho user]
  (oikeudet/vaadi-oikeus "sido" oikeudet/urakat-kohdeluettelo-paallystyskohteet user)
  (try (velho-komponentti/paivita-mhu-urakka-oidt-velhosta velho)
       (catch Throwable t
         (log/error "Virhe Velho-urakoiden haussa: " t)
         false)))

(defrecord VarusteVelho []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          velho (:velho-integraatio this)
          excel (:excel-vienti this)
          db (:db this)]

      (julkaise-palvelu http :hae-urakan-varustetoteuma-ulkoiset
                        (fn [user tiedot]
                          (hae-urakan-uusimmat-varustetoteuma-ulkoiset db user tiedot)))

      (julkaise-palvelu http :hae-varustetoteumat-ulkoiset
                        (fn [user tiedot]
                          (hae-varustetoteumat-ulkoiset db user tiedot)))

      (when excel
        (excel-vienti/rekisteroi-excel-kasittelija! excel :varusteet-ulkoiset-excel
          (partial #'v-excel/vie-ulkoiset-varusteet-exceliin db)))


      (julkaise-palvelu http :petrisi-manuaalinen-testirajapinta-varustetoteumat
                        (fn [user data]
                          (tuo-uudet-varustetoteumat-velhosta velho user)))

      (julkaise-palvelu http :petrisi-manuaalinen-testirajapinta-hae-velhosta-mhu-urakka-oidt
                        (fn [user data]
                          (hae-mhu-urakka-oidt-velhosta velho user)))
    this))
  (stop [this]
    (let [http (:http-palvelin this)]
      (poista-palvelut http :hae-ulkoiset-varustetoteumat)
      (poista-palvelut http :petrisi-manuaalinen-testirajapinta-varustetoteumat)
      (poista-palvelut http :petrisi-manuaalinen-testirajapinta-hae-velhosta-mhu-urakka-oidt))
    this))
