(ns harja.views.urakka.toteumat.varusteet
  "Urakan 'Toteumat' välilehden 'Varusteet' osio"
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
            [harja.views.kartta.popupit :as popupit]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.napit :as napit]
            [harja.tiedot.urakka.toteumat.varusteet.viestit :as v]
            [tuck.core :as t :refer [tuck]]
            [harja.ui.lomake :as lomake]
            [harja.ui.debug :refer [debug]]
            [harja.views.tierekisteri.varustehaku :refer [varustehaku]]
            [harja.domain.tierekisteri.varusteet
             :refer [varusteominaisuus->skeema]
             :as tierekisteri-varusteet]
            [harja.ui.viesti :as viesti])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]))

(def nayta-max-toteumaa 500)

(defn oikeus-varusteen-lisaamiseen? [] (oikeudet/voi-kirjoittaa? oikeudet/urakat-toteumat-varusteet (:id @nav/valittu-urakka)))

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

(def valittu-varustetoteuman-tyyppi (atom nil))

(defn varustekortti-linkki [{:keys [alkupvm tietolaji tunniste]}]
  (when (and tietolaji tunniste)
    (let [url (kommunikaatio/varustekortti-url alkupvm tietolaji tunniste)]
      [:a {:href url :target "_blank"} "Avaa"])))

(defn varustetoteuma-klikattu [_ toteuma]
  (popupit/nayta-popup
    (assoc toteuma
      :toimenpide (tierekisteri-varusteet/varuste-toimenpide->string (keyword (:toimenpide toteuma)))
      :aihe :varustetoteuma-klikattu
      :varustekortti-url (kommunikaatio/varustekortti-url (:alkupvm toteuma) (:tietolaji toteuma) (:tunniste toteuma)))))

(defn toteumataulukko [e! valittu-tyyppi toteumat]
  (let [valitut-toteumat (filter
                           #(if-not valittu-tyyppi
                              toteumat
                              (= (:toimenpide %) valittu-tyyppi))
                           toteumat)]
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
       {:otsikko "Tie" :nimi :tie :tyyppi :positiivinen-numero :leveys 10 :tasaa :oikea}
       {:otsikko "Aosa" :nimi :aosa :tyyppi :positiivinen-numero :leveys 5 :tasaa :oikea}
       {:otsikko "Aet" :nimi :aet :tyyppi :positiivinen-numero :leveys 5 :tasaa :oikea}
       {:otsikko "Losa" :nimi :losa :tyyppi :positiivinen-numero :leveys 5 :tasaa :oikea}
       {:otsikko "Let" :nimi :let :tyyppi :positiivinen-numero :leveys 5 :tasaa :oikea}
       {:otsikko "Kuntoluokka" :nimi :kuntoluokka :tyyppi :positiivinen-numero :leveys 10}
       {:otsikko "Varustekortti" :nimi :varustekortti :tyyppi :komponentti
        :komponentti (fn [rivi] (varustekortti-linkki rivi)) :leveys 10}]
      (take nayta-max-toteumaa valitut-toteumat)]
     (when (> (count valitut-toteumat) nayta-max-toteumaa)
       [:div.alert-warning
        (str "Toteumia löytyi yli " nayta-max-toteumaa ". Tarkenna hakurajausta.")])]))

(defn valinnat [e! valinnat]
  [:span
   ;; Nämä käyttävät suoraan atomeita valintoihin
   [urakka-valinnat/urakan-sopimus]
   [urakka-valinnat/urakan-hoitokausi-ja-kuukausi @nav/valittu-urakka]
   [urakka-valinnat/tienumero (r/wrap (:tienumero valinnat)
                                      #(e! (v/->YhdistaValinnat {:tienumero %})))]

   [harja.ui.valinnat/varustetoteuman-tyyppi
    (r/wrap (:tyyppi valinnat)
            #(e! (v/->ValitseVarusteToteumanTyyppi %)))]])

(defn varustetoteumalomake [e! nykyiset-valinnat varustetoteuma]
  [:span.varustetoteumalomake
   [napit/takaisin "Takaisin varusteluetteloon"
    #(e! (v/->TyhjennaValittuToteuma))]

   [lomake/lomake
    {:otsikko (case (:toiminto varustetoteuma)
                :lisaa "Uusi varuste"
                ;; todo: lisää uudet toiminnot tänne
                "Varustetoteuma")
     :muokkaa! #(e! (v/->AsetaToteumanTiedot %))
     :footer-fn (fn [toteuma]
                  [napit/palvelinkutsu-nappi
                   "Tallenna"
                   #(varustetiedot/tallenna-varustetoteuma nykyiset-valinnat toteuma)
                   {:luokka "nappi-ensisijainen"
                    :ikoni (ikonit/tallenna)
                    :kun-onnistuu #(do
                                     (viesti/nayta! "Varusteen tiedot lähetetty onnistuneesti Tierekisteriin."
                                                    :success viesti/viestin-nayttoaika-keskipitka )
                                     (e! (v/->VarustetoteumaTallennettu %)))
                    :kun-virhe #(viesti/nayta! "Varusteen tallennus epäonnistui" :warning viesti/viestin-nayttoaika-keskipitka)
                    :disabled (not (lomake/voi-tallentaa? toteuma))}])}
    [(lomake/ryhma
       "Varusteen tunnistetiedot"
       {:nimi :tietolaji
        :otsikko "Varusteen tyyppi"
        :tyyppi :valinta
        :valinnat (vec tierekisteri-varusteet/tietolaji->selitys)
        :valinta-nayta second
        :valinta-arvo first}
       {:nimi :tierekisteriosoite
        :otsikko "Tierekisteriosoite"
        :tyyppi :tierekisteriosoite
        :pakollinen? true
        :sijainti (r/wrap (:sijainti varustetoteuma) #(e! (v/->AsetaToteumanTiedot (assoc varustetoteuma :sijainti %))))}
       {:nimi :ajorata
        :otsikko "Ajorata"
        :tyyppi :numero
        :vaadi-ei-negatiivinen? true
        :kokonaisluku? true
        :pakollinen? true
        :leveys 1}
       {:nimi :puoli
        :otsikko "Tien puoli"
        :tyyppi :valinta
        :valinnat tierekisteri-varusteet/tien-puolet
        :pituus 1
        :pakollinen? true}
       {:nimi :alkupvm
        :otsikko "Alkupäivämäärä"
        :tyyppi :pvm
        :pakollinen? true}
       {:nimi :loppupvm
        :otsikko "Loppupäivämäärä"
        :tyyppi :pvm}
       {:nimi :lisatieto
        :otsikko "Lisätietoja"
        :tyyppi :string})

     ;; Muodostetaan varusteen tiedoille kentät tietolajin skeeman perusteella
     (apply lomake/ryhma "Varusteen ominaisuudet"
            (map varusteominaisuus->skeema
                 (:ominaisuudet (:tietolajin-kuvaus varustetoteuma))))]
    varustetoteuma]])

(defn- varusteet* [e! varusteet]
  (e! (v/->YhdistaValinnat @varustetiedot/valinnat))
  (komp/luo
    (komp/watcher varustetiedot/valinnat
                  (fn [_ _ uusi]
                    (e! (v/->YhdistaValinnat uusi))))
    (komp/kuuntelija :varustetoteuma-klikattu
                     (fn [_ i] (e! (v/->ValitseToteuma i))))
    (fn [e! {nykyiset-valinnat :valinnat
             naytettavat-toteumat :naytettavat-toteumat
             toteuma :varustetoteuma
             varustehaun-tiedot :varustehaku}]
      [:span
       [kartta/kartan-paikka]
       (if toteuma
         [varustetoteumalomake e! nykyiset-valinnat toteuma]
         [:span
          [:div.sisalto-container
           [:h1 "Varustekirjaukset Harjassa"]
           [valinnat e! nykyiset-valinnat]
           [toteumataulukko e! (:tyyppi nykyiset-valinnat) naytettavat-toteumat]]
          [:div.sisalto-container
           [:h1 "Varusteet Tierekisterissä"]
           (when oikeus-varusteen-lisaamiseen?
             [napit/uusi "Lisää uusi varuste"
              #(e! (v/->LisaaVaruste))])
           [varustehaku (t/wrap-path e! :varustehaku) varustehaun-tiedot]]])])))

(defn varusteet []
  [tuck varustetiedot/varusteet varusteet*])
