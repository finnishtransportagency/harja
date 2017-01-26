(ns harja-laadunseuranta.core
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.paikannus :as paikannus]
            [harja-laadunseuranta.main :as main]
            [harja-laadunseuranta.tiedot.sovellus :as sovellus]
            [harja-laadunseuranta.tiedot.comms :as comms]
            [harja-laadunseuranta.tiedot.asetukset.asetukset :as asetukset]
            [harja-laadunseuranta.tiedot.tr-haku :as tr-haku]
            [harja-laadunseuranta.tiedot.ilmoitukset :as ilmoitukset]
            [harja-laadunseuranta.utils :as utils]
            [harja-laadunseuranta.tiedot.puhe :as puhe]
            [harja-laadunseuranta.tiedot.tarkastusajon-luonti :as tarkastusajon-luonti]
            [cljs.core.async :as async :refer [<!]]
            [harja-laadunseuranta.tiedot.reitintallennus :as reitintallennus]
            [clojure.string :as str]
            [harja-laadunseuranta.ui.yleiset.dom :as dom]
            [harja-laadunseuranta.asiakas.tapahtumat :as tapahtumat])
  (:require-macros [reagent.ratom :refer [run!]]
                   [cljs.core.async.macros :refer [go]]
                   [harja-laadunseuranta.macros :refer [after-delay]]))

(enable-console-print!)

(defn render []
  (reagent/render-component [main/main] (.getElementById js/document "app")))

(defn- esta-mobiililaitteen-nayton-lukitus []
  ;; PENDING Dirty hack, joka toimii vain Android-laitteissa
  ;; Kannattaa pitää silmällä Wake Lock APIa: http://boiler23.github.io/screen-wake/
  (let [video-paalla (atom false)]
    (let [video (.getElementById js/document "keep-alive-hack")
          soita-video (fn [elementti]
                        (when (and (not @video-paalla) elementti)
                          (.log js/console "Estetään näytön lukko")
                          (.play elementti)
                          (reset! video-paalla true)))]
      ;; Soiton täytyy alkaa suoraan user eventistä
      (.addEventListener js/document.body "click" #(soita-video video)))))

(defn- sovelluksen-alustusviive []
  (run!
    (when (and (not @sovellus/sovellus-alustettu) @sovellus/alustus-valmis)
      (after-delay 1000
                   (reset! sovellus/sovellus-alustettu true)))))

(defonce paikannus-id (cljs.core/atom nil))

(defn- alusta-paikannus-id []
  (reset! paikannus-id (paikannus/kaynnista-paikannus
                         sovellus/sijainti
                         sovellus/ensimmainen-sijainti)))

(defn- alusta-geolokaatio-api []
  (if (paikannus/geolokaatio-tuettu?)
    (reset! sovellus/gps-tuettu true)))

(defn- kuuntele-dom-eventteja []
  (dom/kuuntele-leveyksia)
  (dom/kuuntele-body-klikkauksia))

(defn- kasittele-sivun-nakyvyysmuutos [tarkastusajo-kaynnissa-atom
                                       kuvaa-otetaan-atom]
  (let [piilossa? js/document.hidden]
    (when (and (not piilossa?) ;; Tultiin piilosta pois
               (not @kuvaa-otetaan-atom)
               @tarkastusajo-kaynnissa-atom)
      (ilmoitukset/ilmoita
        "Pidä sovellus näkyvillä, muuten merkinnät eivät tallennu!"
        sovellus/ilmoitus
        {:tyyppi :varoitus}))))

(defn- kuuntele-sivun-nakyvyytta [tarkastusajo-kaynnissa-atom kuvaa-otetaan-atom]
  (.addEventListener js/document "visibilitychange"
                     #(kasittele-sivun-nakyvyysmuutos
                        tarkastusajo-kaynnissa-atom
                        kuvaa-otetaan-atom)))

(defn kaynnista-kayttajatietojen-haku []
  ;; Haetaan käyttäjätiedot kun laite on paikannettu
  ;; Sijainti tarvitaan urakoiden lajitteluun, jotta defaulttina on valittuna lähin
  (run!
    (when (and @sovellus/ensimmainen-sijainti
               (not @sovellus/kayttajanimi))
      (go (let [kayttajatiedot (<! (comms/hae-kayttajatiedot (:nykyinen @sovellus/sijainti)))]
            (reset! sovellus/kayttajanimi (-> kayttajatiedot :ok :nimi))
            (reset! sovellus/kayttajatunnus (-> kayttajatiedot :ok :kayttajanimi))
            (reset! sovellus/oikeus-urakoihin (-> kayttajatiedot :ok :urakat))
            (reset! sovellus/roolit (-> kayttajatiedot :ok :roolit))
            (reset! sovellus/organisaatio (-> kayttajatiedot :ok :organisaatio)))))))

(defn- alusta-sovellus []
  (go

    (reset! sovellus/idxdb (<! (reitintallennus/tietokannan-alustus)))

    (reitintallennus/palauta-tarkastusajo @sovellus/idxdb #(do
                                                             (reset! sovellus/palautettava-tarkastusajo %)
                                                             (when (= "?relogin=true" js/window.location.search)
                                                               (tarkastusajon-luonti/jatka-ajoa!))))

    (reitintallennus/paivita-lahettamattomien-merkintojen-maara!
      @sovellus/idxdb
      asetukset/+pollausvali+
      sovellus/lahettamattomia-merkintoja)

    (kaynnista-kayttajatietojen-haku)

    (reitintallennus/kaynnista-reitinlahetys asetukset/+pollausvali+
                                             @sovellus/idxdb
                                             comms/laheta-reittimerkinnat!)
    (reitintallennus/kaynnista-reitintallennus
      {:sijainnin-tallennus-mahdollinen-atom sovellus/sijainnin-tallennus-mahdollinen
       :sijainti-atom sovellus/sijainti
       :db @sovellus/idxdb
       :tarkastusajo-paattymassa-atom sovellus/tarkastusajo-paattymassa?
       :segmentti-atom sovellus/reittisegmentti
       :reittipisteet-atom sovellus/reittipisteet
       :tarkastusajo-kaynnissa-atom sovellus/tarkastusajo-kaynnissa?
       :tarkastusajo-atom sovellus/tarkastusajo-id
       :tarkastuspisteet-atom sovellus/kirjauspisteet
       :soratiemittaussyotto-atom sovellus/soratiemittaussyotto
       :mittaustyyppi-atom sovellus/mittaustyyppi
       :jatkuvat-havainnot-atom sovellus/jatkuvat-havainnot})
    (tr-haku/alusta-tr-haku sovellus/sijainti sovellus/tr-tiedot)))

(defn main []
  (esta-mobiililaitteen-nayton-lukitus)
  (sovelluksen-alustusviive)
  (alusta-paikannus-id)
  (alusta-geolokaatio-api)
  (kuuntele-dom-eventteja)
  (kuuntele-sivun-nakyvyytta sovellus/tarkastusajo-kaynnissa?
                             sovellus/kuvaa-otetaan?)
  (alusta-sovellus))

;; --- Testausapurit ---

(defn ^:export aja-testireitti
  "Hakee kannasta annetun tarkastusajon id:n ja ajaa sen.
   Päivitysväli kertoo, kuinka tiheästi siirrytään seuraavaan pisteeseen (ms).
   2000 vastaa suurin piirtein todellista ajonopeutta."
  [tarkastusajo-id paivitysvali]
  (.log js/console "Käynnistetään simuloidun reitin ajaminen")
  (paikannus/lopeta-paikannus @paikannus-id)
  (go
    (let [vastaus (<! (comms/hae-simuloitu-tarkastusajo! tarkastusajo-id))]
      (when (and (:ok vastaus)
                 (> (count (:ok vastaus)) 0))
        (.log js/console "Ajetaan testireitti, jossa " (count (:ok vastaus)) " sijaintia")
        (reset! sovellus/keskita-ajoneuvoon? true)
        (doseq [sijainti (:ok vastaus)]
          (.log js/console "Asetetaan simuloitu sijainti: " (pr-str sijainti))
          (paikannus/aseta-testisijainti sovellus/sijainti (:sijainti sijainti))
          (<! (async/timeout paivitysvali)))))))
