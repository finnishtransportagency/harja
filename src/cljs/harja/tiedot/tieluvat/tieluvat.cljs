(ns harja.tiedot.tieluvat.tieluvat
  (:require [reagent.core :as r :refer [atom]]
            [tuck.core :as tuck]
            [cljs.core.async :refer [<!]]
            [harja.loki :refer [log]]
            [harja.tyokalut.tuck :as tt]
            [harja.ui.viesti :as viesti]
            [harja.ui.protokollat :as protokollat]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tyokalut.spec-apurit :as spec-apurit]

            [harja.domain.tielupa :as tielupa])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def tila (atom {:valinnat nil
                 :valittu-tielupa nil
                 :tielupien-haku-kaynnissa? false
                 :tieluvan-tallennus-kaynnissa? false
                 :nakymassa? false}))

(def valintojen-avaimet [:tr :luvan-numero :lupatyyppi :hakija :voimassaolo :sijainti
                         :myonnetty])

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
  (if-let [valinnat (:valinnat app)]
    (or
      (spec-apurit/poista-nil-avaimet
        (assoc {} ::tielupa/hakija-nimi (get-in valinnat [:hakija ::tielupa/hakija-nimi])
                  ::tielupa/tyyppi (:lupatyyppi valinnat)
                  ::tielupa/ulkoinen-tunniste (let [numero (js/parseInt (:luvan-numero valinnat) 10)]
                                                (when-not (js/isNaN numero) numero))
                  ::tielupa/voimassaolon-alkupvm (first (:voimassaolo valinnat))
                  ::tielupa/voimassaolon-loppupvm (second (:voimassaolo valinnat))
                  :myonnetty (:myonnetty valinnat)

                  ::tielupa/sijainnit
                  (let [tie (get-in valinnat [:tr :numero])
                        aosa (get-in valinnat [:tr :alkuosa])
                        aet (get-in valinnat [:tr :alkuetaisyys])
                        losa (get-in valinnat [:tr :loppuosa])
                        let (get-in valinnat [:tr :loppuetaisyys])]
                    {::tielupa/tie tie
                     ::tielupa/aosa aosa
                     ::tielupa/aet aet
                     ::tielupa/losa (when (and losa let) losa)
                     ::tielupa/let (when (and losa let) let)

                     ::tielupa/geometria (:sijainti valinnat)})))
      {})

    {}))

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
      (let [parametrit (hakuparametrit app)]
        (log "hakuparametrit" (pr-str parametrit))
        (-> app
            (tt/post! :hae-tieluvat
                      parametrit
                      {:onnistui ->TieluvatHaettu
                       :epaonnistui ->TieluvatEiHaettu})
            (assoc :tielupien-haku-kaynnissa? true)))

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
    (assoc app :tieluvan-tallennus-kaynnissa? false))

  TielupaEiTallennettu
  (process-event [_ app]
    (viesti/nayta! "Tieluvan tallennus epäonnistui!" :danger)
    (assoc app :tieluvan-tallennus-kaynnissa? false)))