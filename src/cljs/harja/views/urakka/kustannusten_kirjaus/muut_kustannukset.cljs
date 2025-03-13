(ns harja.views.urakka.kustannusten-kirjaus.muut-kustannukset
  "Tiemerkintöjen muut kustannukset välilehti näkymä"
  (:require [harja.views.urakka.kustannusten-kirjaus.muut-kustannukset-tiedot :as tiedot]
            [tuck.core :refer [tuck]]
            [harja.asiakas.kommunikaatio :as komm]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.fmt :as fmt]
            [harja.ui.valinnat :as valinnat]
            [harja.tiedot.navigaatio :as nav]
            [harja.transit :as transit]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :refer [ajax-loader-pieni] :as yleiset]
            [harja.tiedot.istunto :as istunto]
            [harja.domain.tierekisteri :as tr-domain]))


(defn muut-kustannukset-listaus [e! {:keys [rivit valinnat muokataan valittu-rivi
                                            haku-kaynnissa? kustannukset] :as app}]
  (let [alkuaika (:alkuaika valittu-rivi)
        voi-kirjoittaa? true ;; TODO 
        voi-tallentaa? true ;; TODO 
        ] 

    [:div.tiemerkinnat-muut-kustannukset
     ;; Muokkauspaneeli
     (when muokataan
       ;; TODO 
       )

     [:div.tiemerkinnat-muut-kustannukset-listaus
      ;; Suodattimet
      ;; TODO 
      [:div.taulukko-header.header-yhteiset

       ;; Oikealla puolella olevat raporttinapit 
       [:div.flex-oikealla
        [:div.lataus-nappi
         [:form {:style {:margin-left "auto"}
                 :target "_blank" :method "POST"
                 ; :action TODO
                 }
          [:input {:type "hidden" :name "parametrit"
                   :value (transit/clj->transit {:tr (:tr valinnat)
                                                 :aikavali (:aikavali valinnat)
                                                 :urakka-id @nav/valittu-urakka-id})}]
          [:button {:type "submit"
                    :class #{"nappi-toissijainen"}}
           [ikonit/ikoni-ja-teksti (ikonit/livicon-upload) "Tallenna Excel"]]]]]]

      ;; Grid
      [grid/grid {:tyhja (if false ; TODO haku-kaynnissa?
                           [ajax-loader-pieni "Haku käynnissä..."]
                           "Valitulle aikavälille ei löytynyt mitään.")
                  ; :tunniste :id
                  :sivuta grid/vakiosivutus
                  :voi-kumota? false
                  :piilota-toiminnot? true
                  ; :mahdollista-rivin-valinta? true
                  ; TODO :rivi-klikattu #(e! (tiedot/-> modal... %))
                  }

       [{:otsikko "Päivämäärä"
         :tyyppi :pvm
         :nimi :alkuaika
         :luokka "text-nowrap"
         :leveys 0.2}

        {:otsikko "Sijainti"
         :tyyppi :Tyyppi
         :komponentti (fn [arvo _]
                        [:span "test"])
         :luokka "text-nowrap"
         :leveys 0.55}

        {:otsikko "Tyyppi"
         :tyyppi :Tyyppi
         :komponentti (fn [arvo _]
                        [:span "test"])
         :luokka "text-nowrap"
         :leveys 0.55}

        {:otsikko "Selite"
         :tyyppi :Tyyppi
         :komponentti (fn [arvo _]
                        [:span "test"])
         :luokka "text-nowrap"
         :leveys 0.55}]
       []
       ;; 
       ]]]))


(defn muut-kustannukset* [e! _app]
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/sisaan
      #(do
         (println "hae tiedot")
         (e! (tiedot/->HaeTiedot))))

    ;; Näytä listaus
    (fn [e! app]
      [:div
       [muut-kustannukset-listaus e! app]])))


(defn muut-kustannukset []
  [tuck tiedot/tila muut-kustannukset*])
