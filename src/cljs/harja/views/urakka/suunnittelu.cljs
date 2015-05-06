(ns harja.views.urakka.suunnittelu
  "Päätason sivu Hallinta, josta kaikkeen ylläpitötyöhön pääsee käsiksi."
  (:require [reagent.core :refer [atom] :as r]
            [bootstrap :as bs]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.tiedot.urakka.kokonaishintaiset-tyot :as kok-hint-tyot]
            [harja.tiedot.urakka.yksikkohintaiset-tyot :as yks-hint-tyot]
            [harja.tiedot.navigaatio :as nav]

            [harja.views.urakka.valinnat :as valinnat]
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
                       (get (u/ryhmittele-hoitokausittain (into []
                                                                (filter (fn [t]
                                                                          (= (:sopimus t) (first @u/valittu-sopimusnumero))))
                                                                @s/urakan-yks-hint-tyot)
                                                          (u/hoitokaudet urakka)) @u/valittu-hoitokausi))))

(defn suunnittelu [ur]
  ;; suunnittelu-välilehtien yhteiset valinnat hoitokaudelle ja sopimusnumerolle
  (let [hae-urakan-tyot (fn [ur]
                          (go (reset! s/urakan-kok-hint-tyot (<! (kok-hint-tyot/hae-urakan-kokonaishintaiset-tyot ur))))
                          (go (reset! s/urakan-yks-hint-tyot (yksikkohintaiset-tyot/prosessoi-tyorivit ur (<! (yks-hint-tyot/hae-urakan-yksikkohintaiset-tyot (:id ur)))))))
        valitun-hoitokauden-yks-hint-kustannukset (valitun-hoitokauden-yks-hint-kustannukset ur)]
    (hae-urakan-tyot ur)
    
    (r/create-class
      {:component-will-receive-props
       (fn [this [_ ur]]
         (hae-urakan-tyot ur))

       :reagent-render
       (fn [ur]

         [:span.suunnittelu
          (if (= 0 @nav/urakka-suunnittelu-valilehti)
            [valinnat/urakan-sopimus-ja-hoitokausi-ja-toimenpide ur]
            [valinnat/urakan-sopimus-ja-hoitokausi ur])
          ;; suunnittelun välilehdet
          [bs/tabs {:style :tabs :active nav/urakka-suunnittelu-valilehti}

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


