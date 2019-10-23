(ns harja.views.urakka.kulut
  (:require [tuck.core :as tuck]
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
            [harja.ui.yleiset :as yleiset])
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
  (fn [osa] (apply (partial p/aseta-arvo osa) avain-arvot)))

; {:tehtava-id }
;
;

(defn alihankkija-modaali
  [paivitys-fn {:keys [nimi y-tunnus] :as alihankkija}]
  [:div
   [:h1 "Lisää alihankkija"]
   [:div
    [:label "Yrityksen nimi"
     [:input {:type      :text
              :value     nimi
              :on-change #(paivitys-fn (assoc alihankkija :nimi (-> % .-target .-value)))}]]]
   [:div
    [:label "Y-tunnus"
     [:input {:type      :text
              :value     y-tunnus
              :on-change #(paivitys-fn (assoc alihankkija :y-tunnus (-> % .-target .-value)))}]]]])

(defn- sijainti-modaali
  [paivitys-fn {:keys [tie tie-patka-piste tieosa-alku etaisyys-alku tieosa-loppu etaisyys-loppu] :as sijainti}]
  [:div
   [:h1 "Lisää sijainti"]
   [:div
    [:label "Tie *"]
    [:input {:type      :text
             :value     tie
             :on-change #(paivitys-fn (assoc sijainti :tie (-> % .-target .-value)))}]]
   [:div
    [:input {:type :radio
             :value :tie
             :name "tie-patka-piste"
             :on-click #(paivitys-fn (assoc sijainti :tie-patka-piste (-> % .-target .-value)))}]
    [:label "Valitse koko tie"]
    [:input {:type :radio
             :value :patka
             :name "tie-patka-piste"
             :on-click #(paivitys-fn (assoc sijainti :tie-patka-piste (-> % .-target .-value)))}]
    [:label "Valitse tietty tienpätkä"]
    [:input {:type :radio
             :value :piste
             :name "tie-patka-piste"
             :on-click #(paivitys-fn (assoc sijainti :tie-patka-piste (-> % .-target .-value)))}]
    [:label "Valitse piste"]]
   [:div
    [:label.col-xs-2 "Tieosa, alku"
     [:input.form-control {:type      :text
              :value     tieosa-alku
              :on-change #(paivitys-fn (assoc sijainti :tieosa-alku (-> % .-target .-value)))}]]
    [:label.col-xs-2 "Aloitusetäisyys, m"
     [:input.form-control {:type      :text
              :value     etaisyys-alku
              :on-change #(paivitys-fn (assoc sijainti :etaisyys-alku (-> % .-target .-value)))}]]
    [:span.col-xs-2 "->"]
    [:label.col-xs-2 "Tieosa, loppu"
     [:input.form-control {:type      :text
              :value     tieosa-loppu
              :on-change #(paivitys-fn (assoc sijainti :tieosa-loppu (-> % .-target .-value)))}]]
    [:label.col-xs-2 "Lopetusetäisyys, m"
     [:input.form-control {:type      :text
              :value     etaisyys-loppu
              :on-change #(paivitys-fn (assoc sijainti :etaisyys-loppu (-> % .-target .-value)))}]]]
   [:div
    [:input {:type :button
             :value "Tallenna"}]
    [:input {:type :button
             :value "Sulje"}]]])

(defn- validoi
  [pakolliset objekti]
  (some #(not (get % pakolliset)) (keys objekti)))

(defn- kulujen-syottolomake
  [e! {:keys [toimenpiteet tehtavaryhmat] :as app}]
  (let [lomakkeen-tila (reagent.core/atom {:validi?               false
                                           :nayta nil
                                           :tehtavat-lkm          1
                                           :tehtavat              [{:tehtava      nil
                                                                    :tehtavaryhma nil
                                                                    :sijainti     nil
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
                      (let [polut-ja-arvot (partition 2 polut-ja-arvot)]
                        (doseq
                          [[polku arvo] polut-ja-arvot]
                          (swap! lomakkeen-tila
                                 (if (vector? polku)
                                   assoc-in
                                   assoc)
                                 polku arvo))))]
    (fn [e! {:keys [syottomoodi]}]
      (let [{:keys [tehtavat tehtavat-lkm validi? koontilaskun-kuukausi koontilaskun-era alihankkija]} @lomakkeen-tila
            validointi-fn (partial validoi #{:maara :koontilaskun-kuukausi})
            kuukaudet [:lokakuu :marraskuu :joulukuu :tammikuu :helmikuu :maaliskuu :huhtikuu :toukokuu :kesakuu :heinakuu :elokuu :syyskuu]
            erat [:era-1 :era-2 :era-3 :muu]]
        [:div
         [debug/debug @lomakkeen-tila]
         [:div.row
          [:h1 "Uusi kulu"]
          [:input {:type      :radio
                   :on-change #(swap! lomakkeen-tila lisaa-tehtava)}]
          [:label "Kulut kohdistuvat useammalle eri tehtävälle"]]
         (into [:div] (keep-indexed (fn [indeksi t]
                                      (let [{:keys [tehtavaryhma sijainti maara]} t]
                                        [:div.lomake-rivi
                                         [:div.row
                                          [:div.col-xs-12.col-sm-6.label-ja-alasveto
                                           [:label "Tehtäväryhmä"]
                                           [yleiset/livi-pudotusvalikko {:valinta    tehtavaryhma
                                                                         :valitse-fn #(paivitys-fn [:tehtavat indeksi :tehtavaryhma] %)
                                                                         :format-fn  #(get % :tehtavaryhma)}
                                            tehtavaryhmat]]
                                          [:div.col-xs-12.col-sm-6
                                           [:label "Ilmoita sijainti"]
                                           [sijainti-modaali (partial paivitys-fn [:tehtavat indeksi :sijainti]) sijainti]]]

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
          [:div.col-xs-12.col-sm-6.label-ja-alasveto
           [:label "Koontilaskun kuukausi"]
           [yleiset/livi-pudotusvalikko {:valinta    koontilaskun-kuukausi
                                         :valitse-fn #(paivitys-fn :koontilaskun-kuukausi %)
                                         :format-fn  #(str "- " %)}
            kuukaudet]]
          [:div.col-xs-12.col-sm-6.label-ja-alasveto
           [:label "Alihankkija"]
           [yleiset/livi-pudotusvalikko {:valinta    :eka
                                         :valitse-fn #(paivitys-fn :suorittaja %)
                                         :format-fn  #(str "- " %)}
            [:eka :toka]]]]
         [:div.row
          [:div.col-xs-12.col-sm-6.label-ja-alasveto
           [:label "Koontilaskun erä"]
           [yleiset/livi-pudotusvalikko {:valinta    koontilaskun-era
                                         :valitse-fn #(paivitys-fn :koontilaskun-era %)
                                         :format-fn  #(str "- " %)}
            erat]]
          [:div.col-xs-12.col-sm-6
           [:label "Suorittajan y-tunnus"]
           [alihankkija-modaali (partial paivitys-fn [:alihankkija]) alihankkija]
           [:input.form-control
            {:type      :text
             :value  (:y-tunnus alihankkija)
             :on-change #(paivitys-fn :suorittajan-ytunnus (-> % .-target .-value))}]]]
         [:div.row
          [:div.col-xs-12.col-sm-6
           [:label "Laskun viite"]
           [:input.form-control
            {:type      :text
             :on-change #(paivitys-fn :laskun-viite (-> % .-target .-value))}]]
          [:div.col-xs-12.col-sm-6
           [:label "Kirjoita tähän halutessasi lisätietoa"]
           [:input.form-control
            {:type      :text
             :on-change #(paivitys-fn :lisatieto (-> % .-target .-value))}]]]
         [:div.row
          [:div.col-xs-12.col-sm-6
           [:label "Laskun numero"]
           [:input.form-control
            {:type      :text
             :on-change #(paivitys-fn :laskun-numero (-> % .-target .-value))}]]]
         (when (< tehtavat-lkm 2)
           [:div.row
            [:div.col-xs-12.col-sm-6
             [:label "Määrä"]
             [:input.form-control
              {:type      :text
               :on-change #(paivitys-fn [:tehtavat 0 :maara] (-> % .-target .-value js/parseFloat))}]]])
         [:input {:type     :button
                  :value    "Tallenna"
                  :on-click #(e! (tiedot/->TallennaKulu @lomakkeen-tila))}]
         [:input {:type     :button
                  :value    "Peruuta"
                  :on-click #(e! (tiedot/->KulujenSyotto (not syottomoodi)))}]]))))

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
                      (loki/log "Piirretty")
                      (e! (tiedot/->HaeKustannussuunnitelma (-> @tila/yleiset :urakka :id)))
                      (e! (tiedot/->LuoKulutaulukko (luo-kulutaulukko)))))
    (fn [e! {:keys [taulukko syottomoodi] :as app}]
      [:div
       (when syottomoodi [luo-kulumodaali e! app])
       [debug/debug app]
       [debug/debug taulukko]
       [:div "Kohdista mut babe"]
       [:div {:on-click #(e! (tiedot/->KulujenSyotto (not syottomoodi)))}
        "Paina mua kohdistaksees mut"]
       (when taulukko
         [p/piirra-taulukko taulukko])])))

(defn kohdistetut-kulut
  []
  [tuck/tuck tila/laskutus-kohdistetut-kulut kohdistetut*])