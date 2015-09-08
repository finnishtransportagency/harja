(ns harja.views.raportit
  "Harjan raporttien pääsivu."
  (:require [reagent.core :refer [atom] :as reagent]
            [harja.ui.komponentti :as komp]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.lomake :as lomake]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.pvm :as pvm]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.yleiset :refer [livi-pudotusvalikko]]
            [harja.fmt :as fmt]))

(defonce valittu-raportti (atom nil))
(defonce valittu-raporttityyppi (atom nil))


(def +raporttityypit+
    [[:laskutusyhteenveto "Laskutusyhteenveto" #{:urakka} [{:otsikko "Hoitokausi ":nimi :hoitokausi
                                                            :tyyppi :valinta
                                                            :valinnat :valitun-urakan-hoitokaudet}
                                                           {:otsikko  "Kuukausi" :nimi :kuukausi
                                                            :tyyppi   :valinta
                                                            :valinnat :valitun-aikavalin-kuukaudet}
                                                           {:otsikko "Kuinka kivaa 1-5?"
                                                            :nimi :kivaa
                                                            :tyyppi :numero}
                                                           ]]
   [:ymparistoraportti "Ympäristöraportti" #{:urakka :hallintayksikko :yt-alue :koko-maa}]
   [:turvallisuusraportti "Turvallisuusraportti"]
   [:tyomaakokous "Työmaakokousraportti"]])

(tarkkaile! "valittu-raporttityyppi" valittu-raporttityyppi)

(defn lomake-kentta [kentta lomakkeen-tiedot]
  (if (= :valinta (:tyyppi kentta))
    (case (:valinnat kentta)
      :valitun-urakan-hoitokaudet
      (assoc kentta :valinnat @u/valitun-urakan-hoitokaudet
                    :valinta-nayta fmt/pvm-vali-opt)

      :valitun-aikavalin-kuukaudet
      (assoc kentta :valinnat (if-let [hk (:hoitokausi lomakkeen-tiedot)]
                                (pvm/hoitokauden-kuukausivalit hk)
                                [])
                    :valinta-nayta (comp fmt/pvm-opt first)))

    kentta))

(defn raporttinakyma []
  [:div "Tänne tulee myöhemmin raporttinäkymä..."])

(defn raporttivalinnat
  []
  (let [lomakkeen-tiedot (atom nil)
        lomakkeen-virheet (atom nil)]
    (komp/luo
     (fn []
       [:div.raportit
        [:div.label-ja-alasveto
         [:span.alasvedon-otsikko "Valitse raportti"]
         [livi-pudotusvalikko {:valinta    @valittu-raporttityyppi
                               ;;\u2014 on väliviivan unikoodi
                               :format-fn  #(if % (second %) "Valitse")
                               :valitse-fn #(reset! valittu-raporttityyppi %)
                               :class      "valitse-raportti-alasveto"}
          +raporttityypit+]]

        (when-let [tyyppi (second @valittu-raporttityyppi)]
          [:h5 (str "Luo " tyyppi)]
          [lomake/lomake
           {:luokka   :horizontal
            :virheet  lomakkeen-virheet
            :muokkaa! (fn [uusi]
                        (reset! lomakkeen-tiedot uusi))}
           (let [tiedot @lomakkeen-tiedot
                 k (into []
                 (concat
                   [{:otsikko "Kohde" :nimi :kohteen-nimi :hae #(:nimi @nav/valittu-urakka) :muokattava? (constantly false)}]
                   (map #(lomake-kentta % tiedot) (nth @valittu-raporttityyppi 3))))]
             (log "kentät: " (pr-str k))
             k)

           @lomakkeen-tiedot])]))))

(defn raportit []
  (komp/luo
    (fn []
      [:div "Raporttinäkymiä ei ole vielä aloitettu."]
      #_(if @valittu-raportti
        [raporttinakyma]
        [raporttivalinnat]))))
