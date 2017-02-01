(ns harja.tiedot.navigaatio.reitit
  "Määrittelee välilehtien valinnat ja hoittaa URL-muodostuksen
  sekä #-polun tulkinnan"
  (:require [clojure.string :as str]
            [reagent.core :refer [atom wrap]]))

;; Atomi, joka sisältää valitun sivun ja eri osioiden valitut välilehdet
(defonce url-navigaatio
  (atom {:sivu :urakat
         :urakat :yleiset
         :suunnittelu :kokonaishintaiset
         :toteumat :kokonaishintaiset-tyot
         :toteutus :kokonaishintaiset-tyot
         :laskutus :laskutusyhteenveto
         :hallinta :indeksit
         :laadunseuranta :tarkastukset
         :kohdeluettelo-paallystys :paallystyskohteet
         :kohdeluettelo-paikkaus :paikkauskohteet
         :raportit nil
         :tilannekuva :nykytilanne}))

(defn aseta-valittu-valilehti!
  [osio valilehti]
  (swap! url-navigaatio assoc osio valilehti))

(defn valittu-valilehti-atom
  "Palauttaa wrapatyn atomin annetun osion valitulle välilehdelle"
  [osio]
  (wrap (get @url-navigaatio osio)
        (partial aseta-valittu-valilehti! osio)))

(defn valittu-valilehti
  "Palauttaa annetun osion tällä hetkellä valitun välilehden"
  [osio]
  (get @url-navigaatio osio))

(defn muodosta-polku
  "Muodostaa # polun annetun navigaatiotilan perusteella"
  [url-navigaatio]
  (loop [polku [(get url-navigaatio :sivu)]]
    (let [osio (last polku)]
      (if (contains? url-navigaatio osio)
        (recur (conj polku (get url-navigaatio osio)))
        (str/join "/" (keep #(and % (name %)) polku))))))

(defn tulkitse-polku
  "Tulkitsee URL-polun ja palauttaa sen perusteella päivitetyn navigaatiotilan"
  [url-navigaatio polku]
  (loop [url-navigaatio url-navigaatio
         sijainti :sivu
         [osa & osat] (keep #(when-not (str/blank? %)
                               (keyword %))
                            (str/split polku #"/"))]
    (if-not osa
      url-navigaatio
      (recur (assoc url-navigaatio sijainti osa)
             osa
             osat))))
