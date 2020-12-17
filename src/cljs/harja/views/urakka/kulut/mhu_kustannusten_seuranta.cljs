(ns harja.views.urakka.kulut.mhu-kustannusten-seuranta
  "Urakan 'Toteumat' välilehden Määrien toteumat osio"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! chan]]
            [cljs.core.async :refer [<! timeout]]
            [cljs-time.core :as t]
            [clojure.string :as str]
            [tuck.core :as tuck]
            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm]
            [harja.ui.debug :as debug]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko +korostuksen-kesto+]]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.komponentti :as komp]
            [harja.transit :as transit]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.kulut.mhu-kustannusten-seuranta :as kustannusten-seuranta-tiedot]
            [harja.domain.kulut.kustannusten-seuranta :as kustannusten-seuranta]
            [harja.domain.skeema :refer [+tyotyypit+]]
            [harja.tyokalut.big :as big])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

(defn- muotoile-prosentti
  "Olettaa saavansa molemmat parametrit big arvoina."
  [toteuma suunniteltu negatiivinen?]
  (if (or (nil? toteuma)
          (nil? suunniteltu)
          (big/eq (big/->big 0) toteuma)
          (big/eq (big/->big 0) suunniteltu))
    [:span 0]
    [:span (when negatiivinen?
             {:class "pilleri"})
     (big/fmt (big/mul (big/->big 100) (big/div toteuma suunniteltu)) 2)]))

; spekseistä laskettu
(def leveydet {:caret-paaryhma "2%"
               :paaryhma-vari "2%"
               :tehtava "46%"
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

(defn- rivita-lisatyot [e! app lisatyot]
  (for [l lisatyot]
    [:tr.bottom-border
     [:td.paaryhma-center {:style {:width (:caret-paaryhma leveydet)}}]
     [:td.paaryhma-center {:style {:width (:paaryhma-vari leveydet)}}]
     [:td {:style {:width (:tehtava leveydet)}} (or (:tehtava_nimi l) (:toimenpidekoodi_nimi l))]
     [:td.numero {:style {:width (:budjetoitu leveydet)}}]
     [:td.numero {:style {:width (:toteuma leveydet)}} (formatoi-naytolle->big (:toteutunut_summa l) false) " "]
     [:td.numero {:style {:width (:erotus leveydet)}}]
     [:td.numero {:style {:width (:prosentti leveydet)}}]]))

(defn- rivita-toimenpiteet-paaryhmalle [e! app toimenpiteet]
  (let [row-index-atom (r/atom 0)
        avattava? true]
    (map
      (fn [toimenpide]
        (let [_ (reset! row-index-atom (inc @row-index-atom))
              hankinta-tehtavat (filter #(= "hankinta" (:toimenpideryhma %)) (:tehtavat toimenpide))
              rahavaraus-tehtavat (filter #(= "rahavaraus" (:toimenpideryhma %)) (:tehtavat toimenpide))
              negatiivinen? (big/gt (big/->big (or (:toimenpide-toteutunut-summa toimenpide) 0))
                                    (big/->big (or (:toimenpide-budjetoitu-summa toimenpide) 0)))
              muodostetut-tehtavat (if-not (= (get-in app [:valittu-rivi :toimenpide]) toimenpide)
                                     nil
                                     (concat
                                       [^{:key (str @row-index-atom "hankinta-tehtava")}
                                        [:tr.bottom-border
                                         [:td.paaryhma-center {:style {:width (:caret-paaryhma leveydet)}}]
                                         [:td.paaryhma-center {:style {:width (:paaryhma-vari leveydet)}}]
                                         [:td {:style {:width (:tehtava leveydet)}} [:span {:style {:padding-left "8px"}}
                                                                                     [:span {:style {:font-weight "bold"}} "Hankinnat"]]]
                                         [:td.numero {:style {:width (:budjetoitu leveydet)}}]
                                         [:td.numero {:style {:width (:toteuma leveydet)}}]
                                         [:td.numero {:style {:width (:erotus leveydet)}}]
                                         [:td.numero {:style {:width (:prosentti leveydet)}}]]]
                                       (mapcat
                                         (fn [rivi]
                                           (let [_ (reset! row-index-atom (inc @row-index-atom))
                                                 toteutunut-summa (big/->big (or (:toteutunut_summa rivi) 0))]
                                             (concat
                                               [^{:key (str @row-index-atom "-tehtava-" (hash rivi))}
                                                [:tr.bottom-border
                                                 [:td.paaryhma-center {:style {:width (:caret-paaryhma leveydet)}}]
                                                 [:td.paaryhma-center {:style {:width (:paaryhma-vari leveydet)}}]
                                                 [:td {:style {:width (:tehtava leveydet)}} [:span {:style {:padding-left "16px"}} (:tehtava_nimi rivi)]]
                                                 [:td.numero {:style {:width (:budjetoitu leveydet)}}]
                                                 [:td.numero {:style {:width (:toteuma leveydet)}} (str (formatoi-naytolle->big toteutunut-summa false) " ")]
                                                 [:td.numero {:style {:width (:erotus leveydet)}}]
                                                 [:td.numero {:style {:width (:prosentti leveydet)}}]]])))
                                         hankinta-tehtavat)
                                       [^{:key (str @row-index-atom "rahavaraus-tehtava")}
                                        [:tr.bottom-border
                                         [:td.paaryhma-center {:style {:width (:caret-paaryhma leveydet)}}]
                                         [:td.paaryhma-center {:style {:width (:paaryhma-vari leveydet)}}]
                                         [:td {:style {:width (:tehtava leveydet)}} [:span {:style {:padding-left "8px"}}
                                                                                     [:span {:style {:font-weight "bold"}} "Rahavaraukset"]]]
                                         [:td.numero {:style {:width (:budjetoitu leveydet)}}]
                                         [:td.numero {:style {:width (:toteuma leveydet)}}]
                                         [:td.numero {:style {:width (:erotus leveydet)}}]
                                         [:td.numero {:style {:width (:prosentti leveydet)}}]]]
                                       (mapcat
                                         (fn [rivi]
                                           (let [_ (reset! row-index-atom (inc @row-index-atom))
                                                 toteutunut-summa (big/->big (or (:toteutunut_summa rivi) 0))]
                                             (concat
                                               [^{:key (str @row-index-atom "-tehtava-" (hash rivi))}
                                                [:tr.bottom-border
                                                 [:td.paaryhma-center {:style {:width (:caret-paaryhma leveydet)}}]
                                                 [:td.paaryhma-center {:style {:width (:paaryhma-vari leveydet)}}]
                                                 [:td {:style {:width (:tehtava leveydet)}} [:span {:style {:padding-left "16px"}} (:tehtava_nimi rivi)]]
                                                 [:td.numero {:style {:width (:budjetoitu leveydet)}}]
                                                 [:td.numero {:style {:width (:toteuma leveydet)}} (str (formatoi-naytolle->big toteutunut-summa false) " ")]
                                                 [:td.numero {:style {:width (:erotus leveydet)}}]
                                                 [:td.numero {:style {:width (:prosentti leveydet)}}]]])))
                                         rahavaraus-tehtavat)))]
          (doall (concat [^{:key (str "otsikko-" (hash toimenpide))}
                          [:tr.bottom-border
                           (merge
                             (when (> (count (:tehtavat toimenpide)) 0)
                               {:on-click #(e! (kustannusten-seuranta-tiedot/->AvaaRivi :toimenpide toimenpide))}))
                           [:td.paaryhma-center {:style {:width (:caret-paaryhma leveydet)}}]
                           [:td.paaryhma-center {:style {:width (:paaryhma-vari leveydet)}}
                            (when (> (count (:tehtavat toimenpide)) 0)
                              (if (= (get-in app [:valittu-rivi :toimenpide]) toimenpide)
                                [:img {:alt "Expander" :src "images/expander-down.svg"}]
                                [:img {:alt "Expander" :src "images/expander.svg"}]))]
                           [:td {:style {:width (:tehtava leveydet)}} (:toimenpide toimenpide)]
                           [:td.numero {:style {:width (:budjetoitu leveydet)}} (formatoi-naytolle->big (:toimenpide-budjetoitu-summa toimenpide))]
                           [:td.numero {:style {:width (:toteuma leveydet)}} (formatoi-naytolle->big (:toimenpide-toteutunut-summa toimenpide))]
                           [:td {:class (if negatiivinen? "negatiivinen-numero" "numero")
                                 :style {:width (:erotus leveydet)}} (formatoi-naytolle->big (- (:toimenpide-budjetoitu-summa toimenpide) (:toimenpide-toteutunut-summa toimenpide)))]
                           [:td {:class (if negatiivinen? "negatiivinen-numero" "numero")
                                 :style {:width (:prosentti leveydet)}} (muotoile-prosentti
                                                                          (big/->big (or (:toimenpide-toteutunut-summa toimenpide) 0))
                                                                          (big/->big (or (:toimenpide-budjetoitu-summa toimenpide) 0))
                                                                          negatiivinen?)]]]
                         muodostetut-tehtavat))))
      toimenpiteet)))

(defn- kustannukset-taulukko [e! app rivit-paaryhmittain]
  (let [hankintakustannusten-toimenpiteet (rivita-toimenpiteet-paaryhmalle e! app (:hankintakustannukset rivit-paaryhmittain))
        hankintakustannukset-negatiivinen? (big/gt (big/->big (or (get-in rivit-paaryhmittain [:hankintakustannukset-toteutunut]) 0))
                                                   (big/->big (or (get-in rivit-paaryhmittain [:hankintakustannukset-budjetoitu]) 0)))
        hallintakorvaus-negatiivinen? (big/gt (big/->big (or (get-in rivit-paaryhmittain [:johto-ja-hallintakorvaus-toteutunut]) 0))
                                              (big/->big (or (get-in rivit-paaryhmittain [:johto-ja-hallintakorvaus-budjetoitu]) 0)))
        hoidonjohdonpalkkio-negatiivinen? (big/gt (big/->big (or (get-in rivit-paaryhmittain [:hoidonjohdonpalkkio-toteutunut]) 0))
                                                  (big/->big (or (get-in rivit-paaryhmittain [:hoidonjohdonpalkkio-budjetoitu]) 0)))
        erillishankinnat-negatiivinen? (big/gt (big/->big (or (get-in rivit-paaryhmittain [:erillishankinnat-toteutunut]) 0))
                                               (big/->big (or (get-in rivit-paaryhmittain [:erillishankinnat-budjetoitu]) 0)))
        yht-negatiivinen? (big/gt (big/->big (or (get-in app [:kustannukset-yhteensa :yht-toteutunut-summa]) 0))
                                  (big/->big (or (get-in app [:kustannukset-yhteensa :yht-budjetoitu-summa]) 0)))
        jjhk-toimenpiteet (rivita-toimenpiteet-paaryhmalle e! app (:johto-ja-hallintakorvaus rivit-paaryhmittain))
        lisatyot (rivita-lisatyot e! app (:lisatyot rivit-paaryhmittain))
        valittu-hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)
        hoitovuosi-nro (kustannusten-seuranta-tiedot/hoitokauden-jarjestysnumero valittu-hoitokauden-alkuvuosi)
        monesko-hoitovuosi (kustannusten-seuranta-tiedot/hoitokauden-jarjestysnumero (pvm/vuosi (pvm/nyt)))
        hoitovuotta-jaljella (if (= hoitovuosi-nro monesko-hoitovuosi)
                               (pvm/montako-paivaa-valissa
                                 (pvm/nyt)
                                 (pvm/->pvm (str "30.09." (inc valittu-hoitokauden-alkuvuosi))))
                               nil)]
    [:div.col-xs-12 {:style {:padding-top "24px"}}
     [:div
      [:h4 "Hoitovuosi: " hoitovuosi-nro " (1.10." valittu-hoitokauden-alkuvuosi " - 09.30." (inc valittu-hoitokauden-alkuvuosi) ")"]
      (when hoitovuotta-jaljella
        [:span "Hoitovuotta on jäljellä " hoitovuotta-jaljella " päivää."])]
     [:div.table-default {:style {:padding-top "24px"}}
      [:table.table-default-header-valkoinen
       [:thead
        [:tr.bottom-border {:style {:text-transform "uppercase"}}
         [:th.paaryhma-center {:style {:width (:caret-paaryhma leveydet)}}]
         [:th.paaryhma-center {:style {:width (:paaryhma-vari leveydet)}}]
         [:th {:style {:width (:tehtava leveydet)}} "Toimenpide"]
         [:th {:style {:width (:budjetoitu leveydet) :text-align "right"}} "Budjetti €"]
         [:th {:style {:width (:toteuma leveydet) :text-align "right"}} "Toteuma €"]
         [:th {:style {:width (:erotus leveydet) :text-align "right"}} "Erotus €"]
         [:th {:style {:width (:prosentti leveydet) :text-align "right"}} "%"]]]
       [:tbody
        [:tr.bottom-border.selectable {:on-click #(e! (kustannusten-seuranta-tiedot/->AvaaRivi :paaryhma :hankintakustannukset))
                                       :key "Hankintakustannukset"}
         [:td.paaryhma-center {:style {:width (:caret-paaryhma leveydet)}}
          (if
            (= :hankintakustannukset (get-in app [:valittu-rivi :paaryhma]))
            [:img {:alt "Expander" :src "images/expander-down.svg"}]
            [:img {:alt "Expander" :src "images/expander.svg"}])]
         [:td.paaryhma-center {:style {:width (:paaryhma-vari leveydet)}} #_[:span.circle.hankintakustannukset]]
         [:td {:style {:width (:tehtava leveydet)
                       :font-weight "700"}} "Hankintakustannukset"]
         [:td.numero {:style {:width (:budjetoitu leveydet)}} (formatoi-naytolle->big (get-in rivit-paaryhmittain [:hankintakustannukset-budjetoitu]))]
         [:td.numero {:style {:width (:toteuma leveydet)}} (formatoi-naytolle->big (get-in rivit-paaryhmittain [:hankintakustannukset-toteutunut]))]
         [:td {:class (if hankintakustannukset-negatiivinen? "negatiivinen-numero" "numero")
               :style {:width (:erotus leveydet)}} (formatoi-naytolle->big (- (get-in rivit-paaryhmittain [:hankintakustannukset-budjetoitu]) (get-in rivit-paaryhmittain [:hankintakustannukset-toteutunut])))]
         [:td {:class (if hankintakustannukset-negatiivinen? "negatiivinen-numero" "numero")
               :style {:width (:prosentti leveydet)}} (muotoile-prosentti
                                                        (big/->big (or (get-in rivit-paaryhmittain [:hankintakustannukset-toteutunut]) 0))
                                                        (big/->big (or (get-in rivit-paaryhmittain [:hankintakustannukset-budjetoitu]) 0))
                                                        hankintakustannukset-negatiivinen?)]]
        (when (= :hankintakustannukset (get-in app [:valittu-rivi :paaryhma]))
          (doall
            (for [l hankintakustannusten-toimenpiteet]
              ^{:key (hash l)}
              l)))

        [:tr.bottom-border.selectable {:key "Johto- ja hallintokorvaukset"
                                       :on-click #(e! (kustannusten-seuranta-tiedot/->AvaaRivi :paaryhma :johto-ja-hallintakorvaus))}
         [:td.paaryhma-center {:style {:width (:caret-paaryhma leveydet)}}
          (if (= :johto-ja-hallintakorvaus (get-in app [:valittu-rivi :paaryhma]))
            [:img {:alt "Expander" :src "images/expander-down.svg"}]
            [:img {:alt "Expander" :src "images/expander.svg"}])]
         [:td.paaryhma-center {:style {:width (:paaryhma-vari leveydet)}} #_[:span.circle.jjh-korvaukset]]
         [:td {:style {:width (:tehtava leveydet)
                       :font-weight "700"}} "Johto- ja hallintokorvaukset"]
         [:td.numero {:style {:width (:budjetoitu leveydet)}} (formatoi-naytolle->big (get-in rivit-paaryhmittain [:johto-ja-hallintakorvaus-budjetoitu]))]
         [:td.numero {:style {:width (:toteuma leveydet)}} (formatoi-naytolle->big (get-in rivit-paaryhmittain [:johto-ja-hallintakorvaus-toteutunut]))]
         [:td {:class (if hallintakorvaus-negatiivinen? "negatiivinen-numero" "numero")
               :style {:width (:erotus leveydet)}} (formatoi-naytolle->big (- (get-in rivit-paaryhmittain [:johto-ja-hallintakorvaus-budjetoitu]) (get-in rivit-paaryhmittain [:johto-ja-hallintakorvaus-toteutunut])))]
         [:td {:class (if hallintakorvaus-negatiivinen? "negatiivinen-numero" "numero")
               :style {:width (:prosentti leveydet)}} (muotoile-prosentti
                                                        (big/->big (or (get-in rivit-paaryhmittain [:johto-ja-hallintakorvaus-toteutunut]) 0))
                                                        (big/->big (or (get-in rivit-paaryhmittain [:johto-ja-hallintakorvaus-budjetoitu]) 0))
                                                        hallintakorvaus-negatiivinen?)]]
        (when (= :johto-ja-hallintakorvaus (get-in app [:valittu-rivi :paaryhma]))
          (doall
            (for [l jjhk-toimenpiteet]
              ^{:key (hash l)}
              l)))

        [:tr.bottom-border {:key "Hoidonjohdonpalkkio"}
         [:td.paaryhma-center {:style {:width (:caret-paaryhma leveydet)}} ""] ;; Hoidonjohdonpalkiolla ei ole toimenpiteitä tai tehtäviä
         [:td.paaryhma-center {:style {:width (:paaryhma-vari leveydet)}} #_[:span.circle.hj-palkkiot]]
         [:td {:style {:width (:tehtava leveydet)
                       :font-weight "700"}} "Hoidonjohdonpalkkio"]
         [:td.numero {:style {:width (:budjetoitu leveydet)}} (formatoi-naytolle->big (get-in rivit-paaryhmittain [:hoidonjohdonpalkkio-budjetoitu]))]
         [:td.numero {:style {:width (:toteuma leveydet)}} (formatoi-naytolle->big (get-in rivit-paaryhmittain [:hoidonjohdonpalkkio-toteutunut]))]
         [:td {:class (if hoidonjohdonpalkkio-negatiivinen? "negatiivinen-numero" "numero")
               :style {:width (:erotus leveydet)}} (formatoi-naytolle->big (- (get-in rivit-paaryhmittain [:hoidonjohdonpalkkio-budjetoitu]) (get-in rivit-paaryhmittain [:hoidonjohdonpalkkio-toteutunut])))]
         [:td {:class (if hoidonjohdonpalkkio-negatiivinen? "negatiivinen-numero" "numero")
               :style {:width (:prosentti leveydet)}} (muotoile-prosentti
                                                        (big/->big (or (get-in rivit-paaryhmittain [:hoidonjohdonpalkkio-toteutunut]) 0))
                                                        (big/->big (or (get-in rivit-paaryhmittain [:hoidonjohdonpalkkio-budjetoitu]) 0))
                                                        hoidonjohdonpalkkio-negatiivinen?)]]

        [:tr.bottom-border {:key "Erillishankinnat"}
         [:td.paaryhma-center {:style {:width (:caret-paaryhma leveydet)}} ""] ;; Erillishankinnoilla ei ole toimenpiteitä tai tehtäviä
         [:td.paaryhma-center {:style {:width (:paaryhma-vari leveydet)}} #_[:span.circle.erillishankinnat]]
         [:td {:style {:width (:tehtava leveydet)
                       :font-weight "700"}} "Erillishankinnat"]
         [:td.numero {:style {:width (:budjetoitu leveydet)}} (formatoi-naytolle->big (get-in rivit-paaryhmittain [:erillishankinnat-budjetoitu]))]
         [:td.numero {:style {:width (:toteuma leveydet)}} (formatoi-naytolle->big (get-in rivit-paaryhmittain [:erillishankinnat-toteutunut]))]
         [:td {:class (if erillishankinnat-negatiivinen? "negatiivinen-numero" "numero")
               :style {:width (:erotus leveydet)}} (formatoi-naytolle->big (- (get-in rivit-paaryhmittain [:erillishankinnat-budjetoitu]) (get-in rivit-paaryhmittain [:erillishankinnat-toteutunut])))]
         [:td {:class (if erillishankinnat-negatiivinen? "negatiivinen-numero" "numero")
               :style {:width (:prosentti leveydet)}} (muotoile-prosentti
                                                        (big/->big (or (get-in rivit-paaryhmittain [:erillishankinnat-toteutunut]) 0))
                                                        (big/->big (or (get-in rivit-paaryhmittain [:erillishankinnat-budjetoitu]) 0))
                                                        erillishankinnat-negatiivinen?)]]
        ; Näytä yhteensä rivi, mikäli toimenpiteitä on olemassa
        (when true
          [:tr.bottom-border
           [:td.paaryhma-center {:style {:width (:caret-paaryhma leveydet)}}]
           [:td.paaryhma-center {:style {:width (:paaryhma-vari leveydet)}}]
           [:td {:style
                 {:width (:tehtava leveydet)
                  :font-weight "700"}}
            (get-in app [:kustannukset-yhteensa :toimenpide])]

           [:td.numero {:style {:width (:budjetoitu leveydet)}} (formatoi-naytolle->big (get-in app [:kustannukset-yhteensa :yht-budjetoitu-summa]))]
           [:td.numero {:style {:width (:toteuma leveydet)}} (formatoi-naytolle->big (get-in app [:kustannukset-yhteensa :yht-toteutunut-summa]))]
           [:td {:class (if yht-negatiivinen? "negatiivinen-numero" "numero")
                 :style {:width (:erotus leveydet)}} (formatoi-naytolle->big (- (get-in app [:kustannukset-yhteensa :yht-budjetoitu-summa]) (get-in app [:kustannukset-yhteensa :yht-toteutunut-summa])))]
           [:td {:class (if yht-negatiivinen? "negatiivinen-numero" "numero")
                 :style {:width (:prosentti leveydet)}} (muotoile-prosentti
                                                          (big/->big (or (get-in app [:kustannukset-yhteensa :yht-toteutunut-summa]) 0))
                                                          (big/->big (or (get-in app [:kustannukset-yhteensa :yht-budjetoitu-summa]) 0))
                                                          yht-negatiivinen?)]])]]
      ;; Lisätyöt
      [:table.table-default-header-valkoinen {:style {:margin-top "32px"}}
       [:tbody
        [:tr.bottom-border.selectable {:key "Lisätyöt"
                                       :on-click #(e! (kustannusten-seuranta-tiedot/->AvaaRivi :paaryhma :lisatyot))}

         [:td.paaryhma-center {:style {:width (:caret-paaryhma leveydet)}}
          (if (= :lisatyot (get-in app [:valittu-rivi :paaryhma]))
            [:img {:alt "Expander" :src "images/expander-down.svg"}]
            [:img {:alt "Expander" :src "images/expander.svg"}])]
         [:td.paaryhma-center {:style {:width (:paaryhma-vari leveydet)}}]
         [:td {:style {:width (:tehtava leveydet) :font-weight "700"}} "Lisätyöt"]
         [:td.numero {:style {:width (:budjetoitu leveydet)}}]
         [:td.numero {:style {:width (:toteuma leveydet)}} (formatoi-naytolle->big (:lisatyot-summa rivit-paaryhmittain))]
         [:td {:style {:width (:erotus leveydet)}}]
         [:td {:style {:width (:prosentti leveydet)}}]]
        (when (= :lisatyot (get-in app [:valittu-rivi :paaryhma]))
          (doall
            (for [l lisatyot]
              ^{:key (hash l)}
              l)))]]]]))

(defn yhteenveto-laatikko [e! app data]
  (let [valittu-hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)
        hoitovuosi-nro (kustannusten-seuranta-tiedot/hoitokauden-jarjestysnumero valittu-hoitokauden-alkuvuosi)
        tavoitehinta (big/->big (or (kustannusten-seuranta-tiedot/hoitokauden-tavoitehinta hoitovuosi-nro app) 0))
        kattohinta (big/->big (or (kustannusten-seuranta-tiedot/hoitokauden-kattohinta hoitovuosi-nro app) 0))
        toteuma (big/->big (or (get-in app [:kustannukset-yhteensa :yht-toteutunut-summa]) 0))]
    [:div.col-xs-12
     [:div.yhteenveto
      [:div.header [:span "Yhteenveto"]]
      [:div.row [:span "Tavoitehinta: "] [:span.pull-right (formatoi-naytolle->big tavoitehinta true)]]
      (when (big/gt toteuma tavoitehinta)
        [:div.row [:span "Tavoitehinnan ylitys: "]
         [:span.negatiivinen-numero.pull-right
          (str "+ " (formatoi-naytolle->big (big/minus toteuma tavoitehinta)))]])
      [:div.row [:span "Kattohinta: "] [:span.pull-right (formatoi-naytolle->big kattohinta true)]]
      (when (big/gt toteuma kattohinta)
        [:div.row [:span "Kattohinnan ylitys: "]
         [:span.negatiivinen-numero.pull-right
          (str "+ " (formatoi-naytolle->big (big/minus toteuma kattohinta)))]])
      [:div.row [:span "Toteuma: "] [:span.pull-right (formatoi-naytolle->big toteuma true)]]

      [:div.row [:span "Lisätyöt: "] [:span.pull-right (formatoi-naytolle->big (:lisatyot-summa data) false)]]]]))

(defn kustannukset
  "Kustannukset listattuna taulukkoon"
  [e! app]
  (let [{:keys [alkupvm]} (-> @tila/tila :yleiset :urakka)  ;; Ota urakan alkamis päivä
        vuosi (pvm/vuosi alkupvm)
        hoitokaudet (into [] (range vuosi (+ 5 vuosi)))
        taulukon-rivit (:kustannukset app)
        valittu-hoitokausi (if (nil? (get-in app [:hoitokauden-alkuvuosi]))
                             2019
                             (get-in app [:hoitokauden-alkuvuosi]))
        valittu-kuukausi (:valittu-kuukausi app)
        hoitokauden-kuukaudet (pvm/aikavalin-kuukausivalit
                                [(pvm/->pvm (str "01.10." valittu-hoitokausi))
                                 (pvm/->pvm (str "30.09." (inc valittu-hoitokausi)))])
        haun-alkupvm (if valittu-kuukausi
                       (first valittu-kuukausi)
                       (str valittu-hoitokausi "-10-01"))
        haun-loppupvm (if valittu-kuukausi
                        (second valittu-kuukausi)
                        (str (inc valittu-hoitokausi) "-09-30"))]
    [:div.kustannusten-seuranta
     [debug/debug app]
     [:div
      [:div.col-xs-12.header {:style {:padding-top "1rem"}}
       [:h1 "Kustannusten seuranta"]
       [:p.urakka (:nimi @nav/valittu-urakka)]
       [:p "Tavoite- ja kattohinnat sekä budjetit on suunniteltu Suunnittelu-puolella.
     Toteutumissa näkyy ne kustannukset, jotka ovat Laskutus-osiossa syötetty järjestelmään."]]
      [:div.row {:style {:padding-top "24px"}}
       [:div.col-xs-6.col-md-3 {:style {:height "61px"}}
        [:span.alasvedon-otsikko "Hoitokausi"]
        [yleiset/livi-pudotusvalikko {:valinta valittu-hoitokausi
                                      :vayla-tyyli? true
                                      :valitse-fn #(e! (kustannusten-seuranta-tiedot/->ValitseHoitokausi (:id @nav/valittu-urakka) %))
                                      :format-fn #(str "1.10." % "-30.9." (inc %))
                                      :klikattu-ulkopuolelle-params {:tarkista-komponentti? true}}
         hoitokaudet]]
       [:div.col-xs-6.col-md-3 {:style {:height "61px"}}
        [:span.alasvedon-otsikko "Kuukausi"]
        [yleiset/livi-pudotusvalikko {:valinta valittu-kuukausi
                                      :vayla-tyyli? true
                                      :valitse-fn #(e! (kustannusten-seuranta-tiedot/->ValitseKuukausi (:id @nav/valittu-urakka) % valittu-hoitokausi))
                                      :format-fn #(if %
                                                    (let [[alkupvm _] %
                                                          kk-teksti (pvm/kuukauden-nimi (pvm/kuukausi alkupvm))]
                                                      (str (str/capitalize kk-teksti) " " (pvm/vuosi alkupvm)))
                                                    "Koko hoitokausi")
                                      :klikattu-ulkopuolelle-params {:tarkista-komponentti? true}}
         hoitokauden-kuukaudet]]
       [:div.col-xs-6.col-md-3 {:style {:height "61px" :padding-top "21px"}}
        ^{:key "raporttixls"}
        [:form {:style {:margin-left "auto"}
                :target "_blank" :method "POST"
                :action (k/excel-url :kustannukset)}
         [:input {:type "hidden" :name "parametrit"
                  :value (transit/clj->transit {:urakka-id (:id @nav/valittu-urakka)
                                                :urakka-nimi (:nimi @nav/valittu-urakka)
                                                :hoitokauden-alkuvuosi valittu-hoitokausi
                                                :alkupvm haun-alkupvm
                                                :loppupvm haun-loppupvm})}]
         [:button {:type "submit"
                   :class #{"button-secondary-default" "suuri"}} "Tallenna Excel"]]]]]

     [kustannukset-taulukko e! app taulukon-rivit]
     [yhteenveto-laatikko e! app taulukon-rivit]]))

(defn kustannusten-seuranta* [e! app]
  (komp/luo
    (komp/lippu tila/kustannusten-seuranta-nakymassa?)
    (komp/piirretty (fn [this]
                      (do
                        (e! (kustannusten-seuranta-tiedot/->HaeBudjettitavoite))
                        (e! (kustannusten-seuranta-tiedot/->HaeKustannukset (:hoitokauden-alkuvuosi app)
                                                                            nil nil)))))
    (fn [e! app]
      [:div {:id "vayla"}
       [:div
        [kustannukset e! app]]])))

(defn kustannusten-seuranta []
  (tuck/tuck tila/kustannusten-seuranta kustannusten-seuranta*))