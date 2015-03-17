(ns harja.views.urakka.suunnittelu
  "Päätason sivu Hallinta, josta kaikkeen ylläpitötyöhön pääsee käsiksi."
  (:require [reagent.core :refer [atom] :as r]
            [bootstrap :as bs]
            
            [harja.views.urakka.yksikkohintaiset-tyot :as yksikkohintaiset-tyot]
            [harja.pvm :as pvm]
            [harja.loki :refer [log]]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? alasveto-ei-loydoksia alasvetovalinta radiovalinta]])
  
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.ui.yleiset :refer [deftk]]))


(def valittu-valilehti "Valittu välilehti" (atom 0))

(def valittu-sopimusnumero "Sopimusnumero" (atom nil))
(defn valitse-sopimusnumero! [sn]
  (reset! valittu-sopimusnumero sn))


(def +hoitokauden-alkukk-indeksi+ "9")
(def +hoitokauden-alkupv-indeksi+ "1")
(def +hoitokauden-loppukk-indeksi+ "8")
(def +hoitokauden-loppupv-indeksi+ "30")

(def valittu-hoitokausi "Hoitokausi" (atom nil))

(defn valitse-hoitokausi! [hk]
  (reset! valittu-hoitokausi hk))

(defn hoitokaudet [ur]
  (let [ensimmainen-vuosi (.getYear (:alkupvm ur))
        viimeinen-vuosi (.getYear (:loppupvm ur))]
    (mapv (fn [vuosi]
            {:alkupvm (pvm/luo-pvm vuosi +hoitokauden-alkukk-indeksi+ +hoitokauden-alkupv-indeksi+)
             :loppupvm (pvm/luo-pvm (inc vuosi) +hoitokauden-loppukk-indeksi+ +hoitokauden-loppupv-indeksi+)})
          (range ensimmainen-vuosi (inc viimeinen-vuosi)))))


(defn suunnittelu [ur]
  ;; suunnittelu-välilehtien yhteiset valinnat hoitokaudelle ja sopimusnumerolle
  (let [urakan-hoitokaudet (atom (hoitokaudet ur))]
    (valitse-sopimusnumero! (first (:sopimukset ur)))
    (valitse-hoitokausi! (first @urakan-hoitokaudet))
    
    (r/create-class
      {:component-will-receive-props
       (fn [this [_ ur]]
         (reset! urakan-hoitokaudet (hoitokaudet ur))
         (valitse-sopimusnumero! (first (:sopimukset ur)))
         (valitse-hoitokausi! (first @urakan-hoitokaudet)))
       
       
       :reagent-render 
       (fn [ur]
         [:span
          [:div.alasvetovalikot
           [:div.label-ja-alasveto 
            [:span.alasvedon-otsikko "Sopimusnumero"]
            [alasvetovalinta {:valinta @valittu-sopimusnumero
                              :format-fn second
                              :valitse-fn valitse-sopimusnumero!
                              :class "alasveto"
                              }
             (:sopimukset ur)
             ]]
           [:div.label-ja-alasveto
            [:span.alasvedon-otsikko "Hoitokausi"]
            [alasvetovalinta {:valinta @valittu-hoitokausi
                              ;;\u2014 on väliviivan unikoodi
                              :format-fn #(if % (str (pvm/pvm (:alkupvm %)) 
                                                     " \u2014 " (pvm/pvm (:loppupvm %))) "Valitse")
                              :valitse-fn valitse-hoitokausi!
                              :class "alasveto"
                              }
             @urakan-hoitokaudet
             ]]]
          
          ;; suunnittelun välilehdet
          [bs/tabs {:style :pills :active valittu-valilehti}
           
           "Yksikköhintaiset työt"
           ^{:key "yksikkohintaiset-tyot"}
           [yksikkohintaiset-tyot/yksikkohintaiset-tyot ur]
           
           "Kokonaishintaiset työt"
           [:div "Kokonaishintaiset työt"]
           ;;^{:key "kokonaishintaiset-tyot"}
           ;;[yht/yksikkohintaiset-tyot]
           
           "Materiaalit"
           [:div "Materiaalit"]
           ;;^{:key "materiaalit"}
           ]])
       
       })))


