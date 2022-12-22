 (ns harja.views.info
   "Infonäkymä mihin siirretty koulutusvideot julkiselta sisäiseen palvelimeen.
   Videot haetaan tietokannasta rajapintaa käyttäen"
  (:require [tuck.core :refer [tuck]]
            [reagent.core :refer [atom] :as reagent]
            [harja.ui.komponentti :as komp]
            [harja.ui.ikonit :as ikonit]
            [clojure.string :as str]
            [harja.pvm :as pvm]
            [harja.tiedot.info :as tiedot]))

(def klikattu (atom [])) 

(defn lisaa-asetus [asetus]
  (swap! klikattu conj asetus))

(defn swap-klikattu
  "Muokkaa klikattu atom arvoa"
  [params]
  (swap! klikattu
         (fn [old]
           (mapv (fn [order]
                   (if (= (:id order) 
                          (:id params))
                     (merge order params)
                     order))
                 old))))

(defn hae-arvo 
  "Hakee listan indeksin :id arvon"
  [array indeksi]
  (for [x array :when (= (:id x) indeksi)]
    x))

(defn klikattu? 
  "Palauttaa onko @klikattu indeksiä olemassa"
  [indeksi]
  (seq (hae-arvo @klikattu indeksi)))

(defn formatoi-embed-linkki
  "Palauttaa annetun linkin videokoodin"
  [linkki]
  ; Regex pattern ei toimi frontissa (?)
  ; (println "Link: " (nth (re-seq
  ;                        #"^((?:https?:)?\/\/)?((?:www|m)\.)?((?:youtube(-nocookie)?\.com|youtu.be))(\/(?:[\w\-]+\?v=|embed|live\/|v\/)?)([\w\-]+)(\S+)?$"
  ;                        linkki) 6))
  (second (str/split (get (str/split linkki #"/") 3) #"v=")))

(defn videolistaus [_ videot]
  [:div
   [:ul {:class "info-lista"}

    (doall
     (map (fn [{:as m}]
            ^{:key (m :id)}

            [:li
             [:div
              {:class "video-wrap"
               :on-click (fn []
                           ; Videon "klikattu" arvoa ei ole olemassa => lisää 
                           (when (not (klikattu? (m :id)))
                             (lisaa-asetus {:id (m :id) :open false}))

                           ; Vaihda arvo
                           (swap-klikattu
                            {:id (m :id)
                             :open (not (:open (first (hae-arvo @klikattu (m :id)))))}))}
              ; Päivämäärä
              [:span [ikonit/ikoni-ja-teksti [ikonit/harja-icon-misc-clock] (pvm/pvm (m :pvm))]]
              [:br]

              ; Video
              [:span
               (if (:open (first (hae-arvo @klikattu (m :id))))

                 ; Video on auki => näytä video
                 [:div {:class "info-video"}
                  [:div {:class "upotettu-otsikko"} (m :otsikko)]
                  [:iframe {:class "video-iframe"
                            :src (str "https://www.youtube.com/embed/" (formatoi-embed-linkki (m :linkki)))}]]

                 ; Video on kiinni => näytä pelkkä otsikko
                 [:div {:class "video-otsikko"} (m :otsikko)])]]])

          videot))]])

(defn videot* [e! _]
  (komp/luo
   (komp/sisaan
    #(do
       (e! (tiedot/->HaeKoulutusvideot))))

   (fn [e! {:keys [videot]}]
     [:span
      [:div.section
       [:h1 {:class "header-yhteiset"} "Harja Info"
        [:div {:class "otsikko-viiva"}]

        [:ul
         [:h2 {:class "header-yhteiset"} "Harja uutiset"]
         [:ul
          [:h3 {:class "header-yhteiset"} [:a {:href "https://finnishtransportagency.github.io/harja/" :target "_blank" :style {:color "#004D99"}}
                                           [ikonit/ikoni-ja-teksti "https://finnishtransportagency.github.io/harja/ " [ikonit/link]]]]]]

        [:ul
         [:h2 {:class "header-yhteiset"} "Harja Tietosuojaseloste"]
         [:ul
          [:h3 {:class "header-yhteiset"} [:a {:href "https://vayla.fi/tietoa-meista/yhteystiedot/tietosuoja" :target "_blank" :style {:color "#004D99"}}
                                           [ikonit/ikoni-ja-teksti "https://vayla.fi/tietoa-meista/yhteystiedot/tietosuoja " [ikonit/link]]]]]]

        [:ul {:class "info-heading"} "Harja koulutusvideot"
         [:p {:class "info-heading-pieni main"} "Koulutusvideoita HARJA:n käytön tueksi."]

         [:div {:class "videot"}
          [videolistaus e! videot]]]]]])))

(defn info 
  "Hakee koulutusvideot kun käyttäjä tulee näkymään"
  []
  [tuck tiedot/tila videot*]) 