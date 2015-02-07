(ns harja.tiedot.hallintayksikot
  "Hallinnoi hallintayksiköiden tietoja"
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<! >! chan close!]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t])
  
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def hallintayksikot (atom nil))
 
 
 (defn hae-hallintayksikko 
   "Palauttaa kanavan, josta hallintayksikön tiedot voidaan lukea.
   Jos hallintayksikköä ei ole, tehdään palvelinkutsu."
   [id]
   
   (let [ch (chan)]
     (go (if-let [hy @hallintayksikot]
           (do 
             (>! ch (first (filter #(= id (:id %)) hy)))
             (close! ch))
           ;; else: luetaan hy kun on saatu vastaus palvelimelta
           (let [hy (<! (k/post! :hallintayksikot :tie ))]
             (>! ch (first (filter #(= id (:id %)) hy)))
             (close! ch)
             (comment (reset! hallintayksikot
                                  (mapv (fn [hy]
                                           (assoc hy :type :hy)) hy))))))
     ch))
 
;; Kun käyttäjätiedot saapuvat, hae relevantit hallintayksiköt
(t/kuuntele! :kayttajatiedot
             (fn [kayttaja]
               (when-not @hallintayksikot
                 (k/post! :hallintayksikot
                          :tie ;; FIXME: tämä otettava käyttäjän tiedoista 
                          #(reset! hallintayksikot
                                   (mapv (fn [hy]
                                           (assoc hy :type :hy)) %))))))

                 
 
