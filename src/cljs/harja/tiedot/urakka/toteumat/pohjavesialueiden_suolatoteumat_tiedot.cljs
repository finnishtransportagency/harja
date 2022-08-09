(ns harja.tiedot.urakka.toteumat.pohjavesialueiden-suolatoteumat-tiedot
  "Tämän nimiavaruuden avulla voidaan hakea urakan suola- ja lämpötilatietoja."
  (:require [reagent.core :refer [atom] :as r]
            [harja.pvm :as pvm]
            [tuck.core :refer [process-event] :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka :as urakka]
            [harja.ui.viesti :as viesti]
            [clojure.set :as set]))

(defrecord HaeRajoitusalueet [valittu-vuosi])
(defrecord HaeRajoitusalueetOnnistui [vastaus])
(defrecord HaeRajoitusalueetEpaonnistui [vastaus])

(defn- hae-suolarajoitukset [valittu-vuosi]
  (let [urakka-id (-> @tila/yleiset :urakka :id)
        _ (tuck-apurit/post! :hae-suolarajoitukset
            {:hoitokauden_alkuvuosi valittu-vuosi
             :urakka_id urakka-id}
            {:onnistui ->HaeRajoitusalueetOnnistui
             :epaonnistui ->HaeRajoitusalueetEpaonnistui
             :paasta-virhe-lapi? true})]))

(extend-protocol tuck/Event

  HaeRajoitusalueet
  (process-event [{valittu-vuosi :valittu-vuosi} app]
    (do
      (hae-suolarajoitukset valittu-vuosi)
      (-> app
        (assoc :rajoitusalueet nil)
        (assoc :rajoitusalueet-haku-kaynnissa? true))))

  HaeRajoitusalueetOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [; Lisätään ui taulukkoa varten osoiteväli
          _ (js/console.log "HAeRajoitusalueetOnnistui :: vastaus" (pr-str vastaus))
          vastaus (map (fn [rivi]
                         (assoc rivi :osoitevali (str
                                                   (str (:aosa rivi) " / " (:aet rivi))
                                                   " – "
                                                   (str (:losa rivi) " / " (:let rivi)))))
                    vastaus)]
      (-> app
        (assoc :rajoitusalueet-haku-kaynnissa? false)
        (assoc :rajoitusalueet vastaus))))

  HaeRajoitusalueetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (viesti/nayta-toast! "Rajoitsalueiden haku epäonnistui" :varoitus viesti/viestin-nayttoaika-pitka)
      (-> app
        (assoc :rajoitusalueet-haku-kaynnissa? false)
        (assoc :rajoitusalueet nil)))))
