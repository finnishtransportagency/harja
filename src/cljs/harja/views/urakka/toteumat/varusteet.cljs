(ns harja.views.urakka.toteumat.varusteet
  "Urakan 'Toteumat' välilehden 'Varusteet' osio

  Näyttä Harjan kautta kirjatut varustetoteumat sekä mahdollistaa haut ja muokkaukset suoraan Tierekisteriin rajapinnan
  kautta.

  Harjaan tallennettu varustetoteuma sisältää tiedot varsinaisesta työstä. Varusteiden tekniset tiedot päivitetään
  aina Tierekisteriin"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! chan timeout]]
            [harja.atom :refer [paivita!] :refer-macros [reaction<!]]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.tiedot.urakka.toteumat.varusteet :as varustetiedot]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.domain.skeema :refer [+tyotyypit+]]
            [harja.views.kartta :as kartta]
            [harja.ui.komponentti :as komp]
            [harja.pvm :as pvm]
            [harja.tiedot.navigaatio :as nav]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.ui.ikonit :as ikonit]
            [harja.views.urakka.toteumat.yksikkohintaiset-tyot :as yksikkohintaiset-tyot]
            [harja.asiakas.kommunikaatio :as kommunikaatio]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.napit :as napit]
            [harja.tiedot.urakka.toteumat.varusteet.viestit :as v]
            [tuck.core :as t :refer [tuck]]
            [harja.ui.lomake :as lomake]
            [harja.ui.debug :refer [debug]]
            [harja.views.tierekisteri.varusteet :refer [varustehaku]]
            [harja.domain.tierekisteri.varusteet
             :refer [varusteominaisuus->skeema]
             :as tierekisteri-varusteet]
            [harja.ui.viesti :as viesti]
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.kartta :as kartta-tiedot]
            [harja.tiedot.istunto :as istunto])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [tuck.intercept :refer [intercept send-to]]))

(def nayta-max-toteumaa 500)

(defn oikeus-varusteiden-muokkaamiseen? [] (oikeudet/voi-kirjoittaa? oikeudet/urakat-toteumat-varusteet (:id @nav/valittu-urakka)))

(defn varustetoteuman-tehtavat [toteumat toteuma]
  (let [toteumatehtavat (:toteumatehtavat toteuma)]
    [grid/grid
     {:otsikko "Tehtävät"
      :tyhja (if (nil? toteumat)
               [ajax-loader "Haetaan tehtäviä..."]
               "Tehtäviä  ei löytynyt")
      :tunniste :id}
     [{:otsikko "Tehtävä" :nimi :nimi :tyyppi :string :leveys 1}
      {:otsikko "Tyyppi" :nimi :toteumatyyppi :tyyppi :string :leveys 1 :hae (fn [_] (name (:toteumatyyppi toteuma)))}
      {:otsikko "Määrä" :nimi :maara :tyyppi :string :leveys 1}
      (when (= (:toteumatyyppi toteuma) :yksikkohintainen)
        {:otsikko "Toteuma" :nimi :linkki-toteumaan :tyyppi :komponentti :leveys 1
         :komponentti (fn []
                        [:button.nappi-toissijainen.nappi-grid
                         {:on-click #(yksikkohintaiset-tyot/nayta-toteuma-lomakkeessa @nav/valittu-urakka-id (:toteumaid toteuma))}
                         (ikonit/eye-open) " Toteuma"])})]
     toteumatehtavat]))

(defn varustekortti-linkki [{:keys [alkupvm tietolaji tunniste]}]
  (when (and tietolaji tunniste)
    (let [url (kommunikaatio/varustekortti-url alkupvm tietolaji tunniste)]
      [:a {:href url :target "_blank"
           :on-click #(.stopPropagation %)} "Avaa"])))

(defn nayta-varustetoteuman-lahetyksen-tila [{tila :tila lahetetty :lahetetty}]
  (case tila
    "lahetetty" [:span.tila-lahetetty (str "Onnistunut: " (pvm/pvm-aika lahetetty))]
    "virhe" [:span.tila-virhe (str "Epäonnistunut: " (pvm/pvm-aika lahetetty))]
    [:span "Ei lähetetty"]))

(defn toteumataulukko [e! toteumat]
  [:span
   [grid/grid
    {:otsikko "Varustetoteumat"
     :tyhja (if (nil? toteumat) [ajax-loader "Haetaan toteumia..."] "Toteumia ei löytynyt")
     :tunniste :id
     :rivi-klikattu #(e! (v/->ValitseToteuma %))
     :vetolaatikot (zipmap
                     (range)
                     (map
                       (fn [toteuma]
                         (when (:toteumatehtavat toteuma)
                           [varustetoteuman-tehtavat toteumat toteuma]))
                       toteumat))}
    [{:tyyppi :vetolaatikon-tila :leveys 5}
     {:otsikko "Pvm" :tyyppi :pvm :fmt pvm/pvm :nimi :alkupvm :leveys 10}
     {:otsikko "Tunniste" :nimi :tunniste :tyyppi :string :leveys 15}
     {:otsikko "Tietolaji" :nimi :tietolaji :tyyppi :string :leveys 15
      :hae (fn [rivi]
             (or (tierekisteri-varusteet/tietolaji->selitys (:tietolaji rivi))
                 (:tietolaji rivi)))}
     {:otsikko "Toimenpide" :nimi :toimenpide :tyyppi :string :leveys 15
      :hae (fn [rivi]
             (tierekisteri-varusteet/varuste-toimenpide->string (:toimenpide rivi)))}
     {:otsikko "Tie" :nimi :tie :tyyppi :positiivinen-numero :leveys 10 :tasaa :oikea
      :hae #(get-in % [:tierekisteriosoite :numero])}
     {:otsikko "Aosa" :nimi :aosa :tyyppi :positiivinen-numero :leveys 5 :tasaa :oikea
      :hae #(get-in % [:tierekisteriosoite :alkuosa])}
     {:otsikko "Aet" :nimi :aet :tyyppi :positiivinen-numero :leveys 5 :tasaa :oikea
      :hae #(get-in % [:tierekisteriosoite :alkuetaisyys])}
     {:otsikko "Losa" :nimi :losa :tyyppi :positiivinen-numero :leveys 5 :tasaa :oikea
      :hae #(get-in % [:tierekisteriosoite :loppuosa])}
     {:otsikko "Let" :nimi :let :tyyppi :positiivinen-numero :leveys 5 :tasaa :oikea
      :hae #(get-in % [:tierekisteriosoite :loppuetaisyys])}
     {:otsikko "Yleinen kuntoluokitus" :nimi :kuntoluokka :tyyppi :positiivinen-numero :leveys 10}
     {:otsikko "Lähetys Tierekisteriin" :nimi :lahetyksen-tila :tyyppi :komponentti :leveys 9
      :komponentti #(nayta-varustetoteuman-lahetyksen-tila %)
      :fmt pvm/pvm-aika}
     {:otsikko "Varustekortti" :nimi :varustekortti :tyyppi :komponentti
      :komponentti (fn [rivi] (varustekortti-linkki rivi)) :leveys 10}]
    (take nayta-max-toteumaa toteumat)]
   (when (> (count toteumat) nayta-max-toteumaa)
     [:div.alert-warning
      (str "Toteumia löytyi yli " nayta-max-toteumaa ". Tarkenna hakurajausta.")])])

(defn valinnat [e! valinnat]
  [:span
   ;; Nämä käyttävät suoraan atomeita valintoihin
   [urakka-valinnat/urakan-sopimus @nav/valittu-urakka]
   [urakka-valinnat/aikavali]
   [urakka-valinnat/tienumero (r/wrap (:tienumero valinnat)
                                      #(e! (v/->YhdistaValinnat {:tienumero %})))]

   [harja.ui.valinnat/varustetoteuman-tyyppi
    (r/wrap (:tyyppi valinnat)
            #(e! (v/->ValitseVarusteToteumanTyyppi %)))]])

(defn varustetoteuman-tiedot [muokattava? varustetoteuma]
  (when (not muokattava?)
    (lomake/ryhma
      ""
      {:nimi :toimenpide
       :otsikko "Toimenpide"
       :tyyppi :valinta
       :valinnat (vec tierekisteri-varusteet/varuste-toimenpide->string)
       :valinta-nayta second
       :valinta-arvo first
       :muokattava? (constantly false)}
      {:nimi :alkanut
       :otsikko "Kirjattu"
       :tyyppi :pvm
       :muokattava? (constantly false)}
      (when (:lahetetty varustetoteuma)
        {:nimi :lahetetty
         :otsikko "Lähetetty Tierekisteriin"
         :tyyppi :komponentti
         :muokattava? (constantly false)
         :komponentti #(nayta-varustetoteuman-lahetyksen-tila (:data %))})
      (when (and (not muokattava?) (= "lahetetty" (:tila varustetoteuma)))
        {:nimi :varustekortti
         :otsikko "Varustekortti"
         :tyyppi :komponentti
         :komponentti #(varustekortti-linkki (:data %))}))))

(defn varusteen-tunnistetiedot [e! muokattava? varustetoteuma]
  (lomake/ryhma
    "Varusteen tunnistetiedot"
    {:nimi :tietolaji
     :otsikko "Varusteen tyyppi"
     :tyyppi :valinta
     :valinnat (vec tierekisteri-varusteet/tietolaji->selitys)
     :valinta-nayta second
     :valinta-arvo first
     :muokattava? (constantly muokattava?)}
    {:nimi :tierekisteriosoite
     :otsikko "Tierekisteriosoite"
     :tyyppi :tierekisteriosoite
     :pakollinen? muokattava?
     :sijainti (r/wrap (:sijainti varustetoteuma) #(e! (v/->AsetaToteumanTiedot (assoc varustetoteuma :sijainti %))))
     :muokattava? (constantly muokattava?)}
    {:nimi :ajorata
     :otsikko "Ajorata"
     :tyyppi :valinta
     :valinnat (if muokattava?
                 (if (:ajoradat varustetoteuma)
                   (:ajoradat varustetoteuma)
                   tierekisteri-varusteet/oletus-ajoradat)
                 tierekisteri-varusteet/kaikki-ajoradat)
     :pakollinen? muokattava?
     :leveys 1
     :muokattava? (constantly muokattava?)}
    {:nimi :puoli
     :otsikko "Tien puoli"
     :tyyppi :valinta
     :valinnat tierekisteri-varusteet/tien-puolet
     :pituus 1
     :pakollinen? muokattava?
     :muokattava? (constantly muokattava?)}
    {:nimi :alkupvm
     :otsikko "Alkupäivämäärä"
     :tyyppi :pvm
     :pakollinen? muokattava?
     :muokattava? (constantly muokattava?)}
    (when (not= (:toiminto varustetoteuma) :lisatty)
      {:nimi :loppupvm
       :otsikko "Loppupäivämäärä"
       :tyyppi :pvm
       :muokattava? (constantly muokattava?)})
    {:nimi :lisatieto
     :otsikko "Lisätietoja"
     :tyyppi :string
     :muokattava? (constantly muokattava?)}))

(defn varusteen-ominaisuudet [muokattava? ominaisuudet]
  (when (istunto/ominaisuus-kaytossa? :tierekisterin-varusteet)
    (let [poista-tunniste-fn (fn [o] (filter #(not (= "tunniste" (get-in % [:ominaisuus :kenttatunniste]))) o))
          ominaisuudet (if muokattava?
                         (poista-tunniste-fn ominaisuudet)
                         ominaisuudet)]
      (apply lomake/ryhma "Varusteen ominaisuudet" (map #(varusteominaisuus->skeema % muokattava?) ominaisuudet)))))

(defn varustetoteumalomake [e! valinnat varustetoteuma]
  (let [muokattava? (:muokattava? varustetoteuma)
        ominaisuudet (:ominaisuudet (:tietolajin-kuvaus varustetoteuma))]
    [:span.varustetoteumalomake
     [napit/takaisin "Takaisin varusteluetteloon"
      #(e! (v/->TyhjennaValittuToteuma))]

     [lomake/lomake
      {:otsikko (case (:toiminto varustetoteuma)
                  :lisatty "Uusi varuste"
                  :paivitetty "Muokkaa varustetta"
                  "Varustetoteuma")
       :muokkaa! #(e! (v/->AsetaToteumanTiedot %))
       :footer-fn (fn [toteuma]
                    (when muokattava?
                      [:div
                       (when (and (istunto/ominaisuus-kaytossa? :tierekisterin-varusteet) (empty? ominaisuudet))
                         (lomake/yleinen-varoitus "Ladataan tietolajin kuvausta. Kirjaus voidaan tehdä vasta, kun kuvaus on ladattu"))
                       [napit/palvelinkutsu-nappi
                        "Tallenna"
                        #(varustetiedot/tallenna-varustetoteuma valinnat toteuma)
                        {:luokka "nappi-ensisijainen"
                         :ikoni (ikonit/tallenna)
                         :kun-onnistuu #(e! (v/->VarustetoteumaTallennettu %))
                         :kun-virhe #(viesti/nayta! "Varusteen tallennus epäonnistui" :warning viesti/viestin-nayttoaika-keskipitka)
                         :disabled (not (lomake/voi-tallentaa? toteuma))}]]))}
      [(varustetoteuman-tiedot muokattava? varustetoteuma)
       (varusteen-tunnistetiedot e! muokattava? varustetoteuma)
       (varusteen-ominaisuudet muokattava? ominaisuudet)]
      varustetoteuma]]))

(defn varustehakulomake [e! nykyiset-valinnat naytettavat-toteumat app]
  [:span
   [:div.sisalto-container
    [:h1 "Varustekirjaukset Harjassa"]
    [valinnat e! nykyiset-valinnat]
    [toteumataulukko e! naytettavat-toteumat]]
   (when (istunto/ominaisuus-kaytossa? :tierekisterin-varusteet)
     [:div.sisalto-container
      [:h1 "Varusteet Tierekisterissä"]
      (when oikeus-varusteiden-muokkaamiseen?
        [napit/uusi "Lisää uusi varuste" #(e! (v/->UusiVarusteToteuma :lisatty nil))])
      [varustehaku e! app]])])

(defn kasittele-alkutila [e! {:keys [uudet-varustetoteumat muokattava-varuste] }]
  (when uudet-varustetoteumat
    (e! (v/->VarustetoteumatMuuttuneet uudet-varustetoteumat)))

  (when muokattava-varuste
    (e! (v/->UusiVarusteToteuma :paivitetty muokattava-varuste))))

(defn- varusteet* [e! varusteet]
  (e! (v/->YhdistaValinnat @varustetiedot/valinnat))
  (komp/luo
    (komp/watcher varustetiedot/valinnat
                  (fn [_ _ uusi]
                    (e! (v/->YhdistaValinnat uusi))))
    (komp/kuuntelija :varustetoteuma-klikattu
                     (fn [_ i] (e! (v/->ValitseToteuma i))))
    (komp/sisaan-ulos #(do
                         (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                         (kartta-tiedot/kasittele-infopaneelin-linkit!
                           {:varustetoteuma {:toiminto (fn [klikattu-varustetoteuma]
                                                         (e! (v/->ValitseToteuma klikattu-varustetoteuma)))
                                             :teksti "Valitse varustetoteuma"}})
                         (nav/vaihda-kartan-koko! :M))
                      #(do (nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko)
                           (kartta-tiedot/kasittele-infopaneelin-linkit! nil)))
    (fn [e! {nykyiset-valinnat :valinnat
             naytettavat-toteumat :naytettavat-toteumat
             varustetoteuma :varustetoteuma
             virhe :virhe
             :as app}]

      (kasittele-alkutila e! app)

      [:span
       (when virhe
         (yleiset/virheviesti-sailio virhe (fn [_] (e! (v/->VirheKasitelty)))))
       [kartta/kartan-paikka]

       (if varustetoteuma
         [varustetoteumalomake e! nykyiset-valinnat varustetoteuma]
         [varustehakulomake e! nykyiset-valinnat naytettavat-toteumat app])])))

(defn varusteet []
  [tuck varustetiedot/varusteet varusteet*])
