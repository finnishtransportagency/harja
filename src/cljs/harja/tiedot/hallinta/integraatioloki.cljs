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

(defn hae-integraation-tapahtumat [jarjestelma integraatio aikavali]
  (k/post! :hae-integraatiotapahtumat
           (merge {:jarjestelma (:jarjestelma jarjestelma)
                   :integraatio integraatio}
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

(defonce haetut-tapahtumat
  (reaction<! [valittu-jarjestelma @valittu-jarjestelma
               valittu-integraatio @valittu-integraatio
               valittu-aikavali @valittu-aikavali
               nakymassa? @nakymassa?]
              (when nakymassa?
                (hae-integraation-tapahtumat valittu-jarjestelma valittu-integraatio valittu-aikavali))))

(defonce tapahtumien-maarat
         (reaction<! [valittu-jarjestelma @valittu-jarjestelma
                      valittu-integraatio @valittu-integraatio
                      valittu-aikavali @valittu-aikavali
                      nakymassa? @nakymassa?]
                     (when nakymassa?
                       (go (let [maarat (<! (hae-integraatiotapahtumien-maarat valittu-jarjestelma valittu-integraatio))]
                          (if valittu-aikavali
                            (filter #(pvm/valissa? (:pvm %)
                                                   (first valittu-aikavali)
                                                   (second valittu-aikavali))
                                    maarat)
                            maarat))))))

(defn nayta-tapahtumat-eilisen-jalkeen []
  (let [eilen (pvm/aikana (time/yesterday) 0 0 0 0)
        tanaan (pvm/aikana (time/today) 23 59 59 999)]
    (reset! valittu-aikavali [eilen tanaan])))

(defn nayta-uusimmat-tapahtumat []
  (reset! valittu-aikavali nil))

(defn paivita-tapahtumat! []
  (paivita! haetut-tapahtumat))
