(ns harja.atom
  "Apureita atomeille."
  (:require [reagent.ratom :refer [run!]]))


(defmacro reaction<!
  "Asynkroninen reaktio (esim. serveriltä haku). Haun tulee palauttaa kanava (tai nil).
  Kun kanava palauttaa dataa, se asetetaan atomin tilaksi. Reaktio laukaistaan uudelleen
  ennen kuin kanavasta on luettu, hylätään arvo. Jos haku palauttaa nil, asetetaan atomin arvoksi nil.

  Ottaa sisään let-bindings vektorin, [arvo1 @riippuvuusatomi1 ...] jossa kaikki atomit, joihin
  reaktion halutaan riippuvan, tulee määritellä. Arvoja voi käyttää bodyssa hyödyksi.
  Reaktiossa on 20ms kuristus, eli ennen kuin reaktio ajetaan, odotetaan 20ms jos riippuvuudet
  muuttuvat uudelleen. Kun riippuvuudet eivät ole muuttuneet 20ms aikana, ajetaan reaktio 
  viimeisimmillä arvoilla.

  Reaktion tulee palauttaa kanava, josta tuloksen voi lukea."
  [let-bindings & body]
  (let [asetukset {} ;;(into {} (map vec (partition 2 asetukset)))
        bindings (mapv vec (partition 2 let-bindings))
        nimi-symbolit (vec (take (count bindings) (repeatedly #(gensym "ARG"))))
        nimet (mapv first bindings)]
    `(let [arvo# (reagent.core/atom nil)
           parametrit-ch# (cljs.core.async/chan)
           paivita# (reagent.core/atom 0)]
       (cljs.core.async.macros/go
         (loop [haku-ch# nil ;; käynnissä olevan haun kanava
                parametrit# (cljs.core.async/<! parametrit-ch#)]

           (harja.loki/log "reaction-bind LOOP, haku-ch: " haku-ch# "; parametrit: " (pr-str parametrit#))
           ;; Jos parametrit on, katsotaan muuttuvatko ne kurista ajan sisällä
           (let [timeout-ch# (when parametrit# (cljs.core.async/timeout ~(or (:kurista asetukset) 20)))
                 kanavat# (if timeout-ch#
                            [parametrit-ch# timeout-ch#]
                            [parametrit-ch#])
                 kanavat# (if haku-ch#
                            (conj kanavat# haku-ch#)
                            kanavat#)
                 [vastaus# kanava#] (cljs.core.async/alts! kanavat#)]

             (harja.loki/log "reaction-bind luettu, kanava: " (condp = kanava#
                                                                timeout-ch# "timeout"
                                                                parametrit-ch# "parametrit"
                                                                haku-ch# "haku"
                                                                "N/A") ", vastaus: " (pr-str vastaus#))
             (if (= kanava# parametrit-ch#)
               ;; Saatiin parametrit, loopataan uudelleen
               (do (harja.loki/log "saatiin parametrit, loopataan uudelleen")
                   (recur haku-ch# vastaus#))

               (let [[haku# parametrit#]
                     (if (= kanava# haku-ch#)
                       ;; Serveri vastaus tuli läpi => asetetaan tila
                       (do (harja.loki/log "serveri vastaus tuli")
                           (reset! arvo# vastaus#)
                           [nil parametrit#])
                       
                       ;; Timeout, tehdään kutsu
                       (if (= kanava# timeout-ch#)
                         (do (harja.loki/log "timeout, tehdään kutsu")
                             (let [[~@nimet] parametrit#
                                   chan# (do ~@body)]
                               (harja.loki/log "  body evaluoitui: " (pr-str chan#))
                               (if (nil? chan#)
                                 (do (reset! arvo# nil)
                                     [nil (cljs.core.async/<! parametrit-ch#)])
                                 [chan# nil])))
                         (harja.loki/log "tuntematon kanava!!!")))]
                 (recur haku# parametrit#))))))
       

       (reagent.ratom/run!
        (let [~@(interleave nimi-symbolit (map second bindings))]
          @paivita#
          (cljs.core.async.macros/go
            (cljs.core.async/>! parametrit-ch#
                                [~@nimi-symbolit]))))

       (swap! harja.atom/+reaktiot+ assoc arvo# {:paivita #(swap! paivita# inc)})
       arvo#)))
           
(comment
  ;; VANHA eri tavalla toimiva reaction<! vielä esimerkkinä, jos konversiossa uuteen
  ;; tulee ongelmia
  (defmacro reaction<!
    "Asynkroninen reaktio (esim. serveriltä haku). Haun tulee palauttaa kanava (tai nil).
  Kun kanava palauttaa dataa, se asetetaan atomin tilaksi. Reaktio laukaistaan uudelleen
  ennen kuin kanavasta on luettu, hylätään arvo. Jos haku palauttaa nil, asetetaan atomin arvoksi nil.
Optionaalisesti ottaa formin, jolla tulos käsitellään ennen atomin tilaksi asettamista."
    ([haku] `(reaction<! ~haku nil))
    ([haku prosessoi] 
       `(let [kaynnissa# (cljs.core/atom 0) ; käynnissä olevan reaktiohaun numero
              arvo# (reagent.core/atom nil) ; itse atomin arvo
              reaktio# (reagent.ratom/run!
                        (let [num# (swap! kaynnissa# inc)]
                          ;; Emme voi aloittaa go blockia tässä, koska silloin lukemisesta
                          ;; ei nauhoiteta riippuvuutta
                          (let [chan# ~haku]
                            (if (nil? chan#)
                              (when (= num# @kaynnissa#)
                                (reset! arvo# nil))
                              (cljs.core.async.macros/go 
                                (let [res# (cljs.core.async/<! chan#)]
                                  (when (= num# @kaynnissa#)
                                    (reset! arvo# ((or ~prosessoi identity) res#)))))))))]
          (harja.loki/log "ASYNC REAKTIO: " reaktio#)
        
          arvo#))))
            
