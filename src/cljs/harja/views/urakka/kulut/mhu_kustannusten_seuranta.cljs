(ns harja.views.urakka.kulut.mhu-kustannusten-seuranta
  "Urakan 'Toteumat' välilehden Määrien toteumat osio"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko +korostuksen-kesto+]]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.kulut.mhu-kustannusten-seuranta :as kustannusten-seuranta-tiedot]
            [harja.ui.kentat :as kentat]
            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm]
            [cljs.core.async :refer [<! >! chan]]
            [cljs.core.async :refer [<! timeout]]
            [cljs-time.core :as t]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.domain.skeema :refer [+tyotyypit+]]

            [tuck.core :as tuck]
            [harja.ui.debug :as debug]
            [harja.tyokalut.big :as big])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

(defn- laske-prosentti
  "Olettaa saavansa molemmat parametrit big arvoina."
  [toteuma suunniteltu]
  (if (or (nil? toteuma)
          (nil? suunniteltu)
          (big/eq (big/->big 0) toteuma)
          (big/eq (big/->big 0) suunniteltu))
    0
    (big/fmt (big/mul (big/->big 100) (big/div toteuma suunniteltu)) 2)))

; spekseistä laskettu
(def leveydet {:caret-paaryhma "5%"
               :paaryhma-vari "5%"
               :tehtava "40%"
               :budjetoitu "15%"
               :toteuma "15%"
               :erotus "10%"
               :prosentti "10%"})

(defn formatoi-naytolle->big
  ([arvo] (formatoi-naytolle->big arvo false))
  ([arvo on-big?]
   (let [arvo (if on-big?
                arvo
                (big/->big arvo))
         fmt-arvo (harja.fmt/desimaaliluku (or (:b arvo) 0) 2 true)]
     fmt-arvo)))

(defn- rivita-toimenpiteet-paaryhmalle [e! app toimenpiteet]
  (let [row-index-atom (r/atom 0)
        avattava? true]
    (map
      (fn [toimenpide]
        (let [_ (reset! row-index-atom (inc @row-index-atom))
              tehtavat (:tehtavat toimenpide)
              muodostetut-tehtavat (if-not (= (get-in app [:valittu-rivi :toimenpide]) toimenpide)
                                     nil
                                     (mapcat
                                       (fn [rivi]
                                         (let [_ (reset! row-index-atom (inc @row-index-atom))
                                               toteutunut-summa (big/->big (or (:toteutunut_summa rivi) 0))
                                               budjetoitu-summa (big/->big (or (:budjetoitu_summa rivi) 0))
                                               erotus (big/->big (big/minus budjetoitu-summa toteutunut-summa))]
                                           (concat
                                             [^{:key (str @row-index-atom "-tehtava-" (hash rivi))}
                                              [:tr.bottom-border
                                               [:th {:style {:width (:caret-paaryhma leveydet) :padding-left "0px" :padding-right "0px" :text-align "center"}}]
                                               [:th {:style {:width (:paaryhma-vari leveydet) :padding-left "0px" :padding-right "0px" :text-align "center"}}]
                                               [:td {:style {:width (:tehtava leveydet)}} (str ;"Nimi:"
                                                                                            (:tehtava_nimi rivi) ;"/KustannusT:" (:toteutunut rivi)
                                                                                            ;"/MaksuT:" (:maksutyyppi rivi) "/ToimenpKoodi:" (:toimenpidekoodi_nimi rivi)
                                                                                            ;"/ryhmä:" (:toimenpideryhma rivi)
                                                                                            )]
                                               [:td.numero {:style {:width (:budjetoitu leveydet)}} (str (formatoi-naytolle->big budjetoitu-summa false) " ")]
                                               [:td.numero {:style {:width (:toteuma leveydet)}} (str (formatoi-naytolle->big toteutunut-summa false) " ")]
                                               [:td.numero {:class (if (big/gt (big/->big toteutunut-summa)
                                                                               (big/->big budjetoitu-summa))
                                                                     "negatiivinen-numero" "numero")
                                                            :style {:width (:erotus leveydet)}} (str (formatoi-naytolle->big erotus false) " ")]
                                               [:td.numero {:class (if (big/gt (big/->big toteutunut-summa)
                                                                               (big/->big budjetoitu-summa))
                                                                     "negatiivinen-numero" "numero")
                                                            :style {:width (:prosentti leveydet)}} (laske-prosentti toteutunut-summa budjetoitu-summa)]]])))
                                       tehtavat))]
          (doall (concat [^{:key (str "otsikko-" (hash toimenpide))}
                          [:tr.bottom-border
                           (merge
                             (when (> (count tehtavat) 0)
                               {:on-click #(e! (kustannusten-seuranta-tiedot/->AvaaRivi :toimenpide toimenpide))}))
                           [:td {:style {:width (:caret-paaryhma leveydet) :padding-left "0px" :padding-right "0px" :text-align "center"}}]
                           [:td {:style {:width (:paaryhma-vari leveydet) :padding-left "0px" :padding-right "0px" :text-align "center"}}
                            (when (> (count tehtavat) 0)
                              (if (= (get-in app [:valittu-rivi :toimenpide]) toimenpide)
                                [:img {:alt "Expander" :src "images/expander-down.svg"}]
                                [:img {:alt "Expander" :src "images/expander.svg"}]))]
                           [:td {:style {:width (:tehtava leveydet)}} (:toimenpide toimenpide)]
                           [:td.numero {:style {:width (:budjetoitu leveydet)}} (formatoi-naytolle->big (:toimenpide-budjetoitu-summa toimenpide))]
                           [:td.numero {:style {:width (:toteuma leveydet)}} (formatoi-naytolle->big (:toimenpide-toteutunut-summa toimenpide))]
                           [:td {:class (if (big/gt (big/->big (or (:toimenpide-toteutunut-summa toimenpide) 0))
                                                    (big/->big (or (:toimenpide-budjetoitu-summa toimenpide) 0)))
                                          "negatiivinen-numero" "numero")
                                 :style {:width (:erotus leveydet)}} (formatoi-naytolle->big (- (:toimenpide-budjetoitu-summa toimenpide) (:toimenpide-toteutunut-summa toimenpide)))]
                           [:td {:class (if (big/gt (big/->big (or (:toimenpide-toteutunut-summa toimenpide) 0))
                                                    (big/->big (or (:toimenpide-budjetoitu-summa toimenpide) 0)))
                                          "negatiivinen-numero" "numero")
                                 :style {:width (:prosentti leveydet)}} (laske-prosentti
                                                                          (big/->big (or (:toimenpide-toteutunut-summa toimenpide) 0))
                                                                          (big/->big (or (:toimenpide-budjetoitu-summa toimenpide) 0)))]
                           ]]
                         muodostetut-tehtavat))))
      toimenpiteet)))

(defn- ryhmitellyt-taulukko [e! app rivit-paaryhmittain]
  (let [hankintakustannusten-toimenpiteet (rivita-toimenpiteet-paaryhmalle e! app (:hankintakustannukset rivit-paaryhmittain))
        jjhk-toimenpiteet (rivita-toimenpiteet-paaryhmalle e! app (:johto-ja-hallintakorvaus rivit-paaryhmittain))
        valittu-hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)
        hoitovuosi-nro (kustannusten-seuranta-tiedot/hoitokauden-jarjestysnumero valittu-hoitokauden-alkuvuosi)
        monesko-hoitovuosi (kustannusten-seuranta-tiedot/hoitokauden-jarjestysnumero (pvm/vuosi (pvm/nyt)))
        hoitovuotta-jaljella (if (= hoitovuosi-nro monesko-hoitovuosi)
                               (pvm/montako-paivaa-valissa
                                 (pvm/nyt)
                                 (pvm/->pvm (str "30.09." (inc valittu-hoitokauden-alkuvuosi))))
                               nil)]
    [:div
     [:div
      [:h4 "Hoitovuosi: " hoitovuosi-nro " (1.10." valittu-hoitokauden-alkuvuosi " - 09.30." (inc valittu-hoitokauden-alkuvuosi) ")"]
      (when hoitovuotta-jaljella
        [:span "Hoitovuotta on jäljellä " hoitovuotta-jaljella " päivää."])]
     [:div.table-default
      [:table.table-default-header-valkoinen
       [:thead
        [:tr.bottom-border {:style {:text-transform "uppercase"}}
         [:th {:style {:width (:caret-paaryhma leveydet) :padding-left "0px" :padding-right "0px" :text-align "center"}}]
         [:th {:style {:width (:paaryhma-vari leveydet) :padding-left "0px" :padding-right "0px" :text-align "center"}}]
         [:th {:style {:width (:tehtava leveydet)}} "Toimenpide"]
         [:th {:style {:width (:budjetoitu leveydet) :text-align "right"}} "Budjetti €"]
         [:th {:style {:width (:toteuma leveydet) :text-align "right"}} "Toteuma €"]
         [:th {:style {:width (:erotus leveydet) :text-align "right"}} "Erotus €"]
         [:th {:style {:width (:prosentti leveydet) :text-align "right"}} "%"]]]
       [:tbody
        [:tr.bottom-border.selectable {:on-click #(e! (kustannusten-seuranta-tiedot/->AvaaRivi :paaryhma :hankintakustannukset))
                                       :key "Hankintakustannukset"}
         [:td {:style {:width (:caret-paaryhma leveydet) :padding-left "0px" :padding-right "0px" :text-align "center"}}
          (if
            (= :hankintakustannukset (get-in app [:valittu-rivi :paaryhma]))
            [:img {:alt "Expander" :src "images/expander-down.svg"}]
            [:img {:alt "Expander" :src "images/expander.svg"}])]
         [:td {:style {:width (:paaryhma-vari leveydet) :padding-left "0px" :padding-right "0px" :text-align "center"}}
          [:span.circle.hankintakustannukset]]
         [:td {:style {:width (:tehtava leveydet)
                       :font-weight "700"}} "Hankintakustannukset"]
         [:td.numero {:style {:width (:budjetoitu leveydet)}} (formatoi-naytolle->big (get-in rivit-paaryhmittain [:hankintakustannukset-budjetoitu]))]
         [:td.numero {:style {:width (:toteuma leveydet)}} (formatoi-naytolle->big (get-in rivit-paaryhmittain [:hankintakustannukset-toteutunut]))]
         [:td {:class (if (big/gt (big/->big (or (get-in rivit-paaryhmittain [:hankintakustannukset-toteutunut]) 0))
                                  (big/->big (or (get-in rivit-paaryhmittain [:hankintakustannukset-budjetoitu]) 0)))
                        "negatiivinen-numero" "numero")
               :style {:width (:erotus leveydet)}} (formatoi-naytolle->big (- (get-in rivit-paaryhmittain [:hankintakustannukset-budjetoitu]) (get-in rivit-paaryhmittain [:hankintakustannukset-toteutunut])))]
         [:td {:class (if (big/gt (big/->big (or (get-in rivit-paaryhmittain [:hankintakustannukset-toteutunut]) 0))
                                  (big/->big (or (get-in rivit-paaryhmittain [:hankintakustannukset-budjetoitu]) 0)))
                        "negatiivinen-numero" "numero")
               :style {:width (:prosentti leveydet)}} (laske-prosentti
                                                        (big/->big (or (get-in rivit-paaryhmittain [:hankintakustannukset-toteutunut]) 0))
                                                        (big/->big (or (get-in rivit-paaryhmittain [:hankintakustannukset-budjetoitu]) 0)))]]
        (when (= :hankintakustannukset (get-in app [:valittu-rivi :paaryhma]))
          (doall
            (for [l hankintakustannusten-toimenpiteet]
              ^{:key (hash l)}
              l)))


        [:tr.bottom-border {:key "Johto- ja hallintokorvaukset"}
         [:td {:on-click #(e! (kustannusten-seuranta-tiedot/->AvaaRivi :paaryhma :johto-ja-hallintakorvaus))
               :style {:width (:caret-paaryhma leveydet) :padding-left "0px" :padding-right "0px" :text-align "center"}}
          (if (= :johto-ja-hallintakorvaus (get-in app [:valittu-rivi :paaryhma]))
            [:img {:alt "Expander" :src "images/expander-down.svg"}]
            [:img {:alt "Expander" :src "images/expander.svg"}])]
         [:td {:style {:width (:paaryhma-vari leveydet) :padding-left "0px" :padding-right "0px" :text-align "center"}} [:span.circle.jjh-korvaukset]]
         [:td {:style {:width (:tehtava leveydet)
                       :font-weight "700"}} "Johto- ja hallintokorvaukset"]
         [:td.numero {:style {:width (:budjetoitu leveydet)}} (formatoi-naytolle->big (get-in rivit-paaryhmittain [:johto-ja-hallintakorvaus-budjetoitu]))]
         [:td.numero {:style {:width (:toteuma leveydet)}} (formatoi-naytolle->big (get-in rivit-paaryhmittain [:johto-ja-hallintakorvaus-toteutunut]))]
         [:td {:class (if (big/gt (big/->big (or (get-in rivit-paaryhmittain [:johto-ja-hallintakorvaus-toteutunut]) 0))
                                  (big/->big (or (get-in rivit-paaryhmittain [:johto-ja-hallintakorvaus-budjetoitu]) 0)))
                        "negatiivinen-numero" "numero")
               :style {:width (:erotus leveydet)}} (formatoi-naytolle->big (- (get-in rivit-paaryhmittain [:johto-ja-hallintakorvaus-budjetoitu]) (get-in rivit-paaryhmittain [:johto-ja-hallintakorvaus-toteutunut])))]
         [:td {:class (if (big/gt (big/->big (or (get-in rivit-paaryhmittain [:johto-ja-hallintakorvaus-toteutunut]) 0))
                                  (big/->big (or (get-in rivit-paaryhmittain [:johto-ja-hallintakorvaus-budjetoitu]) 0)))
                        "negatiivinen-numero" "numero")
               :style {:width (:prosentti leveydet)}} (laske-prosentti
                                                        (big/->big (or (get-in rivit-paaryhmittain [:johto-ja-hallintakorvaus-toteutunut]) 0))
                                                        (big/->big (or (get-in rivit-paaryhmittain [:johto-ja-hallintakorvaus-budjetoitu]) 0)))]]
        (when (= :johto-ja-hallintakorvaus (get-in app [:valittu-rivi :paaryhma]))
          (doall
            (for [l jjhk-toimenpiteet]
              ^{:key (hash l)}
              l)))

        [:tr.bottom-border {:key "Hoidonjohdonpalkkio"}
         [:td {:style {:width (:caret-paaryhma leveydet) :padding-left "0px" :padding-right "0px" :text-align "center"}} ""] ;; Hoidonjohdonpalkiolla ei ole toimenpiteitä tai tehtäviä
         [:td {:style {:width (:paaryhma-vari leveydet) :padding-left "0px" :padding-right "0px" :text-align "center"}} [:span.circle.hj-palkkiot]]
         [:td {:style {:width (:tehtava leveydet)
                       :font-weight "700"}} "Hoidonjohdonpalkkio"]
         [:td.numero {:style {:width (:budjetoitu leveydet)}} (formatoi-naytolle->big (get-in rivit-paaryhmittain [:hoidonjohdonpalkkio-budjetoitu]))]
         [:td.numero {:style {:width (:toteuma leveydet)}} (formatoi-naytolle->big (get-in rivit-paaryhmittain [:hoidonjohdonpalkkio-toteutunut]))]
         [:td {:class (if (big/gt (big/->big (or (get-in rivit-paaryhmittain [:hoidonjohdonpalkkio-toteutunut]) 0))
                                  (big/->big (or (get-in rivit-paaryhmittain [:hoidonjohdonpalkkio-budjetoitu]) 0)))
                        "negatiivinen-numero" "numero")
               :style {:width (:erotus leveydet)}} (formatoi-naytolle->big (- (get-in rivit-paaryhmittain [:hoidonjohdonpalkkio-budjetoitu]) (get-in rivit-paaryhmittain [:hoidonjohdonpalkkio-toteutunut])))]
         [:td {:class (if (big/gt (big/->big (or (get-in rivit-paaryhmittain [:hoidonjohdonpalkkio-toteutunut]) 0))
                                  (big/->big (or (get-in rivit-paaryhmittain [:hoidonjohdonpalkkio-budjetoitu]) 0)))
                        "negatiivinen-numero" "numero")
               :style {:width (:prosentti leveydet)}} (laske-prosentti
                                                        (big/->big (or (get-in rivit-paaryhmittain [:hoidonjohdonpalkkio-toteutunut]) 0))
                                                        (big/->big (or (get-in rivit-paaryhmittain [:hoidonjohdonpalkkio-budjetoitu]) 0)))]]

        [:tr.bottom-border {:key "Erillishankinnat"}
         [:td {:style {:width (:caret-paaryhma leveydet) :padding-left "0px" :padding-right "0px" :text-align "center"}} ""] ;; Erillishankinnoilla ei ole toimenpiteitä tai tehtäviä
         [:td {:style {:width (:paaryhma-vari leveydet) :padding-left "0px" :padding-right "0px" :text-align "center"}} [:span.circle.erillishankinnat]]
         [:td {:style {:width (:tehtava leveydet)
                       :font-weight "700"}} "Erillishankinnat"]
         [:td.numero {:style {:width (:budjetoitu leveydet)}} (formatoi-naytolle->big (get-in rivit-paaryhmittain [:erillishankinnat-budjetoitu]))]
         [:td.numero {:style {:width (:toteuma leveydet)}} (formatoi-naytolle->big (get-in rivit-paaryhmittain [:erillishankinnat-toteutunut]))]
         [:td {:class (if (big/gt (big/->big (or (get-in rivit-paaryhmittain [:erillishankinnat-toteutunut]) 0))
                                  (big/->big (or (get-in rivit-paaryhmittain [:erillishankinnat-budjetoitu]) 0)))
                        "negatiivinen-numero" "numero")
               :style {:width (:erotus leveydet)}} (formatoi-naytolle->big (- (get-in rivit-paaryhmittain [:erillishankinnat-budjetoitu]) (get-in rivit-paaryhmittain [:erillishankinnat-toteutunut])))]
         [:td {:class (if (big/gt (big/->big (or (get-in rivit-paaryhmittain [:erillishankinnat-toteutunut]) 0))
                                  (big/->big (or (get-in rivit-paaryhmittain [:erillishankinnat-budjetoitu]) 0)))
                        "negatiivinen-numero" "numero")
               :style {:width (:prosentti leveydet)}} (laske-prosentti
                                                        (big/->big (or (get-in rivit-paaryhmittain [:erillishankinnat-toteutunut]) 0))
                                                        (big/->big (or (get-in rivit-paaryhmittain [:erillishankinnat-budjetoitu]) 0)))]]
        ; Näytä yhteensä rivi, mikäli toimenpiteitä on olemassa
        (when true
          [:tr.bottom-border
           [:td {:style {:width (:caret-paaryhma leveydet) :padding-left "0px" :padding-right "0px" :text-align "center"}}]
           [:td {:style {:width (:paaryhma-vari leveydet) :padding-left "0px" :padding-right "0px" :text-align "center"}}]
           [:td {:style
                 {:width (:tehtava leveydet)
                  :font-weight "700"}}
            (get-in app [:kustannukset-yhteensa :toimenpide])]

           [:td.numero {:style {:width (:budjetoitu leveydet)}} (formatoi-naytolle->big (get-in app [:kustannukset-yhteensa :yht-budjetoitu-summa]))]
           [:td.numero {:style {:width (:toteuma leveydet)}} (formatoi-naytolle->big (get-in app [:kustannukset-yhteensa :yht-toteutunut-summa]))]
           [:td {:class (if (big/gt (big/->big (or (get-in app [:kustannukset-yhteensa :yht-toteutunut-summa]) 0))
                                    (big/->big (or (get-in app [:kustannukset-yhteensa :yht-budjetoitu-summa]) 0)))
                          "negatiivinen-numero" "numero")
                 :style {:width (:erotus leveydet)}} (formatoi-naytolle->big (- (get-in app [:kustannukset-yhteensa :yht-budjetoitu-summa]) (get-in app [:kustannukset-yhteensa :yht-toteutunut-summa])))]
           [:td {:class (if (big/gt (big/->big (or (get-in app [:kustannukset-yhteensa :yht-toteutunut-summa]) 0))
                                    (big/->big (or (get-in app [:kustannukset-yhteensa :yht-budjetoitu-summa]) 0)))
                          "negatiivinen-numero" "numero")
                 :style {:width (:prosentti leveydet)}} #_(laske-prosentti
                                                            (big/->big (or (get-in app [:kustannukset-yhteensa :yht-toteutunut-summa]) 0))
                                                            (big/->big (or (get-in app [:kustannukset-yhteensa :yht-budjetoitu-summa]) 0)))]])
        ;; Näytetään lisätyöt
        (when true
          [:tr.bottom-border {:style {:padding-top "40px"}}
           [:td {:style {:width (:caret-paaryhma leveydet) :padding-left "0px" :padding-right "0px" :text-align "center"}}]
           [:td {:style {:width (:paaryhma-vari leveydet) :padding-left "0px" :padding-right "0px" :text-align "center"}}]
           [:td {:style {:width (:tehtava leveydet) :font-weight "700"}} "Lisätyöt"]
           [:td.numero {:style {:width (:budjetoitu leveydet)}}]
           [:td.numero {:style {:width (:toteuma leveydet)}} (formatoi-naytolle->big (:lisatyot rivit-paaryhmittain))]
           [:td {:style {:width (:erotus leveydet)}}]
           [:td {:style {:width (:prosentti leveydet)}}]])
        ]]]]))

(defn yhteenveto-laatikko [app data]
  (let []
    [:div.yhteenveto [:h4 "Yhteenveto"]
     [:div [:span "Tavoitehinta: "] [:span (get-in app [:kustannukset-yhteensa :yht-budjetoitu-summa])]]
     [:div [:span "Toteuma: "] [:span (get-in app [:kustannukset-yhteensa :yht-toteutunut-summa])]]
     [:div [:span "Tavoitehinnan ylitys: "]
      (when (> 0 (- (get-in app [:kustannukset-yhteensa :yht-toteutunut-summa])
                    (get-in app [:kustannukset-yhteensa :yht-budjetoitu-summa])))
        [:span.negatiivinen-numero
         (formatoi-naytolle->big (- (get-in app [:kustannukset-yhteensa :yht-toteutunut-summa])
                                    (get-in app [:kustannukset-yhteensa :yht-budjetoitu-summa])
                                    ))])]
     [:div [:span "Lisätyöt: "] [:span (formatoi-naytolle->big (:lisatyot data))]]]))

(defn kustannukset
  "Kustannukset listattuna taulukkoon"
  [e! app]
  (let [{:keys [alkupvm]} (-> @tila/tila :yleiset :urakka)  ;; Ota urakan alkamis päivä
        vuosi (pvm/vuosi alkupvm)
        hoitokaudet (into [] (range vuosi (+ 5 vuosi)))
        taulukon-rivit (:kustannukset-grouped1 app)
        valittu-hoitokausi (if (nil? (get-in app [:hoitokauden-alkuvuosi]))
                             2019
                             (get-in app [:hoitokauden-alkuvuosi]))]
    [:div.kustannusten-seuranta
     [debug/debug app]
     [:div {:style {:padding-top "1rem"}} [:p "Tavoite- ja kattohinnat sekä budjetit on suunniteltu Suunnittelu-puolella.
     Toteutumissa näkyy ne kustannukset, jotka ovat Laskutus-osiossa syötetty järjestelmään."]]
     [:div.row
      [:div.col-xs-6.col-md-3
       [:span.alasvedon-otsikko "Hoitokausi"]
       [yleiset/livi-pudotusvalikko {:valinta valittu-hoitokausi
                                     :vayla-tyyli? true
                                     :valitse-fn #(e! (kustannusten-seuranta-tiedot/->ValitseHoitokausi (:id @nav/valittu-urakka) %))
                                     :format-fn #(str "1.10." % "-30.9." (inc %))
                                     :klikattu-ulkopuolelle-params {:tarkista-komponentti? true}}
        hoitokaudet]]]

     [ryhmitellyt-taulukko e! app taulukon-rivit]
     [yhteenveto-laatikko app taulukon-rivit]]))

(defn kustannusten-seuranta* [e! app]
  (komp/luo
    (komp/lippu tila/kustannusten-seuranta-nakymassa?)
    (komp/piirretty (fn [this]
                      (e! (kustannusten-seuranta-tiedot/->HaeKustannukset (:hoitokauden-alkuvuosi app)
                                                                          nil nil))))
    (fn [e! app]
      [:div {:id "vayla"}
       [:div
        [kustannukset e! app]]])))

(defn kustannusten-seuranta []
  (tuck/tuck tila/kustannusten-seuranta kustannusten-seuranta*))