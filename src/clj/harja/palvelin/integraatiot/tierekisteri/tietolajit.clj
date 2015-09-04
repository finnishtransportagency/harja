(ns harja.palvelin.integraatiot.tierekisteri.tietolajit
  (:require [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.tietolajin-hakukutsu :as tietolajin-hakukutsu]
            [harja.palvelin.integraatiot.tierekisteri.tyokalut.kutsukasittely :as tierekisteri-palvelu]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [com.stuartsierra.component :as component]

    ;; todo: nuketa
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.testi :as testi])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn validoi-tunniste [tunniste]
  (when (not
          (contains? #{"tl523" "tl501" "tl517" "tl507" "tl508" "tl506" "tl522" "tl513" "tl196" "tl519" "tl505" "tl195"
                       "tl504" "tl198" "tl518" "tl514" "tl509" "tl515" "tl503" "tl510" "tl512" "tl165" "tl516" "tl511"}
                     tunniste))
    (throw+ {:type :tierekisteri-kutsu-epaonnistui :error (str "Tuntematon tietolaji: " tunniste)})))

(defn hae-tietolajit [integraatioloki url tunniste muutospvm]
  (validoi-tunniste tunniste)
  (let [kutsudata (tietolajin-hakukutsu/muodosta tunniste muutospvm)
        palvelu-url (str url "/haetietolajit")]
    (tierekisteri-palvelu/kutsu-palvelua integraatioloki "hae-tietolaji" palvelu-url kutsudata)))

(defn kutsu []
  (let [testitietokanta (apply tietokanta/luo-tietokanta testi/testitietokanta)
        integraatioloki (assoc (integraatioloki/->Integraatioloki nil) :db testitietokanta)]
    (component/start integraatioloki)
    (hae-tietolajit integraatioloki "https://testisonja.liikennevirasto.fi/harja/tierekisteri" "tl507" nil)))
