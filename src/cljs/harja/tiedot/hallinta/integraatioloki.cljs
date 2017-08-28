(ns harja.tiedot.hallinta.integraatioloki
  "Hallinnoi integraatiolokin tietoja"
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log tarkkaile!]]
            [harja.pvm :as pvm]
            [cljs-time.core :as time]
            [harja.atom :refer [paivita!]]
            [cljs-time.core :as t]
            [harja.pvm :as pvm])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defn hae-jarjestelmien-integraatiot []
  (k/post! :hae-jarjestelmien-integraatiot nil))

(defn hae-integraatiotapahtumien-maarat [jarjestelma integraatio]
  (k/post! :hae-integraatiotapahtumien-maarat {:jarjestelma jarjestelma
                                               :integraatio integraatio}))

(defn hae-integraation-tapahtumat [jarjestelma integraatio aikavali hakuehdot]
  (log "hae-integraation-tapahtumat" jarjestelma integraatio (pr-str aikavali) (pr-str hakuehdot))
  (k/post! :hae-integraatiotapahtumat
           (merge {:jarjestelma (:jarjestelma jarjestelma)
                   :integraatio integraatio
                   :hakuehdot hakuehdot}
                  (when aikavali
                    {:alkaen      (first aikavali)
                     ;; loppupvm halutaan seuraavan päivän 00:00:00 aikaan, jotta valitun loppupäivän tapahtumat näkyvät
                     :paattyen    (t/plus (second aikavali) (t/days 1))}))))

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

(defn eilen-tanaan-aikavali []
  [(pvm/aikana (time/yesterday) 0 0 0 0)
   (pvm/aikana (time/today) 23 59 59 999)])


(def haetut-tapahtumat (atom []))

(defn hae-tapahtumat! []
  (log "hae-tapahtumat! kutsuttu")
  (let  [valittu-jarjestelma @valittu-jarjestelma
         valittu-integraatio @valittu-integraatio
         valittu-aikavali (if @nayta-uusimmat-tilassa?
                            (eilen-tanaan-aikavali)
                            @valittu-aikavali)
         nakymassa? @nakymassa?
         hakuehdot @hakuehdot]
    (when nakymassa?
      (do
        (log "haetaan tapahtumat:" valittu-jarjestelma valittu-integraatio (pr-str valittu-aikavali) (pr-str  hakuehdot))
        (go (let [tapahtumat (<! (hae-integraation-tapahtumat valittu-jarjestelma valittu-integraatio valittu-aikavali hakuehdot))]
              (log "saatiin tapahtumat:" (pr-str tapahtumat))
              (reset! haetut-tapahtumat tapahtumat)
              tapahtumat))))))

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
  (reset! valittu-aikavali (eilen-tanaan-aikavali)))

(defn nayta-uusimmat-tapahtumat! []
  (reset! valittu-aikavali nil)
  (reset! nayta-uusimmat-tilassa? true)
  (hae-tapahtumat!))
