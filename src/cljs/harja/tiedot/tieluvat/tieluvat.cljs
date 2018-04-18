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

            [harja.domain.tielupa :as tielupa]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def tila (atom {:valinnat nil
                 :valittu-tielupa nil
                 :tielupien-haku-kaynnissa? false
                 :nakymassa? false}))

(def valintojen-avaimet [:tr :luvan-numero :lupatyyppi :hakija :voimassaolo :sijainti
                         :myonnetty])

(defrecord Nakymassa? [nakymassa?])
(defrecord PaivitaValinnat [uudet])
(defrecord HaeTieluvat [valinnat aikaleima])
(defrecord TieluvatHaettu [tulos aikaleima])
(defrecord TieluvatEiHaettu [virhe aikaleima])
(defrecord ValitseTielupa [tielupa])

(defn valinta-wrap [e! app polku]
  (r/wrap (get-in app [:valinnat polku])
          (fn [u]
            (e! (->PaivitaValinnat {polku u})))))

(defn hakuparametrit [valinnat]
  (or
    (spec-apurit/poista-nil-avaimet
      (assoc {} ::tielupa/hakija-nimi (get-in valinnat [:hakija ::tielupa/hakija-nimi])
                ::tielupa/tyyppi (:lupatyyppi valinnat)
                ::tielupa/paatoksen-diaarinumero (:luvan-numero valinnat)
                ::tielupa/voimassaolon-alkupvm (first (:voimassaolo valinnat))
                ::tielupa/voimassaolon-loppupvm (second (:voimassaolo valinnat))
                :myonnetty (:myonnetty valinnat)

                ::tielupa/haettava-tr-osoite
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

                   #_#_::tielupa/geometria (:sijainti valinnat)})))
    {}))

(def hakijahaku
  (reify protokollat/Haku
    (hae [_ teksti]
      (go (let [vastaus (<! (k/post! :hae-tielupien-hakijat {:hakuteksti teksti}))]
            vastaus)))))

(defn nayta-kentat? [kentat tielupa]
  (let [kentat (->> tielupa
                    kentat
                    :skeemat
                    (map #(or (:hae %) (:nimi %))))]
    (boolean
      (when (and (some? kentat) (not-empty kentat))
        ((apply some-fn kentat) tielupa)))))

(extend-protocol tuck/Event

  Nakymassa?
  (process-event [{n :nakymassa?} app]
    (assoc app :nakymassa? n))

  PaivitaValinnat
  (process-event [{u :uudet} app]
    (let [uudet-valinnat (merge (:valinnat app)
                                (select-keys u valintojen-avaimet))
          haku (tuck/send-async! ->HaeTieluvat)
          aikaleima (pvm/nyt)]
      (log (pr-str u))
      (if-not (or
                ;; Älä hae, jos myönnetystä tai voimassaolosta on annettu vain toinen
                (and (:myonnetty u)
                     (= 1 (count (filter nil? (:myonnetty u)))))
                (and (:voimassaolo u)
                     (= 1 (count (filter nil? (:voimassaolo u))))))
        (do (haku uudet-valinnat aikaleima)
            (assoc app :valinnat uudet-valinnat
                       :tielupien-haku-kaynnissa? true
                       :nykyinen-haku aikaleima))

        (assoc app :valinnat uudet-valinnat))))

  HaeTieluvat
  (process-event [{valinnat :valinnat aikaleima :aikaleima} app]
    (let [parametrit (hakuparametrit valinnat)
          aikaleima (or aikaleima (pvm/nyt))]
      (log "hakuparametrit" (pr-str parametrit))
      (-> app
          (tt/post! :hae-tieluvat
                    parametrit
                    {:onnistui ->TieluvatHaettu
                     :onnistui-parametrit [aikaleima]
                     :epaonnistui ->TieluvatEiHaettu
                     :epaonnistui-parametrit [aikaleima]})
          (assoc :tielupien-haku-kaynnissa? true
                 ;; Aikakenttäkomponentti päivittää tilaansa bugisesti kahdesti, kun sinne syöttää arvon
                 ;; Kun antaa aikavälin alun, päivittyy tilaan aluksi [nil nil], joka laukaisee haun.
                 ;; Täten kun käyttäjä antaa aikavälin toisen osan, on haku jo käynnissä. Tämän takia uuden
                 ;; haun tekemistä, kun vanha on käynnissä, ei voi estää. Sen sijaan otetaan taulukkoon
                 ;; aina vain uusimman haun tulos.
                 :nykyinen-haku aikaleima))))

  TieluvatHaettu
  (process-event [{t :tulos a :aikaleima} app]
    (log "Haettu! " (pr-str a) " " (pr-str (:nykyinen-haku app)))
    (if (= a (:nykyinen-haku app))
      (assoc app :tielupien-haku-kaynnissa? false
                 :haetut-tieluvat t
                 :nykyinen-haku nil)

      app))

  TieluvatEiHaettu
  (process-event [{v :virhe a :aikaleima} app]
    (if (= a (:nykyinen-haku app))
      (do (viesti/nayta! "Tielupien haku epäonnistui!" :danger)
          (assoc app :tielupien-haku-kaynnissa? false
                     :nykyinen-haku nil))

      app))

  ValitseTielupa
  (process-event [{t :tielupa} app]
    (assoc app :valittu-tielupa t)))