(ns harja.views.urakka.suunnittelu
  "Päätason sivu Hallinta, josta kaikkeen ylläpitötyöhön pääsee käsiksi."
  (:require [reagent.core :refer [atom] :as r]
            [bootstrap :as bs]

            [harja.views.urakka.valinnat :as valinnat]
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.tiedot.urakka.kokonaishintaiset-tyot :as kok-hint-tyot]
            [harja.tiedot.urakka.yksikkohintaiset-tyot :as yks-hint-tyot]
            [harja.tiedot.urakka.urakan-toimenpiteet :as urakan-toimenpiteet]

            [harja.views.urakka.yksikkohintaiset-tyot :as yksikkohintaiset-tyot]
            [harja.views.urakka.kokonaishintaiset-tyot :as kokonaishintaiset-tyot]
            [harja.views.urakka.materiaalit :as mat]
            
            [harja.pvm :as pvm]
            [harja.loki :refer [log]]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? livi-pudotusvalikko]])
  
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]))

; TODO Siirrä tietoihin
(defn valitun-hoitokauden-yks-hint-kustannukset [urakka]
(reaction (transduce (map #(* (:maara %) (:yksikkohinta %)))
                     + 0
                     (get (s/ryhmittele-hoitokausittain (into []
                                                              (filter (fn [t]
                                                                          (= (:sopimus t) (first @s/valittu-sopimusnumero))))
                                                              @s/urakan-yks-hint-tyot)
                                                        (s/hoitokaudet urakka)) @s/valittu-hoitokausi))))

(def valittu-valilehti "Valittu välilehti" (atom 0))

(defn suunnittelu [ur]
  ;; suunnittelu-välilehtien yhteiset valinnat hoitokaudelle ja sopimusnumerolle
  (let [urakan-hoitokaudet (atom (s/hoitokaudet ur))
        hae-urakan-tyot (fn [ur]

                          (go (reset! s/urakan-kok-hint-tyot (<! (kok-hint-tyot/hae-urakan-kokonaishintaiset-tyot ur))))
                          (go (reset! s/urakan-yks-hint-tyot (yksikkohintaiset-tyot/prosessoi-tyorivit ur (<! (yks-hint-tyot/hae-urakan-yksikkohintaiset-tyot (:id ur))))))
                          (go (reset! s/urakan-toimenpideinstanssit (<! (urakan-toimenpiteet/hae-urakan-toimenpiteet (:id ur))))))
        valitun-hoitokauden-yks-hint-kustannukset (valitun-hoitokauden-yks-hint-kustannukset ur)]
    (s/valitse-sopimusnumero! (first (:sopimukset ur)))
    (s/valitse-hoitokausi! (first @urakan-hoitokaudet))
    (hae-urakan-tyot ur)
    
    (r/create-class
      {:component-will-receive-props
       (fn [this [_ ur]]
         (reset! urakan-hoitokaudet (s/hoitokaudet ur))
         (s/valitse-sopimusnumero! (first (:sopimukset ur)))
         (s/valitse-hoitokausi! (first @urakan-hoitokaudet))
         (hae-urakan-tyot ur))


       :reagent-render
       (fn [ur]

         [:span.suunnittelu
          (if (= 0 @valittu-valilehti)
            [valinnat/urakan-sopimus-ja-hoitokausi-ja-toimenpide ur]
            [valinnat/urakan-sopimus-ja-hoitokausi ur])
          ;; suunnittelun välilehdet
          [bs/tabs {:style :pills :active valittu-valilehti}

           "Kokonaishintaiset työt"
           ^{:key "kokonaishintaiset-tyot"}
           [kokonaishintaiset-tyot/kokonaishintaiset-tyot ur valitun-hoitokauden-yks-hint-kustannukset]

           "Yksikköhintaiset työt"
           ^{:key "yksikkohintaiset-tyot"}
           [yksikkohintaiset-tyot/yksikkohintaiset-tyot-view ur valitun-hoitokauden-yks-hint-kustannukset]

           "Materiaalit"
           ^{:key "materiaalit"}
           [mat/materiaalit ur]
           ]])

       })))


