(ns harja.tiedot.urakka.suunnittelu.suolarajoitukset-tiedot
  "Tämän nimiavaruuden avulla voidaan hakea urakan suola- ja lämpötilatietoja."
  (:require [reagent.core :refer [atom] :as r]
            [harja.asiakas.kommunikaatio :as k]
            [harja.pvm :as pvm]
            [tuck.core :refer [process-event] :as tuck]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tyokalut.tuck :as tuck-apurit]
            [cljs.core.async :refer [<! >! chan close!]]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.pvm :as pvm]
            [harja.atom :refer-macros [reaction<!]]
            [reagent.ratom :refer [reaction]]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka :as urakka]
            [harja.ui.viesti :as viesti]
            [clojure.set :as set]))
;; Filtterit
(defrecord ValitseHoitovuosi [vuosi])

(defrecord HaeSuolarajoitukset [valittu-vuosi])
(defrecord HaeSuolarajoituksetOnnistui [vastaus])
(defrecord HaeSuolarajoituksetEpaonnistui [vastaus])

(defrecord AvaaTaiSuljeSivupaneeli [tila lomakedata])
;; Päivitys
(defrecord PaivitaLomake [lomake])
(defrecord HaeTierekisterinTiedotOnnistui [vastaus])
(defrecord HaeTierekisterinTiedotEpaonnistui [vastaus])

(defrecord TallennaLomake [lomake tila])
(defrecord TallennaLomakeOnnistui [vastaus])
(defrecord TallennaLomakeEpaonnistui [vastaus])
;; Poista
(defrecord PoistaSuolarajoitus [parametrit])
(defrecord PoistaSuolarajoitusOnnistui [vastaus])
(defrecord PoistaSuolarajoitusEpaonnistui [vastaus])

(defn- hae-suolarajoitukset [valittu-vuosi]
  (let [urakka-id (-> @tila/yleiset :urakka :id)
        _ (tuck-apurit/post! :hae-suolarajoitukset
            {:hoitokauden_alkuvuosi valittu-vuosi
             :urakka_id urakka-id}
            {:onnistui ->HaeSuolarajoituksetOnnistui
             :epaonnistui ->HaeSuolarajoituksetEpaonnistui
             :paasta-virhe-lapi? true})]))

(extend-protocol tuck/Event

  ValitseHoitovuosi
  (process-event [{vuosi :vuosi} app]
    (do
      (js/console.log "ValitseHoitokausi :: vuosi" (pr-str vuosi))
      (urakka/valitse-aikavali! (pvm/->pvm (str "1.10." vuosi)) (pvm/->pvm (str "30.9." (inc vuosi))))
      (hae-suolarajoitukset vuosi)
      (assoc app :valittu-hoitovuosi vuosi)))

  HaeSuolarajoitukset
  (process-event [{valittu-vuosi :valittu-vuosi} app]
    (do
      (hae-suolarajoitukset valittu-vuosi)
      (assoc app :suolarajoitukset-haku-kaynnissa? true)))

  HaeSuolarajoituksetOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [; Lisätään ui taulukkoa varten osoiteväli
          vastaus (map (fn [rivi]
                         (assoc rivi :osoitevali (str
                                                   (str (:aosa rivi) " / " (:aet rivi))
                                                   " – "
                                                   (str (:losa rivi) " / " (:let rivi)))))
                    vastaus)]
      (-> app
        (assoc :suolarajoitukset-haku-kaynnissa? false)
        (assoc :suolarajoitukset vastaus))))

  HaeSuolarajoituksetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Suolarajoitusten haku epäonnistui tallennus onnistui" :varoitus viesti/viestin-nayttoaika-pitka)
    (js/console.log "HaeSuolarajoituksetEpannistui :: vastaus")
    (-> app
      (assoc :suolarajoitukset-haku-kaynnissa? false)
      (assoc :suolarajoitukset nil)))

  AvaaTaiSuljeSivupaneeli
  (process-event [{tila :tila lomakedata :lomakedata} app]
    (-> app
      (assoc :rajoitusalue-lomake-auki? tila)
      (assoc :lomake lomakedata)))

  ;; Päivitetään lomakkeen sisältö app-stateen, mutta ei serverille
  PaivitaLomake
  (process-event [{lomake :lomake} app]
    (let [urakka-id (-> @tila/yleiset :urakka :id)
          vanha-tierekisteri (into #{} (select-keys (:lomake app) [:tie :aosa :aet :losa :let]))
          uusi-tierekisteri (into #{} (select-keys lomake [:tie :aosa :aet :losa :let]))
          app (if (and
                    (not (nil? (:tie lomake)))
                    (not (nil? (:aosa lomake)))
                    (not (nil? (:aet lomake)))
                    (not (nil? (:losa lomake)))
                    (not (nil? (:let lomake)))
                    (not (empty? (set/difference vanha-tierekisteri uusi-tierekisteri))))
                (do
                (js/console.log "OLI EROA!!!!")
                (tuck-apurit/post! :tierekisterin-tiedot
                    {:tie (:tie lomake)
                     :aosa (:aosa lomake)
                     :aet (:aet lomake)
                     :losa (:losa lomake)
                     :let (:let lomake)
                     :urakka_id urakka-id}
                    {:onnistui ->HaeTierekisterinTiedotOnnistui
                     :epaonnistui ->HaeTierekisterinTiedotEpaonnistui
                     :paasta-virhe-lapi? true})
                (assoc app :hae-tiedot-kaynnissa? true))
              app)]
      (assoc app :lomake lomake)))

  HaeTierekisterinTiedotOnnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (js/console.log "HaeTierekisterinTiedotOnnistui :: vastaus" (pr-str vastaus))
      (-> app
        (assoc-in [:lomake :pituus] (:pituus vastaus))
        (assoc-in [:lomake :ajoratojen_pituus] (:ajoratojen_pituus vastaus))
        (assoc-in [:lomake :pohjavesialueet] (:pohjavesialueet vastaus))
        (assoc :hae-tiedot-kaynnissa? false))))

  HaeTierekisterinTiedotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.log "HaeTierekisterinTiedotEpaonnistui :: vastaus" (pr-str vastaus))
    (assoc app :hae-tiedot-kaynnissa? false))


  TallennaLomake
  (process-event [{lomake :lomake sivupaneeli-tila :tila} app]
    (let [urakka-id (-> @tila/yleiset :urakka :id)
          _ (tuck-apurit/post! :tallenna-suolarajoitus
              {:hoitokauden_alkuvuosi (:hoitokauden_alkuvuosi lomake)
               :urakka_id urakka-id
               :suolarajoitus (:suolarajoitus lomake)
               :formiaatti (:formiaatti lomake)
               :rajoitusalue_id (:rajoitusalue_id lomake)
               :rajoitus_id (:rajoitus_id lomake)
               :tie (:tie lomake)
               :aosa (:aosa lomake)
               :aet (:aet lomake)
               :losa (:losa lomake)
               :let (:let lomake)
               :pituus (:pituus lomake)
               :ajoratojen_pituus (:ajoratojen_pituus lomake)
               :kopioidaan-tuleville-vuosille? (:kopioidaan-tuleville-vuosille? lomake)}
              {:onnistui ->TallennaLomakeOnnistui
               :epaonnistui ->TallennaLomakeEpaonnistui
               :paasta-virhe-lapi? true})]
      (-> app
        (assoc :tallennus-kaynnissa? true)
        (assoc :rajoitusalue-lomake-auki? sivupaneeli-tila))))

  TallennaLomakeOnnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Rajoitusalueen tallennus onnistui" :onnistui viesti/viestin-nayttoaika-lyhyt)
    (hae-suolarajoitukset (:valittu-hoitovuosi app))
    (-> app
      (assoc :suolarajoitukset-haku-kaynnissa? true)
      (assoc :tallennus-kaynnissa? false)
      (assoc :lomake nil)))

  TallennaLomakeEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Rajoitusalueen tallennus epäonnistui!" :varoitus viesti/viestin-nayttoaika-pitka)
    (js/console.log "TallennaLomakeEpaonnistui :: vastaus" (pr-str vastaus))
    (-> app
      (assoc :tallennus-kaynnissa? false)))

  PoistaSuolarajoitus
  (process-event [{parametrit :parametrit} app]
    (let [_ (js/console.log "PoistaSuolarajoitus 1")
          hoitokauden-alkuvuosi (pvm/vuosi (first @urakka/valittu-hoitokausi))
          urakka-id (-> @tila/yleiset :urakka :id)
          _ (tuck-apurit/post! :poista-suolarajoitus
              {:hoitokauden_alkuvuosi hoitokauden-alkuvuosi
               :urakka_id urakka-id
               :rajoitusalue_id (:rajoitusalue_id parametrit)}
              {:onnistui ->PoistaSuolarajoitusOnnistui
               :epaonnistui ->PoistaSuolarajoitusEpaonnistui
               :paasta-virhe-lapi? true})]
      (do
        (js/console.log "PoistaSuolarajoitus 2")
        (-> app
            (assoc :poisto-kaynnissa? true)
            (assoc :rajoitusalue-lomake-auki? false)))))

  PoistaSuolarajoitusOnnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (viesti/nayta-toast! "Rajoitusalueen poistaminen onnistui" :onnistui viesti/viestin-nayttoaika-lyhyt)
      (js/console.log "PoistaSuolarajoitusOnnistui")
      (hae-suolarajoitukset (:valittu-hoitovuosi app))
      (-> app
        (assoc :suolarajoitukset-haku-kaynnissa? true)
        (assoc :poisto-kaynnissa? false)
        (assoc :lomake nil))))

  PoistaSuolarajoitusEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Rajoitusalueen tallennus epäonnistui!" :varoitus viesti/viestin-nayttoaika-pitka)
    (js/console.log "TallennaLomakeEpaonnistui :: vastaus" (pr-str vastaus))
    (-> app
      (assoc :poisto-kaynnissa? false))))
