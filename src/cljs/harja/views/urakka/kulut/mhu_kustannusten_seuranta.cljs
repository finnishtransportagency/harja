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
            [harja.tiedot.urakka :as urakka-tiedot]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.kulut.mhu-kustannusten-seuranta :as kustannusten-seuranta-tiedot]
            [harja.domain.kulut.kustannusten-seuranta :as kustannusten-seuranta]
            [harja.domain.skeema :refer [+tyotyypit+]]
            [harja.tyokalut.big :as big]
            [harja.ui.napit :as napit]
            [harja.views.urakka.kulut.valikatselmus :as valikatselmus]
            [harja.views.urakka.kulut.yhteiset :refer [fmt->big yhteenveto-laatikko]]
            [harja.ui.ikonit :as ikonit]
            [harja.tiedot.urakka.kulut.yhteiset :as t-yhteiset])
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
     (str (big/fmt (big/mul (big/->big 100) (big/div toteuma suunniteltu)) 2) " %")]))

(defn- negatiivinen? [avain rivit-paaryhmittain]
  (big/gt (big/->big (or ((keyword (str (name avain) "-toteutunut")) rivit-paaryhmittain) 0))
    (big/->big (or ((keyword (str (name avain) "-budjetoitu-indeksikorjattu")) rivit-paaryhmittain) 0))))

; spekseistä laskettu
(def leveydet {:caret-paaryhma "2%"
               :paaryhma-vari "2%"
               :tehtava "34%"
               :suunniteltu "13%"
               :indeksikorjattu "13%"
               :toteuma "13%"
               :erotus "13%"
               :prosentti "10%"})

(defn- lisaa-taulukkoon-tehtava-rivi [nimi budjetoitu indeksikorjattu vahvistettu toteuma]
  [:tr.bottom-border {:key (hash (str nimi toteuma indeksikorjattu budjetoitu))}
   [:td.paaryhma-center {:style {:width (:caret-paaryhma leveydet)}}]
   [:td.paaryhma-center {:style {:width (:paaryhma-vari leveydet)}}]
   [:td {:style {:width (:tehtava leveydet)}} nimi]
   [:td.numero {:style {:width (:budjetoitu leveydet)}} (when-not (= "0,00" budjetoitu) budjetoitu)]
   [:td.numero {:style {:width (:toteuma leveydet)}} (when-not (= "0,00" toteuma) toteuma)]
   [:td.numero {:style {:width (:erotus leveydet)}}]
   [:td.numero {:style {:width (:prosentti leveydet)}}]])

(defn- taulukoi-paaryhman-tehtavat
  "Listataan kaksiportaisen pääryhmän tehtävät. Eli älä käytä tätä, mikäli pääryhmällä on toimenpiteitä."
  [tehtavat]
  (for [l tehtavat]
    ^{:key (str (hash l))}
    (lisaa-taulukkoon-tehtava-rivi
      [:span.taso2 (or (:tehtava_nimi l) (:toimenpidekoodi_nimi l))]
      (fmt->big (indeksikorjattu-tai-summa :budjetoitu_summa "_" l) false)
      (fmt->big (:toteutunut_summa l) false))))

(defn- taulukoi-toimenpiteen-tehtavat
  "Listaa vain kolmiportaisten pääryhmien tehtävät. Jos pääryhmällä ei ole toimenpiteitä, tätä ei tule käyttää."
  [toimenpide tehtavat]
  (when tehtavat
    (mapcat
      (fn [rivi]
        (let [toteutunut-summa (big/->big (or (:toteutunut_summa rivi) 0))
              budjetoitu-summa (big/->big (or (indeksikorjattu-tai-summa :budjetoitu_summa "_" rivi) 0))]
          (concat
            [^{:key (str toimenpide "-" (hash rivi))}
             (lisaa-taulukkoon-tehtava-rivi [:span {:style {:padding-left "16px"}} (:tehtava_nimi rivi)]
                                            (fmt->big budjetoitu-summa false)
                                            (fmt->big toteutunut-summa false))])))
      tehtavat)))

(defn- rivita-toimenpiteet-paaryhmalle
  "Suunniteltu erikseen hankintakustannuksille, rahavarauksille, johto ja hallintokorvauksille ja hoidonjohdonpalkkioille,
  joilla kaikilla on sekä pääryhmä, toimenpiteet, että tehtävät. Osalla pääryhmistä tulee tehtävät suoraa pääryhmän alle,
  jolloin tätä funkkaria ei tarvita."
  [e! app toimenpiteet]
  (map
    (fn [toimenpide]
      (let [paaryhma (:paaryhma toimenpide)
            toimenpide-nimi (:toimenpide toimenpide)
            rivi-avain (keyword (str paaryhma "-" toimenpide-nimi))
            hankinta-tehtavat (filter #(= "hankinta" (:toimenpideryhma %)) (:tehtavat toimenpide))
            rahavaraus-tehtavat (filter #(= "rahavaraus" (:toimenpideryhma %)) (:tehtavat toimenpide))
            toimistokulu-tehtavat (filter #(= "toimistokulut" (:toimenpideryhma %)) (:tehtavat toimenpide))
            palkka-tehtavat (filter #(= "palkat" (:toimenpideryhma %)) (:tehtavat toimenpide))
            negatiivinen? (big/gt (big/->big (or (:toimenpide-toteutunut-summa toimenpide) 0))
                                  (big/->big (or (indeksikorjattu-tai-summa :toimenpide-budjetoitu-summa toimenpide) 0)))
            muodostetut-tehtavat (if-not (contains? (:avatut-rivit app) rivi-avain)
                                   nil
                                   (concat
                                     (taulukoi-toimenpiteen-tehtavat toimenpide toimistokulu-tehtavat)
                                     (taulukoi-toimenpiteen-tehtavat toimenpide palkka-tehtavat)
                                     (taulukoi-toimenpiteen-tehtavat toimenpide hankinta-tehtavat)
                                     (taulukoi-toimenpiteen-tehtavat toimenpide rahavaraus-tehtavat)))]
        (doall (concat [^{:key (str "otsikko-" (hash toimenpide) "-" (hash toimenpiteet))}
                        [:tr.bottom-border
                         (merge
                           (when (> (count (:tehtavat toimenpide)) 0)
                             {:class "selectable"
                              :on-click #(e! (kustannusten-seuranta-tiedot/->AvaaRivi rivi-avain))}))
                         [:td.paaryhma-center {:style {:width (:caret-paaryhma leveydet)}}]
                         [:td.paaryhma-center {:style {:width (:paaryhma-vari leveydet)}}
                          (when (> (count (:tehtavat toimenpide)) 0)
                            (if (contains? (:avatut-rivit app) rivi-avain)
                              [:img {:alt "Expander" :src "images/expander-down.svg"}]
                              [:img {:alt "Expander" :src "images/expander.svg"}]))]
                         [:td {:style {:width (:tehtava leveydet)
                                       :padding-left "32px"}} (:toimenpide toimenpide)]
                         [:td.numero {:style {:width (:budjetoitu leveydet)}} (fmt->big (indeksikorjattu-tai-summa :toimenpide-budjetoitu-summa toimenpide))]
                         [:td.numero {:style {:width (:toteuma leveydet)}} (fmt->big (:toimenpide-toteutunut-summa toimenpide))]
                         [:td {:class (if negatiivinen? "negatiivinen-numero" "numero")
                               :style {:width (:erotus leveydet)}} (str (when negatiivinen? "+ ") (fmt->big (- (:toimenpide-toteutunut-summa toimenpide)
                                                                                                               (indeksikorjattu-tai-summa :toimenpide-budjetoitu-summa toimenpide))))]
                         [:td {:class (if negatiivinen? "negatiivinen-numero" "numero")
                               :style {:width (:prosentti leveydet)}} (muotoile-prosentti
                                                                        (big/->big (or (indeksikorjattu-tai-summa :toimenpide-toteutunut-summa toimenpide) 0))
                                                                        (big/->big (or (indeksikorjattu-tai-summa :toimenpide-budjetoitu-summa toimenpide) 0))
                                                                        negatiivinen?)]]]
                       muodostetut-tehtavat))))
    toimenpiteet))

(defn- paaryhma-taulukkoon [e! app paaryhma paaryhma-avain toimenpiteet negatiivinen? budjetoitu toteutunut erotus prosentti]
  (let [row-index (r/atom 0)]
    (doall (concat
             [^{:key (str paaryhma "-" (hash toimenpiteet))}
              [:tr.bottom-border.selectable {:on-click #(e! (kustannusten-seuranta-tiedot/->AvaaRivi paaryhma-avain))
                                             :key paaryhma}
               [:td.paaryhma-center {:style {:width (:caret-paaryhma leveydet)}}
                (if (and (> (count toimenpiteet) 0)
                         (contains? (:avatut-rivit app) paaryhma-avain))
                  [:img {:alt "Expander" :src "images/expander-down.svg"}]
                  (when (> (count toimenpiteet) 0)
                    [:img {:alt "Expander" :src "images/expander.svg"}]))]
               [:td.paaryhma-center {:style {:width (:paaryhma-vari leveydet)}}]
               [:td {:style {:width (:tehtava leveydet)
                             :font-weight "700"}} paaryhma]
               [:td.numero {:style {:width (:budjetoitu leveydet)}} budjetoitu]
               [:td.numero {:style {:width (:toteuma leveydet)}} toteutunut]
               [:td {:class (if negatiivinen? "negatiivinen-numero" "numero")
                     :style {:width (:erotus leveydet)}} (str (when negatiivinen? "+ ") erotus)]
               [:td {:class (if negatiivinen? "negatiivinen-numero" "numero")
                     :style {:width (:prosentti leveydet)}} prosentti]]]

             (when (contains? (:avatut-rivit app) paaryhma-avain)
               (mapcat (fn [rivi]
                         (let [_ (reset! row-index (inc @row-index))]
                           [^{:key (str @row-index "-" (hash rivi))}
                            rivi]))
                       toimenpiteet))))))

(defn- paaryhma-taulukkoon [e! app paaryhma paaryhma-avain toimenpiteet rivit-paaryhmittain]
  (let [row-index (r/atom 0)
        neg? (negatiivinen? paaryhma-avain rivit-paaryhmittain)
        budjetoitu (fmt->big ((keyword (str (name paaryhma-avain) "-budjetoitu")) rivit-paaryhmittain))
        indeksikorjattu (fmt->big ((keyword (str (name paaryhma-avain) "-budjetoitu-indeksikorjattu")) rivit-paaryhmittain))
        toteutunut (fmt->big ((keyword (str (name paaryhma-avain) "-toteutunut")) rivit-paaryhmittain))
        erotus (fmt->big (- ((keyword (str (name paaryhma-avain) "-toteutunut")) rivit-paaryhmittain)
                           ((keyword (str (name paaryhma-avain) "-budjetoitu-indeksikorjattu")) rivit-paaryhmittain)))
        prosentti (muotoile-prosentti
                    (big/->big (or ((keyword (str (name paaryhma-avain) "-toteutunut")) rivit-paaryhmittain) 0))
                    (big/->big (or ((keyword (str (name paaryhma-avain) "-budjetoitu-indeksikorjattu")) rivit-paaryhmittain) 0))
                    neg?)
        vahvistettu ((keyword (str (name paaryhma-avain) "-indeksikorjaus-vahvistettu")) rivit-paaryhmittain)]
    (doall (concat
             [^{:key (str paaryhma "-" (hash toimenpiteet))}
              [:tr.bottom-border.selectable {:on-click #(e! (kustannusten-seuranta-tiedot/->AvaaRivi paaryhma-avain))
                                             :key paaryhma}
               [:td.paaryhma-center {:style {:width (:caret-paaryhma leveydet)}}
                (if (and (> (count toimenpiteet) 0)
                      (contains? (:avatut-rivit app) paaryhma-avain))
                  [:img {:alt "Expander" :src "images/expander-down.svg"}]
                  (when (> (count toimenpiteet) 0)
                    [:img {:alt "Expander" :src "images/expander.svg"}]))]
               [:td.paaryhma-center {:style {:width (:paaryhma-vari leveydet)}}]
               [:td {:style {:width (:tehtava leveydet)
                             :font-weight "700"}} paaryhma]
               [:td.numero {:style {:width (:suunniteltu leveydet)}} budjetoitu]
               [:td.numero {:style
                            (merge
                              {:width (:indeksikorjattu leveydet)}
                              (when (or (false? vahvistettu) (nil? vahvistettu))
                                {:color "orange"}))} indeksikorjattu]
               [:td.numero {:style {:width (:toteuma leveydet)}} toteutunut]
               [:td {:class (if neg? "negatiivinen-numero" "numero")
                     :style {:width (:erotus leveydet)}} (str (when neg? "+ ") erotus)]
               [:td {:class (if neg? "negatiivinen-numero" "numero")
                     :style {:width (:prosentti leveydet)}} prosentti]]]

             (when (contains? (:avatut-rivit app) paaryhma-avain)
               (mapcat (fn [rivi]
                         (let [_ (reset! row-index (inc @row-index))]
                           [^{:key (str @row-index "-" (hash rivi))}
                            rivi]))
                 toimenpiteet))))))

(defn- kustannukset-taulukko [e! app rivit-paaryhmittain]
  (let [hankintakustannusten-toimenpiteet (rivita-toimenpiteet-paaryhmalle e! app (:hankintakustannukset rivit-paaryhmittain))
        hoidonjohdonpalkkiot (taulukoi-paaryhman-tehtavat (:tehtavat (:hoidonjohdonpalkkio rivit-paaryhmittain)))
        erillishankinnat (taulukoi-paaryhman-tehtavat (:tehtavat (:erillishankinnat rivit-paaryhmittain)))
        jjhk-toimenpiteet (rivita-toimenpiteet-paaryhmalle e! app (:johto-ja-hallintakorvaus rivit-paaryhmittain))
        lisatyot (taulukoi-paaryhman-tehtavat (:lisatyot rivit-paaryhmittain))
        rahavaraukset-toimenpiteet (rivita-toimenpiteet-paaryhmalle e! app (:rahavaraukset rivit-paaryhmittain))
        bonukset (taulukoi-paaryhman-tehtavat (:tehtavat (:bonukset rivit-paaryhmittain)))
        siirto-toteutunut (get-in rivit-paaryhmittain [:siirto :siirto-toteutunut])
        siirto-negatiivinen? (neg? (or siirto-toteutunut 0))
        siirtoa-viime-vuodelta? (not (or (nil? siirto-toteutunut) (= 0 siirto-toteutunut)))
        tavoitehinnanoikaisut (taulukoi-paaryhman-tehtavat (get-in rivit-paaryhmittain [:tavoitehinnanoikaisu :tehtavat]))
        valittu-hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)
        valittu-hoitovuosi-nro (urakka-tiedot/hoitokauden-jarjestysnumero valittu-hoitokauden-alkuvuosi (-> @tila/yleiset :urakka :loppupvm))
        hoitovuosi-nro-menossa (urakka-tiedot/kuluva-hoitokausi-nro (pvm/nyt) (-> @tila/yleiset :urakka :loppupvm))
        hoitovuotta-jaljella (if (= valittu-hoitovuosi-nro hoitovuosi-nro-menossa)
                               (pvm/montako-paivaa-valissa
                                 (pvm/nyt)
                                 (pvm/->pvm (str "30.09." (inc valittu-hoitokauden-alkuvuosi))))
                               nil)]
    [:div.row.sivuelementti
     [:div.col-xs-12
      [:h4 "Hoitovuosi: " valittu-hoitovuosi-nro " (1.10." valittu-hoitokauden-alkuvuosi " - 09.30." (inc valittu-hoitokauden-alkuvuosi) ")"]
      (when hoitovuotta-jaljella
        [:span "Hoitovuotta on jäljellä " hoitovuotta-jaljella " päivää."])]]
    [:div.row.sivuelementti
     [:div.col-xs-12.col-md-9
      [:div.table-default
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
         (paaryhma-taulukkoon e! app "Suunnitellut hankinnat" :hankintakustannukset hankintakustannusten-toimenpiteet rivit-paaryhmittain)
         (paaryhma-taulukkoon e! app "Rahavaraukset" :rahavaraukset rahavaraukset-toimenpiteet rivit-paaryhmittain)
         (paaryhma-taulukkoon e! app "Johto- ja hallintokorvaukset" :johto-ja-hallintakorvaus jjhk-toimenpiteet rivit-paaryhmittain)
         (paaryhma-taulukkoon e! app "Hoidonjohdonpalkkio" :hoidonjohdonpalkkio hoidonjohdonpalkkiot rivit-paaryhmittain)
         (paaryhma-taulukkoon e! app "Erillishankinnat" :erillishankinnat erillishankinnat rivit-paaryhmittain)
         ;; Näytetään tavoitehinnanoikaisut vain, jos niitä on oikeasti lisätty ja käytetty
         (when (> (count (get-in rivit-paaryhmittain [:tavoitehinnanoikaisu :tehtavat])) 0)
           (paaryhma-taulukkoon e! app "Tavoitehinnan oikaisut" :tavoitehinnanoikaisu tavoitehinnanoikaisut rivit-paaryhmittain))
         ;; Siirto rivi
         (when siirtoa-viime-vuodelta?
           [:tr.bottom-border.tummennettu
            [:td.paaryhma-center {:style {:width (:caret-paaryhma leveydet)}}]
            [:td.paaryhma-center {:style {:width (:paaryhma-vari leveydet)}}]
            [:td {:style {:width (:tehtava leveydet)
                          :font-weight "700"}}
             "Siirto edelliseltä vuodelta"]

            [:td.numero {:style {:width (:budjetoitu leveydet)}}]
            ;; Näytetään plusmerkkinen siirto punaisena, siksi positiivinen->negatiivinen
            [:td.numero {:class (if siirto-negatiivinen? "numero" "negatiivinen-numero")
                         :style {:width (:toteuma leveydet)}} (str (when-not siirto-negatiivinen? "+ ") (fmt->big (get-in rivit-paaryhmittain [:siirto :siirto-toteutunut])))]
            [:td {:style {:width (:erotus leveydet)}}]
            [:td {:style {:width (:prosentti leveydet)}}]])

         ; Näytä yhteensä rivi
         [:tr.bottom-border
          [:td.paaryhma-center {:style {:width (:caret-paaryhma leveydet)}}]
          [:td.paaryhma-center {:style {:width (:paaryhma-vari leveydet)}}]
          [:td {:style {:width (:tehtava leveydet)
                        :font-weight "700"}}
           (get-in app [:kustannukset-yhteensa :toimenpide])]

          [:td.numero {:style {:width (:budjetoitu leveydet)}} (fmt->big (indeksikorjattu-tai-summa :yht-budjetoitu-summa (get app :kustannukset-yhteensa)))]
          [:td.numero {:style {:width (:toteuma leveydet)}} (fmt->big (get-in app [:kustannukset-yhteensa :yht-toteutunut-summa]))]
          [:td {:class (if yht-negatiivinen? "negatiivinen-numero" "numero")
                :style {:width (:erotus leveydet)}} (str (when yht-negatiivinen? "+ ") (fmt->big (- (get-in app [:kustannukset-yhteensa :yht-toteutunut-summa])
                                                                                                                  (indeksikorjattu-tai-summa :yht-budjetoitu-summa (get app :kustannukset-yhteensa)))))]
          [:td {:class (if yht-negatiivinen? "negatiivinen-numero" "numero")
                :style {:width (:prosentti leveydet)}} (muotoile-prosentti
                                                         (big/->big (or (get-in app [:kustannukset-yhteensa :yht-toteutunut-summa]) 0))
                                                         (big/->big (or (indeksikorjattu-tai-summa :yht-budjetoitu-summa (get app :kustannukset-yhteensa)) 0))
                                                         yht-negatiivinen?)]]]]
       ;; Lisätyöt
       [:table.table-default-header-valkoinen {:style {:margin-top "32px"}}
        [:tbody
         [:tr.bottom-border.selectable {:key "Lisätyöt"
                                        :on-click #(e! (kustannusten-seuranta-tiedot/->AvaaRivi :lisatyot))}
          [:td.paaryhma-center {:style {:width (:caret-paaryhma leveydet)}}
           (if (and (contains? (:avatut-rivit app) :lisatyot)
                    (> (count lisatyot) 0))
             [:img {:alt "Expander" :src "images/expander-down.svg"}]
             (when (> (count lisatyot) 0)
               [:img {:alt "Expander" :src "images/expander.svg"}]))]
          [:td.paaryhma-center {:style {:width (:paaryhma-vari leveydet)}}]
          [:td {:style {:width (:tehtava leveydet) :font-weight "700"}} "Lisätyöt"]
          [:td.numero {:style {:width (:budjetoitu leveydet)}}]
          [:td.numero {:style {:width (:toteuma leveydet)}} (fmt->big (:lisatyot-summa rivit-paaryhmittain))]
          [:td {:style {:width (:erotus leveydet)}}]
          [:td {:style {:width (:prosentti leveydet)}}]]
         (when (contains? (:avatut-rivit app) :lisatyot)
           (doall
             (for [l lisatyot]
               ^{:key (hash l)}
               l)))


(defn kustannukset
  "Kustannukset listattuna taulukkoon"
  [e! app]
  (let [{:keys [alkupvm]} (-> @tila/tila :yleiset :urakka) ;; Ota urakan alkamis päivä
        vuosi (pvm/vuosi alkupvm)
        hoitokaudet (into [] (range vuosi (+ 5 vuosi)))
        taulukon-rivit (:kustannukset app)
        valittu-hoitokausi (if (nil? (:hoitokauden-alkuvuosi app))
                             2019
                             (:hoitokauden-alkuvuosi app))
        valittu-kuukausi (:valittu-kuukausi app)
        hoitokauden-kuukaudet (vec (pvm/aikavalin-kuukausivalit
                                     [(pvm/hoitokauden-alkupvm valittu-hoitokausi)
                                      (pvm/hoitokauden-loppupvm (inc valittu-hoitokausi))]))
        hoitokauden-kuukaudet (into ["Kaikki"] hoitokauden-kuukaudet)
        haun-alkupvm (if (and valittu-kuukausi (not= "Kaikki" valittu-kuukausi))
                       (first valittu-kuukausi)
                       (pvm/iso8601 (pvm/hoitokauden-alkupvm valittu-hoitokausi)))
        haun-loppupvm (if (and valittu-kuukausi (not= "Kaikki" valittu-kuukausi))
                        (second valittu-kuukausi)
                        (pvm/iso8601 (pvm/hoitokauden-loppupvm (inc valittu-hoitokausi))))
        valikatselmus-tekematta? (t-yhteiset/valikatselmus-tekematta? app)]
    [:div.kustannusten-seuranta
     [debug/debug app]
     [:div
      [:div.row.header
       [:div.col-xs-12
        [:h1 "Kustannusten seuranta"]
        [:p.urakka (:nimi @nav/valittu-urakka)]
        [:p "Tavoite- ja kattohinnat sekä budjetit on suunniteltu Suunnittelu-puolella.
     Toteutumissa näkyy ne kustannukset, jotka ovat Laskutus-osiossa syötetty järjestelmään."]
        [:p "Taulukossa näkyvät luvut ovat indeksikorjattuja, mikäli indeksit ovat saatavilla."]]] ;; Ei speksissä, voi poistaa jos ei ole tarpeellinen.

      [:div.row.filtterit-container
       [:div.filtteri
        [:span.alasvedon-otsikko-vayla "Hoitovuosi"]
        [yleiset/livi-pudotusvalikko {:valinta valittu-hoitokausi
                                      :vayla-tyyli? true
                                      :data-cy "hoitokausi-valinta"
                                      :valitse-fn #(do (e! (kustannusten-seuranta-tiedot/->ValitseHoitokausi (:id @nav/valittu-urakka) %))
                                                       (e! (t-yhteiset/->NollaaValikatselmuksenPaatokset)))
                                      :format-fn #(str kustannusten-seuranta-tiedot/fin-hk-alkupvm % "-" kustannusten-seuranta-tiedot/fin-hk-loppupvm (inc %))
                                      :klikattu-ulkopuolelle-params {:tarkista-komponentti? true}}
         hoitokaudet]]
       [:div.filtteri.kuukausi
        [:span.alasvedon-otsikko-vayla "Kuukausi"]
        [yleiset/livi-pudotusvalikko {:valinta valittu-kuukausi
                                      :vayla-tyyli? true
                                      :valitse-fn #(e! (kustannusten-seuranta-tiedot/->ValitseKuukausi (:id @nav/valittu-urakka) % valittu-hoitokausi))
                                      :format-fn #(if %
                                                    (if (= "Kaikki" %)
                                                      "Kaikki"
                                                      (let [[alkupvm _] %
                                                            kk-teksti (pvm/kuukauden-nimi (pvm/kuukausi alkupvm))]
                                                        (str (str/capitalize kk-teksti) " " (pvm/vuosi alkupvm))))
                                                    "Kaikki")
                                      :klikattu-ulkopuolelle-params {:tarkista-komponentti? true}}
         hoitokauden-kuukaudet]]
       [:div.filtteri {:style {:padding-top "21px"}}
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
                   :class "nappi-toissijainen"}
          [ikonit/ikoni-ja-teksti [ikonit/livicon-download] "Tallenna Excel"]]]]
       [:div.filtteri {:style {:padding-top "21px"}}
        (if valikatselmus-tekematta?
          [napit/yleinen-ensisijainen
           "Tee välikatselmus"
           #(e! (kustannusten-seuranta-tiedot/->AvaaValikatselmusLomake))]

          [napit/yleinen-ensisijainen "Avaa välikatselmus" #(e! (kustannusten-seuranta-tiedot/->AvaaValikatselmusLomake)) {:luokka "napiton-nappi tumma" :ikoni (ikonit/harja-icon-action-show)}])]]]
     (if (:haku-kaynnissa? app)
       [:div {:style {:padding-left "20px"}} [yleiset/ajax-loader "Haetaan käynnissä"]]
       [:div
        [kustannukset-taulukko e! app taulukon-rivit]
        [yhteenveto-laatikko e! app taulukon-rivit :kustannusten-seuranta]])]))

(defn kustannusten-seuranta* [e! app]
  (komp/luo
    (komp/lippu tila/kustannusten-seuranta-nakymassa?)
    (komp/piirretty (fn [this]
                      (do
                        (e! (kustannusten-seuranta-tiedot/->HaeBudjettitavoite))
                        (e! (kustannusten-seuranta-tiedot/->HaeKustannukset (:hoitokauden-alkuvuosi app)
                                                                            (if (= "Kaikki" (:valittu-kuukausi app))
                                                                              nil
                                                                              (first (:valittu-kuukausi app)))
                                                                            (if (= "Kaikki" (:valittu-kuukausi app))
                                                                              nil
                                                                              (second (:valittu-kuukausi app)))))
                        (e! (kustannusten-seuranta-tiedot/->HaeTavoitehintojenOikaisut (:id @nav/valittu-urakka)))
                        (e! (kustannusten-seuranta-tiedot/->HaeKattohintojenOikaisut (:id @nav/valittu-urakka)))
                        (e! (kustannusten-seuranta-tiedot/->HaeUrakanPaatokset (:id @nav/valittu-urakka))))))
    (fn [e! {:keys [valikatselmus-auki?] :as app}]
      [:div {:id "vayla"}
       (if valikatselmus-auki?
         [:div
          [valikatselmus/valikatselmus e! app]]
         [:div
          [kustannukset e! app]])])))

(defn kustannusten-seuranta []
  (tuck/tuck tila/kustannusten-seuranta kustannusten-seuranta*))
