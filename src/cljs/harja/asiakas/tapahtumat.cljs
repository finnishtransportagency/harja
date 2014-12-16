(ns harja.asiakas.tapahtumat
  "Harjan asiakaspään eventbus"
  (:require [cljs.core.async :refer [<! >! chan alts! pub sub unsub unsub-all]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def julkaisukanava (chan))

(def julkaisu (pub julkaisukanava :aihe))

(defn kuuntele!
  "Aloita tietyn aiheen kuuntelu. Aiheen viestit kirjoitetaan annettuun kanavaan tai 
annetaan parametrina kutsuna funktiolle. Palauttaa 0-arity funktion, jolla kuuntelun
voi lopettaa."
  [aihe kanava-tai-funktio]
  (if (fn? kanava-tai-funktio)
    ;; Kuuntelija on funktio, tehdään go-luuppi joka kutsuu sitä
    (let [kasittelija kanava-tai-funktio
          ch (chan)]
      (go (loop [tapahtuma (<! ch)]
            (when tapahtuma
              (kasittelija tapahtuma)
              (recur (<! ch)))))
      (sub julkaisu aihe ch)
      #(unsub julkaisu aihe ch))

    ;; Kuuntelija on kanava
    (let [kanava kanava-tai-funktio]
      (sub julkaisu aihe kanava)
      #(unsub julkaisu aihe kanava))))

(defn julkaise!
  "Julkaise tapahtuma. Tapahtuman tulee olla mäppi, jossa on vähintään :aihe avain."
  [tapahtuma]
  (go (>! julkaisukanava tapahtuma)))
  
  
