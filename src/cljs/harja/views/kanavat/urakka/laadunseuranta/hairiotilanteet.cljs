(ns harja.views.kanavat.urakka.laadunseuranta.hairiotilanteet
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :refer [tuck]]

            [harja.tiedot.kanavat.urakka.laadunseuranta.hairiotilanteet :as tiedot]
            [harja.loki :refer [tarkkaile! log]]
            [harja.pvm :as pvm]
            [harja.id :refer [id-olemassa?]]

            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.ui.lomake :as lomake]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni tietoja]]
            [harja.ui.debug :refer [debug]]
            [harja.ui.modal :as modal]

            [harja.domain.kanavat.hairiotilanne :as hairiotilanne]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.valinnat :as valinnat]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.napit :as napit]
            [harja.fmt :as fmt])
  (:require-macros
    [cljs.core.async.macros :refer [go]]
    [harja.makrot :refer [defc fnc]]
    [harja.tyokalut.ui :refer [for*]]))

(defn- suodattimet-ja-toiminnot [valittu-urakka]
  [valinnat/urakkavalinnat {:urakka valittu-urakka}
   ^{:key "urakkavalinnat"}
   ;; TODO Lisää filttereitä
   [urakka-valinnat/urakan-hoitokausi valittu-urakka]
   ^{:key "urakkatoiminnot"}
   [valinnat/urakkatoiminnot {:urakka valittu-urakka}
    (let [oikeus? true ;; TODO Oikeustarkistus, roolit-excelin päivitys
          #_(oikeudet/voi-kirjoittaa? oikeudet/urakat-laadunseuranta-sanktiot
                                            (:id valittu-urakka))]
      (yleiset/wrap-if
        (not oikeus?)
        [yleiset/tooltip {} :%
         (oikeudet/oikeuden-puute-kuvaus :kirjoitus
                                         ;; TODO Oikea oikeustarkistus
                                         oikeudet/urakat-laadunseuranta-sanktiot)]
        ^{:key "Lisää sanktio"}
        [napit/uusi "Lisää häiriötilanne"
         #(log "TODO Lisää häiriötilanne")
         {:disabled (not oikeus?)}]))]])

(defn- hairiolista [e! {:keys [hairiotilanteet hairiotilanteiden-haku-kaynnissa?] :as app}]
  [grid/grid
   {:otsikko (if (and (some? hairiotilanteet) hairiotilanteiden-haku-kaynnissa?)
               [ajax-loader-pieni "Päivitetään listaa"]
               "Häiriötilanteet")
    :tunniste ::hairiotilanne/id
    :tyhja (if (nil? hairiotilanteet)
             [ajax-loader "Haetaan häiriötilanteita"]
             "Häiriötilanteita ei löytynyt")}
   [{:otsikko "Päivämäärä" :nimi ::hairiotilanne/pvm :tyyppi :pvm}
    {:otsikko "Kohde" :nimi ::hairiotilanne/kohde :tyyppi :string}
    {:otsikko "Vikaluokka" :nimi ::hairiotilanne/vikaluokka :tyyppi :string}
    {:otsikko "Syy" :nimi ::hairiotilanne/syy :tyyppi :string}
    {:otsikko "Odotusaika (h)" :nimi ::hairiotilanne/odotusaika :tyyppi :numero}
    {:otsikko "Ammattiliikenne lkm" :nimi ::hairiotilanne/ammattiliikenne :tyyppi :numero}
    {:otsikko "Huviliikenne lkm" :nimi ::hairiotilanne/huviliikenne :tyyppi :numero}
    {:otsikko "Korjaustoimenpide" :nimi ::hairiotilanne/korjaustoimenpide :tyyppi :string}
    {:otsikko "Korjausaika" :nimi ::hairiotilanne/korjausaika :tyyppi :numero}
    {:otsikko "Korjauksen tila" :nimi ::hairiotilanne/korjauksen-tila :tyyppi :string}
    {:otsikko "Paikallinen käyttö" :nimi ::hairiotilanne/paikallinen-kaytto
     :tyyppi :string :fmt fmt/totuus}]
   hairiotilanteet])

(defn hairiotilanteet* [e! app]
  (komp/luo
    (komp/watcher tiedot/valinnat (fn [_ _ uusi]
                                    (e! (tiedot/->PaivitaValinnat uusi))))
    (komp/sisaan-ulos #(e! (tiedot/->Nakymassa? true))
                      #(e! (tiedot/->Nakymassa? false)))

    (fn [e! app]
      [:div
       [suodattimet-ja-toiminnot (get-in app [:valinnat :urakka-id])]
       [hairiolista e! app]])))

(defc hairiotilanteet []
  [tuck tiedot/tila hairiotilanteet*])

