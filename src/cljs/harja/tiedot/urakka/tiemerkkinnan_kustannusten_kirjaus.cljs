(ns harja.tiedot.urakka.tiemerkkinnan-kustannusten-kirjaus
  (:require [harja.ui.viesti :as viesti]
            [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]))

(defonce kustannusten-kirjaus-valilehti-nakyvissa? (atom false))

(defonce ^{:private true} nollatut-valinnat {:kustannukset nil
                                             :haku-kaynnissa? true})

(defn kustannusten-summa [rivit avain]
  (let [summa (reduce + 0 (map avain rivit))]
    summa))

(defn pk-osuus-totaalista [rivit avain]
  (reduce + (map (fn [rivi]
                   (* (:kustannus rivi) (/ (avain rivi) 100)))
              rivit)))

(defn prosenttiosuus-kustannuksesta
  [kustannus p-osuus]
  (* (/ p-osuus 100) kustannus))

(defrecord HaeKustannukset [urakka])
(defrecord HaeKustannuksetOnnistui [vastaus])
(defrecord HaeKustannuksetEpaonnistui [vastaus])

(defrecord TallennaKustannukset [tiedot urakka])
(defrecord TallennaKustannuksetOnnistui [vastaus app])
(defrecord TallennaKustannuksetEpaonnistui [vastaus])

(defn- hae-tiedot [urakka]
  (tuck-apurit/post! :hae-tiemerkinta-kustannuskirjaus
    {:urakka (get urakka :urakka)}
    {:onnistui ->HaeKustannuksetOnnistui
     :epaonnistui ->HaeKustannuksetEpaonnistui}))

(defn- epaonnistui [vastaus app]
  (js/console.warn "Tietojen haku epäonnistui: " (pr-str vastaus))
  (viesti/nayta-toast! (str "Tietojen haku epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
  (assoc app :haku-kaynnissa? false))

(extend-protocol tuck/Event
  HaeKustannukset
  (process-event [urakka app]
    (hae-tiedot urakka)
    (->
      (tuck-apurit/nollaa-tuck-tila app nollatut-valinnat)
      (assoc :haku-kaynnissa? true)))

  HaeKustannuksetOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app
      :haku-kaynnissa? false
      :kustannukset vastaus))

  HaeKustannuksetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (epaonnistui vastaus app))

  TallennaKustannukset
  (process-event [{tiedot :tiedot urakka :urakka} app]
    (tuck-apurit/post! :tallenna-tiemerkinta-kustannuskirjaus
      {:urakka urakka :tiedot (into []
                                (map (fn [m]
                                       (dissoc m :id))
                                  tiedot))}
      {:onnistui ->TallennaKustannuksetOnnistui
       :epaonnistui ->TallennaKustannuksetEpaonnistui})
    (assoc app :haku-kaynnissa? true :kustannukset []))

  TallennaKustannuksetOnnistui
  (process-event [{vastaus :vastaus} app]
    ((tuck/current-send-function) (->HaeKustannukset (:urakka vastaus)))
    (viesti/nayta-toast! "Kustannukset tallennettu onnistuneesti" :onnistui viesti/viestin-nayttoaika-keskipitka)
    app)

  TallennaKustannuksetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (epaonnistui vastaus app)))
