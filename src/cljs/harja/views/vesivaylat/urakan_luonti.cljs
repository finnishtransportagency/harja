(ns harja.views.vesivaylat.urakan-luonti
  (:require [reagent.core :refer [atom] :as r]
            [harja.tiedot.vesivaylat.urakan-luonti :as tiedot]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :refer [grid]]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni]]
            [harja.ui.valinnat :refer [urakan-hoitokausi-ja-aikavali]]
            [harja.loki :refer [tarkkaile! log]]
            [cljs.pprint :refer [pprint]]
            [tuck.core :refer [tuck send-value! send-async!]]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.lomake :as lomake]
            [harja.domain.oikeudet :as oikeudet]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm])
  (:require-macros
    [cljs.core.async.macros :refer [go]]
    [harja.makrot :refer [defc fnc]]))

(defc luontilomake [e! {:keys [valittu-urakka tallennus-kaynnissa?] :as app}]
  [:div
   [napit/takaisin "Takaisin luetteloon"
    #(e! (tiedot/->ValitseUrakka nil))
    {:disabled tallennus-kaynnissa?}]
   [lomake/lomake
    {:otsikko (if (:id valittu-urakka)
                "Muokkaa urakkaa"
                "Luo uusi urakka")
     :muokkaa! #(e! (tiedot/->UrakkaaMuokattu %))
     :voi-muokata? (constantly true) #_(oikeudet/voi-kirjoittaa? oikeudet/hallinta-vesivaylaurakoiden-luonti)
     :footer-fn (fn [urakka]
                  [napit/tallenna
                   "Tallenna urakka"
                   #(e! (tiedot/->TallennaUrakka (lomake/ilman-lomaketietoja urakka)))
                   {:ikoni (ikonit/tallenna)
                    :disabled (or tallennus-kaynnissa?
                                  (not (lomake/voi-tallentaa? urakka)))
                    :tallennus-kaynnissa? tallennus-kaynnissa?
                    }])}
    [{:otsikko "Nimi" :nimi :nimi :tyyppi :string}]
    valittu-urakka]])

(defn urakkagrid [e! app]
  (komp/luo
    (komp/sisaan #(e! (tiedot/->HaeUrakat)))
    (fn [e! {:keys [haetut-urakat urakoiden-haku-kaynnissa?] :as app}]
      [:div
      [napit/uusi "Lisää urakka"
       #(e! (tiedot/->UusiUrakka))
       {:disabled (nil? haetut-urakat)}]
      [grid
       {:otsikko (if (and (some? haetut-urakat) urakoiden-haku-kaynnissa?)
                   [ajax-loader-pieni "Päivitetään listaa"] ;; Listassa on jo jotain, mutta sitä päivitetään
                   "Harjaan perustetut vesiväyläurakat")
        :tunniste :id
        :tyhja (if (nil? haetut-urakat)
                 [ajax-loader "Haetaan urakoita"]
                 "Urakoita ei löytynyt")
        :rivi-klikattu #(e! (tiedot/->ValitseUrakka %))}
       [{:otsikko "Nimi" :nimi :nimi}
        {:otsikko "Hallintayksikko" :nimi :hallintayksikon-nimi :hae #(get-in % [:hallintayksikko :nimi])}
        {:otsikko "Hanke" :nimi :hankkeen-nimi :hae #(get-in % [:hanke :nimi])}
        {:otsikko "Sopimukset (kpl)" :nimi :sopimukset-lkm :hae #(count (get % :sopimukset))}
        {:otsikko "Alku" :nimi :alkupvm :tyyppi :pvm :fmt pvm/pvm}
        {:otsikko "Loppu" :nimi :loppupvm :tyyppi :pvm :fmt pvm/pvm}]
       haetut-urakat]])))

(defn vesivaylaurakoiden-luonti* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(e! (tiedot/->Nakymassa? true))
                      #(e! (tiedot/->Nakymassa? false)))

    (fn [e! {valittu-urakka :valittu-urakka :as app}]
      (if valittu-urakka
        [luontilomake e! app]
        [urakkagrid e! app]))))

(defc vesivaylaurakoiden-luonti []
  [tuck tiedot/tila vesivaylaurakoiden-luonti*])