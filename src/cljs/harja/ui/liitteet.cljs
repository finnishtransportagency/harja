(ns harja.ui.liitteet
  "Yleisiä UI-komponentteja liitteiden lataamisen hoitamiseksi."
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! timeout]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.modal :as modal]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.ikonit :as ikonit]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.img-with-exif :refer [img-with-exif]]
            [harja.fmt :as fmt]
            [harja.ui.komponentti :as komp])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn naytettava-liite?
  "Kertoo, voidaanko liite näyttää käyttäjälle modaalissa (esim. kuvat)
   vai onko tarkoitus tarjota puhdas latauslinkki"
  [liite]
  (if liite
    (zero? (.indexOf (:tyyppi liite) "image/"))
    false))

(defn liitekuva-modalissa [liite]
  [img-with-exif {:class "kuva-modalissa"
                  :src (k/liite-url (:id liite))}])

(defn- nayta-liite-modalissa [liite]
  (modal/nayta!
    {:otsikko (str "Liite: " (:nimi liite))
     :leveys "80%"
     :luokka "kuva-modal"}
    (liitekuva-modalissa liite)))

(defn liitetiedosto
  "Näyttää liitteen pikkukuvan ja nimen."
  [tiedosto]
  [:div.liite
   (if (naytettava-liite? tiedosto)
     [:span
      [:img.pikkukuva.klikattava {:src (k/pikkukuva-url (:id tiedosto))
                                  :on-click #(nayta-liite-modalissa tiedosto)}]
      [:span.liite-nimi (:nimi tiedosto)]]
     [:a.liite-linkki {:target "_blank" :href (k/liite-url (:id tiedosto))} (:nimi tiedosto)])])

(defn liite-linkki
  "Näyttää liitteen tekstilinkkinä (teksti voi olla myös ikoni).
   Näytettävät liitteet avataan modaalissa, muutan tarjotaan normaali latauslinkki.

   Optiot:
   nayta-tooltip?     Näyttää liitteen nimen kun hiirtä pidetään linkin päällä (oletus true)"
  ([liite teksti] (liite-linkki liite teksti {}))
  ([liite teksti {:keys [nayta-tooltip?] :as optiot}]
   (if (naytettava-liite? liite)
     [:a.klikattava {:title (let [tooltip (:nimi liite)]
                              (if (nil? nayta-tooltip?)
                                tooltip
                                (when nayta-tooltip? tooltip)))
                     :on-click #(do
                                  (.stopPropagation %)
                                  (nayta-liite-modalissa liite))}
      teksti]
     [:a.klikattava {:title (:nimi liite)
                     :href (k/liite-url (:id liite))
                     :target "_blank"}
      teksti])))

(defn liitteet-numeroina
  "Listaa liitteet numeroina."
  [liitteet]
  [:div.liitteet-numeroina
   (map-indexed
     (fn [index liite]
       ^{:key (:id liite)}
       [:span
        [liite-linkki liite (inc index)]
        [:span " "]])
     liitteet)])

(defn liite-ikonina
  "Näyttää liitteen ikonina."
  ;; PENDING Olisipa kiva jos ikoni heijastelisi tiedoston tyyppiä :-)
  [liite]
  [:span
   [liite-linkki liite (ikonit/file)]
   [:span " "]])

(defn liitteet-ikoneina
  "Listaa liitteet ikoneita."
  [liitteet]
  [:span.liitteet-ikoneina
   (map
     (fn [liite]
       ^{:key (:id liite)}
       [liite-ikonina liite])
     liitteet)])

(defn liitteet-listalla
  "Listaa liitteet leijuvalla listalla."
  [liitteet]
  [:ul.livi-alasvetolista.liitelistaus
   (doall
     (for [liite liitteet]
       ^{:key (hash liite)}
       [:li.harja-alasvetolistaitemi
        [liite-linkki
         liite
         (:nimi liite)
         {:nayta-tooltip? false}]]))])

(defn liitteet-ikonilistana
  "Listaa liitteen ikonina, jota klikkaamalla liitteen voi avata.
   Jos liitteitä on useita, näyttää silti vain yhden ikonin, josta aukeaa lista liitteistä."
  [liitteet]
  (let [lista-auki? (atom false)]
    (komp/luo
      (komp/klikattu-ulkopuolelle #(reset! lista-auki? false))
      (fn [liitteet]
        [:span
         (cond (= (count liitteet) 1)
               [liite-linkki (first liitteet) (ikonit/file)]
               (> (count liitteet) 1)
               [:a.klikattava
                {:on-click (fn []
                             (swap! lista-auki? not))}
                (ikonit/file)])
         (when @lista-auki?
           [liitteet-listalla liitteet])]))))

(defn lisaa-liite
  "Liitetiedosto (file input) komponentti yhden tiedoston lataamiselle.
  Lataa tiedoston serverille ja palauttaa callbackille tiedon onnistuneesta
  tiedoston lataamisesta.

  Optiot voi sisältää:
  grid?              Jos true, optimoidaan näytettäväksi gridissä
  nappi-teksti       Teksti, joka napissa näytetään (vakiona 'Lisää liite')
  liite-ladattu      Funktio, jota kutsutaan kun liite on ladattu onnistuneesti.
                     Parametriksi annetaan mäppi, jossa liitteen tiedot. Esim.

                     :kuvaus, :fileyard-hash, :urakka, :nimi,
                     :id,:lahde,:tyyppi, :koko 65528

                     Metatietona annetaan tieto siitä, oliko kyseessä uuden liitteen
                     lisäys vai vanhan vaihto:

                     :lisays? true/false
                     :vanha-liite { ... }"
  [urakka-id opts]
  (let [;; Ladatun tiedoston tiedot, kun lataus valmis
        tiedosto (atom nil)
        ;; Edistyminen, kun lataus on menossa (nil jos ei lataus menossa)
        edistyminen (atom nil)
        virheviesti (atom nil)]
    (fn [urakka-id {:keys [liite-ladattu nappi-teksti grid?] :as opts}]
      [:span
       ;; Tiedosto ladattu palvelimelle, näytetään se (paitsi gridissä)
       (when (and @tiedosto (not grid?))
         [liitetiedosto @tiedosto])

       (if-let [edistyminen @edistyminen]
         ;; Siirto menossa, näytetään progress
         [:progress {:value edistyminen :max 100}]
         ;; Näytetään liitteen lisäys
         [:span.liitekomponentti
          [:div {:class (str "file-upload nappi-toissijainen " (when grid? "nappi-grid"))
                 :on-click #(.stopPropagation %)}
           [ikonit/ikoni-ja-teksti
            (ikonit/livicon-upload)
            (if @tiedosto
              (if grid?
                (str "Vaihda " (fmt/leikkaa-merkkijono 25 {:pisteet? true} (:nimi @tiedosto)))
                "Vaihda liite")
              (or nappi-teksti "Lisää liite"))]
           [:input.upload
            {:type "file"
             :on-change #(let [ch (k/laheta-liite! (.-target %) urakka-id)]
                           (go
                             (loop [ed (<! ch)]
                               (if (number? ed)
                                 (do (reset! edistyminen ed)
                                     (recur (<! ch)))
                                 (if (and ed (not (k/virhe? ed)))
                                   (do
                                     (reset! edistyminen nil)
                                     (reset! virheviesti nil)
                                     (when liite-ladattu
                                       (let [tiedosto-ennen-latausta @tiedosto
                                             tiedosto-nyt (reset! tiedosto ed)]
                                         (liite-ladattu (with-meta tiedosto-nyt
                                                                   {:uusi-liite? (nil? tiedosto-ennen-latausta)
                                                                    :vanha-liite tiedosto-ennen-latausta})))))
                                   (do
                                     (log "Virhe: " (pr-str ed))
                                     (reset! edistyminen nil)
                                     (reset! virheviesti (str "Liitteen lisääminen epäonnistui"
                                                              (if (:viesti ed)
                                                                (str " (" (:viesti ed) ")"))))))))))}]]
          [:div.liite-virheviesti @virheviesti]])])))

(defn liitteet-ja-lisays
  "Listaa liitteet ja näyttää Lisää liite -napin.

  Optiot voi sisältää:
  uusi-liite-teksti               Teksti uuden liitteen lisäämisen nappiin
  uusi-liite-atom                 Atomi, johon uuden liitteen tiedot tallennetaan
  grid?                           Jos true, optimoidaan näytettäväksi gridissä"
  [urakka-id liitteet {:keys [uusi-liite-teksti uusi-liite-atom grid?]}]
  [:span
   ;; Näytä olemassaolevat liitteet
   (when (and (oikeudet/voi-lukea? oikeudet/urakat-liitteet urakka-id) )
     (for [liite liitteet]
       ^{:key (:id liite)}
       [liitetiedosto liite]))
   ;; Uuden liitteen lähetys
   (when (oikeudet/voi-kirjoittaa? oikeudet/urakat-liitteet urakka-id)
     (when uusi-liite-atom
       [lisaa-liite urakka-id {:liite-ladattu #(reset! uusi-liite-atom %)
                               :nappi-teksti uusi-liite-teksti
                               :grid? grid?}]))])
