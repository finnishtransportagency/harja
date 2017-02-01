(ns harja.palvelin.integraatiot.tierekisteri.tierekisteri-komponentti
  "Tarjoaa yhteydet Tierekisteriin:
  1. Tietolajien kuvauksen hakeminen
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
    [harja.palvelin.integraatiot.tierekisteri.tietueet :as tietueet]
    [harja.palvelin.integraatiot.tierekisteri.tietue :as tietue]
    [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
    [harja.kyselyt.urakat :as urakat-q]
    [harja.kyselyt.toteumat :as toteumat-q]
    [harja.kyselyt.konversio :as konversio]
    [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava])
  (:use [slingshot.slingshot :only [try+ throw+]])
  (:import (java.text SimpleDateFormat)))

(defprotocol TierekisteriPalvelut
  (hae-tietolajit [this tietolajitunniste muutospvm])
  (hae-tietueet [this tierekisteriosoitevali tietolajitunniste voimassaolopvm tilannepvm])
  (hae-urakan-tietueet [this urakka tietolajitunniste tilannepvm])
  (hae-tietue [this tietueen-tunniste tietolajitunniste tilannepvm])
  (paivita-tietue [this tiedot])
  (poista-tietue [this tiedot])
  (lisaa-tietue [this tiedot])
  (laheta-varustetoteuma [this varusteoteuma-id]))

(def tietolajitunnisteet #{"tl523" "tl501" "tl517" "tl507" "tl508" "tl506"
                           "tl522" "tl513" "tl196" "tl519" "tl505" "tl195"
                           "tl504" "tl198" "tl518" "tl514" "tl509" "tl515"
                           "tl503" "tl510" "tl512" "tl165" "tl516" "tl511"})

(defn validoi-tietolajitunniste [tunniste]
  (log/debug "Validoidaan tunniste: " (pr-str tunniste))
  (when (not
          (contains? tietolajitunnisteet tunniste))
    (throw+ {:type virheet/+viallinen-kutsu+ :virheet
             [{:koodi :tuntematon-tietolaji
               :viesti (str "Tietolajia ei voida hakea. Tuntematon tietolaji: " tunniste)}]})))

(defn varusteen-tiedot [{:keys [henkilo
                                organisaatio
                                ytunnus
                                tunniste
                                alkupvm
                                loppupvm
                                tr
                                luotu
                                tietolaji
                                arvot
                                toimenpide]}]
  (let [formatoi-pvm #(when % (.format (SimpleDateFormat. "yyyy-MM-dd") %))
        tekija {:henkilo henkilo
                :jarjestelma "Harja"
                :organisaatio organisaatio
                :yTunnus ytunnus}]
    {:lisaaja tekija
     :poistaja tekija
     :tarkastaja tekija
     :paivittaja tekija
     :tietue {:tunniste tunniste
              :alkupvm (formatoi-pvm alkupvm)
              :loppupvm (formatoi-pvm loppupvm)
              :sijainti {:tie
                         {:numero (:numero tr)
                          :aet (:alkuetaisyys tr)
                          :aosa (:alkuosa tr)
                          :ajr (:ajorata tr)
                          :puoli (:puoli tr)
                          :tilannepvm (formatoi-pvm luotu)}}
              :tietolaji {:tietolajitunniste tietolaji
                          :arvot arvot}}
     :tietolajitunniste tietolaji
     :lisatty (formatoi-pvm luotu)
     :paivitetty (formatoi-pvm luotu)
     :poistettu (formatoi-pvm luotu)
     :tunniste tunniste}))

(defn laheta-varustetoteuma-tierekisteriin [this varustetoteuma-id]
  (log/debug (format "Lähetetään varustetoteuma (id: %s) Tierekisteriin" varustetoteuma-id))
  (try
    (if-let [varustetoteuma (konversio/alaviiva->rakenne (first (toteumat-q/hae-varustetoteuma (:db this) varustetoteuma-id)))]
      (let [toimenpide (:toimenpide varustetoteuma)
            tiedot (varusteen-tiedot varustetoteuma)]
        (let [vastaus (case toimenpide
                        "lisatty" (lisaa-tietue this tiedot)
                        "paivitetty" (paivita-tietue this tiedot)
                        "poistettu" (poista-tietue this tiedot)
                        "tarkastus" (paivita-tietue this tiedot)
                        (log/warn (format "Ei voida lähettää varustetoteumaa (id: %s) Tierekisteriin. Tuntematon toimenpide: %s."
                                          varustetoteuma-id (:toimenpide varustetoteuma))))]
          (toteumat-q/merkitse-varustetoteuma-lahetetyksi! (:db this) "lahetetty" varustetoteuma-id)
          vastaus))
      (do
        (log/warn (format "Ei voida lähettää varustetoteumaa (id: %s) Tierekisteriin. Toteumaa ei löydy." varustetoteuma-id))))
    (catch Exception e
      (toteumat-q/merkitse-varustetoteuma-lahetetyksi! (:db this) "virhe" varustetoteuma-id)
      (log/error e (format "Varustetoteuman (id :%s) lähetys Tierekisteriin epäonnistui." varustetoteuma-id)))))

(defn laheta-varustetoteumat [this]
  (log/debug "Lähetetään epäonnistuneet varustetoteumat uudestaan Tierekisteriin")
  (let [varustetoteuma-idt (map :id (toteumat-q/hae-epaonnistuneet-varustetoteuman-lahetykset (:db this)))]
    (doseq [varustetoteuma-id varustetoteuma-idt]
      (laheta-varustetoteuma-tierekisteriin this varustetoteuma-id)))
  (log/debug "Varustetoteumien lähetys valmis"))

(defn tee-uudelleenlahetystehtava [this aikavali-minuutteina]
  (if aikavali-minuutteina
    (do
      (log/debug (format "Ajastetaan varustetoteumien uudelleenlähetys %s minuutin välein" aikavali-minuutteina))
      (ajastettu-tehtava/ajasta-minuutin-valein
        aikavali-minuutteina
        (fn [_] (laheta-varustetoteumat this))))
    (fn [])))

(defrecord Tierekisteri [tierekisteri-api-url uudelleenlahetys-aikavali-minuutteina]
  component/Lifecycle
  (start [this]
    (assoc this :uudelleenlahetys-tehtava (tee-uudelleenlahetystehtava this uudelleenlahetys-aikavali-minuutteina)))
  (stop [this]
    (:uudelleenlahetys-tehtava this)
    this)

  TierekisteriPalvelut
  (hae-tietolajit
    [this tietolajitunniste muutospvm]
    (validoi-tietolajitunniste tietolajitunniste)
    (when (not (empty? tierekisteri-api-url))
      (tietolajit/hae-tietolajit
        (:db this) (:integraatioloki this) tierekisteri-api-url tietolajitunniste muutospvm)))

  (hae-tietueet
    [this tr tietolajitunniste voimassaolopvm tilannepvm]
    (validoi-tietolajitunniste tietolajitunniste)
    (when-not (empty? tierekisteri-api-url)
      (tietueet/hae-tietueet
        (:db this) (:integraatioloki this)
        tierekisteri-api-url tr tietolajitunniste voimassaolopvm tilannepvm)))

  (hae-urakan-tietueet [this urakka tietolajitunniste tilannepvm]
    (validoi-tietolajitunniste tietolajitunniste)
    (let [alueurakkanumero (:alueurakkanro (urakat-q/hae-urakan-alueurakkanumero (:db this) urakka))]
      (when-not (empty? tierekisteri-api-url)
        (tietueet/hae-urakan-tietueet
          (:db this) (:integraatioloki this) tierekisteri-api-url alueurakkanumero tietolajitunniste tilannepvm))))

  (hae-tietue [this tietueen-tunniste tietolajitunniste tilannepvm]
    (validoi-tietolajitunniste tietolajitunniste)
    (when-not (empty? tierekisteri-api-url)
      (tietue/hae-tietue
        (:db this) (:integraatioloki this)
        tierekisteri-api-url tietueen-tunniste tietolajitunniste tilannepvm)))

  (lisaa-tietue [this tiedot]
    (validoi-tietolajitunniste (get-in tiedot [:tietue :tietolaji :tietolajitunniste] tiedot))
    (when-not (empty? tierekisteri-api-url)
      (tietue/lisaa-tietue
        (:db this) (:integraatioloki this) tierekisteri-api-url tiedot)))

  (paivita-tietue [this tiedot]
    (validoi-tietolajitunniste (get-in tiedot [:tietue :tietolaji :tietolajitunniste] tiedot))
    (when-not (empty? tierekisteri-api-url)
      (tietue/paivita-tietue
        (:db this) (:integraatioloki this) tierekisteri-api-url tiedot)))

  (poista-tietue [this tiedot]
    (validoi-tietolajitunniste (:tietolajitunniste tiedot))
    (when-not (empty? tierekisteri-api-url)
      (tietue/poista-tietue
        (:db this) (:integraatioloki this) tierekisteri-api-url tiedot)))

  (laheta-varustetoteuma [this varustetoteuma-id]
    (when-not (empty? tierekisteri-api-url)
      (laheta-varustetoteuma-tierekisteriin this varustetoteuma-id))))

