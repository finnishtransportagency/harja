(ns harja.views.urakka.toteumat.velho-varusteet
  "Urakan 'Toteumat' välilehden 'Varusteet' osio

  Näyttä Harjan kautta kirjatut varustetoteumat sekä mahdollistaa haut ja muokkaukset suoraan Tierekisteriin rajapinnan
  kautta.

  Harjaan tallennettu varustetoteuma sisältää tiedot varsinaisesta työstä. Varusteiden tekniset tiedot päivitetään
  aina Tierekisteriin"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! chan timeout]]
            [harja.atom :refer [paivita!] :refer-macros [reaction<!]]
            [harja.ui.debug :as debug]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat :refer [tee-kentta]]
            [harja.ui.komponentti :as komp]
            [harja.ui.liitteet :as liitteet]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.viesti :as viesti]
            [harja.ui.yleiset :as yleiset :refer [ajax-loader]]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.domain.skeema :refer [+tyotyypit+]]
            [harja.views.kartta :as kartta]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka.urakka :as urakka-tila]
            [harja.tiedot.navigaatio :as nav]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.views.urakka.toteumat.yksikkohintaiset-tyot :as yksikkohintaiset-tyot]
            [harja.asiakas.kommunikaatio :as kommunikaatio]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.urakka.toteumat.varusteet.viestit :as v]
            [tuck.core :as tuck]
            [harja.views.tierekisteri.varusteet :refer [varustehaku] :as view]
            [harja.domain.tierekisteri.varusteet
             :refer [varusteominaisuus->skeema]
             :as tierekisteri-varusteet]
            [harja.tiedot.kartta :as kartta-tiedot]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.tierekisteri.varusteet :as tv]
            [harja.geo :as geo])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [tuck.intercept :refer [intercept send-to]]))
(defn suodatuslomake [e! app]
  (let [hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)
        hoitokaudet 0 valittu-kuukausi 0 hoitokauden-kuukaudet 0]
    [:div "Suodatuslomake   ============================================     Varustelistaus | Yhtenveto"]
    #_ [:div.row.filtterit-container
     [:div.filtteri
      [:span.alasvedon-otsikko-vayla "Hoitovuosi"]
      [yleiset/livi-pudotusvalikko {:valinta hoitokauden-alkuvuosi
                                    :vayla-tyyli? true
                                    :data-cy "hoitokausi-valinta"
                                    :valitse-fn #(do (e! (kustannusten-seuranta-tiedot/->ValitseHoitokausi (:id @nav/valittu-urakka) %)))
                                    :format-fn #(str kustannusten-seuranta-tiedot/fin-hk-alkupvm % "-" kustannusten-seuranta-tiedot/fin-hk-loppupvm (inc %))
                                    :klikattu-ulkopuolelle-params {:tarkista-komponentti? true}}
       hoitokaudet]]
     [:div.filtteri.kuukausi
      [:span.alasvedon-otsikko-vayla "Kuukausi"]
      [yleiset/livi-pudotusvalikko {:valinta valittu-kuukausi
                                    :vayla-tyyli? true
                                    :valitse-fn #(e! (kustannusten-seuranta-tiedot/->ValitseKuukausi (:id @nav/valittu-urakka) % hoitokauden-alkuvuosi))
                                    :format-fn #(if %
                                                  (if (= "Kaikki" %)
                                                    "Kaikki"
                                                    (let [[alkupvm _] %
                                                          kk-teksti (pvm/kuukauden-nimi (pvm/kuukausi alkupvm))]
                                                      (str (str/capitalize kk-teksti) " " (pvm/vuosi alkupvm))))
                                                  "Kaikki")
                                    :klikattu-ulkopuolelle-params {:tarkista-komponentti? true}}
       hoitokauden-kuukaudet]]
     [:div.filtteri {:style {:padding-top "21px"}}
      ^{:key "raporttixls"}
      [:form {:style {:margin-left "auto"}
              :target "_blank" :method "POST"
              :action (k/excel-url :kustannukset)}
       [:input {:type "hidden" :name "parametrit"
                :value (transit/clj->transit {:urakka-id (:id @nav/valittu-urakka)
                                              :urakka-nimi (:nimi @nav/valittu-urakka)
                                              :hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                              :alkupvm haun-alkupvm
                                              :loppupvm haun-loppupvm})}]
       [:button {:type "submit"
                 :class "nappi-toissijainen"}
        [ikonit/ikoni-ja-teksti [ikonit/livicon-download] "Tallenna Excel"]]]]
     [:div.filtteri {:style {:padding-top "21px"}}
      (if valikatselmus-tekematta?
        [napit/yleinen-ensisijainen
         "Tee välikatselmus"
         #(e! (kustannusten-seuranta-tiedot/->AvaaValikatselmusLomake))]

        [napit/yleinen-ensisijainen "Avaa välikatselmus" #(e! (kustannusten-seuranta-tiedot/->AvaaValikatselmusLomake)) {:luokka "napiton-nappi tumma" :ikoni (ikonit/harja-icon-action-show)}])]]))
(defn- varusteet* [e! app]
  [:div [suodatuslomake e! app] [:div "Kartta"] [:div "Listaus"]])

(defn velho-varusteet []
  [tuck/tuck urakka-tila/velho-varusteet varusteet*])
