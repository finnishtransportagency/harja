(ns harja.views.urakka.suunnittelu.materiaalit
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.yleiset :refer [raksiboksi]]
            [harja.tiedot.urakka.suunnittelu.materiaalit :as t]
            [harja.loki :refer [log logt]]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.komponentti :as komp]
            [harja.ui.viesti :as viesti]

            [cljs.core.async :refer [<!]]
            [harja.views.urakka.valinnat :as valinnat]
            [harja.domain.oikeudet :as oikeudet])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [run! reaction]]))

(defn aseta-hoitokausi [rivi]
  (let [[alkupvm loppupvm] @u/valittu-hoitokausi]
    ;; lisätään kaikkiin riveihin valittu hoitokausi
    (assoc rivi :alkupvm alkupvm :loppupvm loppupvm)))

(defn yleiset-materiaalit-grid [{:keys [virheet voi-muokata?]}
                                ur valittu-hk valittu-sop
                                materiaalikoodit yleiset-materiaalit-muokattu]
  (let [g (grid/grid-ohjaus)]
    (komp/luo
     {:component-will-receive-props (fn [& _]
                                      (grid/nollaa-historia! g))}
     (fn [{:keys [virheet voi-muokata?]}
          ur valittu-hk valittu-sop
          materiaalikoodit yleiset-materiaalit-muokattu]
       [:div.row
        [grid/muokkaus-grid
         {:otsikko "Materiaalit"
          :luokat ["col-md-10"]
          :voi-muokata? voi-muokata?
          :voi-poistaa? (constantly false)
          :voi-lisata? false
          :ohjaus g
          :tyhja "Ei kirjattuja materiaaleja."
          :uusi-rivi aseta-hoitokausi
          :muutos (when virheet
                    #(reset! virheet (grid/hae-virheet %)))
          :jarjesta (comp :nimi :materiaali)
          }

         [{:otsikko "Materiaali" :nimi :materiaali :fmt :nimi :leveys "60%"
           :muokattava? (constantly false)
           :tyyppi :valinta :valinnat materiaalikoodit :valinta-nayta #(or (:nimi %) "- materiaali -")
           :validoi [[:ei-tyhja "Valitse materiaali"]]}
          {:otsikko "Maksimi\u00ADmäärä" :nimi :maara :leveys "30%"
           :muokattava? (constantly true)
           :tyyppi :positiivinen-numero}
          {:otsikko "Yks." :nimi :yksikko :hae (comp :yksikko :materiaali) :leveys "10%"
           :tyyppi :string :muokattava? (constantly false)}]

         yleiset-materiaalit-muokattu]]))))

(defn materiaalit [ur]
  (let [;; haetaan kaikki materiaalit urakalle
        urakan-materiaalit (atom nil)

        hae-urakan-materiaalit (fn [ur]
                                 (go (reset! urakan-materiaalit (<! (t/hae-urakan-materiaalit (:id ur))))))

        ;; ryhmitellään valitun sopimusnumeron materiaalit hoitokausittain
        sopimuksen-materiaalit-hoitokausittain
        (reaction (let [[sopimus-id _] @u/valittu-sopimusnumero]
                    (u/ryhmittele-hoitokausittain (filter #(= sopimus-id (:sopimus %))
                                                          @urakan-materiaalit)
                                                  (u/hoitokaudet ur))))


        ;; valitaan materiaaleista vain valitun hoitokauden
        materiaalit (reaction (let [hk @u/valittu-hoitokausi]
                                (get @sopimuksen-materiaalit-hoitokausittain hk)))

        ;; Haetaan kaikki materiaalikoodit ja valitaan tälle urakalle sopivat
        materiaalikoodit (reaction (filter #(= (:tyyppi ur) (:urakkatyyppi %)) @(t/hae-materiaalikoodit)))

        ;; Jaetaan materiaalikoodit yleisiin ja kohdistettaviin
        yleiset-materiaalikoodit (reaction (filter #(not (:kohdistettava %)) @materiaalikoodit))
        kohdistettavat-materiaalikoodit (reaction (filter :kohdistettava @materiaalikoodit))


        ;; luokitellaan yleiset materiaalit ja pohjavesialueiden materiaalit
        yleiset-materiaalit (reaction (let [materiaalit (into {}
                                                              (comp (filter #(not (contains? % :pohjavesialue)))
                                                                    (map (juxt :id identity)))
                                                              @materiaalit)
                                            kaytetyt-materiaali-idt (into #{} (map (comp :id :materiaali) (vals materiaalit)))]
                                        (loop [materiaalit materiaalit
                                               [mk & materiaalikoodit] @yleiset-materiaalikoodit]
                                          (if-not mk
                                            materiaalit
                                            (if (kaytetyt-materiaali-idt (:id mk))
                                              (recur materiaalit materiaalikoodit)
                                              (let [id (- (:id mk))
                                                    [alku loppu] @u/valittu-hoitokausi]
                                                (recur (assoc materiaalit id {:id id :materiaali mk :alkupvm alku :loppupvm loppu})
                                                       materiaalikoodit)))))))

        yleiset-materiaalit-virheet (atom nil)
        yleiset-materiaalit-muokattu (reaction @yleiset-materiaalit)

        ;; kopioidaanko myös tuleville kausille (oletuksena false, vaarallinen)
        tuleville? (atom false)

        ;; jos tulevaisuudessa on dataa, joka poikkeaa tämän hoitokauden materiaaleista, varoita ylikirjoituksesta
        varoita-ylikirjoituksesta?
        (reaction (let [kopioi? @tuleville?
                        varoita? (s/varoita-ylikirjoituksesta? @sopimuksen-materiaalit-hoitokausittain
                                                               @u/valittu-hoitokausi)]
                    (if-not kopioi?
                      false
                      varoita?)))]

    (hae-urakan-materiaalit ur)

    (komp/luo

     {:component-will-receive-props (fn [this & [_ ur]]
                                      (hae-urakan-materiaalit ur))}

     (fn [ur]
       (let [muokattu? (not= @yleiset-materiaalit @yleiset-materiaalit-muokattu)
             virheita? (not (empty? @yleiset-materiaalit-virheet))

             voi-tallentaa? (and (or muokattu? @tuleville?) (not virheita?))
             voi-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-suunnittelu-materiaalit (:id ur))]
         [:div.materiaalit
          [valinnat/urakan-sopimus-ja-hoitokausi ur]
          [yleiset-materiaalit-grid {:voi-muokata? voi-muokata?
                                     :virheet yleiset-materiaalit-virheet}
           ur @u/valittu-hoitokausi @u/valittu-sopimusnumero
           @yleiset-materiaalikoodit yleiset-materiaalit-muokattu]

          (when voi-muokata?
            [raksiboksi "Tallenna tulevillekin hoitokausille" @tuleville?
             #(swap! tuleville? not)
             [:div.raksiboksin-info (ikonit/livicon-warning-sign) "Tulevilla hoitokausilla eri tietoa, jonka tallennus ylikirjoittaa."]
             (and @tuleville? @varoita-ylikirjoituksesta?)
             ])

          (when voi-muokata?
            [:div.toiminnot
             [:button.nappi-ensisijainen
              {:disabled (not voi-tallentaa?)
               :on-click #(do (.preventDefault %)
                              (go
                                (let [rivit (vals @yleiset-materiaalit-muokattu)
                                      rivit (if @tuleville?
                                              (u/rivit-tulevillekin-kausille ur rivit @u/valittu-hoitokausi)
                                              rivit)
                                      _ (logt rivit)
                                      uudet-materiaalit (<! (t/tallenna (:id ur)
                                                                        (first @u/valittu-sopimusnumero)
                                                                        @u/valittu-hoitokausi
                                                                        @u/valitun-urakan-hoitokaudet
                                                                        @tuleville?
                                                                        rivit))]
                                  (when uudet-materiaalit
                                    (viesti/nayta! "Materiaalit tallennettu." :success)
                                    (reset! tuleville? false)
                                    (reset! urakan-materiaalit uudet-materiaalit)))))}
              "Tallenna materiaalit"]])])))))
