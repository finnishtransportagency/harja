(ns harja.palvelin.palvelut.varuste-ulkoiset
  "Varustetoteumien backend"
  (:require [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.toteumat :as toteumat-q]
            [harja.kyselyt.velho-nimikkeistot :as nimikkeistot-q]
            [harja.palvelin.integraatiot.velho.velho-komponentti :as velho-komponentti]
            [harja.palvelin.komponentit.excel-vienti :as excel-vienti]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.palvelin.palvelut.varuste-ulkoiset-excel :as v-excel]))

(defn kelvollinen-tr-filter [tie aosa aeta losa leta]
  (or
    ; kaikki kentät annettu?
    ; osien järjestys pitää olla oikein ja jos osat samat etäisyyksien pitää olla suuruusjärjestyksessä
    (and (every? some? [tie aosa aeta losa leta])
         (or
           (and (= aosa losa) (<= aeta leta))
           (< aosa losa)))
    ; tie, alkuosa ja alkuetäisyys annettu ja ei ole annettu loppuosaa eikä loppuetäisyyttä
    (and (every? some? [tie aosa aeta]) (every? nil? [losa leta]))
    ; tie annettu ja ei muuta
    (and tie (every? nil? [aosa aeta losa leta]))
    ; ei ole annettu mitään tr-osotteen kenttää
    (every? nil? [tie aosa aeta losa leta])))

(defn hae-urakan-uusimmat-varustetoteuma-ulkoiset
  [db user {:keys [urakka-id hoitokauden-alkuvuosi tie aosa aeta losa leta] :as tiedot}]
  (when (nil? urakka-id) (throw (IllegalArgumentException. "urakka-id on pakollinen")))
  (when (nil? hoitokauden-alkuvuosi) (throw (IllegalArgumentException. "hoitokauden-alkuvuosi on pakollinen")))
  (when-not (kelvollinen-tr-filter tie aosa aeta losa leta)
    (throw (IllegalArgumentException. "tr-osoitteessa pakolliset, tie TAI tie aosa aeta TAI kaikki")))
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-varusteet user urakka-id)
  {:urakka-id urakka-id :toteumat (toteumat-q/hae-uusimmat-varustetoteuma-ulkoiset db tiedot)})

(defn hae-varustetoteumat-ulkoiset
  [db user {:keys [urakka-id ulkoinen-oid]}]
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

(defn hae-urakan-varustetoteumat-velhosta [velho user {:keys [urakka-id] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-varusteet user urakka-id)
  (velho-komponentti/hae-urakan-varustetoteumat velho tiedot))

(defn hae-varusteen-historia-velhosta [velho user {:keys [urakka-id] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-varusteet user urakka-id)
  (velho-komponentti/hae-varusteen-historia velho tiedot))

(defn hae-varustetoteuma-nimikkeistot [db user]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-varusteet user)
  (nimikkeistot-q/hae-nimikkeistot db))

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

      (julkaise-palvelu http :hae-urakan-varustetoteumat
        (fn [user tiedot]
          (hae-urakan-varustetoteumat-velhosta velho user tiedot)))

      (julkaise-palvelu http :hae-varusteen-historia
        (fn [user tiedot]
          (hae-varusteen-historia-velhosta velho user tiedot)))

      (julkaise-palvelu http :hae-varustetoteuma-nimikkeistot
        (fn [user _]
          (hae-varustetoteuma-nimikkeistot db user)))

      (when excel
        (excel-vienti/rekisteroi-excel-kasittelija! excel :varusteet-ulkoiset-excel
          (partial #'v-excel/vie-ulkoiset-varusteet-exceliin db)))


      (julkaise-palvelu http :petrisi-manuaalinen-testirajapinta-varustetoteumat
                        (fn [user _]
                          (tuo-uudet-varustetoteumat-velhosta velho user)))

      (julkaise-palvelu http :petrisi-manuaalinen-testirajapinta-hae-velhosta-mhu-urakka-oidt
                        (fn [user _]
                          (hae-mhu-urakka-oidt-velhosta velho user)))
    this))
  (stop [this]
    (let [http (:http-palvelin this)]
      (poista-palvelut http :hae-ulkoiset-varustetoteumat)
      (poista-palvelut http :petrisi-manuaalinen-testirajapinta-varustetoteumat)
      (poista-palvelut http :petrisi-manuaalinen-testirajapinta-hae-velhosta-mhu-urakka-oidt))
    this))
