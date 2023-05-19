(ns harja.views.tieluvat.tielupa-nakyma
  (:require
    [tuck.core :refer [tuck]]
    [harja.loki :refer [log]]
    [clojure.string :as str]
    [harja.pvm :as pvm]
    [reagent.core :as r :refer [atom]]
    [harja.domain.tielupa :as tielupa]
    [harja.tiedot.tieluvat.tielupa-tiedot :as tiedot]
    [harja.tiedot.kartta :as kartta-tiedot]
    [harja.ui.komponentti :as komp]
    [harja.ui.lomake :as lomake]
    [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni]]
    [harja.ui.grid :as grid]
    [harja.ui.debug :as debug]
    [harja.ui.valinnat :as valinnat]
    [harja.ui.kentat :as kentat]
    [harja.ui.napit :as napit]
    [harja.views.kartta :as kartta]
    [harja.views.kartta.tasot :as tasot]
    [harja.views.tieluvat.tielupa-lomake :as tielupa-lomake])
  (:require-macros
    [harja.makrot :refer [defc fnc]]
    [harja.tyokalut.ui :refer [for*]]))

(defn suodattimet [e! valinnat alueurakat _ _]
  (let [luo-atomi (fn [avain]
                    (atom (avain valinnat)))
        sijainti-atomi (luo-atomi :sijainti)
        tr-atomi (luo-atomi :tr)
        luvan-numero-atomi (luo-atomi :luvan-numero)
        lupatyyppi-atomi (luo-atomi :lupatyyppi)
        alueurakka-atomi (luo-atomi :alueurakka)
        urakka-atomi (luo-atomi :urakka)
        hakija-atomi (luo-atomi :hakija)
        myonnetty-atomi (luo-atomi :myonnetty)
        voimassaolo-atomi (luo-atomi :voimassaolo)
        tarkasta-aikavalit! (fn []
                              (let [myonnetty-pitaa-rukata? (when-let [myonnetty-aikavali @myonnetty-atomi]
                                                              (= 1 (count (filter nil? myonnetty-aikavali))))
                                    voimassaolo-pitaa-rukata? (when-let [voimassaolo-aikavali @voimassaolo-atomi]
                                                                (= 1 (count (filter nil? voimassaolo-aikavali))))]
                                (when myonnetty-pitaa-rukata?
                                  (swap! myonnetty-atomi tiedot/lisaa-puuttuva-aika))
                                (when voimassaolo-pitaa-rukata?
                                  (swap! voimassaolo-atomi tiedot/lisaa-puuttuva-aika))))
        hae-luvat (fn []
                    (tarkasta-aikavalit!)
                    (e! (tiedot/->PaivitaValinnat)))
        suodatin-avain (fn [avain]
                         (keyword (str "tilu-suodatin-" (name avain))))
        lisaa-watch (fn [atomi avain]
                      (add-watch atomi (suodatin-avain avain)
                        (fn [_ _ _ uusi-tila]
                          (e! (tiedot/->MuutaTila [:valinnat avain] uusi-tila)))))
        poista-watch (fn [atomi avain]
                       (remove-watch atomi (suodatin-avain avain)))

        lupatyyppivalinnat (into [nil] (sort tielupa/lupatyyppi-vaihtoehdot))
        lupatyyppinayta-fn #(or (tielupa/tyyppi-fmt %) "- Ei käytössä -")

        alueurakkanayta-fn #(or (:harja.domain.alueurakka-domain/nimi %) "- Ei käytössä -")]

    (komp/luo
      (komp/sisaan-ulos #(do
                           (e! (tiedot/->KayttajanUrakat))

                           (lisaa-watch sijainti-atomi :sijainti)
                           (lisaa-watch tr-atomi :tr)
                           (lisaa-watch luvan-numero-atomi :luvan-numero)
                           (lisaa-watch lupatyyppi-atomi :lupatyyppi)
                           (lisaa-watch alueurakka-atomi :alueurakka)
                           (lisaa-watch urakka-atomi :urakka)
                           (lisaa-watch hakija-atomi :hakija)
                           (lisaa-watch myonnetty-atomi :myonnetty)
                           (lisaa-watch voimassaolo-atomi :voimassaolo))
        #(do
           (poista-watch sijainti-atomi :sijainti)
           (poista-watch tr-atomi :tr)
           (poista-watch luvan-numero-atomi :luvan-numero)
           (poista-watch lupatyyppi-atomi :lupatyyppi)
           (poista-watch alueurakka-atomi :alueurakka)
           (poista-watch urakka-atomi :urakka)
           (poista-watch hakija-atomi :hakija)
           (poista-watch myonnetty-atomi :myonnetty)
           (poista-watch voimassaolo-atomi :voimassaolo)))
      (fn [_ valinnat alueurakat alueurakka-haku-kaynnissa? tielupien-haku-kaynnissa?]
        [valinnat/urakkavalinnat
         {}
         ^{:key "valinnat"}
         [:div
          [:div.row
           [:div.col-lg-5.col-md-6.col-sm-8.col-xs-12
            [kentat/tee-kentta {:tyyppi :tierekisteriosoite
                                :sijainti sijainti-atomi
                                :vayla-tyyli? true
                                ::lomake/col-luokka ""} tr-atomi]]
           (if (nil? alueurakat)
             [:div.col-lg-1.col-md-2.col-sm-2.col-xs-12
              [ajax-loader-pieni "Haetaan alueurakoita..." {:style {:font-weight "normal"
                                                                    :float "right"}}]]
             [:div.col-lg-1.col-md-2.col-sm-2.col-xs-12
              [kentat/tee-otsikollinen-kentta {:otsikko [:span "Alueurakka"]
                                               :kentta-params {:tyyppi :valinta
                                                               :valinnat (sort-by
                                                                           :harja.domain.alueurakka-domain/nimi
                                                                           alueurakat)
                                                               :valinta-nayta alueurakkanayta-fn
                                                               :vayla-tyyli? true}
                                               :luokka ""
                                               :arvo-atom alueurakka-atomi}]])
           [:div.col-lg-2.col-md-2.col-sm-2.col-xs-12
            [kentat/tee-otsikollinen-kentta {:otsikko "Lupatyyppi"
                                             :kentta-params {:tyyppi :valinta
                                                             :valinnat lupatyyppivalinnat
                                                             :valinta-nayta lupatyyppinayta-fn
                                                             :vayla-tyyli? true}
                                             :luokka ""
                                             :arvo-atom lupatyyppi-atomi}]]
           [:div.col-lg-2.col-md-2.col-sm-2.col-xs-12
            [kentat/tee-otsikollinen-kentta {:otsikko "Luvan numero"
                                             :kentta-params {:tyyppi :string
                                                             :vayla-tyyli? true}
                                             :arvo-atom luvan-numero-atomi
                                             :luokka ""}]]
           [:div.col-lg-2.col-md-2.col-sm-4.col-xs-12
            [kentat/tee-otsikollinen-kentta {:otsikko "Hakija"
                                             :kentta-params {:tyyppi :haku
                                                             :nayta ::tielupa/hakija-nimi
                                                             :hae-kun-yli-n-merkkia 2
                                                             :lahde tiedot/hakijahaku
                                                             :vayla-tyyli? true}
                                             :luokka ""
                                             :arvo-atom hakija-atomi}]]]
          [:div.row
           [:div.col-lg-5.col-md-4.col-sm.6.col-xs-12
            [valinnat/aikavali voimassaolo-atomi {:otsikko "Voimassaolon aikaväli"
                                                  :vayla-tyyli? true}]]
           [:div.col-lg-5.col-md-4.col-sm.6.col-xs-12
            [valinnat/aikavali myonnetty-atomi {:otsikko "Myönnetty välillä"
                                                :vayla-tyyli? true}]]]

          [:div.row.hae-painike
           (if tielupien-haku-kaynnissa?
             [ajax-loader-pieni "Haetaan tielupia..." {:style {:font-weight "normal"
                                                               :float "right"}}]
             [napit/yleinen-ensisijainen "Hae lupia" hae-luvat])
           ]]]))))

(defn tielupataulukko [e! {:keys [haetut-tieluvat tielupien-haku-kaynnissa? valinnat alueurakat alueurakka-haku-kaynnissa?]}]
  [:div.tienpidon-luvat-nakyma
   [suodattimet e! valinnat alueurakat alueurakka-haku-kaynnissa? tielupien-haku-kaynnissa?]
   [grid/grid
    {:otsikko "Tienpidon luvat"
     :tunniste ::tielupa/id
     :sivuta grid/vakiosivutus
     :rivi-klikattu #(e! (tiedot/->ValitseTielupa %))
     :tyhja (if tielupien-haku-kaynnissa?
              [ajax-loader "Haku käynnissä"]
              "Ei liikennetapahtumia")}
    [{:otsikko "Myön\u00ADnetty"
      :leveys 2
      :tyyppi :pvm
      :fmt pvm/pvm-opt
      :nimi ::tielupa/myontamispvm}
     {:otsikko "Voi\u00ADmas\u00ADsaolon alku"
      :leveys 2
      :tyyppi :pvm
      :fmt pvm/pvm-opt
      :nimi ::tielupa/voimassaolon-alkupvm}
     {:otsikko "Voimas\u00ADsaolon loppu"
      :leveys 2
      :tyyppi :pvm
      :fmt pvm/pvm-opt
      :nimi ::tielupa/voimassaolon-loppupvm}
     {:otsikko "Lupa\u00ADtyyppi"
      :leveys 2
      :tyyppi :string
      :nimi ::tielupa/tyyppi
      :fmt tielupa/tyyppi-fmt}
     {:otsikko "Ha\u00ADkija"
      :leveys 3
      :tyyppi :string
      :nimi ::tielupa/hakija-nimi}
     {:otsikko "Luvan numero"
      :leveys 2
      :tyyppi :string
      :nimi ::tielupa/paatoksen-diaarinumero}
     {:otsikko "TR-osoitteet"
      :tyyppi :komponentti
      :leveys 2
      :nimi :tr-osoitteet
      :komponentti (fn [rivi]
                     (let [sijainnit (::tielupa/sijainnit rivi)]
                       [:div
                        (doall
                          (map-indexed
                            (fn [i osoite]
                              ^{:key (str i "_" osoite)}
                              [:div osoite])
                            (->> sijainnit
                              (sort-by (juxt ::tielupa/tie
                                         ::tielupa/aosa
                                         ::tielupa/aet
                                         ::tielupa/losa
                                         ::tielupa/let))
                              (map (juxt ::tielupa/tie
                                     ::tielupa/aosa
                                     ::tielupa/aet
                                     ::tielupa/losa
                                     ::tielupa/let))
                              (map (partial keep identity))
                              (map (partial str/join "/")))))]))}]
    (sort-by
      (juxt
        ::tielupa/myontamispvm
        ::tielupa/voimassaolon-alkupvm
        ::tielupa/voimassaolon-loppupvm
        ::tielupa/tyyppi
        ::tielupa/hakija)
      (fn [[myonto-a alku-a loppu-a :as a]
           [myonto-b alku-b loppu-b :as b]]

        (cond
          (not= myonto-a myonto-b)
          (pvm/jalkeen? myonto-a myonto-b)

          (not= alku-a alku-b)
          (pvm/jalkeen? alku-a alku-b)

          (not= loppu-a loppu-b)
          (pvm/jalkeen? loppu-a loppu-b)

          :default
          (compare a b)))
      haetut-tieluvat)]])

(defn tieluvat* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(do (e! (tiedot/->Nakymassa? true))
                           (e! (tiedot/->HaeAlueurakat))
                           (tasot/taso-paalle! :tieluvat)
                           (tasot/taso-pois! :organisaatio)
                           (kartta-tiedot/piilota-infopaneeli!)
                           (kartta-tiedot/kasittele-infopaneelin-linkit!
                             {:tielupa {:toiminto (fn [lupa]
                                                    ;; Koska ei olla täysin varmoja, että kartan tiedot
                                                    ;; pysyy aina täysin oikeana (yksi tielupa on rikottu moneen palaan)
                                                    (e! (tiedot/->AvaaTielupaPaneelista (::tielupa/id lupa))))
                                        :teksti "Avaa tielupa"}}))
      #(do (e! (tiedot/->Nakymassa? false))
           (tasot/taso-pois! :tieluvat)
           (tasot/taso-paalle! :organisaatio)
           (kartta-tiedot/piilota-infopaneeli!)
           (kartta-tiedot/kasittele-infopaneelin-linkit! nil)))
    (fn [e! app]
      [:div
       [kartta/kartan-paikka]
       [debug/debug app]
       (if-not (:valittu-tielupa app)
         [tielupataulukko e! app]
         [tielupa-lomake/tielupalomake* e! app])])))

(defc tieluvat []
  [tuck tiedot/tila tieluvat*])
