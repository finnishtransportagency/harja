(ns harja.palvelin.integraatiot.api.varusteet
  "Varusteiden API-kutsut."
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET DELETE PUT]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [tee-sisainen-kasittelyvirhevastaus
                                                                             tee-viallinen-kutsu-virhevastaus
                                                                             tee-vastaus
                                                                             tee-kirjausvastauksen-body]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu-async]]
            [harja.palvelin.integraatiot.tierekisteri.tierekisteri-komponentti :as tierekisteri]
            [harja.palvelin.integraatiot.api.sanomat.tierekisteri-sanomat :as tierekisteri-sanomat]
            [harja.palvelin.integraatiot.api.validointi.parametrit :as validointi]
            [harja.kyselyt.livitunnisteet :as livitunnisteet]
            [harja.tyokalut.merkkijono :as merkkijono]
            [harja.domain.tierekisterin-tietolajin-kuvauksen-kasittely :as tr-tietolaji]
            [harja.pvm :as pvm]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet])
  (:use [slingshot.slingshot :only [try+ throw+]])
  (:import (java.text SimpleDateFormat)))

(defn- muunna-tietolajin-arvot-stringiksi [tietolajin-kuvaus arvot-map]
  (tr-tietolaji/tietolajin-arvot-map->merkkijono
    (clojure.walk/stringify-keys arvot-map)
    tietolajin-kuvaus))

(defn validoi-ja-muunna-arvot-merkkijonoksi
  "Hakee tietolajin kuvauksen, validoi arvot sen pohjalta ja muuntaa arvot merkkijonoksi"
  [tierekisteri arvot tietolaji]
  (let [vastaus (tierekisteri/hae-tietolajit
                  tierekisteri
                  tietolaji
                  nil)
        tietolajin-kuvaus (:tietolaji vastaus)]
    (try
      (tr-tietolaji/validoi-tietolajin-arvot
        tietolaji
        (clojure.walk/stringify-keys arvot)
        tietolajin-kuvaus)
      (catch Exception e
        (throw+ {:type virheet/+viallinen-kutsu+
                 :virheet [{:koodi virheet/+sisainen-kasittelyvirhe-koodi+ :viesti (.getMessage e)}]})))
    (muunna-tietolajin-arvot-stringiksi
      tietolajin-kuvaus
      arvot)))

(defn tarkista-parametrit [saadut vaaditut]
  (doseq [{:keys [parametri tyyppi]} vaaditut]
    (if-let [arvo (get saadut parametri)]
      (when tyyppi
        (case tyyppi
          :int (when (not (merkkijono/vaadi-kokonaisluku arvo))
                 (validointi/heita-virheelliset-parametrit-poikkeus (format "Parametri: %s ei ole kokonaisluku. Annettu arvo: %s." parametri arvo)))
          :date (when (not (merkkijono/vaadi-iso-8601-paivamaara arvo))
                  (validointi/heita-virheelliset-parametrit-poikkeus (format "Parametri: %s ei ole päivämäärä. Anna arvo muodossa: yyyy-MM-dd. Annettu arvo: %s." parametri arvo)))
          "default"))
      (validointi/heita-virheelliset-parametrit-poikkeus (format "Pakollista parametria: %s ei ole annettu" parametri)))))

(defn tarkista-tietolajihaun-parametrit [parametrit]
  (tarkista-parametrit
    parametrit
    [{:parametri "tunniste"
      :tyyppi :string}]))

(defn tarkista-tietueiden-haun-parametrit [parametrit]
  (tarkista-parametrit
    parametrit
    [{:parametri "tietolajitunniste"
      :tyyppi :string}
     {:parametri "numero"
      :tyyppi :int}
     {:parametri "aosa"
      :tyyppi :int}
     {:parametri "aet"
      :tyyppi :int}
     {:parametri "aet"
      :tyyppi :int}
     {:parametri "let"
      :tyyppi :int}
     {:parametri "voimassaolopvm"
      :tyyppi :date}
     {:parametri "tilannepvm"
      :tyyppi :date}]))

(defn tarkista-tietueen-haun-parametrit [parametrit]
  (tarkista-parametrit
    parametrit
    [{:parametri "tunniste"
      :tyyppi :string}
     {:parametri "tietolajitunniste"
      :tyyppi :string}
     {:parametri "tilannepvm"
      :tyyppi :date}]))

(defn hae-tietolaji [tierekisteri parametrit kayttaja]
  (tarkista-tietolajihaun-parametrit parametrit)
  (let [tunniste (get parametrit "tunniste")
        muutospaivamaara (get parametrit "muutospaivamaara")]
    (log/debug "Haetaan tietolajin: " tunniste " kuvaus muutospäivämäärällä: " muutospaivamaara " käyttäjälle: " kayttaja)
    (let [vastausdata (tierekisteri/hae-tietolajit tierekisteri tunniste muutospaivamaara)
          ominaisuudet (get-in vastausdata [:tietolaji :ominaisuudet])
          muunnettu-vastausdata (tierekisteri-sanomat/muunna-tietolajin-hakuvastaus vastausdata ominaisuudet)]
      muunnettu-vastausdata)))

(defn- muodosta-tietueiden-hakuvastaus [tierekisteri vastausdata]
  {:varusteet
   (mapv
     (fn [tietue]
       (let [tietolaji (get-in tietue [:tietue :tietolaji :tietolajitunniste])
             vastaus (tierekisteri/hae-tietolajit
                       tierekisteri
                       tietolaji
                       nil)
             tietolajin-kuvaus (:tietolaji vastaus)
             arvot (first (get-in tietue [:tietue :tietolaji :arvot]))
             arvot-mappina (tr-tietolaji/tietolajin-arvot-merkkijono->map
                             arvot
                             tietolajin-kuvaus)]
         {:varuste
          {:tunniste (get-in tietue [:tietue :tunniste])
           :tietue
           {:sijainti {:tie {:numero (get-in tietue [:tietue :sijainti :tie :numero]),
                             :aosa (get-in tietue [:tietue :sijainti :tie :aosa]),
                             :aet (get-in tietue [:tietue :sijainti :tie :aet]),
                             :losa (get-in tietue [:tietue :sijainti :tie :losa]),
                             :let (get-in tietue [:tietue :sijainti :tie :let]),
                             :ajr (get-in tietue [:tietue :sijainti :tie :ajr]),
                             :puoli (get-in tietue [:tietue :sijainti :tie :puoli]),
                             :kaista (get-in tietue [:tietue :sijainti :tie :kaista])}},
            :alkupvm (get-in tietue [:tietue :alkupvm])
            :loppupvm (get-in tietue [:tietue :loppupvm])
            :karttapvm (get-in tietue [:tietue :karttapvm]),
            :kuntoluokitus (get-in tietue [:tietue :kuntoluokka]),
            :ely (Integer/parseInt (get-in tietue [:tietue :piiri]))
            :tietolaji {:tunniste tietolaji,
                        :arvot arvot-mappina}}}}))
     (:tietueet vastausdata))})

(defn hae-varusteet [tierekisteri parametrit kayttaja]
  (tarkista-tietueiden-haun-parametrit parametrit)
  (let [tierekisteriosoite (tierekisteri-sanomat/luo-tierekisteriosoite parametrit)
        tietolajitunniste (get parametrit "tietolajitunniste")
        voimassaolopvm (pvm/iso-8601->pvm (get parametrit "voimassaolopvm"))
        tilannepvm (pvm/iso-8601->pvm (get parametrit "tilannepvm"))]
    (log/debug "Haetaan tietueet tietolajista " tietolajitunniste " voimassaolopäivämäärällä " voimassaolopvm
               ", käyttäjälle " kayttaja " tr osoitteesta: "
               (pr-str tierekisteriosoite) " tilannepäivämäärällä: " tilannepvm)
    (let [vastaus (tierekisteri/hae-tietueet tierekisteri
                                             tierekisteriosoite
                                             tietolajitunniste
                                             voimassaolopvm
                                             tilannepvm)
          muunnettu-vastausdata (muodosta-tietueiden-hakuvastaus tierekisteri vastaus)]
      (if (> (count (:varusteet muunnettu-vastausdata)) 0)
        muunnettu-vastausdata
        {}))))

(defn hae-varuste [tierekisteri parametrit kayttaja]
  (tarkista-tietueen-haun-parametrit parametrit)
  (let [tunniste (get parametrit "tunniste")
        tietolajitunniste (get parametrit "tietolajitunniste")
        tilannepvm (pvm/iso-8601->pvm (get parametrit "tilannepvm"))]
    (log/debug "Haetaan tietue tunnisteella " tunniste " tietolajista " tietolajitunniste " kayttajalle " kayttaja)
    (let [vastaus (tierekisteri/hae-tietue tierekisteri tunniste tietolajitunniste tilannepvm)
          muunnettu-vastausdata (muodosta-tietueiden-hakuvastaus tierekisteri vastaus)]
      (if (> (count (:varusteet muunnettu-vastausdata)) 0)
        muunnettu-vastausdata
        {}))))

(defn lisaa-varuste [tierekisteri db {:keys [otsikko] :as data} kayttaja]
  (log/debug "Lisätään varuste käyttäjän " kayttaja " pyynnöstä.")
  (let [livitunniste (livitunnisteet/hae-seuraava-livitunniste db)
        toimenpiteen-tiedot (:varusteen-lisays data)
        tietolaji (get-in toimenpiteen-tiedot [:varuste :tietue :tietolaji :tunniste])
        tietolajin-arvot (get-in toimenpiteen-tiedot [:varuste :tietue :tietolaji :arvot])
        arvot-string (when tietolajin-arvot
                       (validoi-ja-muunna-arvot-merkkijonoksi
                         tierekisteri
                         tietolajin-arvot
                         tietolaji))
        lisayssanoma (tierekisteri-sanomat/luo-tietueen-lisayssanoma
                           otsikko
                           livitunniste
                           toimenpiteen-tiedot
                           arvot-string)]
    (tierekisteri/lisaa-tietue tierekisteri lisayssanoma)
    (tee-kirjausvastauksen-body
      {:id livitunniste
       :ilmoitukset (str "Uusi varuste lisätty onnistuneesti tunnisteella: " livitunniste)})))

(defn paivita-varuste [tierekisteri {:keys [otsikko] :as data} kayttaja]
  (log/debug "Päivitetään varuste käyttäjän " kayttaja " pyynnöstä.")
  (let [toimenpiteen-tiedot (:varusteen-paivitys data)
        tietolaji (get-in toimenpiteen-tiedot [:varuste :tietue :tietolaji :tunniste])
        tietolajin-arvot (get-in toimenpiteen-tiedot [:varuste :tietue :tietolaji :arvot])
        arvot-string (when tietolajin-arvot
                       (validoi-ja-muunna-arvot-merkkijonoksi
                         tierekisteri
                         tietolajin-arvot
                         tietolaji))
        paivityssanoma (tierekisteri-sanomat/luo-tietueen-paivityssanoma
                              otsikko
                              toimenpiteen-tiedot
                              arvot-string)]
    (tierekisteri/paivita-tietue tierekisteri paivityssanoma))
  (tee-kirjausvastauksen-body {:ilmoitukset "Varuste päivitetty onnistuneesti"}))

(defn poista-varuste [tierekisteri {:keys [otsikko] :as data} kayttaja]
  (log/debug "Poistetaan varuste käyttäjän " kayttaja " pyynnöstä.")
  (let [poistosanoma (tierekisteri-sanomat/luo-tietueen-poistosanoma
                             otsikko
                             (:varusteen-poisto data))]
    (println "Sanoma tehtiin: " (pr-str poistosanoma))
    (tierekisteri/poista-tietue tierekisteri poistosanoma))
  (tee-kirjausvastauksen-body {:ilmoitukset "Varuste poistettu onnistuneesti"}))

(defrecord Varusteet []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki tierekisteri :tierekisteri :as this}]
    (julkaise-reitti
      http :hae-tietolaji
      (GET "/api/varusteet/tietolaji" request
        (kasittele-kutsu-async db integraatioloki :hae-tietolaji request nil json-skeemat/tietolajien-haku
                               (fn [parametrit _ kayttaja _]
                                 (hae-tietolaji tierekisteri parametrit kayttaja)))))

    (julkaise-reitti
      http :hae-tietueet
      (GET "/api/varusteet/haku" request
        (kasittele-kutsu-async db integraatioloki :hae-tietueet request nil json-skeemat/varusteiden-haku-vastaus
                               (fn [parametrit _ kayttaja _]
                                 (hae-varusteet tierekisteri parametrit kayttaja)))))

    (julkaise-reitti
      http :hae-tietue
      (GET "/api/varusteet/varuste" request
        (kasittele-kutsu-async db integraatioloki :hae-tietue request nil json-skeemat/varusteiden-haku-vastaus
                               (fn [parametrit _ kayttaja _]
                                 (hae-varuste tierekisteri parametrit kayttaja)))))

    (julkaise-reitti
      http :lisaa-tietue
      (POST "/api/varusteet/varuste" request
        (kasittele-kutsu-async db integraatioloki :lisaa-tietue request json-skeemat/varusteen-lisays json-skeemat/kirjausvastaus
                               (fn [_ data kayttaja _]
                                 (lisaa-varuste tierekisteri db data kayttaja)))))

    (julkaise-reitti
      http :paivita-tietue
      (PUT "/api/varusteet/varuste" request
        (kasittele-kutsu-async db integraatioloki :paivita-tietue request json-skeemat/varusteen-paivitys json-skeemat/kirjausvastaus
                               (fn [_ data kayttaja _]
                                 (paivita-varuste tierekisteri data kayttaja)))))

    (julkaise-reitti
      http :poista-tietue
      (DELETE "/api/varusteet/varuste" request
        (kasittele-kutsu-async db integraatioloki :poista-tietue request json-skeemat/varusteen-poisto json-skeemat/kirjausvastaus
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
