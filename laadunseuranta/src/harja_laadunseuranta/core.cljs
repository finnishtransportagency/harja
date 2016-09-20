(ns harja-laadunseuranta.core
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.paikannus :as paikannus]
            [harja-laadunseuranta.main :as main]
            [harja-laadunseuranta.sovellus :as sovellus]
            [harja-laadunseuranta.utils :as utils]
            [harja-laadunseuranta.comms :as comms]
            [harja-laadunseuranta.asetukset :as asetukset]
            [harja-laadunseuranta.tr-haku :as tr-haku]
            [harja-laadunseuranta.puhe :as puhe]
            [cljs.core.async :as async :refer [<!]]
            [harja-laadunseuranta.reitintallennus :as reitintallennus]
            [clojure.string :as str])
  (:require-macros [reagent.ratom :refer [run!]]
                   [cljs.core.async.macros :refer [go]]
                   [harja-laadunseuranta.macros :refer [after-delay]]))

(enable-console-print!)

(defn render []
  (reagent/render-component [main/main] (.getElementById js/document "app")))

(defn- sovelluksen-alustusviive []
  (run!
   (when (and (not @sovellus/sovellus-alustettu) @sovellus/alustus-valmis)
     (after-delay 1000
       (reset! sovellus/sovellus-alustettu true)))))

(defonce paikannus-id (cljs.core/atom nil))

(defn main []
  (sovelluksen-alustusviive)

  (reset! paikannus-id (paikannus/kaynnista-paikannus sovellus/sijainti))

  (if @paikannus-id
    (reset! sovellus/gps-tuettu true))

  (go
    (let [kayttajatiedot (<! (comms/hae-kayttajatiedot))]
      (reset! sovellus/kayttajanimi (-> kayttajatiedot :ok :nimi))
      (reset! sovellus/kayttajatunnus (-> kayttajatiedot :ok :kayttajanimi))
      (reset! sovellus/vakiohavaintojen-kuvaukset (-> kayttajatiedot :ok :vakiohavaintojen-kuvaukset)))

    (reset! sovellus/idxdb (<! (reitintallennus/tietokannan-alustus)))

    (reitintallennus/palauta-tarkastusajo @sovellus/idxdb #(do
                                                             (reset! sovellus/palautettava-tarkastusajo %)
                                                             (when (= "?relogin=true" js/window.location.search)
                                                               (main/jatka-ajoa))))

    (reitintallennus/paivita-lahettamattomien-maara @sovellus/idxdb asetukset/+pollausvali+ sovellus/lahettamattomia)

    (reitintallennus/kaynnista-reitinlahetys asetukset/+pollausvali+ @sovellus/idxdb comms/laheta-tapahtumat!)
    (reitintallennus/kaynnista-reitintallennus sovellus/sijainnin-tallennus-mahdollinen
                                               sovellus/sijainti
                                               @sovellus/idxdb
                                               sovellus/reittisegmentti
                                               sovellus/reittipisteet
                                               sovellus/tallennus-kaynnissa
                                               sovellus/havainnot
                                               sovellus/tarkastustyyppi
                                               sovellus/tarkastusajo
                                               sovellus/kirjauspisteet)
    (tr-haku/alusta-tr-haku sovellus/sijainti sovellus/tr-tiedot)))

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
