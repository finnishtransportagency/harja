(ns harja.tiedot.istunto
  "Harjan istunnon tiedot"
  (:require [harja.asiakas.tapahtumat :as t]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]]
            
            [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))
 
(def kayttaja (atom nil))

(def istunto-alkoi (atom nil))

(defn- aseta-kayttaja [k]
  (reset! kayttaja k)
  (t/julkaise! (merge {:aihe :kayttajatiedot} k)))

(t/kuuntele! :harja-ladattu (fn []
                              (go
                                (aseta-kayttaja (<! (k/post! :kayttajatiedot
                                                             (reset! istunto-alkoi (js/Date.))))))))


(def rooli-jarjestelmavastuuhenkilo          "jarjestelmavastuuhenkilo")
(def rooli-tilaajan-kayttaja                 "tilaajan kayttaja")
(def rooli-urakanvalvoja                     "urakanvalvoja")
(def rooli-vaylamuodon-vastuuhenkilo         "vaylamuodon vastuuhenkilo")
(def rooli-liikennepaivystaja                "liikennepäivystäjä")
(def rooli-tilaajan-asiantuntija             "tilaajan asiantuntija")
(def rooli-tilaajan-laadunvalvontakonsultti  "tilaajan laadunvalvontakonsultti")
(def rooli-urakoitsijan-paakayttaja          "urakoitsijan paakayttaja")
(def rooli-urakoitsijan-urakan-vastuuhenkilo "urakoitsijan urakan vastuuhenkilo")
(def rooli-urakoitsijan-kayttaja             "urakoitsijan kayttaja")
(def rooli-urakoitsijan-laatuvastaava        "urakoitsijan laatuvastaava")

;; mahdollisesti jaettavaa koodia backendin kanssa? Checkaa kun clojure 1.7 otetaan käyttöön
(defn rooli-urakassa?
  "Tarkistaa onko käyttäjällä tietty rooli urakassa."
  [rooli urakka-id]
  (if-let [urakkaroolit (some->> (:urakkaroolit @kayttaja)
                                 (filter #(= (:id (:urakka %)) urakka-id))
                                 (map :rooli) 
                                 (into #{}))]
    (if (urakkaroolit rooli)
      true
      false)
    false))



(defn jos-rooli-urakassa
  "Palauttaa komponentin käyttöliittymään jos käyttäjän rooli sallii. 
  Palauttaa muutoin-komponentin jos ei kyseistä roolia."
  ([rooli urakka-id sitten] (jos-rooli-urakassa rooli urakka-id sitten nil))
  ([rooli urakka-id sitten muutoin]
    (if (and @kayttaja (rooli-urakassa? rooli urakka-id))
     sitten
     (let [viesti (str "Käyttäjällä '" (:kayttajanimi @kayttaja) "' ei vaadittua roolia '" rooli "' urakassa " urakka-id)]
       (log viesti) 
       muutoin))))