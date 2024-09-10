(ns harja.tiedot.urakka.laadunseuranta.talvihoitoreitit-tiedot
  (:require [tuck.core :as tuck]
            [reagent.core :as r]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tyokalut.yleiset :as yleiset-tyokalut]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.kartta.ikonit :refer [sijainti-ikoni pinni-ikoni nuoli-ikoni]]
            [harja.ui.kartta.asioiden-ulkoasu :as asioiden-ulkoasu]
            [harja.ui.viesti :as viesti]
            [taoensso.timbre :as log])
  (:require-macros
    [reagent.ratom :refer [reaction]]))

(def talvihoitoreittien-varit
  ["#FF5733" "#FF8D1A" "#FFC300" "#FFD700" "#FFFF66" "#DFFF00" "#ADFF2F" "#7FFF00" "#32CD32" "#00FF7F"
   "#20B2AA" "#40E0D0" "#48D1CC" "#87CEEB" "#00BFFF" "#1E90FF" "#6495ED" "#7B68EE" "#9370DB" "#8A2BE2"
   "#9932CC" "#9400D3" "#8B008B" "#C71585" "#FF1493" "#FF69B4" "#FFB6C1" "#FFA07A" "#FF6347" "#FF4500"
   "#DC143C" "#CD5C5C" "#F08080" "#FA8072" "#E9967A" "#FFA07A" "#FFDAB9" "#FFE4B5" "#FFEBCD" "#F0E68C"])

(defn anna-random-vari [_]
  (let [varien-maara (dec (count talvihoitoreittien-varit))
        random-luku (yleiset-tyokalut/random-luku-valilta 0 varien-maara)]
    (nth talvihoitoreittien-varit random-luku)))

;; Tehdään set, jossa on määriteltynä mitä kohteita kartalla näytetään
;; Mikäli mitään ei ole valittu, näytetään kaikki
(defonce valitut-kohteet-atom (r/atom #{}))

(defonce karttataso-talvihoitoreitit (r/atom []))
(defonce karttataso-nakyvissa? (r/atom true))

(defonce talvihoitoreitit-kartalla
  (reaction
    (let [;; Näytä vain valittu kohde kartalla
          valitut-kohteet (if (= (count @valitut-kohteet-atom) 0)
                            #{}
                            @valitut-kohteet-atom)
          talvihoitoreitit (keep (fn [reitti] (when (contains? valitut-kohteet (:id reitti)) reitti)) @karttataso-talvihoitoreitit)
          sijainnit (map (fn [rivi] (map :sijainti (:reitit rivi))) talvihoitoreitit)
          extentit (harja.geo/extent-monelle (flatten sijainnit))
          map-reitit (into [] (flatten (mapv (fn [reitti]
                                               (mapv (fn [r]
                                                       (when (:sijainti r)
                                                         {:alue (merge {:tyyppi-kartalla :talvihoitoreitit
                                                                        :stroke {:width 8
                                                                                 :color (:vari reitti)}}
                                                                  (:sijainti r))
                                                          :extent (harja.geo/extent (:sijainti r))
                                                          :tyyppi-kartalla :talvihoitoreitit
                                                          :selite {:teksti "Talvihoitoreitti"
                                                                   :img (pinni-ikoni "sininen")}
                                                          :infopaneelin-tiedot {:pituus (:pituus r)
                                                                                :nimi (:nimi reitti)}
                                                          :ikonit [{:tyyppi :merkki
                                                                    :paikka [:loppu]
                                                                    :zindex 21
                                                                    :img (pinni-ikoni "sininen")}]}))
                                                 (:reitit reitti)))
                                         talvihoitoreitit)))]
      (when (and (not-empty talvihoitoreitit) (not (nil? talvihoitoreitit)) @karttataso-nakyvissa?)
        (with-meta map-reitit
          {:selitteet [{:vari (map :color asioiden-ulkoasu/talvihoitoreitit)
                        :teksti "Talvihoitoreitit"
                        :extent extentit}]
           :extent extentit})))))


(defrecord HaeTalvihoitoreitit [])
(defrecord HaeTalvihoitoreititOnnistui [vastaus])
(defrecord HaeTalvihoitoreititEpaonnistui [vastaus])

;; Manipuloi kartalla näytettäviä reittejä atomin kautta
(defrecord PoistaValittuKohdeKartalta [id])
(defrecord LisaaValittuKohdeKartalle [id])

;; Listan käsittely
(defrecord AvaaTalvihoitoreitti [avain])

(extend-protocol tuck/Event

  HaeTalvihoitoreitit
  (process-event
    [_ app]
    (tuck-apurit/post! :hae-urakan-talvihoitoreitit
      {:urakka-id (:id @nav/valittu-urakka)}
      {:onnistui ->HaeTalvihoitoreititOnnistui
       :epaonnistui ->HaeTalvihoitoreititEpaonnistui})

    (assoc app :haku-kaynnissa? true))

  HaeTalvihoitoreititOnnistui
  (process-event
    [{:keys [vastaus]} app]
    (let [;; Lisätään jokaiselle talvihoitoreitille joku random väri, jolla se esitellään näkymässä
          vastaus (mapv (fn [rivi]
                          (assoc rivi :vari (anna-random-vari nil)))
                    vastaus)]

      ;; Jos talvihoitoreittejä löytyy, resetoi kartta
      (do
        (reset! karttataso-talvihoitoreitit vastaus)
        (reset! valitut-kohteet-atom (set (mapv :id vastaus)))
        (-> app
          (assoc :talvihoitoreitit vastaus)
          (assoc :haku-kaynnissa? false)))))

  HaeTalvihoitoreititEpaonnistui
  (process-event
    [{:keys [vastaus]} app]
    (log/error "HaeTalvihoitoreititEpaonnistui :: vastaus" (pr-str vastaus))
    (viesti/nayta-toast! "Urakoiden haussa virhe" :varoitus)
    (-> app
      (assoc :haku-kaynnissa? false)
      (dissoc :talvihoitoreitit)))

  AvaaTalvihoitoreitti
  (process-event [{avain :avain} app]
    (let [avoimet-otsikot (if (:talvihoitoreittien-tilat app)
                            (into #{} (:talvihoitoreittien-tilat app))
                            #{})
          avoimet-otsikot (if (contains? avoimet-otsikot avain)
                            (into #{} (disj avoimet-otsikot avain))
                            (into #{} (cons avain avoimet-otsikot)))]
      (assoc app :talvihoitoreittien-tilat avoimet-otsikot)))

  PoistaValittuKohdeKartalta
  (process-event [{:keys [id]} app]
    (let [_ (reset! valitut-kohteet-atom (disj @valitut-kohteet-atom id))
          valitut-kohteet (if (= (count @valitut-kohteet-atom) 0)
                            #{}
                            @valitut-kohteet-atom)
          valitut-talvihoitoreitit (keep (fn [reitti] (when (contains? valitut-kohteet (:id reitti)) reitti)) @karttataso-talvihoitoreitit)
          alue (harja.geo/extent-monelle (map :sijainti valitut-talvihoitoreitit))]
      (reset! nav/kartan-extent alue)
      app))

  LisaaValittuKohdeKartalle
  (process-event [{:keys [id]} app]
    (let [_ (reset! valitut-kohteet-atom (conj @valitut-kohteet-atom id))
          valitut-kohteet (if (= (count @valitut-kohteet-atom) 0)
                            #{}
                            @valitut-kohteet-atom)
          valitut-talvihoitoreitit (keep (fn [reitti] (when (contains? valitut-kohteet (:id reitti)) reitti)) @karttataso-talvihoitoreitit)
          alue (harja.geo/extent-monelle (map :sijainti valitut-talvihoitoreitit))]
      (reset! nav/kartan-extent alue)
      app)))
