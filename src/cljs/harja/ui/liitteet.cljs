(ns harja.ui.liitteet
  "Yleisiä UI-komponentteja liitteiden lataamisen hoitamiseksi."
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! timeout]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.modal :as modal]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.ikonit :as ikonit])
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

(defn tarkista-liite [liite]
  (let [max-koko-tavuina 16000000 ; FIXME Nämä pitäisi ehkä laittaa jonnekin asetustiedostoon? --> Laitetaan cljc:n niin pysyy synkassa frontilla ja backendilla
        whitelist #{"image/png" "application/zip" "image/jpeg"}]
    (if (> (:koko liite) max-koko-tavuina)
      {:hyvaksytty false :viesti (str "Liite on liian suuri (max-koko " max-koko-tavuina " tavua).")} ;; FIXME Näytä megatavuina
      (if (nil? (whitelist (:tyyppi liite)))
        {:hyvaksytty false :viesti "Tiedostotyyppi ei ole sallittu."}
        {:hyvaksytty true :viesti nil}))))

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
      (if-let [tiedosto @tiedosto]
        ;; Tiedosto on jo ladatty palvelimelle, näytetään se
        [liitetiedosto tiedosto]

        ;; Ei tiedostoa vielä, joko siirto on menossa tai ei vielä alkanut
        (if-let [edistyminen @edistyminen]
          ;; Siirto menossa, näytetään progress
          [:progress {:value edistyminen :max 100}]

          ;; Tiedostoa ei vielä valittu
          [:span.liitekomponentti
           [:div.file-upload.nappi-toissijainen
            [:span (ikonit/upload) (or nappi-teksti " Valitse tiedosto")]
            [:input.upload
             {:type      "file"
              :on-change #(let [ch (k/laheta-liite! (.-target %) (:urakka-id opts))]
                           (go
                             (loop [ed (<! ch)]
                               (if (number? ed)
                                 (do (reset! edistyminen ed)
                                     (recur (<! ch)))
                                 (do
                                   (let [tarkistus-tulos (tarkista-liite ed)]
                                     (if (:hyvaksytty tarkistus-tulos)
                                       (do
                                         (log "Liite OK. Tiedot: " (pr-str ed))
                                         (liite-ladattu (reset! tiedosto ed)))
                                       (do
                                         (reset! virheviesti (str "Liite hylätty: " (:viesti tarkistus-tulos)))
                                         (reset! edistyminen nil)
                                         (log "Liite hylätty: " (:viesti tarkistus-tulos))))))))))}]]
            [:div.liite-virheviesti @virheviesti]])))))