(ns harja.views.kanavat.urakka.toimenpiteet.kokonaishintaiset
  (:require [reagent.core :refer [atom]]
            [tuck.core :refer [tuck]]
            [harja.tiedot.kanavat.urakka.toimenpiteet.kokonaishintaiset :as tiedot]
            [harja.tiedot.navigaatio :as navigaatio]
            [harja.tiedot.kanavat.urakka.kanavaurakka :as kanavaurakka]
            [harja.tiedot.urakka :as urakkatiedot]
            [harja.loki :refer [tarkkaile! log]]
            [harja.ui.lomake :as lomake]
            [harja.ui.debug :as debug]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.ui.napit :as napit]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni tietoja]]
            [harja.ui.debug :refer [debug]]
            [harja.ui.valinnat :as valinnat]
            [harja.domain.urakka :as urakka-domain]
            [harja.domain.kanavat.kanavan-toimenpide :as kanavan-toimenpide]
            [harja.domain.vesivaylat.materiaali :as materiaali]
            [harja.views.kanavat.urakka.toimenpiteet :as toimenpiteet-view]
            [harja.views.kartta :as kartta]
            [harja.views.kartta.tasot :as tasot]
            [harja.ui.debug :as debug]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]
            [harja.ui.yleiset :as yleiset]
            [harja.domain.kanavat.kohde :as kohde]
            [reagent.core :as r]
            [taoensso.timbre :as log]
            [harja.tiedot.kartta :as kartta-tiedot])
  (:require-macros
    [cljs.core.async.macros :refer [go]]
    [harja.makrot :refer [defc fnc]]))

(defn hakuehdot [e! app kohteet]
  (let [urakka-map (get-in app [:valinnat :urakka])]
    [valinnat/urakkavalinnat {:urakka urakka-map}
     ^{:key "valinnat"}
     [:div
      [urakka-valinnat/urakan-sopimus-ja-hoitokausi-ja-aikavali-ja-toimenpide urakka-map]
      [valinnat/kanava-kohde
       (r/wrap (first (filter #(= (::kohde/id %) (get-in app [:valinnat :kanava-kohde-id])) kohteet))
               (fn [uusi]
                 (e! (tiedot/->PaivitaValinnat {:kanava-kohde-id (::kohde/id uusi)}))))
       (into [nil] kohteet)
       #(let [nimi (kohde/fmt-kohteen-nimi %)]
          (if (empty? nimi) "Kaikki" nimi))]]
     ^{:key "toiminnot"}
     [valinnat/urakkatoiminnot {:urakka urakka-map :sticky? true}
      
      ; Piilotetaan nappi kanavaurakoilta
      (when (not (urakka-domain/kanavaurakka? urakka-map))
        ^{:key "uusi-nappi"}
        [napit/yleinen-ensisijainen
         "Siirrä valitut muutos- ja lisätöihin"
         (fn [_]
           (e! (tiedot/->SiirraValitut)))
         {:disabled (zero? (count (:valitut-toimenpide-idt app)))}])
      [napit/uusi
       "Uusi toimenpide"
       (fn [_]
         (e! (tiedot/->UusiToimenpide)))]]]))

(defn kokonaishintaiset-toimenpiteet-taulukko [e! {:keys [toimenpiteet haku-kaynnissa?] :as app}]
  [:div
   [toimenpiteet-view/ei-yksiloity-vihje]
   [grid/grid
    {:otsikko "Kokonaishintaiset toimenpiteet"
     :voi-lisata? false
     :voi-muokata? false
     :voi-poistaa? false
     :voi-kumota? false
     :piilota-toiminnot? true
     :rivi-klikattu (fn [rivi] (e! (tiedot/->AsetaLomakkeenToimenpiteenTiedot rivi)))
     :tyhja (if haku-kaynnissa? [ajax-loader "Haetaan toimenpiteitä"] "Ei toimenpiteitä")
     :jarjesta ::kanavan-toimenpide/pvm
     :tunniste ::kanavan-toimenpide/id}
    (toimenpiteet-view/toimenpidesarakkeet
      e! app
      {:kaikki-valittu?-fn #(= (count (:toimenpiteet app))
                               (count (:valitut-toimenpide-idt app)))
       :otsikko-valittu-fn (fn [uusi-arvo]
                             (e! (tiedot/->ValitseToimenpiteet
                                   {:kaikki-valittu? uusi-arvo})))
       :rivi-valittu?-fn (fn [rivi]
                           (boolean ((:valitut-toimenpide-idt app)
                                      (::kanavan-toimenpide/id rivi))))
       :rivi-valittu-fn (fn [rivi uusi-arvo]
                          (e! (tiedot/->ValitseToimenpide
                                {:id (::kanavan-toimenpide/id rivi)
                                 :valittu? uusi-arvo})))})
    (sort-by ::kanavan-toimenpide/pvm >
             (kanavan-toimenpide/korosta-ei-yksiloidyt toimenpiteet))]])



(defn kokonaishintainen-toimenpidelomake [e! {:keys [avattu-toimenpide kohteet toimenpideinstanssit
                                                     tehtavat huoltokohteet tallennus-kaynnissa?] :as app}]
  [toimenpiteet-view/toimenpidelomake app {:tyhjenna-fn #(e! (tiedot/->TyhjennaAvattuToimenpide))
                                           :aseta-toimenpiteen-tiedot-fn #(e! (tiedot/->AsetaLomakkeenToimenpiteenTiedot %))
                                           :tallenna-lomake-fn #(e! (tiedot/->TallennaToimenpide % false))
                                           :poista-toimenpide-fn #(e! (tiedot/->PoistaToimenpide %))
                                           :paikannus-kaynnissa-fn #(e! (tiedot/->KytkePaikannusKaynnissa))
                                           :lisaa-materiaali-fn #(e! (tiedot/->LisaaMateriaali))
                                           :muokkaa-materiaaleja-fn #(e! (tiedot/->MuokkaaMateriaaleja %))
                                           :lisaa-virhe-fn #(e! (tiedot/->LisaaVirhe %))}])

(defn kokonaishintaiset-nakyma [e! {:keys [avattu-toimenpide] :as app} kohteet]
  (let [nakyma-voidaan-nayttaa? (some? kohteet)]
    (if nakyma-voidaan-nayttaa?
      [:span
       [kartta/kartan-paikka]
       [:div
        (if avattu-toimenpide
          [kokonaishintainen-toimenpidelomake e! app]
          [:div
           [hakuehdot e! app kohteet]
           [kokonaishintaiset-toimenpiteet-taulukko e! app]])
        [debug/debug app]]]
      [ajax-loader "Ladataan..."])))

(defn kokonaishintaiset* [e! _]
  (komp/luo
    (komp/watcher tiedot/valinnat (fn [_ _ uusi] (e! (tiedot/->PaivitaValinnat uusi))))
    (komp/sisaan-ulos #(do
                         (tasot/taso-paalle! :kan-kohteet)
                         (tasot/taso-paalle! :kan-toimenpiteet)
                         (tasot/taso-pois! :organisaatio)
                         (kartta-tiedot/kasittele-infopaneelin-linkit!
                           {:kan-toimenpide {:toiminto (fn [t]
                                                         (e! (tiedot/->AsetaLomakkeenToimenpiteenTiedot t))
                                                         (kartta-tiedot/piilota-infopaneeli!))
                                             :teksti "Avaa toimenpide"}})
                         (e! (tiedot/->NakymaAvattu)))
                      #(do
                         (tasot/taso-pois! :kan-kohteet)
                         (tasot/taso-pois! :kan-toimenpiteet)
                         (tasot/taso-paalle! :organisaatio)
                         (kartta-tiedot/kasittele-infopaneelin-linkit! nil)
                         (e! (tiedot/->NakymaSuljettu))))
    (fn [e! app]
      ;; Reaktio on pakko lukea komponentissa, muuten se ei päivity!
      @tiedot/valinnat

      [kokonaishintaiset-nakyma e! app @kanavaurakka/kanavakohteet])))

(defc kokonaishintaiset []
      [tuck tiedot/tila kokonaishintaiset*])
