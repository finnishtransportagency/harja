(ns harja.palvelin.integraatiot.tierekisteri.tierekisteri-komponentti
  (:require
    [com.stuartsierra.component :as component]
    [harja.palvelin.integraatiot.tierekisteri.tietolajit :as tietolajit]
    [harja.palvelin.integraatiot.tierekisteri.tietueet :as tietueet])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn validoi-tunniste [tunniste]
  (when (not
          (contains? #{"tl523" "tl501" "tl517" "tl507" "tl508" "tl506" "tl522" "tl513" "tl196" "tl519" "tl505" "tl195"
                       "tl504" "tl198" "tl518" "tl514" "tl509" "tl515" "tl503" "tl510" "tl512" "tl165" "tl516" "tl511"}
                     tunniste))
    (throw+ {:type :tierekisteri-kutsu-epaonnistui :error (str "Tietolajia ei voida hakea. Tuntematon tietolaji: " tunniste)})))

(defprotocol TierekisteriPalvelut
  (hae-tietolajit [this tietolajitunniste muutospvm])
  (hae-tietue [this tietueen-tunniste tietolajitunniste]))

(defrecord Tierekisteri [tierekisteri-api-url]
  component/Lifecycle
  (start [this] this)
  (stop [this] this)

  TierekisteriPalvelut
  (hae-tietolajit [this tietolajitunniste muutospvm]
    (validoi-tunniste tietolajitunniste)
    (when (not (empty? tierekisteri-api-url))
      (tietolajit/hae-tietolajit (:integraatioloki this) tierekisteri-api-url tietolajitunniste muutospvm)))

  (hae-tietue [this tietueen-tunniste tietolajitunniste]
    (validoi-tunniste tietolajitunniste)
    (when-not (empty? tierekisteri-api-url)
      (tietueet/hae-tietueet (:integraatioloki this) tierekisteri-api-url tietueen-tunniste tietolajitunniste))))

