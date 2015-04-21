(ns harja.tiedot.urakka.valitavoitteet
  "Ylläpidon urakoiden välitavoitteiden tiedot."
  (:require [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :refer [<! >! chan]]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]))



(defn hae-urakan-valitavoitteet [urakka-id]
  (let [ch (chan)]
    (go
      (>! ch (<! (k/post! :hae-urakan-valitavoitteet urakka-id))))
    (comment [{:id 1 :nimi "Suojatiet" :takaraja (pvm/luo-pvm 2015 2 17)
               :valmis {:pvm (pvm/luo-pvm 2015 2 16) :kommentti "saatiin ne tehtyä vaikka tiukille meni aika"}
               :sakko 1500}
                        
              {:id 2 :nimi "Keskustan keltaiset viivat" :valmis nil :takaraja (pvm/luo-pvm 2015 6 7)
               :sakko 2000}])
    
    
    ;;(>! ch (<! (k/post! :hae-urakan-valitavoitteet urakka-id))))
    ch))

