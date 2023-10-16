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



      (julkaise-palvelu http :hae-urakan-varustetoteumat
        (fn [user tiedot]
          (hae-urakan-varustetoteumat-velhosta velho user tiedot)))

      (julkaise-palvelu http :hae-varusteen-historia
        (fn [user tiedot]
          (hae-varusteen-historia-velhosta velho user tiedot)))

      (julkaise-palvelu http :hae-varustetoteuma-nimikkeistot
        (fn [user _]
          (hae-varustetoteuma-nimikkeistot db user)))

      ;; TODO: Toteuta exceliin vienti
      (when excel
        (excel-vienti/rekisteroi-excel-kasittelija! excel :varusteet-ulkoiset-excel
          (partial #'v-excel/vie-ulkoiset-varusteet-exceliin db)))
    this))
  (stop [this]
    (let [http (:http-palvelin this)]
      (poista-palvelut http :hae-urakan-varustetoteumat)
      (poista-palvelut http :hae-varusteen-historia)
      (poista-palvelut http :hae-varustetoteuma-nimikkeistot))
    this))
