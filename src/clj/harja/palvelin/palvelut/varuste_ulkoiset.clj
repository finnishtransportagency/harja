(ns harja.palvelin.palvelut.varuste-ulkoiset
  "Varustetoteumien backend"
  (:require [com.stuartsierra.component :as component]
            [clj-time.core :as t]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [taoensso.timbre :as log]
            [slingshot.slingshot :refer [try+]]
            [harja.geo :as geo]
            [harja.palvelin.integraatiot.velho.velho-komponentti :as velho-komponentti]
            [harja.palvelin.integraatiot.velho.varusteet :as varusteet]
            [harja.kyselyt.toteumat :as toteumat-q]
            [harja.kyselyt.konversio :as konv]
            [harja.domain.oikeudet :as oikeudet]
            [harja.pvm :as pvm]))

(defn luo-pvm-oikein [vuosi kuukausi paiva]
  (pvm/luo-pvm vuosi (- kuukausi 1) paiva))

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
    (every? nil? [tie aosa aeta losa leta])
    ))

(defn muunna-sijainti
  [varuste]
  (let [sijainti (geo/pg->clj (:sijainti varuste))]
    (assoc varuste :sijainti sijainti)))

(defn hae-urakan-uusimmat-varustetoteuma-ulkoiset
  [db user {:keys [urakka-id hoitovuosi kuukausi tie aosa aeta losa leta kuntoluokat tietolajit toteuma] :as tiedot}]
  (when (nil? urakka-id) (throw (IllegalArgumentException. "urakka-id on pakollinen")))
  (when (nil? hoitovuosi) (throw (IllegalArgumentException. "hoitovuosi on pakollinen")))
  (when-not (kelvollinen-tr-filter tie aosa aeta losa leta)
    (throw (IllegalArgumentException. "tr-osoitteessa pakolliset, tie TAI tie aosa aeta TAI kaikki")))
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-varusteet user urakka-id)
  (let [hoitokauden-alkupvm (luo-pvm-oikein hoitovuosi 10 01)
        hoitokauden-loppupvm (luo-pvm-oikein (+ 1 hoitovuosi) 9 30)
        toteumat (toteumat-q/hae-urakan-uusimmat-varustetoteuma-ulkoiset db {:urakka urakka-id
                                                                             :hoitokauden_alkupvm (konv/sql-date hoitokauden-alkupvm)
                                                                             :hoitokauden_loppupvm (konv/sql-date hoitokauden-loppupvm)
                                                                             :kuukausi kuukausi
                                                                             :tie tie
                                                                             :aosa aosa
                                                                             :aeta aeta
                                                                             :losa losa
                                                                             :leta leta
                                                                             :tietolajit (or tietolajit [])
                                                                             :kuntoluokat (or kuntoluokat [])
                                                                             :toteuma toteuma})]
    {:urakka-id urakka-id :toteumat (map muunna-sijainti toteumat)}))

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
          velho (:velho-integraatio this)]

      (julkaise-palvelu http :hae-urakan-varustetoteuma-ulkoiset
                        (fn [user tiedot]
                          (hae-urakan-uusimmat-varustetoteuma-ulkoiset (:db this) user tiedot)))

      (julkaise-palvelu http :hae-varustetoteumat-ulkoiset
                        (fn [user tiedot]
                          (hae-varustetoteumat-ulkoiset (:db this) user tiedot)))


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
