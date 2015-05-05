(ns harja.atom
  "Apureita atomeille."
  (:require [reagent.ratom :refer [run!]]))

(defmacro reaction<!
  "Asynkroninen reaktio (esim. serveriltä haku). Haun tulee palauttaa kanava (tai nil).
  Kun kanava palauttaa dataa, se asetetaan atomin tilaksi. Reaktio laukaistaan uudelleen
  ennen kuin kanavasta on luettu, hylätään arvo. Jos haku palauttaa nil, asetetaan atomin arvoksi nil.
Optionaalisesti ottaa formin, jolla tulos käsitellään ennen atomin tilaksi asettamista."
  ([haku] `(reaction<! ~haku nil))
  ([haku prosessoi] 
     `(let [kaynnissa# (cljs.core/atom 0) ; käynnissä olevan reaktiohaun numero
            arvo# (reagent.core/atom nil)] ; itse atomin arvo
        (harja.loki/log "reaction<!  luotu uusi asynk reaktio..")
        (reagent.ratom/run!
         (let [num# (swap! kaynnissa# inc)]
           (harja.loki/log "reaction<!  run! lohkossa, ennen go lohkoa, num = " num#)
           (let [chan# ~haku]
             (harja.loki/log "reaction<!  body palautti: " chan#)
             (if (nil? chan#)
               (when (= num# @kaynnissa#)
                 (reset! arvo# nil))
               (cljs.core.async.macros/go
                 (let [res# (cljs.core.async/<! chan#)]
                   (harja.loki/log "reaction<!  hakutulos saatu: " (pr-str res#))
                   (if (= num# @kaynnissa#)
                     (do
                       (harja.loki/log "reaction<!  pyynnön numero sama, asetetaan atomin tila")
                       (reset! arvo# ((or ~prosessoi identity) res#)))
                     (do
                       (harja.loki/log "reaction<!  vanha pyynnön numero " num# " => ei aseteta atomin tilaa")))))))))
        (harja.loki/log "reaction<!  run! ajettu...")
        arvo#)))
            
