(ns harja.views.urakka.toteumat.varusteet
  "Urakan 'Toteumat' välilehden 'Varusteet' osio

  Näyttä Harjan kautta kirjatut varustetoteumat sekä mahdollistaa haut ja muokkaukset suoraan Tierekisteriin rajapinnan
  kautta.

  Harjaan tallennettu varustetoteuma sisältää tiedot varsinaisesta työstä. Varusteiden tekniset tiedot päivitetään
  aina Tierekisteriin"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.debug :as debug]
            [harja.ui.grid :as grid]
            [harja.ui.komponentti :as komp]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.yleiset :as yleiset :refer [ajax-loader]]
            [harja.tiedot.urakka.toteumat.varusteet :as varustetiedot]
            [harja.views.kartta :as kartta]
            [harja.pvm :as pvm]
            [harja.tiedot.navigaatio :as nav]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.tiedot.urakka.toteumat.varusteet.viestit :as v]
            [tuck.core :refer [tuck]]
            [harja.domain.tierekisteri.varusteet
             :refer [varusteominaisuus->skeema]
             :as tierekisteri-varusteet]
            [harja.tiedot.kartta :as kartta-tiedot]))

(def nayta-max-toteumaa 2000)

(defn nayta-varustetoteuman-lahetyksen-tila [{tila :tila lahetetty :lahetetty}]
  (case tila
    "lahetetty" [:span.tila-lahetetty (str "Onnistunut: " (pvm/pvm-aika lahetetty))]
    "virhe" [:span.tila-virhe (str "Epäonnistunut: " (pvm/pvm-aika lahetetty))]
    [:span.tila-odottaa-vastausta "Ei lähetetty"]))

(defn toteumataulukko [e! toteumat varustetoteumien-haku-kaynnissa?]
  [:span
   (when (> (count toteumat) nayta-max-toteumaa)
     [:div.alert-warning
      (str "Toteumia löytyi yli " nayta-max-toteumaa ". Tarkenna hakurajausta.")])

   [grid/grid
    {:otsikko "Varustetoteumat"
     :tyhja (if varustetoteumien-haku-kaynnissa?
              [ajax-loader "Haetaan toteumia..." {:sama-rivi? true}]
              [:span "Toteumia ei löytynyt"])
     :tunniste :id
     :sivuta 100
     :rivi-klikattu #(e! (v/->ValitseToteuma %))}
    [{:tyyppi :vetolaatikon-tila :leveys 5}
     {:otsikko "Tehty" :tyyppi :pvm :fmt pvm/pvm-aika :nimi :luotu :leveys 10}
     {:otsikko "Tekijä" :tyyppi :string :nimi :tekija :hae #(str (:luojan-etunimi %) " " (:luojan-sukunimi %)) :leveys 10}
     {:otsikko "Tunniste" :nimi :tunniste :tyyppi :string :leveys 15}
     {:otsikko "Tietolaji" :nimi :tietolaji :tyyppi :string :leveys 15
      :hae (fn [rivi]
             (or (tierekisteri-varusteet/tietolaji->selitys (:tietolaji rivi))
                 (:tietolaji rivi)))}
     {:otsikko "Toimenpide" :nimi :toimenpide :tyyppi :string :leveys 15
      :hae (fn [rivi]
             (tierekisteri-varusteet/varuste-toimenpide->string (:toimenpide rivi)))}
     {:otsikko "Tie" :nimi :tie :tyyppi :positiivinen-numero :leveys 10 :tasaa :oikea
      :hae #(get-in % [:tierekisteriosoite :numero]) :kokonaisluku? true}
     {:otsikko "Aosa" :nimi :aosa :tyyppi :positiivinen-numero :leveys 5 :tasaa :oikea
      :hae #(get-in % [:tierekisteriosoite :alkuosa]) :kokonaisluku? true}
     {:otsikko "Aet" :nimi :aet :tyyppi :positiivinen-numero :leveys 5 :tasaa :oikea
      :hae #(get-in % [:tierekisteriosoite :alkuetaisyys]) :kokonaisluku? true}
     {:otsikko "Losa" :nimi :losa :tyyppi :positiivinen-numero :leveys 5 :tasaa :oikea
      :hae #(get-in % [:tierekisteriosoite :loppuosa]) :kokonaisluku? true}
     {:otsikko "Let" :nimi :let :tyyppi :positiivinen-numero :leveys 5 :tasaa :oikea
      :hae #(get-in % [:tierekisteriosoite :loppuetaisyys]) :kokonaisluku? true}
     {:otsikko "Yleinen kuntoluokitus" :nimi :kuntoluokka :tyyppi :positiivinen-numero :leveys 10 :kokonaisluku? true
      :tasaa :oikea}
     {:otsikko "Lähetys Tierekisteriin" :nimi :lahetyksen-tila :tyyppi :komponentti :leveys 9
      :komponentti #(nayta-varustetoteuman-lahetyksen-tila %)
      :fmt pvm/pvm-aika}]
    (take nayta-max-toteumaa toteumat)] ])

(defn valinnat [e! valinnat]
  [:span
   ;; Nämä käyttävät suoraan atomeita valintoihin
   [urakka-valinnat/urakan-sopimus @nav/valittu-urakka]
   [urakka-valinnat/aikavali]
   [urakka-valinnat/tienumero (r/wrap (:tienumero valinnat)
                                      #(e! (v/->YhdistaValinnat {:tienumero %})))]

   [harja.ui.valinnat/varustetoteuman-tyyppi
    (r/wrap (:tyyppi valinnat)
            #(e! (v/->ValitseVarusteToteumanTyyppi %)))]
   [yleiset/pudotusvalikko
    "Lähetyksen tila"
    {:valinta (:virheelliset-ainoastaan? valinnat)
     :format-fn #(if % "Vain virheelliset" "Kaikki")
     :valitse-fn #(e! (v/->ValitseVarusteNaytetaanVirheelliset %))}
    [false true]]

   [:span
    [:div.label-ja-alasveto
     [:span.alasvedon-otsikko "Tietolaji"]
     [valinnat/checkbox-pudotusvalikko
      (:tietolajit valinnat)
      (fn [tietolaji valittu?]
        (e! (v/->YhdistaValinnat {:tietolajit (map #(if (= (:id tietolaji) (:id %))
                                                     (assoc % :valittu? valittu?)
                                                     %)
                                                  (:tietolajit valinnat))})))
      [" tietolaji valittu" " tietolajia valittu"]
      {:kaikki-valinta-fn (fn []
                            (let [valitse-kaikki? (some :valittu? (:tietolajit valinnat))]
                              (e! (v/->YhdistaValinnat {:tietolajit (map #(assoc % :valittu? (not valitse-kaikki?))
                                                                         (:tietolajit valinnat))}))))}]]]])

(defn hae-ajoradat [muokattava? varustetoteuma]
  (if muokattava?
    (if (:ajoradat varustetoteuma)
      (:ajoradat varustetoteuma)
      tierekisteri-varusteet/oletus-ajoradat)
    tierekisteri-varusteet/kaikki-ajoradat))

(defn varustehakulomake [e! nykyiset-valinnat naytettavat-toteumat app]
  [:span
   [:div.sisalto-container
    [:h1 "Vanhat varustekirjaukset Harjassa"]
    [valinnat e! nykyiset-valinnat]
    [toteumataulukko e! naytettavat-toteumat (:varustetoteumien-haku-kaynnissa? app)]]])

(defn kasittele-alkutila [e! {:keys [uudet-varustetoteumat muokattava-varuste naytettava-varuste]}]
  (when uudet-varustetoteumat
    (e! (v/->VarustetoteumatMuuttuneet uudet-varustetoteumat))))

(defn- varusteet* [e! _]
  (e! (v/->YhdistaValinnat @varustetiedot/valinnat))
  (komp/luo
    (komp/lippu varustetiedot/karttataso-varustetoteuma)
    (komp/watcher varustetiedot/valinnat
                  (fn [_ _ uusi]
                    (e! (v/->YhdistaValinnat uusi))))
    (komp/kuuntelija :varustetoteuma-klikattu
                     (fn [_ i] (e! (v/->ValitseToteuma i))))
    (komp/sisaan-ulos #(kartta-tiedot/kasittele-infopaneelin-linkit!
                         {:varustetoteuma {:toiminto (fn [klikattu-varustetoteuma]
                                                       (e! (v/->ValitseToteuma klikattu-varustetoteuma)))
                                           :teksti "Valitse varustetoteuma"}})
                      #(kartta-tiedot/kasittele-infopaneelin-linkit! nil))
    (fn [e! {nykyiset-valinnat :valinnat
             naytettavat-toteumat :naytettavat-toteumat
             virhe :virhe
             :as app}]

      (kasittele-alkutila e! app)

      [:span
       [debug/debug app]
       [yleiset/info-laatikko :vahva-ilmoitus "Varusteiden syöttäminen Harjan kautta päättyy" "Varusteet syötetään jatkossa urakoitsijoiden omien järjestelmien kautta.
        Siirtymävaiheessa varustekirjaukset voidaan kirjata urakoitsijan järjestelmään tai Velhon excel-lomakkeella.
        Vanhat varustekirjaukset löytyvät Harjasta edelleen, mutta varusteiden yksityiskohtaisia tietoja ei voida tarkastella.
        Velhon varustetiedot tulevat Harjaan näkyviin arviolta kesällä 2023." "100%"]
       (when virhe
         (yleiset/virheviesti-sailio virhe (fn [_] (e! (v/->VirheKasitelty)))))
       [kartta/kartan-paikka]
       [varustehakulomake e! nykyiset-valinnat naytettavat-toteumat app]])))

(defn varusteet []
  [tuck varustetiedot/varusteet varusteet*])
