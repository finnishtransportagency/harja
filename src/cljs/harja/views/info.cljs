(ns harja.views.info
  "FIXME Infonäkymä mihin siirretty koulutusvideot julkiselta sisäiseen palvelimeen.
   Videot haetaan tietokannasta rajapintaa käyttäen"
  (:require [tuck.core :refer [tuck]]
            [reagent.core :refer [atom] :as reagent]
            [harja.ui.komponentti :as komp]
            [harja.ui.ikonit :as ikonit]
            [clojure.string :as st]
            [harja.tiedot.info :as tiedot]))

(def clicked (atom []))

(defn lisaa-asetus
  "Tekee uuden lista arvon"
  [asetus]
  (swap! clicked conj asetus))

(defn muokkaa-tilaa
  "Muokkaa annetun listan arvoa"
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
  "Tarkistaa onko @clicked indeksiä olemassa"
  [indeksi]
  (if (= (hae-arvo @clicked indeksi) ())
    false
    true))

(defn formatoi-embed-linkki 
  "Palauttaa annetun linkin videokoodin"
  [linkki]
  (second (st/split (get (st/split linkki #"/") 3) #"v=")))

(defn listaa-videot 
  "Iteroi kaikki tietokannan tulokset (:id,otsikko,linkki,pvm) joka palautetaan html listana" 
  [_ app]
  (let 
   [videot (:videot app)] 
    
    ; {:id 1, 
    ; :otsikko test_title, 
    ; :linkki https://www.youtube.com/watch?v=cTTxPCdU9zs&feature=emb_title, 
    ; :pvm #inst "2022-11-30T22:00:00.000-00:00"} 
    
    [:div
     [:ul {:class "info-lista"}
      (doall (map (fn
             [{id :_id :as m}]
             [(keyword id) m]
             [:li (m :otsikko)]
             ^{:key (m :id)}  
             [:li
              [:div {:class "info-heading" :on-click (fn []
                                                       (if (not (klikattu? (m :id))) (lisaa-asetus {:id (m :id) :open false}) (println "löytyi asetus id: " (m :id)))
                                                       (println "target: " (m :id) " id? " (hae-arvo @clicked (m :id)) " open? " (hae-arvo @clicked (m :open)))
                                                       (println "arr: " @clicked )
                                                       (muokkaa-tilaa {:id (m :id) :open (not (:open (first (hae-arvo @clicked (m :id)))))}))}
               
               (ikonit/harja-icon-misc-clock) " " (st/join " " 
                                                           (take 4 (st/split (m :pvm) #" ")))
               [:br] 
               [:span 
                   (if (:open (first (hae-arvo @clicked (m :id))))
                     [:div {:class "info-video"}
                      [:div {:class "embed-title"} (m :otsikko)]
                      [:iframe {:class "info-iframe" 
                                :src (str "https://www.youtube.com/embed/" (formatoi-embed-linkki (m :linkki)))
                                }]]
                     [:div {:class "info-click"} (m :otsikko)])]
               
               ]])
           
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
         [:h3 {:class "info-title main"} "Harja infosivu" 
          [:li
           [:a {:class "info-heading" :href "https://finnishtransportagency.github.io/harja/" :target "_blank"}
            "https://finnishtransportagency.github.io/harja/"]]]
         [:h3 {:class "info-title"} "Koulutusvideot"]
         [:p {:class "info-heading main"} "Koulutusvideoita HARJA:n käyttöönoton tueksi."]
         
         ]

        [:div
         [:div (listaa-videot e! app)]]]))))

(defn info 
  "Hakee koulutusvideot kun käyttäjä tulee näkymään"
  []
  [tuck tiedot/data videot*])