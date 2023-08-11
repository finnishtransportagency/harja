(ns harja.palvelin.integraatiot.tierekisteri.tierekisteri-komponentti
  "Tarjoaa yhteydet Tierekisteriin:
  1. Tietolajien kuvausten hakeminen
  2. Varusteiden hakeminen
  3. Varusteiden hallinta: lisäys, poisto, päivitys

  Tarjoaa myös mahdollisuuden lähettää suoraan varustetoteumia. Tiedot lähetetään XML-sanomina Tierekisteriin, jossa
  tietueen arvot tallennettu määrämuotoisena merkkijonona, jossa yksittäiset kentät ovat tietyissä positioissa.
  Tietolajin kuvaus kuvaa skeeman näistä.

  Käsitteistöä:
  - Tietolaji: Yksittäisen tallennetavan asian tietosisällön kuvaus. Esim. liikennemerkit (tl506).
  - Tietolajin kuvaus: Kenttäkohtainen kuvaus yksittäisen tietolajin kentistä/ominaisuuksista.
  - Tietue: Rivi Tierekisterissä. Esim. yksi liikennemerkki.
  - Varustetoteuma: Harjaan tallennettu yksittäinen varusteeseen kohdistunut työsuorite. Esim. liikennemerkin lisäys."

  (:require
    [com.stuartsierra.component :as component]
    [taoensso.timbre :as log]
    [harja.palvelin.integraatiot.tierekisteri.tietolajit :as tietolajit]
    [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
    [harja.palvelin.asetukset :refer [ominaisuus-kaytossa?]] )
  (:use [slingshot.slingshot :only [try+ throw+]])
  (:import (java.text SimpleDateFormat)))

(defprotocol TierekisteriPalvelut
  (hae-tietolaji [this tietolajitunniste muutospvm])
  (hae-kaikki-tietolajit [this muutospvm])
  (laheta-varustetoteuma [this varusteoteuma-id]))

(def tietolajitunnisteet #{"tl523" "tl501" "tl517" "tl507" "tl508" "tl506"
                           "tl522" "tl513" "tl520" "tl505"
                           "tl504" "tl518" "tl514" "tl509"
                           "tl515" "tl503" "tl510" "tl512" "tl516"
                           "tl511" "tl524"})

(defn validoi-tietolajitunniste [tunniste]
  (log/debug "Validoidaan tunniste: " (pr-str tunniste))
  (when (not
          (contains? tietolajitunnisteet tunniste))
    (throw+ {:type virheet/+viallinen-kutsu+ :virheet
                   [{:koodi  :tuntematon-tietolaji
                     :viesti (str "Tietolajia ei voida hakea. Tuntematon tietolaji: " tunniste)}]})))

(defrecord Tierekisteri [tierekisteri-api-url uudelleenlahetys-aikavali-minuutteina]
  component/Lifecycle
  (start [this]
    this)
  (stop [this]
    this)

  TierekisteriPalvelut
  (hae-tietolaji
    [this tietolajitunniste muutospvm]
    (if (ominaisuus-kaytossa? :tierekisteri)
      (do
        (validoi-tietolajitunniste tietolajitunniste)
        (when (not (empty? tierekisteri-api-url))
          (tietolajit/hae-tietolaji
            (:db this) (:integraatioloki this) tierekisteri-api-url tietolajitunniste muutospvm)))
      (throw+ {:virhe :tierekisteri-pois-kaytosta})))

  (hae-kaikki-tietolajit
    [this muutospvm]
    (if (ominaisuus-kaytossa? :tierekisteri)
      (when (not (empty? tierekisteri-api-url))
        (mapv
          #(tietolajit/hae-tietolaji (:db this) (:integraatioloki this) tierekisteri-api-url % muutospvm)
          tietolajitunnisteet))
      (throw+ {:virhe :tierekisteri-pois-kaytosta}))))
