(ns harja.views.vesivaylat.hallinta.liikennetapahtumien-ketjutus
  (:require [harja.ui.komponentti :as komp]
            [tuck.core :refer [tuck]]
            [harja.tiedot.vesivaylat.hallinta.liikennetapahtumien-ketjutus :as tiedot]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni]]
            [harja.ui.grid :as grid]
            [harja.pvm :as pvm]
            [harja.domain.sopimus :as sopimus]
            [harja.domain.urakka :as urakka]
            [harja.ui.kentat :as kentat]
            [harja.ui.lomake :as lomake]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.debug :as debug]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.valinnat :as valinnat]
            [reagent.core :refer [atom] :as r]))

(defn sopimusgrid [e! app]
  (komp/luo
    (komp/sisaan #(e! (tiedot/->HaeSopimukset)))
    (fn [e! {:keys [haetut-sopimukset sopimuksien-haku-kaynnissa?] :as app}]
      [:div
       
       [grid/grid
        {:otsikko (if (and (some? haetut-sopimukset) sopimuksien-haku-kaynnissa?)
                    [ajax-loader-pieni "Päivitetään listaa"]
                    "Liikennetapahtumien ketjutus")
         :tunniste ::sopimus/id
         :tyhja (if (nil? haetut-sopimukset)
                  [ajax-loader "Haetaan sopimuksia"]
                  "Sopimuksia ei löytynyt")}
        [{:otsikko "Sopimus" :nimi ::sopimus/nimi :tyyppi :string}
         {:otsikko "Alku" :nimi ::sopimus/alkupvm :tyyppi :pvm :fmt pvm/pvm-opt}
         {:otsikko "Loppu" :nimi ::sopimus/loppupvm :tyyppi :pvm :fmt pvm/pvm-opt}
         {:otsikko "Urakka" :nimi :urakan-nimi :hae #(get-in % [::sopimus/urakka ::urakka/nimi])}

         {:otsikko (str "Ketjutus käytössä?")
          :tyyppi :komponentti
          :tasaa :keskita
          :nimi :valinta
          :solu-klikattu (fn [rivi]
                           (let [
                                 ;kuuluu-urakkaan? (tiedot/kohde-kuuluu-urakkaan? app rivi valittu-urakka)
                                 ]
                             (println "Painettu ketjutus " rivi)
                             
                             ))
          
          :komponentti (fn [rivi]
                         [kentat/tee-kentta
                          {:tyyppi :checkbox}
                          (r/wrap
                            true
                            (fn [uusi]
                              (println "Uusi: " uusi " Rivi: " rivi)))
                          
                          ])}
         
         ]
        haetut-sopimukset]])))

(defn liikennetapahtumien-ketjutus* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(e! (tiedot/->Nakymassa? true))
      #(e! (tiedot/->Nakymassa? false)))

    (fn [e! {valittu-sopimus :valittu-sopimus :as app}]
      [:div
       [sopimusgrid e! app]])))

(defn liikennetapahtumien-ketjutus []
  [tuck tiedot/tila liikennetapahtumien-ketjutus*])
