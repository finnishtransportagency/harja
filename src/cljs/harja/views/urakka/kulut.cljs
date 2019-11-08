(ns harja.views.urakka.kulut
  (:require [tuck.core :as tuck]
            [reagent.core :as r]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.ui.debug :as debug]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka.mhu-laskutus :as tiedot]
            [harja.ui.taulukko.taulukko :as taulukko]
            [harja.ui.taulukko.jana :as jana]
            [harja.ui.taulukko.osa :as osa]
            [harja.ui.taulukko.tyokalut :as tyokalu]
            [harja.ui.taulukko.protokollat :as p]
            [harja.ui.modal :as modal]
            [harja.loki :refer [log]]
            [harja.loki :as loki]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.pvm :as pvm-valinta])
  (:require-macros [harja.ui.taulukko.tyokalut :refer [muodosta-taulukko]]))

(defn- osien-paivitys-fn
  [funktiot]
  (fn [osat]
    (mapv
      (fn [osa]
        (let [paivitys (partial (get funktiot (p/osan-id osa)))]
          (paivitys osa)))
      osat)))

(defn- luo-paivitys-fn
  [& avain-arvot]
  (fn [osa]
    (apply
      (partial p/aseta-arvo osa)
      avain-arvot)))

(defn alihankkija-modaali
  [paivitys-fn tallennus-fn sulku-fn {:keys [nimi ytunnus] :as alihankkija}]
  [:div.peitto-modal
   [:div.peitto-kontentti
    [:h1 "Lisää alihankkija"
     [:input {:type  :button
              :value "X"}]]
    [:label "Yrityksen nimi"
     [:input.form-control
      {:type      :text
       :value     nimi
       :on-change #(paivitys-fn (assoc alihankkija :nimi (-> % .-target .-value)))}]]
    [:label "Y-tunnus"
     [:input.form-control
      {:type      :text
       :value     ytunnus
       :on-change #(paivitys-fn (assoc alihankkija :ytunnus (-> % .-target .-value)))}]]
    [:div
     [:button
      {:class    #{"tallenna"}
       :on-click tallennus-fn}
      "Tallenna"]
     [:button
      {:class    #{"sulje"}
       :on-click sulku-fn}
      "Sulje"]]]])

(defn- validoi
  [pakolliset objekti]
  (some #(not (get % pakolliset)) (keys objekti)))

(defn droppari-lisakamppeella
  [{:keys [valittu valinnat valitse-fn formaatti-fn]} komponentti nayta-kun]
  [:div
   [yleiset/livi-pudotusvalikko
    {:valinta    valittu
     :valitse-fn valitse-fn
     :format-fn  formaatti-fn}
    valinnat]
   komponentti])

(defn- kulujen-syottolomake
  [e! {:keys [toimenpiteet tehtavaryhmat aliurakoitsijat] :as app}]
  (let [lomakkeen-tila (r/atom {:validi?               false
                                :nayta                 nil
                                :aliurakoitsijat       []
                                :tehtavat-lkm          1
                                :tehtavat              [{:tehtavaryhma nil
                                                         :maara        nil}]
                                :koontilaskun-kuukausi nil
                                :koontilaskun-era      nil})
        lisaa-tehtava (fn [m]
                        (-> m
                            (update :tehtavat-lkm inc)
                            (update :tehtavat conj {:tehtava      nil
                                                    :tehtavaryhma nil
                                                    :sijainti     nil
                                                    :maara        nil})))
        paivitys-fn (fn [& polut-ja-arvot]
                      (e! (tiedot/->PaivitaLomake polut-ja-arvot))
                      (let [polut-ja-arvot (partition 2 polut-ja-arvot)]
                        (doseq
                          [[polku arvo] polut-ja-arvot]
                          (swap! lomakkeen-tila
                                 (if (vector? polku)
                                   (if (fn? arvo) update-in assoc-in)
                                   (if (fn? arvo) update assoc))
                                 polku arvo))))]
    (fn [e! {:keys [syottomoodi]}]
      (let [{:keys [tehtavat tehtavat-lkm nayta validi? koontilaskun-kuukausi koontilaskun-pvm koontilaskun-era alihankkija alihankkijat]} @lomakkeen-tila
            validointi-fn (partial validoi #{:maara :koontilaskun-kuukausi})
            kuukaudet [:lokakuu :marraskuu :joulukuu :tammikuu :helmikuu :maaliskuu :huhtikuu :toukokuu :kesakuu :heinakuu :elokuu :syyskuu]
            erat [:era-1 :era-2 :era-3 :muu]]
        [:div
         [debug/debug @tila/yleiset]
         [debug/debug @lomakkeen-tila]
         [:div.row
          [:h1 "Uusi kulu"]
          [:input {:type      :radio
                   :on-change #(swap! lomakkeen-tila lisaa-tehtava)}]
          [:label "Kulut kohdistuvat useammalle eri tehtävälle"]]
         (into [:div] (keep-indexed (fn [indeksi t]
                                      (let [{:keys [tehtavaryhma maara]} t]
                                        [:div.lomake-rivi
                                         [:div.row
                                          [:div.col-xs-12.col-sm-6.label-ja-alasveto
                                           [:label "Tehtäväryhmä"]
                                           [yleiset/livi-pudotusvalikko {:valinta    tehtavaryhma
                                                                         :valitse-fn #(paivitys-fn [:tehtavat indeksi :tehtavaryhma] %)
                                                                         :format-fn  #(get % :tehtavaryhma)}
                                            tehtavaryhmat]]]
                                         (when (> tehtavat-lkm 1)
                                           [:div.row
                                            [:div.col-xs-12.col-sm-6
                                             [:label "Määrä"]
                                             [:input.form-control
                                              {:type      :text
                                               :value     maara
                                               :on-change #(paivitys-fn [:tehtavat indeksi :maara] (-> % .-target .-value js/parseFloat))}]]]
                                           )])) tehtavat))
         (when (> tehtavat-lkm 1)
           [:div.row
            [:div.col-xs-12.col-sm-6 {:on-click #(swap! lomakkeen-tila lisaa-tehtava)} "+ lisää juttu"]])
         [:div.row
          [:div.col-sm-6.col-xs-12
           [:h2 "Koontilaskun tiedot"]
           [:div.col-xs-12.label-ja-alasveto.toimenpide
            [:label.alasvedon-otsikko "Koontilaskun kuukausi"]
            [yleiset/livi-pudotusvalikko {:valinta    koontilaskun-kuukausi
                                          :valitse-fn #(paivitys-fn :koontilaskun-kuukausi %)
                                          :format-fn  #(str "- " %)}
             kuukaudet]]
           [:div.col-xs-12
            [:label "Laskun pvm"]
            [pvm-valinta/pvm-valintakalenteri-inputilla {:valitse       #(paivitys-fn :koontilaskun-pvm %)
                                                         :pvm           koontilaskun-pvm
                                                         :pakota-suunta false
                                                         :valittava?-fn #(true? true)}]]
           [:div.col-xs-12
            [:label "Laskun viite"]
            [:input.form-control
             {:type      :text
              :on-change #(paivitys-fn :laskun-viite (-> % .-target .-value))}]]
           [:div.col-xs-12
            [:label "Laskun numero"]
            [:input.form-control
             {:type      :text
              :on-change #(paivitys-fn :laskun-numero (-> % .-target .-value))}]]
           (when (< tehtavat-lkm 2)
             [:div.row
              [:div.col-xs-12
               [:label "Määrä"]
               [:input.form-control
                {:type      :text
                 :on-change #(paivitys-fn [:tehtavat 0 :maara] (-> % .-target .-value js/parseFloat))}]]])]
          [:div.col-sm-6.col-xs-12
           [:h2 "Lisätiedot"]
           [:div.col-xs-12
            [:label "Alihankkija"]
            [droppari-lisakamppeella {:valittu      alihankkija
                                      :valinnat     alihankkijat
                                      :valinta-fn   #(paivitys-fn :alihankkija %)
                                      :formaatti-fn #(get % :nimi)}
             #([:div "Oon kampe"])]
            :kaikki]
           [:div.col-xs-12
            [:label {:on-click #(paivitys-fn :nayta :alihankkija-modaali)} "Alihankkijan y-tunnus"]
            (when (= nayta :alihankkija-modaali)
              [alihankkija-modaali
               (fn [arvo]
                 (paivitys-fn
                   :alihankkija arvo))
               (fn [] (paivitys-fn :alihankkijat
                                   (fn [alihankkijat]
                                     (conj alihankkijat alihankkija))))
               #(paivitys-fn :nayta nil)
               alihankkija])
            [:input.form-control
             {:type      :text
              :value     (:ytunnus alihankkija)
              :on-change #(paivitys-fn :ytunnus (-> % .-target .-value))}]]
           [:div.col-xs-12
            [:label "Kirjoita tähän halutessasi lisätietoa"]
            [:input.form-control
             {:type      :text
              :on-change #(paivitys-fn :lisatieto (-> % .-target .-value))}]]]]
         [:button {:class    #{"nappi" "nappi-ensisijainen"}
                   :on-click #(e! (tiedot/->TallennaKulu @lomakkeen-tila))}
          "Tallenna"]
         [:button {:class    #{"nappi" "nappi-toissijainen"}
                   :on-click #(e! (tiedot/->KulujenSyotto (not syottomoodi)))}
          "Peruuta"]]))))

(defn- luo-kulumodaali
  [e! app]
  [kulujen-syottolomake e! app])

(defn- luo-kulutaulukko
  []
  (loki/log "taulukon luonti")
  (let [paivitysfunktiot {"kk/hoitov."   (luo-paivitys-fn
                                           :id :kk-hoito-v
                                           :arvo "kk/hoitov.")
                          "Erä"          (luo-paivitys-fn
                                           :id :era
                                           :arvo "Erä")
                          "Toimenpide"   (luo-paivitys-fn
                                           :id :toimenpide
                                           :arvo "Toimenpide")
                          "Tehtäväryhmä" (luo-paivitys-fn
                                           :id :tehtavaryhma
                                           :arvo "Tehtäväryhmä")
                          "Määrä"        (luo-paivitys-fn
                                           :id :maara
                                           :arvo "Määrä")}
        otsikot-rivi (fn [rivi]
                       (-> rivi
                           (p/aseta-arvo :id :otsikko-rivi
                                         :class #{"table-default" "table-default-header"})
                           (p/paivita-arvo :lapset
                                           (osien-paivitys-fn paivitysfunktiot))))
        kulut-rivi (fn [rivi]
                     (-> rivi
                         (p/aseta-arvo :id :kulut-rivi
                                       :class #{"table-default-even"})))]
    (muodosta-taulukko :kohdistetut-kulut-taulukko
                       {:otsikot {:janan-tyyppi jana/Rivi
                                  :osat         [osa/Teksti osa/Teksti osa/Teksti osa/Teksti osa/Teksti]}
                        :kulut   {:janan-tyyppi jana/Rivi
                                  :osat         [osa/Teksti osa/Teksti osa/Teksti osa/Teksti osa/Teksti]}}
                       ["kk/hoitov." "Erä" "Toimenpide" "Tehtäväryhmä" "Määrä"]
                       [:otsikot otsikot-rivi
                        :kulut kulut-rivi]
                       {:class                 #{}
                        :taulukon-paivitys-fn! (fn [uusi]
                                                 (loki/log "UUSI" (type uusi) uusi (->
                                                                                     tila/laskutus-kohdistetut-kulut
                                                                                     (swap! assoc-in [:taulukko] uusi)
                                                                                     :taulukko))
                                                 (->
                                                   tila/laskutus-kohdistetut-kulut
                                                   (swap! assoc-in [:taulukko] uusi)
                                                   :taulukko))})))

(defn- kohdistetut*
  [e! app]
  (komp/luo
    (komp/piirretty (fn [this]
                      (e! (tiedot/->HaeAliurakoitsijat))
                      (e! (tiedot/->HaeUrakanLaskut (select-keys (-> @tila/yleiset :urakka) [:id :alkupvm :loppupvm])))
                      (e! (tiedot/->HaeUrakanToimenpiteet (-> @tila/yleiset :urakka :id)))
                      (e! (tiedot/->LuoKulutaulukko (luo-kulutaulukko)))))
    (fn [e! {:keys [taulukko syottomoodi] :as app}]
      [:div
       (if syottomoodi
         [luo-kulumodaali e! app]
         [:div
          [debug/debug app]
          [debug/debug taulukko]
          [:button {:class    #{"nappi" "nappi-toissijainen"}
                    :disabled true} "Tallenna Excel"]
          [:button {:class    #{"nappi" "nappi-toissijainen"}
                    :disabled true} "Tallenna PDF"]
          [:button {:class    #{"nappi" "nappi-ensisijainen"}
                    :on-click #(e! (tiedot/->KulujenSyotto (not syottomoodi)))} "Uusi kulu"]
          (when taulukko
            [p/piirra-taulukko taulukko])])
       ])))

(defn kohdistetut-kulut
  []
  [tuck/tuck tila/laskutus-kohdistetut-kulut kohdistetut*])