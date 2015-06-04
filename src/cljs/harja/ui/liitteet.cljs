(ns harja.ui.liitteet
  "Yleisiä UI-komponentteja liitteiden lataamisen hoitamiseksi."
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! timeout]]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defn liite
  "Liitetiedosto (file input) komponentti yhden tiedoston lataamiselle.
Lataa tiedoston serverille ja palauttaa callbackille tiedon onnistuneesta
tiedoston lataamisesta.

Optiot voi sisältää:

  :liite-ladattu     Funktio, jota kutsutaan kun liite on ladattu onnistuneesti.
                     Parametriksi annetaan mäppi, jossa liitteen tiedot: :id,
                     :nimi, :tyyppi, :pikkukuva-url, :url. "

  [opts]
  (let [;; Ladatun tiedoston tiedot, kun lataus valmis
        tiedosto (atom nil)

        ;; Edistymi nen, kun lataus on menossa (nil jos ei lataus menossa)
        edistyminen (atom nil)]
    
    (fn [opts]
      (if-let [tiedosto @tiedosto]
        ;; Tiedosto on jo ladatty palvelimelle, näytetään se
        [:div.liite
         (when-let [pikkukuva-url (:pikkukuva-url tiedosto)]
           [:img.pikkukuva {:src pikkukuva-url}])
         ;; FIXME: voiko tässä poistaa myös?
         (:nimi tiedosto)]

        ;; Ei tiedostoa vielä, joko siirto on menossa tai ei vielä alkanut
        (if-let [edistyminen @edistyminen]
          ;; Siirto menossa, näytetään progress
          [:progress {:value edistyminen :max 100}]
          
          ;; Tiedostoa ei vielä valittu
          [:input {:type "file"
                   :on-change #(let [ch (k/laheta-liite! (.-target %))]
                                 (go
                                   (loop [ed (<! ch)]
                                     (if (number? ed)
                                       (do (reset! edistyminen ed)
                                           (recur (<! ch)))

                                       (reset! tiedosto ed)))))}])))))
          
                                   
                                 
