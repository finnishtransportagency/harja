(ns harja.views.vesivaylat.hallinta.liikennetapahtumien-ketjutus
  (:require [harja.ui.komponentti :as komp]
            [tuck.core :refer [tuck]]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.vesivaylat.hallinta.liikennetapahtumien-ketjutus :as tiedot]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni]]
            [harja.ui.grid :as grid]
            [harja.pvm :as pvm]
            [harja.domain.sopimus :as sopimus]
            [harja.domain.urakka :as urakka]
            [harja.ui.kentat :as kentat]
            [reagent.core :refer [atom] :as r]))

(defn sopimusgrid [e! haetut-sopimukset sopimuksien-haku-kaynnissa?]
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

    {:otsikko "Ketjutus käytössä?"
     :tyyppi :komponentti
     :tasaa :keskita
     :nimi :valinta
     :komponentti (fn [rivi]
                    [kentat/tee-kentta
                     {:tyyppi :checkbox}
                     (r/wrap
                       (boolean (::sopimus/ketjutus rivi))
                       (fn [uusi]
                         (tuck-apurit/e-kanavalla! e! tiedot/->TallennaKetjutus rivi uusi)))])}]
   haetut-sopimukset])

(defn liikennetapahtumien-ketjutus* [e! _]
  (komp/luo
    (komp/sisaan
      #(do
         ;; nil = Hae kaikki
         (e! (tiedot/->HaeSopimukset nil nil))))

    (fn [e! {:keys [haetut-sopimukset sopimuksien-haku-kaynnissa?]}]
      [:div
       [sopimusgrid e! haetut-sopimukset sopimuksien-haku-kaynnissa?]])))

(defn liikennetapahtumien-ketjutus []
  [tuck tiedot/tila liikennetapahtumien-ketjutus*])
