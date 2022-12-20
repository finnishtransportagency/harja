 (ns harja.views.info
   "Infonäkymä mihin siirretty koulutusvideot julkiselta sisäiseen palvelimeen.
   Videot haetaan tietokannasta rajapintaa käyttäen"
  (:require [tuck.core :refer [tuck]]
            [reagent.core :refer [atom] :as reagent]
            [harja.ui.komponentti :as komp]
            [harja.ui.ikonit :as ikonit]
            [clojure.string :as st]
            [harja.tiedot.info :as tiedot]))

(def clicked (atom [])) 

(defn lisaa-asetus
  [asetus]
  (swap! clicked conj asetus))

(defn swap-clicked
  "Muokkaa clicked atom arvoa"
  [params]
  (swap! clicked
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
  "Palauttaa onko @clicked indeksiä olemassa"
  [indeksi]
  (if (= (hae-arvo @clicked indeksi) ())
    false
    true))

(defn formatoi-embed-linkki
  "Palauttaa annetun linkin videokoodin"
  [linkki]
  ; Regex pattern ei toimi frontissa (?)
  ; (println "Link: " (nth (re-seq
  ;                        #"^((?:https?:)?\/\/)?((?:www|m)\.)?((?:youtube(-nocookie)?\.com|youtu.be))(\/(?:[\w\-]+\?v=|embed|live\/|v\/)?)([\w\-]+)(\S+)?$"
  ;                        linkki) 6))
  (second (st/split (get (st/split linkki #"/") 3) #"v=")))

(defn listaa-videot
  "Iteroi kaikki tietokannan tulokset (:id,otsikko,linkki,pvm) joka palautetaan html listana"
  [_ app]
  
  (let [videot (:videot app)]

    [:div
     [:ul {:class "info-lista"}

      (doall (map (fn [{:as m}]
                    ^{:key (m :id)}
                    [:li
                     [:div {:class "info-videot"
                            :on-click (fn []
                                        ; Videon "klikattu" arvoa ei ole olemassa => create 
                                        (when (not (klikattu? (m :id)))
                                          (lisaa-asetus {:id (m :id) :open false}))

                                        ; Vaihda arvo
                                        (swap-clicked
                                         {:id (m :id)
                                          :open (not (:open (first (hae-arvo @clicked (m :id)))))}))}

                      (let [paivamaara (st/join " " (rest (take 4 (st/split (.toDateString (js/Date. (m :pvm))) #" "))))
                            ikoni (ikonit/harja-icon-misc-clock)
                            suomennettu (-> paivamaara
                                            (st/replace #"Jan" "Tammikuu")
                                            (st/replace #"Feb" "Helmikuu")
                                            (st/replace #"Mar" "Maaliskuu")
                                            (st/replace #"Apr" "Huhtikuu")
                                            (st/replace #"May" "Toukokuu")
                                            (st/replace #"Jun" "Kesäkuu")
                                            (st/replace #"Jul" "Heinäkuu")
                                            (st/replace #"Aug" "Elokuu")
                                            (st/replace #"Sep" "Syyskuu")
                                            (st/replace #"Oct" "Lokakuu")
                                            (st/replace #"Nov" "Marraskuu")
                                            (st/replace #"Dec" "Joulukuu"))]

                        [:span ikoni " " suomennettu])

                      [:br]
                      [:span
                       (if (:open (first (hae-arvo @clicked (m :id))))

                         ; Video on auki => näytä video
                         [:div {:class "info-video"}
                          [:div {:class "embed-title"} (m :otsikko)]
                          [:iframe {:class "info-iframe"
                                    :src (str "https://www.youtube.com/embed/" (formatoi-embed-linkki (m :linkki)))}]]

                         ; Video on kiinni => näytä pelkkä otsikko
                         [:div {:class "info-click"} (m :otsikko)])]]])

                  videot))]]))

(defn videot*
  "Rajapintakutsun callback"
  [e!]

  (komp/luo
   (komp/sisaan
    #(do
       (e! (tiedot/->HaeKoulutusvideot))))

   (fn [e! app]
     (when (:videot app)

       [:span
        [:div.section
         [:h3 {:class "info-title"} "Harja Info"
          [:div {:class "otsikko-viiva"}]
          [:ul {:class "info-heading f"} "Harja uutiset"

           [:ul [:a {:class "info-heading-small" :href "https://finnishtransportagency.github.io/harja/" :target "_blank"}
                 "https://finnishtransportagency.github.io/harja/ " (ikonit/link)]]]

          [:ul {:class "info-heading f"} "Harja Tietosuojaseloste"
           [:ul [:a {:class "info-heading-small" :href "https://vayla.fi/tietoa-meista/yhteystiedot/tietosuoja" :target "_blank"}
                 "https://vayla.fi/tietoa-meista/yhteystiedot/tietosuoja " (ikonit/link)]]]

          [:ul {:class "info-heading"} "Harja koulutusvideot"

           [:p {:class "info-heading-small main"} "Koulutusvideoita HARJA:n käytön tueksi."]

           [:div {:class "videot"}
            [:div (listaa-videot e! app)]]]]]]))))

(defn info 
  "Hakee koulutusvideot kun käyttäjä tulee näkymään"
  []
  [tuck tiedot/data videot*]) 