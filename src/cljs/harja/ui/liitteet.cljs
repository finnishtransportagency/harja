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

(defn naytettava-liite?
  "Kertoo, voidaanko liite näyttää käyttäjälle esim. modaalissa vai onko tarkoitus tarjota puhdas latauslinkki"
  [liite]
  (zero? (.indexOf (:tyyppi liite) "image/")))

(defn liitekuva-modalissa [liite]
  [:div.liite-ikkuna
   [:img {:src (k/liite-url (:id liite))}]])

(defn liitetiedosto
  "Näyttää liitteen pikkukuvan ja nimen. Näytettävä liite avataan modalissa, muuten tarjotaan normaali latauslinkki."
  [tiedosto]
  [:div.liite
   (if (naytettava-liite? tiedosto)
     [:span
      [:img.pikkukuva.klikattava {:src (k/pikkukuva-url (:id tiedosto))
                                  :on-click #(modal/nayta!
                                              {:otsikko (str "Liite: " (:nimi tiedosto))}
                                              (liitekuva-modalissa tiedosto))}]
      [:span.liite-nimi (:nimi tiedosto)]]
     [:a.liite-linkki {:target "_blank" :href (k/liite-url (:id tiedosto))} (:nimi tiedosto)])])

(defn liitelistaus
  "Listaa liitteet numeroina. Näytettävät liitteet avataan modalissa, muuten tarjotaan normaali latauslinkki."
  [liitteet]
  [:div.liitelistaus
   (map-indexed
     (fn [index liite]
       ^{:key (:id liite)}
       [:span
        (if (naytettava-liite? liite)
          [:a.klikattava {:on-click #(modal/nayta!
                                         {:otsikko (str "Liite: " (:nimi liite))}
                                         (liitekuva-modalissa liite))}
           (inc index)]
          [:a {:href (k/liite-url (:id liite))
               :target "_blank"}
           (inc index)])
        [:span " "]])
     liitteet)])

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
           [:span (ikonit/livicon-upload) (if @tiedosto
                                    " Vaihda liite"
                                    (str " " (or nappi-teksti "Valitse tiedosto")))]
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

(defn liitteet [{:keys [uusi-liite-teksti uusi-liite-atom urakka-id]} liitteet]
  [:span
   ;; Näytä olemassaolevat liitteet
   (for [liite liitteet]
     ^{:key (:id liite)}
     [liitetiedosto liite])
   ;; Uuden liitteen lähetys
   (when uusi-liite-atom
     [liite {:urakka-id urakka-id
             :liite-ladattu #(reset! uusi-liite-atom %)
             :nappi-teksti (or uusi-liite-teksti "Lisää liite")}])])
