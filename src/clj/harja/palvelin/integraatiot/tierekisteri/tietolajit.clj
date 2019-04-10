(ns harja.palvelin.integraatiot.tierekisteri.tietolajit
  (:require [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.tietolajin-hakukutsu :as kutsusanoma]
            [harja.palvelin.integraatiot.tierekisteri.vastauksenkasittely :refer :all]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(def tietolajien-kuvaukset-cache
  ^{:doc "Atomi, johon cachetetaan haettujen tietolajien ominaisuudet (kenttien kuvaukset)
          aina kun haku tehdään."
    :private true}
  (atom {}))

(defn- cacheta-onnistunut-vastaus
  "Ottaa tierekisteristä saadun vastauksen haetulle tietolajille ja cachettaa sen jos vastaus on onnistunut.
   Cacheen menee siis koko vastaus, jotta kutsuja saa aina saman vastauksen riippumatta
   siitä tehtiinkö haku oikeasti vai haettiinko se cachesta."
  [tunniste muutospvm vastaus]
  (when (:onnistunut vastaus)
    (swap! tietolajien-kuvaukset-cache
           assoc-in
           [tunniste muutospvm]
           vastaus)))

(defn tyhjenna-tietolajien-kuvaukset-cache []
  (reset! tietolajien-kuvaukset-cache nil))

(defn- kasittele-tietolajin-hakuvastaus
  [url tunniste muutospvm vastaus-xml]
  (kasittele-vastaus
    vastaus-xml
    (str "Tietolajin haku epäonnistui (URL: " url ") tunnisteella: " tunniste
         " & muutospäivämäärällä: " muutospvm ".")
    :tietolaji-haku-epaonnistui
    (str "Tietolajin haku palautti virheitä (URL: " url ") tunnisteella: " tunniste
         " & muutospäivämäärällä: " muutospvm ".")))

(defn- hae-tietolajin-kuvaus-tierekisterista [db integraatioloki url tunniste muutospvm]
  (log/debug "Hae tietolajin: " tunniste " ominaisuudet muutospäivämäärällä: "
             muutospvm " Tierekisteristä")
  (let [url (str url "/haetietolaji")
        otsikot {"Content-Type" "text/xml; charset=utf-8"}
        http-asetukset {:metodi :POST :url url :otsikot otsikot}
        tyonkulku (fn [konteksti]
                    (let [kutsudata (kutsusanoma/muodosta-kutsu tunniste muutospvm)
                          {vastaus-xml :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset kutsudata)]
                      (kasittele-tietolajin-hakuvastaus url tunniste muutospvm vastaus-xml)))]
    (let [vastaus (integraatiotapahtuma/suorita-integraatio db integraatioloki "tierekisteri" "hae-tietolaji" tyonkulku)]
      (cacheta-onnistunut-vastaus tunniste muutospvm vastaus)
      vastaus)))

(defn- hae-tietolajin-kuvaus-cachesta [tunniste muutospvm]
  (get-in @tietolajien-kuvaukset-cache [tunniste muutospvm]))

(defn- suodata-jarjestysnumerottomat-ominaisuudet [{:keys [ominaisuudet] :as tietolajin-kuvaus}]
  (let [suodatetut-ominaisuudet (filter #(some? (:jarjestysnumero %)) ominaisuudet)]
    (assoc tietolajin-kuvaus :ominaisuudet suodatetut-ominaisuudet)))

(defn hae-tietolaji
  "Hakee annetun tietolajin kuvauksen.
  Vastaus palautetaan cachesta jos löytyy, muuten haetaan uusin kuvaus tierekisteristä"
  [db integraatioloki url tunniste muutospvm]
  (let [vastaus (or (hae-tietolajin-kuvaus-cachesta tunniste muutospvm)
                    (hae-tietolajin-kuvaus-tierekisterista db integraatioloki url tunniste muutospvm))
        suodatettu-tietolajin-kuvaus (suodata-jarjestysnumerottomat-ominaisuudet (:tietolaji vastaus))
        palautettava-vastaus (assoc vastaus :tietolaji suodatettu-tietolajin-kuvaus)]
    palautettava-vastaus))
