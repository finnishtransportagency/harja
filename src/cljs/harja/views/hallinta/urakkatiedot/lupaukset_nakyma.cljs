(ns harja.views.hallinta.urakkatiedot.lupaukset-nakyma
  "Lupauksiin liittyvää hallinnointia esim. linkityksiä urakkaan"
  (:require
    [clojure.string :as str]
    [harja.asiakas.kommunikaatio :as k]
    [harja.pvm :as pvm]
    [harja.tiedot.hallinta.lupaukset-tiedot :as tiedot]
    [harja.ui.grid :as grid]
    [harja.ui.ikonit :as ikonit]
    [harja.ui.komponentti :as komp]
    [harja.ui.lomake :as lomake]
    [harja.ui.napit :as napit]
    [harja.ui.yleiset :refer [ajax-loader-pieni] :as yleiset]
    [tuck.core :refer [tuck]]))

(defn- laskentakaava-debug-osio [kustannusennuste]
  (when kustannusennuste
    [:div.laskentakaava-debug
     [:div.panel.panel-info
      [:div.panel-heading
       [:h4.panel-title "Laskentakaava"]]
      [:div.panel-body

       ;; Perustiedot
       [:div.perustiedot
        [:div.perustiedot-rivi
         [:strong "Määräpäivä: "] [:span (str (:maarapaiva kustannusennuste))]]
        [:div.perustiedot-rivi
         [:strong "Kuukausi: "] [:span (str (:kuukausi kustannusennuste))]]
        [:div.perustiedot-rivi
         [:strong "Pisteet: "] [:span (str (:lasketut_pisteet kustannusennuste))]]
        [:div.perustiedot-rivi
         [:strong "Tarkkuus: "] [:span (str (:tarkkuus_prosentti kustannusennuste) "%")]]]

       ;; Pääkaava
       (when-let [kaava-teksti (:laskentakaava-teksti kustannusennuste)]
         [:div.mt-3
          [:h5 "Kaava"]
          [:pre.kaava-teksti kaava-teksti]])

       ;; Parametrit taulukko
       (when-let [parametrit-map (:laskentakaava-parametrit kustannusennuste)]
         (let [;; Erota kertoimet muista parametreista
               kertoimet (:kertoimet parametrit-map)
               muut-parametrit (dissoc parametrit-map :kertoimet)
               ;; Muunna mapit vektoreiksi iterointia varten
               param-rivit (for [[avain arvo] muut-parametrit]
                             {:muuttuja (name avain) :arvo arvo})
               kerroin-rivit (when kertoimet
                               (for [[avain arvo] kertoimet]
                                 {:muuttuja (name avain) :arvo arvo}))]
           [:div.mt-3
            [:h5 "Parametrit"]
            [:table.table.table-striped.table-hover
             [:thead
              [:tr
               [:th "Muuttuja"]
               [:th "Arvo"]]]
             [:tbody
              (concat
                (for [param param-rivit]
                  ^{:key (str "param-" (:muuttuja param))}
                  [:tr
                   [:td [:code (:muuttuja param)]]
                   [:td (:arvo param)]])
                (for [kerroin kerroin-rivit]
                  ^{:key (str "kerroin-" (:muuttuja kerroin))}
                  [:tr.kerroin-rivi
                   [:td [:code (str "  " (:muuttuja kerroin))]]
                   [:td [:em (:arvo kerroin)]]]))]]]))


       ;; Laskentavaiheet taulukko
       (when-let [vaiheet-map (:laskentakaava-vaiheet kustannusennuste)]
         (let [;; Järjestä vaiheet numerojärjestykseen (vaihe-1, vaihe-2, ...)
               vaihe-entries (sort-by (fn [[k _]]
                                        (let [match (re-find #"vaihe-(\d+)" (name k))]
                                          (when match
                                            (js/parseInt (second match)))))
                               (filter (fn [[k _]]
                                         (re-matches #"vaihe-\d+" (name k)))
                                 vaiheet-map))
               ;; Muunna vaihe-mapit vektoreiksi lisäämällä numero
               vaiheet (map-indexed (fn [idx [_ vaihe-data]]
                                      (assoc vaihe-data :vaihenro (inc idx)))
                         vaihe-entries)]
           [:div.mt-3
            [:h5 "Laskentavaiheet"]
            [:table.table.table-striped.table-hover
             [:thead
              [:tr
               [:th "#"]
               [:th "Vaihe"]
               [:th "Tulos"]
               [:th "Selite"]]]
             [:tbody
              (concat
                (for [vaihe vaiheet]
                  ^{:key (str "vaihe-" (:vaihenro vaihe))}
                  [:tr
                   [:td (:vaihenro vaihe)]
                   [:td [:code (:kaava vaihe)]]
                   [:td (:tulos vaihe)]
                   [:td (:kuvaus vaihe)]])
                (when-let [lopputulos (:lopputulos-prosentti vaiheet-map)]
                  [^{:key "lopputulos"}
                   [:tr.font-weight-bold
                    [:td "→"]
                    [:td "Lopputulos"]
                    [:td (str lopputulos " %")]
                    [:td ""]]]))]]]))]]]))

(defn- testausosio [e! {:keys [testaus-auki? testaus-urakat testaus-valittu-urakka
                               testaus-valittu-hoitokausi testaus-data testaus-parametrit
                               laskenta-kaynnissa? tee-taytto-kaynnissa? taytto-edistyminen
                               valittu-kustannusennuste]}]
  [:div.testaustyokalut
   [:h2 {:on-click #(if testaus-auki?
                      (e! (tiedot/->SuljeTestausosio))
                      (e! (tiedot/->AvaaTestausosio)))
         :style {:cursor "pointer"}}
    (if testaus-auki?
      [ikonit/livicon-chevron-down]
      [ikonit/livicon-chevron-right])
    " Kustannusennuste testaustyökalut"]

   (when testaus-auki?
     [:div
      [:div.alert
       [:p [:strong "⚠️ Kehittäjätyökalu - vain kehitysympäristössä"]]
       [:p "Tämä työkalu on tarkoitettu vain kustannusennustelaskelman testaukseen kehitysympäristössä."]
       [:p [:strong "Huom!"] " Testauksen aikana muokataan valitun urakan lupaukset-tietokantatauluja (kustannusennusteet ja lopputilanne)."]]
      ;; Urakan valinta
      [:div.row
       [:div.col-md-6
        [yleiset/pudotusvalikko
         "Valitse urakka"
         {:valitse-fn #(e! (tiedot/->ValitseTestausUrakka %))
          :valinta testaus-valittu-urakka
          :format-fn #(or (:urakka-nimi %) "Valitse urakka")}
         testaus-urakat]]]

      ;; Hoitokauden valinta
      (when testaus-valittu-urakka
        (let [alkuvuosi (pvm/vuosi (:alkupvm testaus-valittu-urakka))
              loppuvuosi (pvm/vuosi (:loppupvm testaus-valittu-urakka))
              hoitokaudet (range alkuvuosi loppuvuosi)]
          [:div.row
           [:div.col-md-6
            [yleiset/pudotusvalikko
             "Valitse hoitokausi"
             {:valitse-fn #(e! (tiedot/->ValitseTestausHoitokausi %))
              :valinta testaus-valittu-hoitokausi
              :format-fn #(if % (str % " - " (inc %)) "Valitse hoitokausi")}
             hoitokaudet]]]))

      ;; Parametrien syöttö
      (when (and testaus-valittu-urakka testaus-valittu-hoitokausi)
        [:div
         [:h3 "Parametrit välikatselmoinnista"]
         [:div.row
          [:div.col-md-6
           [lomake/lomake
            {:luokka :horizontal
             :muokkaa! #(e! (tiedot/->PaivitaTestausparametri :toteutunut-tavoitehinta (:toteutunut-tavoitehinta %)))}
            [{:nimi :toteutunut-tavoitehinta
              :otsikko "Toteutunut tavoitehinta (€)"
              :tyyppi :positiivinen-numero
              :kokonaisluku? false}]
            testaus-parametrit]]
          [:div.col-md-6
           [lomake/lomake
            {:luokka :horizontal
             :muokkaa! #(e! (tiedot/->PaivitaTestausparametri :toteutunut-kustannus (:toteutunut-kustannus %)))}
            [{:nimi :toteutunut-kustannus
              :otsikko "Toteutunut kustannus (€)"
              :tyyppi :positiivinen-numero
              :kokonaisluku? false}]
            testaus-parametrit]]]

         ;; Pikatäyttö-toiminnot
         [:div.row.mt-3
          [:div.col-md-12
           [:div.panel.panel-default
            [:div.panel-heading
             [:h4.panel-title "Pikatäyttö testaukseen"]]
            [:div.panel-body
             [:p.text-muted
              "Täyttää automaattisesti kustannusennusteet määritellyille kuukausille "
              "realistisilla variaatioilla (±5%). "
              "Käyttää " [:code "lupaus_kustannusennuste_kuukausi_pisteet"]
              " taulun määräpäiviä."]

             [:div.button-toolbar
              [napit/yleinen-toissijainen
               "Täytä määritellyt kuukaudet"
               #(e! (tiedot/->TaytaKustannusennusteetTestaukseen))
               {:disabled (or tee-taytto-kaynnissa?
                            (not testaus-valittu-urakka)
                            (not testaus-valittu-hoitokausi)
                            (not (:toteutunut-tavoitehinta testaus-parametrit))
                            (not (:toteutunut-kustannus testaus-parametrit)))
                :ikoni (ikonit/livicon-plus)}]

              " "

              [napit/kielteinen
               "Poista kaikki kirjaukset"
               #(e! (tiedot/->PoistaKustannusennusteetTestaukseen))
               {:disabled (or tee-taytto-kaynnissa?
                            (not testaus-valittu-urakka)
                            (not testaus-valittu-hoitokausi))
                :ikoni (ikonit/livicon-trash)}]]

             (when tee-taytto-kaynnissa?
               (let [{:keys [yhteensa valmis]} taytto-edistyminen]
                 [:div.mt-2
                  [ajax-loader-pieni
                   (if (> yhteensa 0)
                     (str "Tallennetaan " valmis "/" yhteensa " kuukautta...")
                     "Haetaan määräpäiviä...")]]))]]]]

         ;; Laske-nappi
         [:div.row
          [:div.col-md-12
           [napit/yleinen-ensisijainen
            "Laske pisteet"
            #(e! (tiedot/->TriggerLaskenta))
            {:disabled laskenta-kaynnissa?}]
           (when laskenta-kaynnissa?
             [ajax-loader-pieni "Lasketaan..."])]]

         ;; Tulokset
         (when testaus-data
           (let [kustannusennusteet (:kustannusennusteet testaus-data)
                 lopputilanne (:lopputilanne testaus-data)]
             [:div
              [:h3 "Kustannusennusteet (12 kk)"]
              [:p.text-muted [:em "💡 Klikkaa riviä nähdäksesi laskentakaavan yksityiskohdat (kaava, parametrit, laskentavaiheet)"]]
              [grid/grid
               {:otsikko "Kustannusennusteet"
                :tyhja "Ei kustannusennusteita."
                :tunniste :id
                :rivi-klikattu #(e! (tiedot/->ValitseKustannusennuste %))}
               [{:otsikko "Määräpäivä" :nimi :maarapaiva :tyyppi :pvm :leveys 1}
                {:otsikko "Kuukausi" :nimi :kuukausi :tyyppi :numero :leveys 0.5}
                {:otsikko "Ennustettu tavoitehinta (€)" :nimi :ennustettu_tavoitehinta :tyyppi :numero :leveys 1}
                {:otsikko "Ennustetut kustannukset (€)" :nimi :ennustetut_kustannukset :tyyppi :numero :leveys 1}
                {:otsikko "Pisteet" :nimi :lasketut_pisteet :tyyppi :numero :leveys 1 :desimaalien-maara 2}
                {:otsikko "Tarkkuus %" :nimi :tarkkuus_prosentti :tyyppi :numero :leveys 1 :desimaalien-maara 2}
                {:otsikko "Ajoissa" :nimi :syotetty_ajoissa :tyyppi :checkbox :leveys 1}
                {:otsikko "Pisterajat" :nimi :pisterajat :leveys 2
                 :fmt (fn [pisterajat]
                        (if pisterajat
                          (->> pisterajat
                            (map #(str (:kuvaus %) " = " (:pisteet %) "p"))
                            (str/join " | "))
                          "-"))}]
               kustannusennusteet]

              ;; Laskentakaavan debug-osio
              (when valittu-kustannusennuste
                [laskentakaava-debug-osio valittu-kustannusennuste])

              [:h3 "Lopputilanne"]
              (if lopputilanne
                [:div
                 [:p (str "Keskiarvo pisteet: " (:kustannusennuste_keskiarvo_pisteet lopputilanne))]
                 [:p (str "Käytetyt kuukaudet laskennassa: " (str/join ", " (:kaytetyt-kuukaudet lopputilanne)))]
                 [:p (str "Välikatselmus: " (:valikatselmus_pvm lopputilanne))]
                 [:p (str "Vahvistettu: " (:vahvistettu lopputilanne))]]
                [:p "Ei vielä lopputilannetta."])

              [:h3 "Debug (JSON)"]
              [:pre (pr-str testaus-data)]]))])])])

(defn lupaukset* [e! app]
  (komp/luo
    (komp/sisaan #(do (e! (tiedot/->HaeLupaustenLinkitykset))
                    (e! (tiedot/->HaeLupaustenKategoriat))))
    (fn [e! {:keys [lupausten-linkitykset lupausten-kategoriat kategorian-urakat urakan-lupaukset valittu-kategoria valittu-urakka haku-kaynnissa?] :as app}]
      (let [puuttuvat-urakat (:puuttuvat-urakat lupausten-linkitykset)
            rivin-tunnistin-selitteet (:rivin-tunnistin-selitteet lupausten-kategoriat)
            kategorian-urakat (:kategorian-urakat kategorian-urakat)
            urakan-lupaukset (:urakan-lupaukset urakan-lupaukset)]
        [:div
         [:h2 "Lupauksien linkitys"]
         [:p "Lupaukset täytyy aina linkittää tiettyyn urakkaan. Tällä sivulla kerrotaan linkityksien tilanne ja on mahdollista tarkastella lupauksille syötettyjä tietoja."]
         (if (seq puuttuvat-urakat)
           [:div.alert
            [:p "Kehittäjän tulee korjata tilanne tekemällä linkki puutteellisille urakoille tai lupaukset eivät toimi näillä urakoilla."]
            [:p "Tällä hetkellä linkitykset puuttuvat seuraavissa urakoissa:"]
            [:ul
             (for [urakka puuttuvat-urakat]
               ^{:key (str "urakka" (:id urakka))}
               [:li (:nimi urakka)])]]
           [:div.alert "Lupausten linkityksessä ei ole puutteita."])


         [:h2 "Lupauksien tarkistaminen"]
         [:p "Voit tarkistaa lupauksien linkitykset ja syötettyjä tietoja valitselmalla ensin kategorian ja sen jälkeen haluamasi urakan."]

         ;; Kategorian valinta
         [yleiset/pudotusvalikko
          "Lupaus kategoriat"
          {:valitse-fn #(e! (tiedot/->ValitseKategoria %))
           :valinta valittu-kategoria
           :format-fn #(cond
                         (and (:rivin-tunnistin-selite %) (:urakan-alkuvuosi %)) (str (:rivin-tunnistin-selite %) " - " (:urakan-alkuvuosi %))
                         (:urakan-alkuvuosi %) (:urakan-alkuvuosi %)
                         (:rivin-tunnistin-selite %) (:rivin-tunnistin-selite %)
                         :else "Valitse kategoria")}
          rivin-tunnistin-selitteet]


         ;; Urakan valinta
         (if haku-kaynnissa?
           [ajax-loader-pieni "Haetaan tietoja..."]
           (when (seq kategorian-urakat)
             [yleiset/pudotusvalikko
              "Kategorian urakat"
              {:valitse-fn #(e! (tiedot/->ValitseUrakka %))
               :valinta valittu-urakka
               :format-fn #(or (:nimi %) "Valitse urakka")}
              kategorian-urakat]))

         ;; Lupaukset taulukko
         (when (seq urakan-lupaukset)
           [grid/grid
            {:otsikko "Lupaukset"
             :tyhja "Ei lupauksia."
             :tunniste :lupaus-id}
            [{:otsikko "Lupausryhmän numero"
              :nimi :lupausryhma-jarjestys
              :tyyppi :string
              :leveys 1}
             {:otsikko "Lupausryhmän otsikko"
              :nimi :otsikko
              :tyyppi :string
              :leveys 1}
             {:otsikko "Lupauksen järjestys"
              :nimi :lupaus-jarjestys
              :tyyppi :string
              :leveys 1}
             {:otsikko "Kuvaus"
              :nimi :kuvaus
              :tyyppi :string
              :leveys 1}
             {:otsikko "Sisalto"
              :nimi :sisalto
              :tyyppi :string
              :leveys 2}
             {:otsikko "Pisteet"
              :nimi :pisteet
              :tyyppi :string
              :leveys 1}
             {:otsikko "Lupaustyyppi"
              :nimi :lupaustyyppi
              :tyyppi :string
              :leveys 1}
             {:otsikko "Kirjauskuukaudet"
              :nimi :kirjaus-kkt
              :leveys 1
              :fmt str}
             {:otsikko "Jousto kuukaudet"
              :nimi :joustovara-kkta
              :tyyppi :string
              :leveys 1}
             {:otsikko "Päätöskuukausi"
              :nimi :paatos-kk
              :tyyppi :string
              :leveys 1}]
            urakan-lupaukset])

         ;; Testaustyökalut
         (when (k/kehitysymparistossa?)
           [testausosio e! app])]))))

(defn lupaukset []
  [tuck tiedot/tila lupaukset*])
