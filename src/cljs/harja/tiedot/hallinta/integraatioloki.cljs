(ns harja.tiedot.hallinta.integraatioloki
  "Hallinnoi integraatiolokin tietoja"
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!] :as async]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log tarkkaile!]]
            [harja.pvm :as pvm]
            [cljs-time.core :as time]
            [harja.atom :refer [paivita!]]
            [cljs-time.core :as t]
            [harja.pvm :as pvm])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go go-loop]]
                   [reagent.ratom :refer [reaction]]))

(defn hae-jarjestelmien-integraatiot []
  (k/post! :hae-jarjestelmien-integraatiot nil))

(defn hae-integraatiotapahtumien-maarat [jarjestelma integraatio]
  (k/post! :hae-integraatiotapahtumien-maarat {:jarjestelma jarjestelma
                                               :integraatio integraatio}))

(defn hae-integraation-tapahtumat [jarjestelma integraatio aikavali hakuehdot]
  (k/post! :hae-integraatiotapahtumat
           (merge {:jarjestelma (:jarjestelma jarjestelma)
                   :integraatio integraatio
                   :hakuehdot hakuehdot}
                  (when aikavali
                    {:alkaen (first aikavali)
                     ;; loppupvm halutaan seuraavan päivän 00:00:00 aikaan, jotta valitun loppupäivän tapahtumat näkyvät
                     :paattyen (when (second aikavali)
                                 (pvm/paivan-alussa (t/plus (second aikavali) (t/days 1))))}))))

(defn hae-integraatiotapahtuman-viestit [tapahtuma-id]
  (k/post! :hae-integraatiotapahtuman-viestit tapahtuma-id))

(def nakymassa? (atom false))

(defonce jarjestelmien-integraatiot (reaction<! [nakymassa? @nakymassa?]
                                                (when nakymassa?
                                                  (hae-jarjestelmien-integraatiot))))

(defonce valittu-jarjestelma (atom nil))
(defonce valittu-integraatio (atom nil))
(defonce valittu-aikavali (atom nil))
(defonce hakuehdot (atom {:tapahtumien-tila :kaikki}))
(defonce nayta-uusimmat-tilassa? (atom true))
;; Kun seurataan ulkoista integraatiolokiin linkkaavaa urlia - näitä lokitetaan ja linkin voi avata suoraan slackista
(defonce tapahtuma-id (atom nil))
(defonce tultiin-urlin-kautta (atom nil))

(defn eilen-tanaan-aikavali []
  [(pvm/aikana (time/yesterday) 0 0 0 0)
   (pvm/aikana (time/today) 23 59 59 999)])


(def haetut-tapahtumat (atom [])) ;; nil jos haku käynnissä, [] jos tyhjä
(def hae-automaattisesti? (atom true))

(defn hae-tapahtumat! []
  (let  [valittu-jarjestelma @valittu-jarjestelma
         valittu-integraatio @valittu-integraatio
         valittu-aikavali (if @nayta-uusimmat-tilassa?
                            (eilen-tanaan-aikavali)
                            @valittu-aikavali)
         nakymassa? @nakymassa?
         hakuehdot @hakuehdot]
    (when nakymassa?
      (reset! haetut-tapahtumat nil)
      ;; Palvelimen päässä on määritelty, että maksimissaan 500 tulosta palautetaan
      (go (let [tapahtumat (<! (hae-integraation-tapahtumat valittu-jarjestelma valittu-integraatio valittu-aikavali hakuehdot))]
            (reset! haetut-tapahtumat tapahtumat)
            (when @tultiin-urlin-kautta
              (go-loop [aukinainen-vetolaatikko (aget (.getElementsByClassName js/document "vetolaatikko-auki") 0)
                        kertoja-loopattu 0]
                       (if (or (= kertoja-loopattu 10) aukinainen-vetolaatikko)
                         (try (.scrollIntoView aukinainen-vetolaatikko true)
                              (catch :default e
                                (log "VIRHE: Skrollaaminen avattuun vetolaatikkoon ei onnistunut" e)))
                         (do
                           (<! (async/timeout 1200))
                           (recur (aget (.getElementsByClassName js/document "vetolaatikko-auki") 0)
                                  (inc kertoja-loopattu)))))
              (reset! tultiin-urlin-kautta nil))
            tapahtumat)))))

(defonce tapahtumien-maarat
         (reaction<! [valittu-jarjestelma @valittu-jarjestelma
                      valittu-integraatio @valittu-integraatio
                      valittu-aikavali @valittu-aikavali
                      nakymassa? @nakymassa?]
                     {:nil-kun-haku-kaynnissa? true}
                     (when nakymassa?
                       (go (let [maarat (<! (hae-integraatiotapahtumien-maarat valittu-jarjestelma valittu-integraatio))]
                          (if valittu-aikavali
                            (filter #(pvm/valissa? (:pvm %)
                                                   (first valittu-aikavali)
                                                   (second valittu-aikavali))
                                    maarat)
                            maarat))))))

(defn nayta-tapahtumat-eilisen-jalkeen []
  (reset! nayta-uusimmat-tilassa? false)
  (reset! haetut-tapahtumat [])
  (reset! valittu-aikavali (eilen-tanaan-aikavali)))

(defn nayta-uusimmat-tapahtumat! []
  (reset! valittu-aikavali nil)
  (reset! nayta-uusimmat-tilassa? true)
  (reset! hakuehdot {:tapahtumien-tila :kaikki})
  (hae-tapahtumat!))
