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
            arvo# (reagent.core/atom nil)  ; itse atomin arvo
            reaktio# (reagent.ratom/run!
                      (let [num# (swap! kaynnissa# inc)]
                        (let [chan# ~haku]
                          (if (nil? chan#)
                            (when (= num# @kaynnissa#)
                              (reset! arvo# nil))
                            (cljs.core.async.macros/go
                              (let [res# (cljs.core.async/<! chan#)]
                                (when (= num# @kaynnissa#)
                                  (reset! arvo# ((or ~prosessoi identity) res#)))))))))]
        (harja.loki/log "ASYNC REAKTIO: " reaktio#)
        
        arvo#)))
            
