(ns harja.ui.liitteet
  "Yleisiä UI-komponentteja liitteiden lataamisen hoitamiseksi."
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! timeout]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.modal :as modal])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn liitetiedosto
  "Olemassaolevan liitteen näyttäminen. Näyttää pikkukuvan ja tiedoston nimen."
  [tiedosto]
  (let [naytettava? (zero? (.indexOf (:tyyppi tiedosto) "image/"))] ; jos kuva MIME tyyppi, näytetään modaalissa
    [:div.liite {:on-click #(modal/nayta!
                             {:otsikko (str "Liite: " (:nimi tiedosto))}
                             [:div.liite-ikkuna
                              [:img {:src (k/liite-url (:id tiedosto))}]])}
     [:img.pikkukuva {:src (k/pikkukuva-url (:id tiedosto))}]
     ;; FIXME: voiko tässä poistaa myös?
     (if-not naytettava?
       [:a.liite-linkki {:target "_blank" :href (k/liite-url (:id tiedosto))} (:nimi tiedosto)]
       [:span.liite-nimi (:nimi tiedosto)])]))

(defn liite
  "Liitetiedosto (file input) komponentti yhden tiedoston lataamiselle.
Lataa tiedoston serverille ja palauttaa callbackille tiedon onnistuneesta
tiedoston lataamisesta.

Optiot voi sisältää:
  :urakka-id         urakan id, jolle liite lisätään
  :liite-ladattu     Funktio, jota kutsutaan kun liite on ladattu onnistuneesti.
                     Parametriksi annetaan mäppi, jossa liitteen tiedot: :id,
                     :nimi, :tyyppi, :pikkukuva-url, :url. "

  [opts]
  (let [;; Ladatun tiedoston tiedot, kun lataus valmis
        tiedosto (atom nil)
        ;; Edistyminen, kun lataus on menossa (nil jos ei lataus menossa)
        edistyminen (atom nil)]
    
    (fn [{:keys [liite-ladattu] :as opts}]
      (if-let [tiedosto @tiedosto]
        ;; Tiedosto on jo ladatty palvelimelle, näytetään se
        [liitetiedosto tiedosto]

        ;; Ei tiedostoa vielä, joko siirto on menossa tai ei vielä alkanut
        (if-let [edistyminen @edistyminen]
          ;; Siirto menossa, näytetään progress
          [:progress {:value edistyminen :max 100}]
          
          ;; Tiedostoa ei vielä valittu
          [:div.file-upload.btn.btn-primary
           [:span "Valitse tiedosto"]
           [:input.upload
            {:type "file"
             :on-change #(let [ch (k/laheta-liite! (.-target %) (:urakka-id opts))]
                           (go
                             (loop [ed (<! ch)]
                               (if (number? ed)
                                 (do (reset! edistyminen ed)
                                     (recur (<! ch)))

                                 (liite-ladattu (reset! tiedosto ed))))))}]])))))
          
                                   
                                 
