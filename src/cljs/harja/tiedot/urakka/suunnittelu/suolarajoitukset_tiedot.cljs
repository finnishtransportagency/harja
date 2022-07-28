(ns harja.tiedot.urakka.suunnittelu.suolarajoitukset-tiedot
  "Tämän nimiavaruuden avulla voidaan hakea urakan suola- ja lämpötilatietoja."
  (:require [reagent.core :refer [atom] :as r]
            [harja.asiakas.kommunikaatio :as k]
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
            [harja.ui.viesti :as viesti]))

(defrecord HaeSuolarajoitukset [])
(defrecord HaeSuolarajoituksetOnnistui [vastaus])
(defrecord HaeSuolarajoituksetEpaonnistui [vastaus])

(defrecord AvaaTaiSuljeSivupaneeli [tila lomakedata])
(defrecord PaivitaLomake [lomake])
(defrecord TallennaLomake [lomake tila])
(defrecord TallennaLomakeOnnistui [vastaus])
(defrecord TallennaLomakeEpaonnistui [vastaus])
;; Poista
(defrecord PoistaSuolarajoitus [parametrit])
(defrecord PoistaSuolarajoitusOnnistui [vastaus])
(defrecord PoistaSuolarajoitusEpaonnistui [vastaus])

(defn- hae-suolarajoitukset []
  (let [hoitokausi @urakka/valittu-hoitokausi
        _ (js/console.log "hae-suolarajoitukset :: hoitokausi" (pr-str hoitokausi))
        hoitokauden-alkuvuosi (pvm/vuosi (first hoitokausi))
        _ (js/console.log "hae-suolarajoitukset :: hoitokauden-alkuvuosi" (pr-str hoitokauden-alkuvuosi))
        urakka-id (-> @tila/yleiset :urakka :id)
        _ (tuck-apurit/post! :hae-suolarajoitukset
            {:hoitokauden_alkuvuosi hoitokauden-alkuvuosi
             :urakka_id urakka-id}
            {:onnistui ->HaeSuolarajoituksetOnnistui
             :epaonnistui ->HaeSuolarajoituksetEpaonnistui
             :paasta-virhe-lapi? true})]))

(extend-protocol tuck/Event

  HaeSuolarajoitukset
  (process-event [_ app]
    (do
      (hae-suolarajoitukset)
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
    ;;TODO: Pituuden laskenta pitäisi hoitaa tässä
    (assoc app :lomake lomake))


  TallennaLomake
  (process-event [{lomake :lomake sivupaneeli-tila :tila} app]
    (let [hoitokausi @urakka/valittu-hoitokausi
          hoitokauden-alkuvuosi (pvm/vuosi (first hoitokausi))
          urakka-id (-> @tila/yleiset :urakka :id)
          _ (tuck-apurit/post! :tallenna-suolarajoitus
              {:hoitokauden_alkuvuosi hoitokauden-alkuvuosi
               :urakka_id urakka-id
               :suolarajoitus (:suolarajoitus lomake)
               :formiaatti (:formiaatti lomake)
               :rajoitusalue_id (:rajoitusalue_id lomake)
               :rajoitus_id (:rajoitus_id lomake)
               :tie (:tie lomake)
               :aosa (:aosa lomake)
               :aet (:aet lomake)
               :losa (:losa lomake)
               :let (:let lomake)}
              {:onnistui ->TallennaLomakeOnnistui
               :epaonnistui ->TallennaLomakeEpaonnistui
               :paasta-virhe-lapi? true})]
      (-> app
        (assoc :tallennus-kaynnissa? true)
        (assoc :rajoitusalue-lomake-auki? sivupaneeli-tila))))

  TallennaLomakeOnnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Rajoitusalueen tallennus onnistui" :onnistui viesti/viestin-nayttoaika-lyhyt)
    (hae-suolarajoitukset)
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
      (hae-suolarajoitukset)
      (-> app
        (assoc :suolarajoitukset-haku-kaynnissa? true)
        (assoc :poisto-kaynnissa? false)
        (assoc :lomake nil))))

  PoistaSuolarajoitusEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast "Rajoitusalueen tallennus epäonnistui!" :varoitus viesti/viestin-nayttoaika-pitka)
    (js/console.log "TallennaLomakeEpaonnistui :: vastaus" (pr-str vastaus))
    (-> app
      (assoc :poisto-kaynnissa? false))))
