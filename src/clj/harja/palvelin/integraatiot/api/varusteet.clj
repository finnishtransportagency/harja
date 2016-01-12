(ns harja.palvelin.integraatiot.api.varusteet
  "Varusteiden API-kutsut"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET DELETE PUT]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [tee-sisainen-kasittelyvirhevastaus tee-viallinen-kutsu-virhevastaus tee-vastaus]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.integraatiot.tierekisteri.tierekisteri-komponentti :as tierekisteri]
            [harja.palvelin.integraatiot.api.sanomat.tierekisteri-sanomat :as tierekisteri-sanomat]
            [harja.palvelin.integraatiot.api.validointi.parametrit :as validointi]
            [harja.kyselyt.livitunnisteet :as livitunnisteet]
            [harja.tyokalut.merkkijono :as merkkijono])
  (:use [slingshot.slingshot :only [try+ throw+]])
  (:import (java.text SimpleDateFormat)))

(defn tarkista-parametrit [saadut vaaditut]
  (doseq [{:keys [parametri tyyppi]} vaaditut]
    (if-let [arvo (get saadut parametri)]
      (when tyyppi
        (case tyyppi
          :int (when (not (merkkijono/onko-kokonaisluku? arvo))
                 (validointi/heita-virheelliset-parametrit-poikkeus (format "Parametri: %s ei ole kokonaisluku. Annettu arvo: %s." parametri arvo)))
          :date (when (not (merkkijono/onko-paivamaara? arvo))
                  (validointi/heita-virheelliset-parametrit-poikkeus (format "Parametri: %s ei ole päivämäärä. Anna arvo muodossa: yyyy-MM-dd. Annettu arvo: %s." parametri arvo)))
          "default"))
      (validointi/heita-virheelliset-parametrit-poikkeus (format "Pakollista parametria: %s ei ole annettu" parametri)))))

(defn tarkista-tietolajihaun-parametrit [parametrit]
  (tarkista-parametrit
    parametrit
    [{:parametri "tunniste"
      :tyyppi    :string}]))

(defn tarkista-tietueiden-haun-parametrit [parametrit]
  (tarkista-parametrit
    parametrit
    [{:parametri "tietolajitunniste"
      :tyyppi    :string}
     {:parametri "numero"
      :tyyppi    :int}
     {:parametri "aosa"
      :tyyppi    :int}
     {:parametri "aet"
      :tyyppi    :int}
     {:parametri "aet"
      :tyyppi    :int}
     {:parametri "let"
      :tyyppi    :int}
     {:parametri "voimassaolopvm"
      :tyyppi    :date}]))

(defn tarkista-tietueen-haun-parametrit [parametrit]
  (tarkista-parametrit
    parametrit
    [{:parametri "tunniste"
      :tyyppi    :string}
     {:parametri "tietolajitunniste"
      :tyyppi    :string}]))

(defn hae-tietolaji [tierekisteri parametrit kayttaja]
  (tarkista-tietolajihaun-parametrit parametrit)
  (let [tunniste (get parametrit "tunniste")
        muutospaivamaara (get parametrit "muutospaivamaara")]
    (log/debug "Haetaan tietolajin: " tunniste " kuvaus muutospäivämäärällä: " muutospaivamaara " käyttäjälle: " kayttaja)
    (let [vastausdata (tierekisteri/hae-tietolajit tierekisteri tunniste muutospaivamaara)
          ominaisuudet (get-in vastausdata [:tietolaji :ominaisuudet])
          muunnettu-vastausdata (tierekisteri-sanomat/muunna-tietolajin-hakuvastaus vastausdata ominaisuudet)]
      muunnettu-vastausdata)))

(defn hae-varusteet [tierekisteri parametrit kayttaja]
  (tarkista-tietueiden-haun-parametrit parametrit)
  (let [tierekisteriosoite (tierekisteri-sanomat/luo-tierekisteriosoite parametrit)
        tietolajitunniste (get parametrit "tietolajitunniste")
        voimassaolopvm (.format (SimpleDateFormat. "yyyy-MM-dd") (.parse (SimpleDateFormat. "yyyy-MM-dd") (get parametrit "voimassaolopvm")))]
    (log/debug "Haetaan tietueet tietolajista " tietolajitunniste " voimassaolopäivämäärällä " voimassaolopvm
               ", käyttäjälle " kayttaja " tr osoitteesta: " (pr-str tierekisteriosoite))
    (let [vastausdata (tierekisteri/hae-tietueet tierekisteri tierekisteriosoite tietolajitunniste voimassaolopvm)
          muunnettu-vastausdata (tierekisteri-sanomat/muunna-tietueiden-hakuvastaus vastausdata)]
      (if (> (count (:varusteet muunnettu-vastausdata)) 0)
        muunnettu-vastausdata
        {}))))

(defn hae-varuste [tierekisteri parametrit kayttaja]
  (tarkista-tietueen-haun-parametrit parametrit)
  (let [tunniste (get parametrit "tunniste")
        tietolajitunniste (get parametrit "tietolajitunniste")]
    (log/debug "Haetaan tietue tunnisteella " tunniste " tietolajista " tietolajitunniste " kayttajalle " kayttaja)
    (let [vastausdata (tierekisteri/hae-tietue tierekisteri tunniste tietolajitunniste)
          muunnettu-vastausdata (tierekisteri-sanomat/muunna-tietueiden-hakuvastaus vastausdata)]
      (if (> (count (:varusteet muunnettu-vastausdata)) 0)
        muunnettu-vastausdata
        {}))))

(defn lisaa-varuste [tierekisteri db data kayttaja]
  (log/debug "Lisätään varuste käyttäjän " kayttaja " pyynnöstä.")
  (let [livitunniste (livitunnisteet/hae-seuraava-livitunniste db)
        data (assoc-in data [:varuste :tunniste] livitunniste)
        lisattava-tietue (tierekisteri-sanomat/luo-tietueen-lisayssanoma data)]
    (tierekisteri/lisaa-tietue tierekisteri lisattava-tietue)
    {:id          livitunniste
     :ilmoitukset (str "Uusi varuste lisätty onnistuneesti tunnisteella: " livitunniste)}))

(defn paivita-varuste [tierekisteri data kayttaja]
  (log/debug "Päivitetään varuste käyttäjän " kayttaja " pyynnöstä.")
  (let [paivitettava-tietue (tierekisteri-sanomat/luo-tietueen-paivityssanoma data)]
    (tierekisteri/paivita-tietue tierekisteri paivitettava-tietue))
  {:ilmoitukset "Varuste päivitetty onnistuneesti"})

(defn poista-varuste [tierekisteri data kayttaja]
  (log/debug "Poistetaan varuste käyttäjän " kayttaja " pyynnöstä.")
  (let [poistettava-tietue (tierekisteri-sanomat/luo-tietueen-poistosanoma data)]
    (tierekisteri/poista-tietue tierekisteri poistettava-tietue))
  {:ilmoitukset "Varuste poistettu onnistuneesti"})

(defrecord Varusteet []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki tierekisteri :tierekisteri :as this}]
    (julkaise-reitti
      http :hae-tietolaji
      (GET "/api/varusteet/tietolaji" request
        (kasittele-kutsu db integraatioloki :hae-tietolaji request nil json-skeemat/+tietolajien-haku+
                         (fn [parametrit _ kayttaja _]
                           (hae-tietolaji tierekisteri parametrit kayttaja)))))

    (julkaise-reitti
      http :hae-tietueet
      (GET "/api/varusteet/haku" request
        (kasittele-kutsu db integraatioloki :hae-tietueet request nil json-skeemat/+varusteiden-haku-vastaus+
                         (fn [parametrit _ kayttaja _]
                           (hae-varusteet tierekisteri parametrit kayttaja)))))

    (julkaise-reitti
      http :hae-tietue
      (GET "/api/varusteet/varuste" request
        (kasittele-kutsu db integraatioloki :hae-tietue request nil json-skeemat/+varusteen-haku-vastaus+
                         (fn [parametrit _ kayttaja _]
                           (hae-varuste tierekisteri parametrit kayttaja)))))

    (julkaise-reitti
      http :lisaa-tietue
      (POST "/api/varusteet/varuste" request
        (kasittele-kutsu db integraatioloki :lisaa-tietue request json-skeemat/+varusteen-lisays+ json-skeemat/+kirjausvastaus+
                         (fn [_ data kayttaja _]
                           (lisaa-varuste tierekisteri db data kayttaja)))))

    (julkaise-reitti
      http :paivita-tietue
      (PUT "/api/varusteet/varuste" request
        (kasittele-kutsu db integraatioloki :paivita-tietue request json-skeemat/+varusteen-paivitys+ json-skeemat/+kirjausvastaus+
                         (fn [_ data kayttaja _]
                           (paivita-varuste tierekisteri data kayttaja)))))

    (julkaise-reitti
      http :poista-tietue
      (DELETE "/api/varusteet/varuste" request
        (kasittele-kutsu db integraatioloki :poista-tietue request json-skeemat/+varusteen-poisto+ json-skeemat/+kirjausvastaus+
                         (fn [_ data kayttaja _]
                           (poista-varuste tierekisteri data kayttaja)))))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
                     :hae-tietolaji
                     :hae-tietueet
                     :hae-tietue
                     :lisaa-tietue
                     :paivita-tietue
                     :poista-tietue)
    this))