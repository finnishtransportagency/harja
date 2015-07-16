(ns harja.views.hallinta.integraatioloki
  "Integraatiolokin näkymä"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.hallinta.integraatioloki :as tiedot]
            [harja.pvm :as pvm]
            [harja.loki :refer [log]]
            [harja.ui.yleiset :refer [ajax-loader livi-pudotusvalikko]]
            [harja.ui.grid :refer [grid]]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.modal :refer [modal] :as modal])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.ui.yleiset :refer [deftk]]))

(defn kartta-merkkijonoksi [kartta]
  (when kartta
    (clojure.string/join
      (map #(str (name (key %)) ": " (val %)) kartta))))

(defn kartta-listaksi [kartta]
  (when kartta
    [:ul
     (for [elementti kartta]
       [:li
        [:b (name (key elementti))]
        (str ": " (val elementti))])]))

(defn leikkaa-merkkijono [merkkijono pituus]
  (when merkkijono (subs merkkijono 0 (min (count merkkijono) pituus))))

(defn nayta-sisalto-modaalissa-dialogissa [otsikko sisalto]
  (modal/nayta! {:otsikko otsikko
                 :leveys "600px"}
                [:div.kayttajan-tiedot sisalto]))

(defn tapahtuman-tiedot []
  [:span
   [:div.container
    [grid
     {:otsikko "Viestit"}

     [{:otsikko     "Suunta" :nimi :suunta :leveys "10%" :tyyppi :komponentti
       :komponentti #(if (= "sisään" (:suunta %))
                      [:span.integraatioloki-onnistunut (ikonit/circle-arrow-right) " Sisään"]
                      [:span.integraatioloki-varoitus (ikonit/circle-arrow-left) " Ulos"])}
      {:otsikko     "Parametrit" :nimi :parametrit :leveys "20%" :tyyppi :komponentti
       :komponentti #(leikkaa-merkkijono (kartta-merkkijonoksi (:parametrit %)) 50)}
      {:otsikko     "Otsikko" :nimi :otsikko :leveys "40%" :tyyppi :komponentti
       :komponentti #(let [sisalto (kartta-merkkijonoksi (:otsikko %))]
                      (if (> (count sisalto) 30)
                        [:div (str (leikkaa-merkkijono sisalto 30) "...")
                         [:span.pull-right
                          [:button.nappi-toissijainen.grid-lisaa
                           {:on-click
                            (fn [e]
                              (nayta-sisalto-modaalissa-dialogissa "Otsikko" (kartta-listaksi (:otsikko %))))}
                           (ikonit/eye-open)]]]
                        sisalto))}
      {:otsikko "Siirtotyyppi" :nimi :siirtotyyppi :leveys "20%"}
      {:otsikko "Sisältötyyppi" :nimi :sisaltotyyppi :leveys "20%"}
      {:otsikko     "Sisältö" :nimi :sisalto :leveys "40%" :tyyppi :komponentti
       :komponentti #(let [sisalto (:sisalto %)]
                      (if (> (count sisalto) 30)
                        [:div (str (leikkaa-merkkijono sisalto 30) "...")
                         [:span.pull-right
                          [:button.nappi-toissijainen.grid-lisaa
                           {:on-click
                            (fn [e]
                              (nayta-sisalto-modaalissa-dialogissa "Viestin sisältö" [:pre sisalto]))}
                           (ikonit/eye-open)]]]
                        sisalto))}]

     (:viestit @tiedot/valittu-tapahtuma)]]])

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
                           :format-fn  #(str %)
                           :valitse-fn #(reset! tiedot/valittu-integraatio %)
                           :class      "suunnittelu-alasveto"}
      (:integraatiot @tiedot/valittu-jarjestelma)]]

    [valinnat/hoitokauden-aikavali tiedot/valittu-aikavali]

    [grid
     {:otsikko       "Tapahtumat"
      :tyhja         (if @tiedot/haetut-tapahtumat "Tapahtumia ei löytynyt" [ajax-loader "Haetaan tapahtumia"])
      :rivi-klikattu #(reset! tiedot/valittu-tapahtuma %)}

     [{:otsikko     "Tila" :nimi :onnistunut :leveys "10%" :tyyppi :komponentti
       :komponentti #(if (nil? (:paattynyt %))
                       [:span.integraatioloki-varoitus (ikonit/time) " Kesken"]
                       (if (:onnistunut %) [:span.integraatioloki-onnistunut (ikonit/thumbs-up) " Onnistunut"]
                                           [:span.integraatioloki-virhe (ikonit/thumbs-down) " Epäonnistunut"]))}
      {:otsikko "Alkanut" :nimi :alkanut :leveys "15%"
       :hae     #(if (:alkanut %) (pvm/pvm-aika-sek (:alkanut %)) "-")}
      {:otsikko "Päättynyt" :nimi :paattynyt :leveys "15%"
       :hae     #(if (:paattynyt %) (pvm/pvm-aika-sek (:paattynyt %)) "-")}
      {:otsikko "Ulkoinen id" :nimi :ulkoinenid :leveys "20%"}
      {:otsikko "Lisätietoja" :nimi :lisatietoja :leveys "40%"}]

     @tiedot/haetut-tapahtumat]]])

(defn integraatioloki []
  (komp/luo
    (komp/lippu tiedot/nakymassa?)

    (fn []
      [:div
       [tapahtumien-paanakyma]
       (when @tiedot/valittu-tapahtuma
         [tapahtuman-tiedot])])))


