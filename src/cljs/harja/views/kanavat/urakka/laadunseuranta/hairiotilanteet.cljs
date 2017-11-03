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
            [harja.domain.kanavat.kanavan-kohde :as kkohde]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.valinnat :as valinnat]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.napit :as napit]
            [harja.fmt :as fmt]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.domain.kanavat.hairiotilanne :as hairio]
            [harja.ui.debug :as debug])
  (:require-macros
    [cljs.core.async.macros :refer [go]]
    [harja.makrot :refer [defc fnc]]
    [harja.tyokalut.ui :refer [for*]]))

(defn- suodattimet-ja-toiminnot [valittu-urakka]
  [valinnat/urakkavalinnat {:urakka valittu-urakka}
   ^{:key "urakkavalinnat"}
   ;; TODO Lisää filttereitä
   [urakka-valinnat/urakan-sopimus-ja-hoitokausi-ja-aikavali
    valittu-urakka {:sopimus {:optiot {:kaikki-valinta? true}}}]
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
   [{:otsikko "Päivä\u00ADmäärä" :nimi ::hairiotilanne/pvm :tyyppi :pvm :fmt pvm/pvm-opt :leveys 4}
    {:otsikko "Kohde" :nimi ::hairiotilanne/kohde :tyyppi :string
     :fmt kkohde/fmt-kohde-ja-kanava :leveys 10}
    {:otsikko "Vika\u00ADluokka" :nimi ::hairiotilanne/vikaluokka :tyyppi :string :leveys 6
     :fmt hairio/fmt-vikaluokka}
    {:otsikko "Syy" :nimi ::hairiotilanne/syy :tyyppi :string :leveys 6}
    {:otsikko "Odo\u00ADtus\u00ADaika (h)" :nimi ::hairiotilanne/odotusaika-h :tyyppi :numero :leveys 2}
    {:otsikko "Am\u00ADmat\u00ADti\u00ADlii\u00ADkenne lkm" :nimi ::hairiotilanne/ammattiliikenne-lkm :tyyppi :numero :leveys 2}
    {:otsikko "Hu\u00ADvi\u00ADlii\u00ADkenne lkm" :nimi ::hairiotilanne/huviliikenne-lkm :tyyppi :numero :leveys 2}
    {:otsikko "Kor\u00ADjaus\u00ADtoimenpide" :nimi ::hairiotilanne/korjaustoimenpide :tyyppi :string :leveys 10}
    {:otsikko "Kor\u00ADjaus\u00ADaika" :nimi ::hairiotilanne/korjausaika-h :tyyppi :numero :leveys 2}
    {:otsikko "Kor\u00ADjauk\u00ADsen tila" :nimi ::hairiotilanne/korjauksen-tila :tyyppi :string :leveys 3
     :fmt hairio/fmt-korjauksen-tila}
    {:otsikko "Paikal\u00ADlinen käyt\u00ADtö" :nimi ::hairiotilanne/paikallinen-kaytto
     :tyyppi :string :fmt fmt/totuus :leveys 2}]
   hairiotilanteet])

(defn hairiotilanteet* [e! app]
  (komp/luo
    (komp/watcher tiedot/valinnat (fn [_ _ uusi]
                                    (e! (tiedot/->PaivitaValinnat uusi))))
    (komp/sisaan-ulos #(do (e! (tiedot/->Nakymassa? true))
                           (e! (tiedot/->PaivitaValinnat
                                 {:urakka @nav/valittu-urakka
                                  :sopimus-id (first @u/valittu-sopimusnumero)})))
                      #(e! (tiedot/->Nakymassa? false)))

    (fn [e! app]
      [:div
       [suodattimet-ja-toiminnot (get-in app [:valinnat :urakka])]
       [hairiolista e! app]])))

(defc hairiotilanteet []
  [tuck tiedot/tila hairiotilanteet*])

