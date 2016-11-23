(ns harja-laadunseuranta.core
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.paikannus :as paikannus]
            [harja-laadunseuranta.main :as main]
            [harja-laadunseuranta.tiedot.sovellus :as sovellus]
            [harja-laadunseuranta.utils :as utils]
            [harja-laadunseuranta.tiedot.comms :as comms]
            [harja-laadunseuranta.tiedot.asetukset.asetukset :as asetukset]
            [harja-laadunseuranta.ui.tr-haku :as tr-haku]
            [harja-laadunseuranta.tiedot.puhe :as puhe]
            [harja-laadunseuranta.tiedot.tarkastusajon-luonti :as tarkastusajon-luonti]
            [cljs.core.async :as async :refer [<!]]
            [harja-laadunseuranta.tiedot.reitintallennus :as reitintallennus]
            [clojure.string :as str]
            [harja-laadunseuranta.ui.ilmoitukset :as ilmoitukset]
            [harja-laadunseuranta.ui.dom :as dom])
  (:require-macros [reagent.ratom :refer [run!]]
                   [cljs.core.async.macros :refer [go]]
                   [harja-laadunseuranta.macros :refer [after-delay]]))

(enable-console-print!)

(defn render []
  (reagent/render-component [main/main] (.getElementById js/document "app")))

(defn- esta-mobiililaitteen-nayton-lukitus []
  ;; FIXME Dirty hack kunnes on parempi tapa tehdä tämä.
  ;; Toimii niin, että laitteen sleep timer resetoituu kun pyydetään uutta sivua:
  ;; http://stackoverflow.com/questions/18905413/how-can-i-prevent-iphone-including-ios-7-from-going-to-sleep-in-html-or-js
  ;; Kannattaa pitää silmällä Wake Lock APIa: http://boiler23.github.io/screen-wake/
  (.setInterval js/window (fn []
                            (set! (-> js/window .-location .-href) "/prevent/sleep")
                            (.setTimeout js/window
                                          (fn []
                                            (.stop js/window))
                                          0))
                5000))

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
      (reset! sovellus/kayttajanimi (-> kayttajatiedot :ok :nimi))
      (reset! sovellus/kayttajatunnus (-> kayttajatiedot :ok :kayttajanimi))
      (reset! sovellus/vakiohavaintojen-kuvaukset (-> kayttajatiedot :ok :vakiohavaintojen-kuvaukset)))

    (reset! sovellus/idxdb (<! (reitintallennus/tietokannan-alustus)))

    (reitintallennus/palauta-tarkastusajo @sovellus/idxdb #(do
                                                            (reset! sovellus/palautettava-tarkastusajo %)
                                                            (when (= "?relogin=true" js/window.location.search)
                                                              (tarkastusajon-luonti/jatka-ajoa!))))

    (reitintallennus/paivita-lahettamattomien-merkintojen-maara @sovellus/idxdb asetukset/+pollausvali+ sovellus/lahettamattomia-merkintoja)

    (reitintallennus/kaynnista-reitinlahetys asetukset/+pollausvali+
                                             @sovellus/idxdb
                                             comms/laheta-reittimerkinnat!)
    (reitintallennus/kaynnista-reitintallennus sovellus/sijainnin-tallennus-mahdollinen
                                               sovellus/sijainti
                                               @sovellus/idxdb
                                               sovellus/reittisegmentti
                                               sovellus/reittipisteet
                                               sovellus/tallennus-kaynnissa
                                               sovellus/tarkastusajo-id
                                               sovellus/kirjauspisteet)
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
