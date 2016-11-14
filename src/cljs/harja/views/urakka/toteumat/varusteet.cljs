(ns harja.views.urakka.toteumat.varusteet
  "Urakan 'Toteumat' välilehden 'Varusteet' osio"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! chan timeout]]
            [harja.atom :refer [paivita!] :refer-macros [reaction<!]]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.tiedot.urakka :as tiedot-urakka]
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
            [tuck.core :as t :refer [tuck]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]))

(def nayta-max-toteumaa 500)

(defn varustetoteuman-tehtavat [toteumat toteuma]
  (let [toteumatehtavat (:toteumatehtavat toteuma)]
    [grid/grid
     {:otsikko  "Tehtävät"
      :tyhja    (if (nil? toteumat)
                  [ajax-loader "Haetaan tehtäviä..."]
                  "Tehtäviä  ei löytynyt")
      :tunniste :id}
     [{:otsikko "Tehtävä" :nimi :nimi :tyyppi :string :leveys 1}
      {:otsikko "Tyyppi" :nimi :toteumatyyppi :tyyppi :string :leveys 1 :hae (fn [_] (name (:toteumatyyppi toteuma)))}
      {:otsikko "Määrä" :nimi :maara :tyyppi :string :leveys 1}
      (when (= (:toteumatyyppi toteuma) :yksikkohintainen)
        {:otsikko     "Toteuma" :nimi :linkki-toteumaan :tyyppi :komponentti :leveys 1
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
      :toimenpide (varustetiedot/varuste-toimenpide->string (keyword (:toimenpide toteuma)))
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
      {:otsikko      "Varustetoteumat"
       :tyhja        (if (nil? toteumat) [ajax-loader "Haetaan toteumia..."] "Toteumia ei löytynyt")
       :tunniste     :id
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
        :hae     (fn [rivi]
                   (or (varustetiedot/tietolaji->selitys (:tietolaji rivi))
                       (:tietolaji rivi)))}
       {:otsikko "Toimenpide" :nimi :toimenpide :tyyppi :string :leveys 15
        :hae     (fn [rivi]
                   (varustetiedot/varuste-toimenpide->string (:toimenpide rivi)))}
       {:otsikko "Tie" :nimi :tie :tyyppi :positiivinen-numero :leveys 10 :tasaa :oikea}
       {:otsikko "Aosa" :nimi :aosa :tyyppi :positiivinen-numero :leveys 5 :tasaa :oikea}
       {:otsikko "Aet" :nimi :aet :tyyppi :positiivinen-numero :leveys 5 :tasaa :oikea}
       {:otsikko "Losa" :nimi :losa :tyyppi :positiivinen-numero :leveys 5 :tasaa :oikea}
       {:otsikko "Let" :nimi :let :tyyppi :positiivinen-numero :leveys 5 :tasaa :oikea}
       {:otsikko "Kuntoluokka" :nimi :kuntoluokka :tyyppi :positiivinen-numero :leveys 10}
       {:otsikko     "Varustekortti" :nimi :varustekortti :tyyppi :komponentti
        :komponentti (fn [rivi] (varustekortti-linkki rivi)) :leveys 10}]
      (take nayta-max-toteumaa valitut-toteumat)]
     (when (> (count valitut-toteumat) nayta-max-toteumaa)
       [:div.alert-warning
        (str "Toteumia löytyi yli " nayta-max-toteumaa ". Tarkenna hakurajausta.")])]))


(defn valinnat [e! valinnat]
  (let [oikeus? (oikeudet/voi-kirjoittaa?
                 oikeudet/urakat-toteumat-varusteet (:id @nav/valittu-urakka))]
    [:span
     ;; Nämä käyttävät suoraan atomeita valintoihin
     [urakka-valinnat/urakan-sopimus]
     [urakka-valinnat/urakan-hoitokausi-ja-kuukausi @nav/valittu-urakka]
     [urakka-valinnat/tienumero (r/wrap (:tienumero valinnat)
                                        #(e! (v/->YhdistaValinnat {:tienumero %})))]

     [harja.ui.valinnat/varustetoteuman-tyyppi
      (r/wrap (:tyyppi valinnat)
              #(e! (v/->ValitseVarusteToteumanTyyppi %)))]
     (when oikeus?
       [napit/uusi "Lisää toteuma"
        #(e! (v/->UusiVarusteToteuma))])]))

(defn varustetoteumalomake [e! varustetoteuma]
  [:span.varustetoteumalomake
   [napit/takaisin "Takaisin toteumaluetteloon"
    #(e! (v/->TyhjennaValittuToteuma))]

   [:div
    "katsot toteumaa: " (pr-str varustetoteuma)]])

(defn- varusteet* [e! varusteet]
  (e! (v/->YhdistaValinnat @varustetiedot/valinnat))
  (komp/luo
   (komp/watcher varustetiedot/valinnat
                 (fn [_ _ uusi]
                   (e! (v/->YhdistaValinnat uusi))))
   (komp/kuuntelija :varustetoteuma-klikattu
                    (fn [_ i] (e! (v/->ValitseToteuma i))))
   (fn [e! {nykyiset-valinnat :valinnat
            toteumat :toteumat
            toteuma :varustetoteuma}]
     [:span
      [kartta/kartan-paikka]
      (if toteuma
        [varustetoteumalomake e! toteuma]
        [:span
         [valinnat e! nykyiset-valinnat]
         [toteumataulukko e! (:tyyppi nykyiset-valinnat) toteumat]])])))

(defn varusteet []
  [tuck varustetiedot/varusteet varusteet*])
