(ns harja.atom
  "Erinäisiä atomien ja tilan käsittelyn apureita"
  (:require [cljs.core.async :refer [<! >! chan put! alts! timeout]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

;; Tallennetaan reaktioiden kanavat, komentamista varten
(defonce +reaktiot+ (atom {}))

(defn paivita!
  "Pakottaa reaktion päivittymään."
  [reaktio<!]
  (when-let [paivita (:paivita (get @+reaktiot+ reaktio<!))]
    (paivita)))

(defn paivita-periodisesti [reaktio periodi-ms]
  (let [paivita? (atom true)]
    (go
      (loop []
        (<! (timeout periodi-ms))
        (when @paivita?
          (paivita! reaktio)
          (recur))))
    #(reset! paivita? false)))

(defn kuristin
  "Palauttaa funktion, joka ottaa samat parametrit kuin annettu paivitys-fn, mutta
   ei suorita funktiota heti, vaan odottaa jos parametrit muuttuvat uudelleen.
   Oletuksena odotusaika on 100ms.

   Päivitysfunktion odotetaan tekevän jotain sivuvaikutuksia (kuton atomin reset),
   eikä sen paluuarvolla tehdä mitään."
  ([paivitys-fn] (kuristin 100 paivitys-fn))
  ([odotusaika paivitys-fn]
   (let [parametrit-ch (chan)]
     (go (loop [parametrit (<! parametrit-ch)]
           (let [[arvo kanava] (alts! [parametrit-ch (timeout odotusaika)])]
             (if (= kanava parametrit-ch)
               ;; Uudet parametrit tuli ennen timeouttia
               (recur arvo)
               
               ;; timeout
               (do (apply paivitys-fn parametrit)
                   (recur (<! parametrit-ch)))))))
     (fn [& parametrit]
       (put! parametrit-ch parametrit)))))

(defn haku-lippu
  "Palauttaa funktion, joka ottaa samat parametrit kuin annettu paivitys-fn.
   Kun funktiota kutsutaan, se asettaa lippuna annetun atomin true ja asettaa
   sen takaisin falseksi kun paivitys-fn palauttamasta kanavasta luetaan arvo."
  [lippu paivitys-fn]
  (fn [& parametrit]
    (reset! lippu true)
    (go (let [k (apply paivitys-fn parametrit)]
          (when k
            (<! k))
          (reset! lippu false)))))

(defn paivita-jos-muuttunut
  "Palauttaa funktion, joka ottaa samat parametrit kuin annettu paivitys-fn, mutta 
   ei kutsu funktiota jos parametrit ovat täysin samat kuin edellisellä kerralla.
   Optionaalisesti ottaa arvon, joka palautetaan kun samoilla parametreillä kutsutaan
   (oletus nil).

   Tämä on tarkoitettu lähinnä kanavia palauttaville funktioille, eikä siis vastaa
   memoize funktiota, koska samoille parametreilla ei palauteta samaa tulosta."
  ([paivitys-fn] (paivita-jos-muuttunut paivitys-fn nil))
  ([paivitys-fn paluuarvo-jos-sama]
   (let [edelliset-parametrit (atom ::ensimmainen-kutsu)]
     (fn [& parametrit]
       (let [edelliset @edelliset-parametrit]
         (if (= edelliset parametrit)
           paluuarvo-jos-sama
           (do (reset! edelliset-parametrit parametrit)
               (apply paivitys-fn parametrit))))))))
            
(defn paivittaja
  "Koostaa haku-lippu, kuristin ja paivita-jos-muuttunut funktioiden 
   toiminnallisuuden käteväksi kokonaisuudeksi.
   Palauttaa kaksi funktiota vektorissa: päivitys ja päivityksen lopetus. "
  [kurista-ms haku-lippu-atom paivita-fn]
  (let [aktiivinen (cljs.core/atom true)]
    [(->> (fn [& args]
            (when @aktiivinen
              (apply paivita-fn args)))
          paivita-jos-muuttunut
          (haku-lippu haku-lippu-atom)
          (kuristin kurista-ms))
     #(reset! aktiivinen false)]))
  
