(ns harja.tiedot.tieluvat.tieluvat
  (:require [reagent.core :as r :refer [atom]]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tt]
            [cljs.core.async :refer [<!]]
            [harja.ui.viesti :as viesti]
            [harja.ui.protokollat :as protokollat]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def tila (atom {}))

(def valintojen-avaimet [:tr :luvan-numero :lupatyyppi :hakija :tila :aikavali])

(defrecord Nakymassa? [nakymassa?])
(defrecord PaivitaValinnat [uudet])
(defrecord HaeTieluvat [])
(defrecord TieluvatHaettu [tulos])
(defrecord TieluvatEiHaettu [virhe])
(defrecord ValitseTielupa [tielupa])
(defrecord TallennaTielupa [lupa])
(defrecord TielupaTallennettu [tulos])
(defrecord TielupaEiTallennettu [virhe])

(defn valinta-wrap [e! app polku]
  (r/wrap (get-in app [:valinnat polku])
          (fn [u]
            (e! (->PaivitaValinnat {polku u})))))

(defn hakuparametrit [app]
  {})

(defn voi-tallentaa? [app]
  true)

(def hakijahaku
  (reify protokollat/Haku
    (hae [_ teksti]
      (go (let [vastaus (<! (k/post! :hae-tielupien-hakijat {:hakuteksti teksti}))]
            vastaus)))))

(extend-protocol tuck/Event

  Nakymassa?
  (process-event [{n :nakymassa?} app]
    (assoc app :nakymassa? n))

  PaivitaValinnat
  (process-event [{u :uudet} app]
    (let [uudet-valinnat (merge (:valinnat app)
                                (select-keys u valintojen-avaimet))
          haku (tuck/send-async! ->HaeTieluvat)]
      (go (haku uudet-valinnat))
      (assoc app :valinnat uudet-valinnat)))

  HaeTieluvat
  (process-event [_ {:keys [tielupien-haku-kaynnissa?] :as app}]
    (if-not tielupien-haku-kaynnissa?
      (-> app
          (tt/post! :hae-tieluvat
                    (hakuparametrit app)
                    {:onnistui ->TieluvatHaettu
                     :epaonnistui ->TieluvatEiHaettu})
          (assoc :tielupien-haku-kaynnissa? true))

      app))

  TieluvatHaettu
  (process-event [{t :tulos} app]
    (assoc app :tielupien-haku-kaynnissa? false
               :haetut-tieluvat t))

  TieluvatEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Tielupien haku epäonnistui!" :danger)
    (assoc app :tielupien-haku-kaynnissa? false))

  ValitseTielupa
  (process-event [{t :tielupa} app]
    (assoc app :valittu-tielupa t))

  TallennaTielupa
  (process-event [{l :lupa} app]
    app)

  TielupaTallennettu
  (process-event [{t :tulos} app]
    (assoc app :tielupien-tallennus-kaynnissa? false))

  TielupaEiTallennettu
  (process-event [_ app]
    (viesti/nayta! "Tieluvan tallennus epäonnistui!" :danger)
    (assoc app :tielupien-tallennus-kaynnissa? false)))