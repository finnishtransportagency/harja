(ns harja.views.hallinta.integraatioloki
  "Integraatiolokin näkymä"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.hallinta.integraatioloki :as tiedot]
            [harja.pvm :as pvm]
            [harja.ui.yleiset :refer [ajax-loader livi-pudotusvalikko]]
            [harja.ui.grid :refer [grid]]
            [harja.views.urakka.valinnat :as urakka-valinnat])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.ui.yleiset :refer [deftk]]))

(defn tapahtuman-tiedot []
  [:div "Tapahtuman tiedot"])

(defn tapahtumien-paanakyma []
  [:span

   [:div.container

    [:div.label-ja-alasveto
     [:span.alasvedon-otsikko "Järjestelmä"]
     [livi-pudotusvalikko {:valinta    @tiedot/valittu-jarjestelma
                           ;;\u2014 on väliviivan unikoodi
                           :format-fn  :jarjestelma
                           :valitse-fn #(reset! tiedot/valittu-jarjestelma %)
                           :class      "suunnittelu-alasveto"}
      @tiedot/jarjestelmien-integraatiot]]

    [:div.label-ja-alasveto
     [:span.alasvedon-otsikko "Integraatio"]
     [livi-pudotusvalikko {:valinta    @tiedot/valittu-integraatio
                           ;;\u2014 on väliviivan unikoodi
                           :format-fn  :nimi
                           :valitse-fn #(reset! tiedot/valittu-integraatio %)
                           :class      "suunnittelu-alasveto"}
      (:integraatiot @tiedot/valittu-jarjestelma)]]

    [urakka-valinnat/hoitokauden-aikavali @tiedot/valittu-aikavali]

    [grid
     {:tyhja         (if @tiedot/haetut-tapahtumat "Ei löytyneitä tapahtumia" [ajax-loader "Haetaan tapahtumia"])
      :rivi-klikattu #(reset! tiedot/valittu-tapahtuma %)}

     [{:otsikko "Järjestelmä" :nimi :jarjestelma :leveys "20%"}
      {:otsikko "Integraatio" :nimi :integraatio :leveys "20%"}
      {:otsikko "Alkanut" :nimi :alkanut :leveys "20%"}
      {:otsikko "Päättynyt" :nimi :paattynyt :leveys "20%"}
      {:otsikko "Onnistunut" :nimi :onnistunut :leveys "20%"}
      {:otsikko "Ulkoinen id" :nimi :ulkoinenid :leveys "20%"}
      {:otsikko "Lisätietoja" :nimi :lisatietoja :leveys "20%"}]

     @tiedot/haetut-tapahtumat]]])

(defn integraatioloki []
  (komp/luo
    (komp/lippu tiedot/nakymassa?)

    (fn []
      (if @tiedot/valittu-tapahtuma
        [tapahtuman-tiedot]
        [tapahtumien-paanakyma]))))


