(ns harja.views.urakka.laadunseuranta.laatupoikkeamat
  "Listaa urakan laatupoikkeamat."
  (:require [reagent.core :refer [atom] :as r]
            [harja.tiedot.urakka.laadunseuranta.laatupoikkeamat :as laatupoikkeamat]
            [harja.tiedot.urakka.laadunseuranta.laatupoikkeamat-kartalla :as lp-kartalla]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka :as u]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.komponentti :as komp]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.napit :as napit]
            [harja.domain.laadunseuranta :refer [validi-laatupoikkeama?]]
            [harja.views.urakka.laadunseuranta.laatupoikkeama :refer [laatupoikkeamalomake]]
            [cljs.core.async :refer [<!]]
            [harja.views.kartta :as kartta]
            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta]
            [harja.domain.tierekisteri :as tierekisteri]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.kartta :as kartta-tiedot]
            [harja.ui.debug :as debug]
            [harja.fmt :as fmt]
            [harja.domain.yllapitokohde :as yllapitokohde-domain]
            [harja.domain.urakka :as urakka]
            [harja.ui.valinnat :as valinnat])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defn laatupoikkeamalistaus
  "Listaa urakan laatupoikkeamat"
  [e! app optiot]
  (let [hoitokaudet (u/hoito-tai-sopimuskaudet (-> @tila/yleiset :urakka))
        app (assoc app :urakan-hoitokaudet hoitokaudet)
        poikkeamat (when (:laatupoikkeamat app)
                     (reverse (sort-by :aika (:laatupoikkeamat app))))
        nakyma (:nakyma optiot)
        paikallinen-aikavali-atom (atom nil)
        urakka-id (-> @tila/yleiset :urakka :id)]
    [:div.laatupoikkeamat
     [debug/debug app]
     ;; Wrapataan filtterit urakkavalinnat elementin sisään
     [valinnat/urakkavalinnat {:urakka urakka-id}
      ^{:key "urakkavalinnat"}
      [valinnat/urakan-hoitokausi-tuck (:valittu-hoitokausi app)
       (:urakan-hoitokaudet app)
       #(e! (laatupoikkeamat/->HoitokausiVaihdettu urakka-id %))
       {:wrapper-luokka "inline-block"}]
      [yleiset/pudotusvalikko
       "Näytä laatupoikkeamat"
       {:valinta @laatupoikkeamat/listaus
        :valitse-fn #(do
                       (e! (laatupoikkeamat/->PaivitaListausTyyppi % (:valittu-aikavali app) urakka-id))
                       (reset! laatupoikkeamat/listaus %))
        :format-fn #(case %
                      :kaikki "Kaikki"
                      :kasitellyt "Käsitellyt (päätös tehty)"
                      :selvitys "Odottaa urakoitsijan selvitystä"
                      :omat "Minun kirjaamat / kommentoimat"
                      :poikkeamaraportilliset "Poikkeamaraportilliset")
        :vayla-tyyli? true}
       [:kaikki :selvitys :kasitellyt :omat :poikkeamaraportilliset]]
      ^{:key (hash (:valittu-aikavali app))}
      [valinnat/aikavali
       (r/wrap (:valittu-aikavali app)
         (fn [arvo]
           (do
             ;; Tätä kutsutaan komponentin luonteesta johtuen kahdesti (aikaväli input laatikoita on 2)
             ;; Joten ei päivitetä mitään, jos arvo ei vaihdu.
             (when-not (= arvo @paikallinen-aikavali-atom)
               (reset! paikallinen-aikavali-atom arvo)
               (e! (laatupoikkeamat/->PaivitaAikavali arvo (:listaus-tyyppi app) urakka-id))))))
       {:otsikko "Aikaväli"
        :luokka #{"label-ja-aikavali " "ei-tiukkaa-leveytta "}
        :ikoni-sisaan? true
        :vayla-tyyli? true}]

      ^{:key "urakkatoiminnot"}
      [valinnat/urakkatoiminnot {:urakka urakka-id}
       (let [oikeus? @laatupoikkeamat/voi-kirjata?]
         (yleiset/wrap-if
           (not oikeus?)
           [yleiset/tooltip {} :%
            (oikeudet/oikeuden-puute-kuvaus :kirjoitus
                                            oikeudet/urakat-laadunseuranta-laatupoikkeamat)]
           ^{:key "uusi-laatupoikkeama"}
           [napit/uusi "Uusi laatupoikkeama"
            #(reset! laatupoikkeamat/valittu-laatupoikkeama-id :uusi)
            {:disabled (not oikeus?)}]))]]

     [grid/grid
      {:otsikko "Laatu\u00ADpoikkeamat" :rivi-klikattu #(reset! laatupoikkeamat/valittu-laatupoikkeama-id (:id %))
       :tyhja (if (nil? poikkeamat)
                [yleiset/ajax-loader "Laatupoikkeamia ladataan"]
                "Ei laatupoikkeamia")}
      [{:otsikko "Päivä\u00ADmäärä" :nimi :aika :fmt pvm/pvm-aika :leveys 1}
       (when (or (= :paallystys nakyma)
                 (= :paikkaus nakyma)
                 (= :tiemerkinta nakyma))
         {:otsikko "Yllä\u00ADpito\u00ADkoh\u00ADde" :nimi :kohde :leveys 2
          :hae (fn [rivi]
                 (yllapitokohde-domain/yllapitokohde-tekstina {:kohdenumero (get-in rivi [:yllapitokohde :numero])
                                                               :nimi (get-in rivi [:yllapitokohde :nimi])}))})
       (when (= :hoito nakyma)
         {:otsikko "Koh\u00ADde" :nimi :kohde :leveys 1})
       (when-not (urakka/vesivaylaurakkatyyppi? nakyma)
         {:otsikko "TR-osoite"
          :nimi :tr
          :leveys 2
          :fmt tierekisteri/tierekisteriosoite-tekstina})
       {:otsikko "Kuvaus" :nimi :kuvaus :leveys 3}
       {:otsikko "Tekijä" :nimi :tekija :leveys 1 :fmt laatupoikkeamat/kuvaile-tekija}
       {:otsikko "Päätös" :nimi :paatos :fmt laatupoikkeamat/kuvaile-paatos :leveys 2}
       {:otsikko "Poik\u00ADkeama\u00ADraport\u00ADti" :nimi :sisaltaa-poikkeamaraportin? :fmt fmt/totuus :tasaa :keskita :leveys 1}]
      poikkeamat]]))

(defn paatos?
  "Onko annetussa laatupoikkeamassa päätös?"
  [laatupoikkeama]
  (not (nil? (get-in laatupoikkeama [:paatos :paatos]))))

(defn- vastaava-laatupoikkeama [lp]
  (some (fn [haettu-lp] (when (= (:id haettu-lp) (:id lp)) haettu-lp)) (:laatupoikkeamat @tila/laatupoikkeamat)))

(defn laatupoikkeamat [e! app optiot]
  (komp/luo
    (komp/lippu lp-kartalla/karttataso-laatupoikkeamat)
    (komp/kuuntelija :laatupoikkeama-klikattu #(reset! laatupoikkeamat/valittu-laatupoikkeama-id (:id %2)))
    (komp/ulos (kartta-tiedot/kuuntele-valittua! laatupoikkeamat/valittu-laatupoikkeama))
    (komp/sisaan-ulos #(do
                         (e! (laatupoikkeamat/->HaeLaatupoikkeamat (-> @tila/yleiset :urakka :id) :kaikki (:valittu-hoitokausi app)))
                         (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                         (kartta-tiedot/kasittele-infopaneelin-linkit!
                           {:laatupoikkeama {:toiminto (fn [lp]
                                                         (reset! laatupoikkeamat/valittu-laatupoikkeama-id
                                                                 (:id (vastaava-laatupoikkeama lp))))
                                             :teksti "Valitse laatupoikkeama"}})
                         (nav/vaihda-kartan-koko! :M))
                      #(do (nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko)
                           (kartta-tiedot/kasittele-infopaneelin-linkit! nil)))
    (fn [e! app optiot]
      [:span.laatupoikkeamat
       [kartta/kartan-paikka]
       (if @laatupoikkeamat/valittu-laatupoikkeama
         [laatupoikkeamalomake e! laatupoikkeamat/valittu-laatupoikkeama
          (merge optiot
                 {:yllapitokohteet @laadunseuranta/urakan-yllapitokohteet-lomakkeelle
                  :nakyma (:tyyppi @nav/valittu-urakka)})]
         [laatupoikkeamalistaus e! app optiot])])))
