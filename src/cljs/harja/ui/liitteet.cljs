(ns harja.ui.liitteet
  "Yleisiä UI-komponentteja liitteiden lataamisen hoitamiseksi."
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! timeout]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.modal :as modal]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.ikonit :as ikonit]
            [harja.tietoturva.liitteet :as t-liitteet])
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
        edistyminen (atom nil)
        virheviesti (atom nil)]

    (fn [{:keys [liite-ladattu nappi-teksti] :as opts}]
      [:span
       (if-let [tiedosto @tiedosto]
         [liitetiedosto tiedosto]) ;; Tiedosto ladattu palvelimelle, näytetään se
       (if-let [edistyminen @edistyminen]
         [:progress {:value edistyminen :max 100}] ;; Siirto menossa, näytetään progress
         [:span.liitekomponentti
          [:div.file-upload.nappi-toissijainen
           [:span (ikonit/upload) (if @tiedosto
                                    "Vaihda liite"
                                    (or nappi-teksti " Valitse tiedosto"))]
           [:input.upload
            {:type      "file"
             :on-change #(let [ch (k/laheta-liite! (.-target %) (:urakka-id opts))]
                          (go
                            (loop [ed (<! ch)]
                              (if (number? ed)
                                (do (reset! edistyminen ed)
                                    (recur (<! ch)))
                                (if (and ed (not (k/virhe? ed)))
                                  (do
                                    (reset! edistyminen nil)
                                    (reset! virheviesti nil)
                                    (liite-ladattu (reset! tiedosto ed)))
                                  (do
                                    (log "Virhe: " (pr-str ed))
                                    (reset! edistyminen nil)
                                    (reset! virheviesti (str "Liitteen lisääminen epäonnistui"
                                                             (if (:viesti ed)
                                                               (str " (" (:viesti ed) ")"))))))))))}]]
          [:div.liite-virheviesti @virheviesti]])])))