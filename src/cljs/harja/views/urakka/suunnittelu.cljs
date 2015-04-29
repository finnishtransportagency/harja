(ns harja.views.urakka.suunnittelu
  "Päätason sivu Hallinta, josta kaikkeen ylläpitötyöhön pääsee käsiksi."
  (:require [reagent.core :refer [atom] :as r]
            [bootstrap :as bs]
            
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.tiedot.urakka.kokonaishintaiset-tyot :as kok-hint-tyot]
            [harja.tiedot.urakka.yksikkohintaiset-tyot :as yks-hint-tyot]

            [harja.views.urakka.yksikkohintaiset-tyot :as yksikkohintaiset-tyot]
            [harja.views.urakka.kokonaishintaiset-tyot :as kokonaishintaiset-tyot]
            [harja.views.urakka.materiaalit :as mat]
             
            [harja.pvm :as pvm]
            [harja.loki :refer [log]]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? livi-pudotusvalikko]])
  
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]))



(def valittu-valilehti "Valittu välilehti" (atom 0))


(defn suunnittelu [ur]
  ;; suunnittelu-välilehtien yhteiset valinnat hoitokaudelle ja sopimusnumerolle
  (let [urakan-hoitokaudet (atom (s/hoitokaudet ur))
        hae-urakan-tyot (fn [ur]
                               (go (reset! s/urakan-kok-hint-tyot (<! (kok-hint-tyot/hae-urakan-kokonaishintaiset-tyot ur))))
                               (go (reset! s/urakan-yks-hint-tyot (yksikkohintaiset-tyot/prosessoi-tyorivit ur (<! (yks-hint-tyot/hae-urakan-yksikkohintaiset-tyot (:id ur)))))))]
    (s/valitse-sopimusnumero! (first (:sopimukset ur)))
    (s/valitse-hoitokausi! (first @urakan-hoitokaudet))
    (hae-urakan-tyot ur)
    
    (r/create-class
      {:component-will-receive-props
       (fn [this [_ ur]]
         (reset! urakan-hoitokaudet (s/hoitokaudet ur))
         (s/valitse-sopimusnumero! (first (:sopimukset ur)))
         (s/valitse-hoitokausi! (first @urakan-hoitokaudet)))
       
       
       :reagent-render 
       (fn [ur]

         [:span.suunnittelu
          [:div.label-ja-alasveto
           [:span.alasvedon-otsikko "Sopimusnumero"]
           [livi-pudotusvalikko {:valinta @s/valittu-sopimusnumero
                                 :format-fn second
                                 :valitse-fn s/valitse-sopimusnumero!
                                 :class "suunnittelu-alasveto"
                                 }
            (:sopimukset ur)
            ]]
          [:div.label-ja-alasveto
           [:span.alasvedon-otsikko (if (= :hoito (:tyyppi ur)) "Hoitokausi" "Sopimuskausi")]
           [livi-pudotusvalikko {:valinta @s/valittu-hoitokausi
                                 ;;\u2014 on väliviivan unikoodi
                                 :format-fn #(if % (str (pvm/pvm (first %))
                                                        " \u2014 " (pvm/pvm (second %))) "Valitse")
                                 :valitse-fn s/valitse-hoitokausi!
                                 :class "suunnittelu-alasveto"
                                 }
            @urakan-hoitokaudet]]
          
          ;; suunnittelun välilehdet
          [bs/tabs {:style :pills :active valittu-valilehti}
           
           "Kokonaishintaiset työt"
           ^{:key "kokonaishintaiset-tyot"}
           [kokonaishintaiset-tyot/kokonaishintaiset-tyot ur]
           
           "Yksikköhintaiset työt"
           ^{:key "yksikkohintaiset-tyot"}
           [yksikkohintaiset-tyot/yksikkohintaiset-tyot-view ur]
           
           "Materiaalit"
           ^{:key "materiaalit"}
           [mat/materiaalit ur]
           ]])
       
       })))


