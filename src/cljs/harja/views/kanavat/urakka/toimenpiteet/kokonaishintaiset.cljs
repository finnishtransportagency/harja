(ns harja.views.kanavat.urakka.toimenpiteet.kokonaishintaiset
  (:require [reagent.core :refer [atom]]
            [tuck.core :refer [tuck]]
            [harja.tiedot.kanavat.urakka.toimenpiteet.kokonaishintaiset :as tiedot]
            [harja.tiedot.navigaatio :as navigaatio]
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
            [harja.domain.kanavat.kanavan-toimenpide :as kanavan-toimenpide]
            [harja.views.kanavat.urakka.toimenpiteet :as toimenpiteet-view]
            [harja.ui.debug :as debug]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]
            [harja.ui.yleiset :as yleiset])
  (:require-macros
    [cljs.core.async.macros :refer [go]]
    [harja.makrot :refer [defc fnc]]))

(defn hakuehdot [e! app]
  (let [urakka (get-in app [:valinnat :urakka])]
    [valinnat/urakkavalinnat {:urakka urakka}
     ^{:key "valinnat"}
     [urakka-valinnat/urakan-sopimus-ja-hoitokausi-ja-aikavali-ja-toimenpide urakka]
     ^{:key "toiminnot"}
     [valinnat/urakkatoiminnot {:urakka urakka :sticky? true}
      ^{:key "uusi-nappi"}
      [napit/yleinen-ensisijainen
       "Siirrä valitut muutos- ja lisätöihin"
       (fn [_]
         (e! (tiedot/->SiirraValitut)))
       {:disabled (zero? (count (:valitut-toimenpide-idt app)))}]
      [napit/uusi
       "Uusi toimenpide"
       (fn [_]
         (e! (tiedot/->UusiToimenpide)))]]]))

(defn kokonaishintaiset-toimenpiteet-taulukko [e! {:keys [toimenpiteet] :as app}]
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
     :tyhja "Ei kokonaishitaisia toimenpiteita"
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
    (kanavan-toimenpide/korosta-ei-yksiloidyt toimenpiteet)]])

(defn lomake-toiminnot [e! toimenpide]
  [:div
   [napit/tallenna
    "Tallenna"
    #(e! (tiedot/->TallennaToimenpide toimenpide))
    {:tallennus-kaynnissa? tallennus-kaynnissa?
     :disabled (not (lomake/voi-tallentaa? toimenpide))}]
   (when (not (nil? (::kanavan-toimenpide/id toimenpide)))
     [napit/poista
      "Poista"
      #(varmista-kayttajalta/varmista-kayttajalta
         {:otsikko "Toimenpiteen poistaminen"
          :sisalto [:div "Haluatko varmasti poistaa toimenpiteen?"]
          :hyvaksy "Poista"
          :toiminto-fn (fn [] (e! (tiedot/->PoistaToimenpide toimenpide)))})])])

(defn kokonaishintainen-toimenpidelomake [e! {:keys [avattu-toimenpide
                                                     kohteet
                                                     toimenpideinstanssit
                                                     tehtavat
                                                     huoltokohteet
                                                     tallennus-kaynnissa?]
                                              :as app}]
  (let [urakka (get-in app [:valinnat :urakka])
        sopimukset (:sopimukset urakka)
        lomake-valmis? (not (empty? huoltokohteet))]
    [:div
     [napit/takaisin "Takaisin varusteluetteloon"
      #(e! (tiedot/->TyhjennaAvattuToimenpide))]
     (if lomake-valmis?
       [lomake/lomake
        {:otsikko "Uusi toimenpide"
         :muokkaa! #(e! (tiedot/->AsetaLomakkeenToimenpiteenTiedot %))
         :footer-fn (fn [toimenpide] (lomake-toiminnot e! toimenpide))}
        (toimenpiteet-view/toimenpidelomakkeen-kentat {:avattu-toimenpide avattu-toimenpide
                                                       :sopimukset sopimukset
                                                       :kohteet kohteet
                                                       :huoltokohteet huoltokohteet
                                                       :toimenpideinstanssit toimenpideinstanssit
                                                       :tehtavat tehtavat})
        avattu-toimenpide]
       [ajax-loader "Ladataan..."])]))

(defn kokonaishintaiset-nakyma [e! {:keys [avattu-toimenpide]
                                    :as app}]
  [:div
   (if avattu-toimenpide
     [kokonaishintainen-toimenpidelomake e! app]
     [:div
      [hakuehdot e! app]
      [kokonaishintaiset-toimenpiteet-taulukko e! app]])
   [debug/debug app]])

(defn kokonaishintaiset* [e! _]
  (komp/luo
    (komp/watcher tiedot/valinnat (fn [_ _ uusi] (e! (tiedot/->PaivitaValinnat uusi))))
    (komp/sisaan-ulos #(e! (tiedot/->NakymaAvattu)) #(e! (tiedot/->NakymaSuljettu)))
    (fn [e! app]
      ;; Reaktio on pakko lukea komponentissa, muuten se ei päivity!
      @tiedot/valinnat
      [:span
       [kokonaishintaiset-nakyma e! app]])))

(defc kokonaishintaiset []
      [tuck tiedot/tila kokonaishintaiset*])
