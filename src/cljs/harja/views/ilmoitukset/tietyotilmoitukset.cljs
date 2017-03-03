(ns harja.views.ilmoitukset.tietyotilmoitukset
  (:require [reagent.core :refer [atom] :as r]
            [clojure.string :as s]
            [harja.tiedot.ilmoitukset.tietyotilmoitukset :as tiedot]
            [harja.ui.bootstrap :as bs]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :refer [grid]]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.napit :refer [palvelinkutsu-nappi] :as napit]
            [harja.ui.valinnat :refer [urakan-hoitokausi-ja-aikavali]]
            [harja.ui.lomake :as lomake]
            [harja.ui.protokollat :as protokollat]
            [harja.ui.debug :as ui-debug]
            [harja.loki :refer [tarkkaile! log]]
            [cljs.pprint :refer [pprint]]
            [harja.tiedot.istunto :as istunto]
            [harja.fmt :as fmt]
            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm]
            [harja.views.kartta :as kartta]
            [harja.ui.ikonit :as ikonit]
            [harja.domain.tierekisteri :as tr-domain]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.notifikaatiot :as notifikaatiot]
            [tuck.core :refer [tuck send-value! send-async!]]
            [harja.ui.kentat :as kentat]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.kartta :as kartta-tiedot])
  (:require-macros
                   [cljs.core.async.macros :refer [go]]))

(defn aikavalivalitsin [valinnat-nyt]
  (let [alkuaika (:alkuaika valinnat-nyt)
        alkuaikakentta {:nimi :alkuaika
                        :otsikko "Alku"
                        :tyyppi :pvm-aika
                        :validoi [[:ei-tyhja "Anna alkuaika"]]}
        loppuaikakentta {:nimi :loppuaika
                         :otsikko "Loppu"
                         :tyyppi :pvm-aika
                         :validoi [[:ei-tyhja "Anna loppuaika"]
                                   [:pvm-toisen-pvmn-jalkeen alkuaika "Loppuajan on oltava alkuajan jälkeen"]]}]
    (lomake/ryhma
     {:rivi? true}
     alkuaikakentta
     loppuaikakentta)))

(defn ilmoitusten-hakuehdot [e! valinnat-nyt]
  (log "---> renskaillaan hakuehdot. valinnat nyt: " (pr-str (:alkuaika valinnat-nyt))
       (pr-str (:loppuaika valinnat-nyt))) ;; nil nil toisella kerralla, ekalla ok
  [lomake/lomake
   {:luokka :horizontal
    :muokkaa! #(e! (tiedot/->AsetaValinnat %))}

   [(aikavalivalitsin valinnat-nyt)]
   valinnat-nyt])

(defn ilmoitusten-paanakyma
  [e! {valinnat-nyt :valinnat
       haetut-ilmoitukset :ilmoitukset
       ilmoituksen-haku-kaynnissa? :ilmoituksen-haku-kaynnissa? :as ilmoitukset}]
  (log "renskaillaan paanakyma, valinnat" (pr-str valinnat-nyt))
  [:span.tietyoilmoitukset

   [ilmoitusten-hakuehdot e! valinnat-nyt]
   [:div
    [grid
     {:tyhja (if haetut-ilmoitukset
               "Ei löytyneitä tietoja"
               [ajax-loader "Haetaan ilmoituksia"])
      :rivi-klikattu (when-not ilmoituksen-haku-kaynnissa? #(e! (tiedot/->ValitseIlmoitus %)))
      :piilota-toiminnot true
      :max-rivimaara 500
      :max-rivimaaran-ylitys-viesti "Yli 500 ilmoitusta. Tarkenna hakuehtoja."}

     [
      {:otsikko "Urakka" :nimi :urakka_nimi :leveys 5
       :hae (comp fmt/lyhennetty-urakan-nimi :urakka_nimi)}
      {:otsikko "Tie" :nimi :tie
       :hae #(str (:tr_numero % "(ei tien numeroa)") " " (:tien_nimi % "(ei tien nimeä)"))
       :leveys 4}
      {:otsikko "Alkupvm" :nimi :alku
       :hae (comp pvm/pvm-aika :alku)

       :leveys 2}
      {:otsikko "Loppupvm" :nimi :loppu
       :hae (comp pvm/pvm-aika :loppu) :leveys 2}
      {:otsikko "Työn tyyppi" :nimi :tyotyypit
       :hae #(s/join ", " (->> % :tyotyypit (map :tyyppi)))
       :leveys 4}
      {:otsikko "Ilmoittaja" :nimi :ilmoittaja
       :hae #(str (:ilmoittaja_etunimi %) " " (:ilmoittaja_sukunimi %))
       :leveys 7}]
     haetut-ilmoitukset]]])

(defn ilmoituksen-tiedot [e! ilmoitus]
  [:div
   [:span
    [napit/takaisin "Listaa ilmoitukset" #(e! (tiedot/->PoistaIlmoitusValinta))]
    [:div "Tässä ois leikisti ilmoituksen tiedot"]
    #_[it/ilmoitus e! ilmoitus]]])

(defn ilmoitukset* [e! ilmoitukset]
  (log "z 2")
  (e! (tiedot/->YhdistaValinnat @tiedot/ulkoisetvalinnat))
  (komp/luo
   (komp/lippu tiedot/karttataso-ilmoitukset)
   (komp/kuuntelija :ilmoitus-klikattu (fn [_ i] (e! (tiedot/->ValitseIlmoitus i))))
   (komp/sisaan-ulos #(do
                         (notifikaatiot/pyyda-notifikaatiolupa)
                         (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                         (nav/vaihda-kartan-koko! :M)
                         (kartta-tiedot/kasittele-infopaneelin-linkit!
                           {:ilmoitus {:toiminto (fn [ilmoitus-infopaneelista]
                                                   (e! (tiedot/->ValitseIlmoitus ilmoitus-infopaneelista)))
                                       :teksti "Valitse ilmoitus"}}))
                      #(do
                         (kartta-tiedot/kasittele-infopaneelin-linkit! nil)
                         (nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko)))
   (fn [e! {valittu-ilmoitus :valittu-ilmoitus :as ilmoitukset}]
     [:span
      #_[ui-debug/debug {:ilmoitukset @tiedot/ilmoitukset
                       ;;:tiedot/ulkoisetvalinnat @tiedot/ulkoisetvalinnat
                       }]
      [ui-debug/debug  @tiedot/ilmoitukset]
      [kartta/kartan-paikka]
      (if valittu-ilmoitus
        [ilmoituksen-tiedot e! valittu-ilmoitus]
        [ilmoitusten-paanakyma e! ilmoitukset])])))
