(ns harja-laadunseuranta.core
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.paikannus :as paikannus]
            [harja-laadunseuranta.main :as main]
            [harja-laadunseuranta.tiedot.sovellus :as sovellus]
            [harja-laadunseuranta.tiedot.comms :as comms]
            [harja-laadunseuranta.tiedot.asetukset.asetukset :as asetukset]
            [harja-laadunseuranta.tiedot.tr-haku :as tr-haku]
            [harja-laadunseuranta.utils :as utils]
            [harja-laadunseuranta.tiedot.puhe :as puhe]
            [harja-laadunseuranta.tiedot.tarkastusajon-luonti :as tarkastusajon-luonti]
            [cljs.core.async :as async :refer [<!]]
            [harja-laadunseuranta.tiedot.reitintallennus :as reitintallennus]
            [clojure.string :as str]
            [harja-laadunseuranta.ui.dom :as dom]
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

(defn- kuuntele-eventteja []
  (dom/kuuntele-leveyksia)
  (dom/kuuntele-body-klikkauksia))

(defn- alusta-sovellus []
  (go
    (let [kayttajatiedot (<! (comms/hae-kayttajatiedot))]
      (reset! sovellus/kayttajanimi (-> kayttajatiedot :nimi))
      (reset! sovellus/kayttajatunnus (-> kayttajatiedot :kayttajanimi))
      (reset! sovellus/vakiohavaintojen-kuvaukset (-> kayttajatiedot :vakiohavaintojen-kuvaukset))
      (reset! sovellus/oikeus-urakoihin (-> kayttajatiedot :urakat)))

    (reset! sovellus/idxdb (<! (reitintallennus/tietokannan-alustus)))

    (reitintallennus/palauta-tarkastusajo @sovellus/idxdb #(do
                                                             (reset! sovellus/palautettava-tarkastusajo %)
                                                             (when (= "?relogin=true" js/window.location.search)
                                                               (tarkastusajon-luonti/jatka-ajoa!))))

    (reitintallennus/paivita-lahettamattomien-merkintojen-maara @sovellus/idxdb asetukset/+pollausvali+ sovellus/lahettamattomia-merkintoja)

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
       :soratiemittaussyotto sovellus/soratiemittaussyotto
       :mittaustyyppi sovellus/mittaustyyppi
       :jatkuvat-havainnot sovellus/jatkuvat-havainnot})
    (tr-haku/alusta-tr-haku sovellus/sijainti sovellus/tr-tiedot)))

(defn main []
  (esta-mobiililaitteen-nayton-lukitus)
  (sovelluksen-alustusviive)
  (alusta-paikannus-id)
  (alusta-geolokaatio-api)
  (kuuntele-eventteja)
  (alusta-sovellus))

(defn ^:export aja-testireitti [url]
  (paikannus/lopeta-paikannus @paikannus-id)
  (go
    (let [tiedosto (<! (comms/hae-tiedosto url))
          sijainnit (str/split tiedosto "\n")]
      (.log js/console "Ajetaan testireitti, jossa " (count sijainnit) " sijaintia")
      (loop [[sijainti & sijainnit] sijainnit]
        (<! (async/timeout 2000))
        (let [[x y] (map js/parseFloat (str/split sijainti " "))]
          (.log js/console "Sijainti: " x ", " y)
          (paikannus/aseta-testisijainti sovellus/sijainti [x y]))
        (recur sijainnit)))))
