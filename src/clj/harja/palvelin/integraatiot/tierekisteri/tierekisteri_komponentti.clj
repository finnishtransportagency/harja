(ns harja.palvelin.integraatiot.tierekisteri.tierekisteri-komponentti
  (:require
    [com.stuartsierra.component :as component]
    [taoensso.timbre :as log]
    [harja.palvelin.integraatiot.tierekisteri.tietolajit :as tietolajit]
    [harja.palvelin.integraatiot.tierekisteri.tietueet :as tietueet]
    [harja.palvelin.integraatiot.tierekisteri.tietue :as tietue]
    [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn validoi-tietolajitunniste [tunniste]
  (log/debug "Validoidaan tunniste: " (pr-str tunniste))
  (when (not
          (contains? #{"tl523" "tl501" "tl517" "tl507" "tl508" "tl506" "tl522" "tl513" "tl196" "tl519" "tl505" "tl195"
                       "tl504" "tl198" "tl518" "tl514" "tl509" "tl515" "tl503" "tl510" "tl512" "tl165" "tl516" "tl511"}
                     tunniste))
    (throw+ {:type virheet/+viallinen-kutsu+ :virheet
                   [{:koodi  :tuntematon-tietolaji
                     :viesti (str "Tietolajia ei voida hakea. Tuntematon tietolaji: " tunniste)}]})))

(defprotocol TierekisteriPalvelut
  (hae-tietolajit [this tietolajitunniste muutospvm])
  (hae-tietueet [this tierekisteriosoitevali tietolajitunniste voimassaolopvm])
  (hae-tietue [this tietueen-tunniste tietolajitunniste])
  (paivita-tietue [this tiedot])
  (poista-tietue [this tiedot])
  (lisaa-tietue [this tiedot]))

(defrecord Tierekisteri [tierekisteri-api-url]
  component/Lifecycle
  (start [this] this)
  (stop [this] this)

  TierekisteriPalvelut
  (hae-tietolajit [this tietolajitunniste muutospvm]
    (validoi-tietolajitunniste tietolajitunniste)
    (when (not (empty? tierekisteri-api-url))
      (tietolajit/hae-tietolajit (:integraatioloki this) tierekisteri-api-url tietolajitunniste muutospvm)))

  (hae-tietueet [this tr tietolajitunniste voimassaolopvm]
    (validoi-tietolajitunniste tietolajitunniste)
    (when-not (empty? tierekisteri-api-url)
      (tietueet/hae-tietueet
        (:integraatioloki this) tierekisteri-api-url tr tietolajitunniste voimassaolopvm)))

  (hae-tietue [this tietueen-tunniste tietolajitunniste]
    (validoi-tietolajitunniste tietolajitunniste)
    (when-not (empty? tierekisteri-api-url)
      (tietue/hae-tietue (:integraatioloki this) tierekisteri-api-url tietueen-tunniste tietolajitunniste)))

  (paivita-tietue [this tiedot]
    (validoi-tietolajitunniste (get-in tiedot [:tietue :tietolaji :tietolajitunniste] tiedot))
    (when-not (empty? tierekisteri-api-url)
      (tietue/paivita-tietue (:integraatioloki this) tierekisteri-api-url tiedot)))

  (poista-tietue [this tiedot]
    (validoi-tietolajitunniste (:tietolajitunniste tiedot))
    (when-not (empty? tierekisteri-api-url)
      (tietue/poista-tietue (:integraatioloki this) tierekisteri-api-url tiedot)))

  (lisaa-tietue [this tiedot]
    (validoi-tietolajitunniste (get-in tiedot [:tietue :tietolaji :tietolajitunniste] tiedot))
    (when-not (empty? tierekisteri-api-url)
      (tietue/lisaa-tietue (:integraatioloki this) tierekisteri-api-url tiedot))))

