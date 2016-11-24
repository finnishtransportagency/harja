(ns harja-laadunseuranta.core
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.paikannus :as paikannus]
            [harja-laadunseuranta.main :as main]
            [harja-laadunseuranta.tiedot.sovellus :as sovellus]
            [harja-laadunseuranta.tiedot.comms :as comms]
            [harja-laadunseuranta.tiedot.asetukset.asetukset :as asetukset]
            [harja-laadunseuranta.ui.tr-haku :as tr-haku]
            [harja-laadunseuranta.utils :as utils]
            [harja-laadunseuranta.tiedot.puhe :as puhe]
            [harja-laadunseuranta.tiedot.tarkastusajon-luonti :as tarkastusajon-luonti]
            [cljs.core.async :as async :refer [<!]]
            [harja-laadunseuranta.tiedot.reitintallennus :as reitintallennus]
            [clojure.string :as str]
            [harja-laadunseuranta.ui.dom :as dom])
  (:require-macros [reagent.ratom :refer [run!]]
                   [cljs.core.async.macros :refer [go]]
                   [harja-laadunseuranta.macros :refer [after-delay]]))

(enable-console-print!)

(defn render []
  (reagent/render-component [main/main] (.getElementById js/document "app")))

(defn- esta-mobiililaitteen-nayton-lukitus []
  ;; FIXME Dirty hack joka ei edes toimi luotettavasti.
  ;; Kannattaa pitää silmällä Wake Lock APIa: http://boiler23.github.io/screen-wake/
  #_(let [pollaa-tyhjaa-sivua
        (fn [] (.setInterval js/window
                             (fn []
                               (when-not js/document.hidden
                                 ;; Jos dokumentti on piilossa, sivu vaihtuu, mitä ei haluta
                                 (set! (-> js/window .-location .-href) "/prevent/sleep"))
                               (.setTimeout js/window
                                            (fn []
                                              (.stop js/window))
                                            0))
                             5000))
        soita-tyhja-video
        (fn []
          (let [media-webm "data:video/webm;base64,GkXfo0AgQoaBAUL3gQFC8oEEQvOBCEKCQAR3ZWJtQoeBAkKFgQIYU4BnQI0VSalmQCgq17FAAw9CQE2AQAZ3aGFtbXlXQUAGd2hhbW15RIlACECPQAAAAAAAFlSua0AxrkAu14EBY8WBAZyBACK1nEADdW5khkAFVl9WUDglhohAA1ZQOIOBAeBABrCBCLqBCB9DtnVAIueBAKNAHIEAAIAwAQCdASoIAAgAAUAmJaQAA3AA/vz0AAA="
                media-mp4 "data:video/mp4;base64,AAAAHGZ0eXBpc29tAAACAGlzb21pc28ybXA0MQAAAAhmcmVlAAAAG21kYXQAAAGzABAHAAABthADAowdbb9/AAAC6W1vb3YAAABsbXZoZAAAAAB8JbCAfCWwgAAAA+gAAAAAAAEAAAEAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAAAAAQAAAAAAAAAAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAIAAAIVdHJhawAAAFx0a2hkAAAAD3wlsIB8JbCAAAAAAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAAAAAQAAAAAAAAAAAAAAAAAAQAAAAAAIAAAACAAAAAABsW1kaWEAAAAgbWRoZAAAAAB8JbCAfCWwgAAAA+gAAAAAVcQAAAAAAC1oZGxyAAAAAAAAAAB2aWRlAAAAAAAAAAAAAAAAVmlkZW9IYW5kbGVyAAAAAVxtaW5mAAAAFHZtaGQAAAABAAAAAAAAAAAAAAAkZGluZgAAABxkcmVmAAAAAAAAAAEAAAAMdXJsIAAAAAEAAAEcc3RibAAAALhzdHNkAAAAAAAAAAEAAACobXA0dgAAAAAAAAABAAAAAAAAAAAAAAAAAAAAAAAIAAgASAAAAEgAAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABj//wAAAFJlc2RzAAAAAANEAAEABDwgEQAAAAADDUAAAAAABS0AAAGwAQAAAbWJEwAAAQAAAAEgAMSNiB9FAEQBFGMAAAGyTGF2YzUyLjg3LjQGAQIAAAAYc3R0cwAAAAAAAAABAAAAAQAAAAAAAAAcc3RzYwAAAAAAAAABAAAAAQAAAAEAAAABAAAAFHN0c3oAAAAAAAAAEwAAAAEAAAAUc3RjbwAAAAAAAAABAAAALAAAAGB1ZHRhAAAAWG1ldGEAAAAAAAAAIWhkbHIAAAAAAAAAAG1kaXJhcHBsAAAAAAAAAAAAAAAAK2lsc3QAAAAjqXRvbwAAABtkYXRhAAAAAQAAAABMYXZmNTIuNzguMw=="
                add-source (fn [element type dataURI]
                             (let [source (.createElement js/document "source")]
                               (set! (.-src source) dataURI)
                               (set! (.-type source) (str "video/" type))
                               (.appendChild element source)))
                video-paalla (atom false)
                video (.createElement js/document "video")
                soita-video (fn [elementti]
                              (when-not @video-paalla
                                (.log js/console "Soitetaan video")
                                (.play elementti)
                                (reset! video-paalla true)))]
            (.setAttribute video "loop" "")
            (add-source video "webm" media-webm)
            (add-source video "mp4" media-mp4)
            (.appendChild js/document.body video)
            (tapahtumat/kuuntele! :body-click #(soita-video video))))]
    (pollaa-tyhjaa-sivua)
    (soita-tyhja-video)))

(defn- esta-zoomaus []
  ;; "user-scaleable=no is disabled in Safari for iOS 10.
  ;; The reason is that Apple is trying to improve accessibility by allowing people to zoom on web pages."
  ;; FIXME Edelleen sallii zoomauksen yhden sormen tuplakosketuksella, ei voi mitään.
  #_(when (or (utils/iphone?)
            (utils/ipad?))
    (.addEventListener js/document "gesturestart" #(.preventDefault %))))

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
    (reitintallennus/kaynnista-reitintallennus
      {:sijainnin-tallennus-mahdollinen-atom sovellus/sijainnin-tallennus-mahdollinen
       :sijainti-atom sovellus/sijainti
       :db @sovellus/idxdb
       :tarkastusajo-paattymassa sovellus/tarkastusajo-paattymassa
       :segmentti-atom sovellus/reittisegmentti
       :reittipisteet-atom sovellus/reittipisteet
       :tallennus-kaynnissa-atom sovellus/tallennus-kaynnissa
       :tarkastusajo-atom sovellus/tarkastusajo-id
       :tarkastuspisteet-atom sovellus/kirjauspisteet
       :soratiemittaussyotto sovellus/soratiemittaussyotto
       :mittaustyyppi sovellus/mittaustyyppi
       :jatkuvat-havainnot sovellus/jatkuvat-havainnot})
    (tr-haku/alusta-tr-haku sovellus/sijainti sovellus/tr-tiedot)))

(defn main []
  (esta-mobiililaitteen-nayton-lukitus)
  (esta-zoomaus)
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
