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
            [harja.fmt :as fmt]
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
            [harja.tiedot.urakka.siirtymat :as siirtymat]
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

(defn- lisaa-taulukkoon-tehtava-rivi [nimi budjetoitu indeksikorjattu vahvistettu toteuma erotus prosentti ]
  [:tr.bottom-border {:key (hash (str nimi toteuma indeksikorjattu budjetoitu))}
   [:td.paaryhma-center {:style {:width (:caret-paaryhma leveydet)}}]
   [:td.paaryhma-center {:style {:width (:paaryhma-vari leveydet)}}]
   [:td {:style {:width (:tehtava leveydet)}} nimi]
   [:td.numero {:style {:width (:suunniteltu leveydet)}} (when-not (= "0,00" budjetoitu) budjetoitu)]
   [:td.numero {:class (when (false? vahvistettu)
                                "vahvistamatta")
                :style {:width (:indeksikorjattu leveydet)}}
    (when-not (= "0,00" indeksikorjattu) indeksikorjattu)]
   [:td.numero {:style {:width (:toteuma leveydet)}} (when-not (= "0,00" toteuma) toteuma)]
   [:td.numero {:style {:width (:erotus leveydet)}} (when erotus erotus)]
   [:td.numero {:style {:width (:prosentti leveydet)}} (when prosentti prosentti)]])

(defn- taulukoi-paaryhman-tehtavat
  "Listataan kaksiportaisen pääryhmän tehtävät. Eli älä käytä tätä, mikäli pääryhmällä on toimenpiteitä."
  [paaryhma-avain tehtavat]
  (for [l tehtavat
        :let [vahvistettu ((keyword (str (name paaryhma-avain) "-indeksikorjaus-vahvistettu")) l)]]
    ^{:key (str (hash l))}
    (lisaa-taulukkoon-tehtava-rivi
      [:span.taso2 (or (:tehtava_nimi l) (:toimenpidekoodi_nimi l))]
      (fmt->big (:budjetoitu_summa l) false)
      (fmt->big (:budjetoitu_summa_indeksikorjattu l) false)
      vahvistettu
      (fmt->big (:toteutunut_summa l) false)
      nil
      nil)))


(defn- tehtavatason-rivitys
  "Listaa vain kolmiportaisten pääryhmien tehtävät, eli kolmannen portaan. Jos pääryhmällä ei ole toimenpiteitä, tätä ei tule käyttää."
  [toimenpide tehtavat nayta-erotus?]
  (when tehtavat
    (mapcat
      (fn [rivi]
        (let [toteutunut-summa (or (:toteutunut_summa rivi) 0)
              budjetoitu-summa (or (:budjetoitu_summa rivi) 0)
              budjetoitu-summa-indeksikorjattu (or (:budjetoitu_summa_indeksikorjattu rivi) 0)
              erotus (- toteutunut-summa budjetoitu-summa-indeksikorjattu)
              neg? (big/gt (big/->big toteutunut-summa) (big/->big budjetoitu-summa-indeksikorjattu))]
          (concat
            [^{:key (str toimenpide "-" (hash rivi))}
             (lisaa-taulukkoon-tehtava-rivi [:span {:style {:padding-left "16px"}} (:tehtava_nimi rivi)]
               (fmt->big (big/->big budjetoitu-summa) false)
               (fmt->big budjetoitu-summa-indeksikorjattu false)
               true ;; Kaikki kolmannen portaan tehtävät merkitään "vahvistetuksi" koska niille ei näytetä summaa
               (fmt->big (big/->big toteutunut-summa) false)
               (when nayta-erotus? (fmt->big erotus false))
               (when nayta-erotus?
                 (muotoile-prosentti
                   (big/->big toteutunut-summa)
                   (big/->big budjetoitu-summa-indeksikorjattu)
                   neg?)))])))
      tehtavat)))

(defn- toimenpidetason-rivitys
  "Suunniteltu erikseen hankintakustannuksille, rahavarauksille, johto ja hallintokorvauksille,
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
                            (big/->big (or (:toimenpide-budjetoitu-summa-indeksikorjattu toimenpide) 0)))
            muodostetut-tehtavat (if-not (contains? (:avatut-rivit app) rivi-avain)
                                   nil
                                   (concat
                                     (tehtavatason-rivitys toimenpide toimistokulu-tehtavat false)
                                     (tehtavatason-rivitys toimenpide palkka-tehtavat false)
                                     (tehtavatason-rivitys toimenpide hankinta-tehtavat false)
                                     (tehtavatason-rivitys toimenpide rahavaraus-tehtavat true)))
            vahvistettu? (or (nil? (get toimenpide (keyword (str paaryhma "-indeksikorjaus-vahvistettu") )))
                           (true? (get toimenpide (keyword (str paaryhma "-indeksikorjaus-vahvistettu") ))))]
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
                                       :padding-left "8px"}} (:toimenpide toimenpide)]
                         [:td.numero {:style {:width (:suunniteltu leveydet)}} (fmt->big (:toimenpide-budjetoitu-summa toimenpide))]
                         [:td.numero {:class (when (false? vahvistettu?)
                                                      "vahvistamatta")
                                      :style {:width (:indeksikorjattu leveydet)}}
                          (fmt->big (:toimenpide-budjetoitu-summa-indeksikorjattu toimenpide))]
                         [:td.numero {:style {:width (:toteuma leveydet)}} (fmt->big (:toimenpide-toteutunut-summa toimenpide))]
                         [:td {:class (if negatiivinen? "negatiivinen-numero" "numero")
                               :style {:width (:erotus leveydet)}} (str (when negatiivinen? "+ ") (fmt->big (- (:toimenpide-toteutunut-summa toimenpide)
                                                                                                              (:toimenpide-budjetoitu-summa-indeksikorjattu toimenpide))))]
                         [:td {:class (if negatiivinen? "negatiivinen-numero" "numero")
                               :style {:width (:prosentti leveydet)}} (muotoile-prosentti
                                                                        (big/->big (or (:toimenpide-toteutunut-summa toimenpide) 0))
                                                                        (big/->big (or (:toimenpide-budjetoitu-summa-indeksikorjattu toimenpide) 0))
                                                                        negatiivinen?)]]]
                 ;; Lisää kolmannen tason eli tehtävätason hiccup koodit seuraavaksi
                 muodostetut-tehtavat))))
    toimenpiteet))

(defn- paaryhman-rivitys [e! app paaryhma paaryhma-avain toimenpiteet rivit-paaryhmittain]
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
        vahvistettu (get rivit-paaryhmittain (keyword (str (name paaryhma-avain) "-indeksikorjaus-vahvistettu")))]
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
               [:td.numero {:class (when (or (false? vahvistettu))
                                     "vahvistamatta")
                            :style {:width (:indeksikorjattu leveydet)}
                            ;; Alustavaa hahmotelmaa, miten voitaisiin saada siirtymä kustannusten suunnitteluun
                            ;; Voidaan tehdä loppuun, kun kustannusten suunnittelu on ensin refaktoroitu kokonaan
                            ;:on-click
                            #_(fn [e]
                                (do
                                  (.preventDefault e)
                                  (siirtymat/kustannusten-seurantaan paaryhma)))}
                indeksikorjattu]
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

(defn- onko-kaikki-vahvistettu?
  "Etsitään vain false statuksen omaavia pääryhmän tehtäviä. Nil on silloin, kun pääryhmällä ei ole tehtäviä ja
  vahvistus-statusta ei voi tietää"
  [avain-set rivit-paaryhmittain]
  (let [kaikki-vahvistettu? (every? (fn [avain]
                                     (if
                                       (or (true? (get rivit-paaryhmittain (keyword (str (name avain) "-indeksikorjaus-vahvistettu"))))
                                         (nil? (get rivit-paaryhmittain (keyword (str (name avain) "-indeksikorjaus-vahvistettu")))))
                                       true
                                       false))
                               avain-set)]
    (if (false? kaikki-vahvistettu?)
      false
      true)))

(defn- piirra-taulukko-rivi [asetukset tiedot]
  [:tr.bottom-border {:class (str (:tr-luokka asetukset))}
   [:td.paaryhma-center {:style {:width (:caret-paaryhma leveydet)}}]
   [:td.paaryhma-center {:style {:width (:paaryhma-vari leveydet)}}]
   [:td {:style {:width (:tehtava leveydet)
                 :font-weight "700"}}
    (:otsikko tiedot)]

   [:td.numero {:style {:width (:suunniteltu leveydet)}} (:budjetoitu-summa tiedot)]
   [:td.numero {:style {:width (:indeksikorjattu leveydet)}} (:indeksikorjattu-budjetoitu-summa tiedot)]
   [:td.numero {:style {:width (:toteuma leveydet)}} (:toteutunut-summa tiedot)]
   [:td {:style {:width (:erotus leveydet)}} (:erotus tiedot)]
   [:td {:style {:width (:prosentti leveydet)}} (:prosentti tiedot)]])

(defn- vuoden-paattamiskulu-rivi [toteutunut-rivi]
  [:tr.bottom-border.selectable
   [:td.paaryhma-center {:style {:width (:caret-paaryhma leveydet)}}]
   [:td.paaryhma-center {:style {:width (:paaryhma-vari leveydet)}}]
   [:td {:style {:width (:tehtava leveydet)
                 :font-weight "700"}}
    (:toimenpide toteutunut-rivi)]

   [:td.numero {:style {:width (:suunniteltu leveydet)}}
    (str
      (fmt->big (get toteutunut-rivi :toimenpide-budjetoitu-summa)))]
   [:td.numero {:style {:width (:indeksikorjattu leveydet)}}]
   [:td.numero {:style {:width (:toteuma leveydet)}}
    (str
      (if (neg? (get toteutunut-rivi :toimenpide-toteutunut-summa))
        (fmt->big (- (get toteutunut-rivi :toimenpide-toteutunut-summa)))
        (fmt->big (get toteutunut-rivi :toimenpide-toteutunut-summa))))]
   [:td {:style {:width (:erotus leveydet)}}]
   [:td {:style {:width (:prosentti leveydet)}}]])

(defn- toteuma-rivi [rivi]
  (let [toteutunut-avain (keyword (str (:paaryhma rivi) "-toteutunut"))]
    [:tr.bottom-border
       [:td.paaryhma-center {:style {:width (:caret-paaryhma leveydet)}}]
       [:td.paaryhma-center {:style {:width (:paaryhma-vari leveydet)}}]
       [:td {:style {:width (:tehtava leveydet)
                     :font-weight "700"}}
        (str/capitalize (:toimenpide rivi))]

       [:td.numero {:style {:width (:suunniteltu leveydet)}}]
       [:td.numero {:style {:width (:indeksikorjattu leveydet)}}]
       [:td.numero {:style {:width (:toteuma leveydet)}} (str (fmt->big (get rivi toteutunut-avain)))]
       [:td {:style {:width (:erotus leveydet)}}]
       [:td {:style {:width (:prosentti leveydet)}}]]))

(defn- kustannukset-taulukko [e! app rivit-paaryhmittain]
  (let [hankintakustannusten-toimenpiteet (toimenpidetason-rivitys e! app (:hankintakustannukset rivit-paaryhmittain))
        hoidonjohdonpalkkiot (taulukoi-paaryhman-tehtavat :hoidonjohdonpalkkio (:tehtavat (:hoidonjohdonpalkkio rivit-paaryhmittain)))
        erillishankinnat (taulukoi-paaryhman-tehtavat :hoidonjohdonpalkkio (:tehtavat (:erillishankinnat rivit-paaryhmittain)))
        jjhk-toimenpiteet (toimenpidetason-rivitys e! app (:johto-ja-hallintakorvaus rivit-paaryhmittain))
        lisatyot (taulukoi-paaryhman-tehtavat :hoidonjohdonpalkkio (:lisatyot rivit-paaryhmittain))
        rahavaraukset-toimenpiteet (toimenpidetason-rivitys e! app (:rahavaraukset rivit-paaryhmittain))
        bonukset (:bonukset rivit-paaryhmittain)
        ulkopuoliset-rahavaraukset (:ulkopuoliset-rahavaraukset rivit-paaryhmittain)
        sanktiot (:sanktiot rivit-paaryhmittain)
        siirto-toteutunut (get-in rivit-paaryhmittain [:siirto :siirto-toteutunut])
        siirto-negatiivinen? (neg? (or siirto-toteutunut 0))
        siirtoa-viime-vuodelta? (not (or (nil? siirto-toteutunut) (= 0 siirto-toteutunut)))
        tavoitehinnanoikaisut (taulukoi-paaryhman-tehtavat :hoidonjohdonpalkkio (get-in rivit-paaryhmittain [:tavoitehinnanoikaisu :tehtavat]))
        tavoitepalkkio (get rivit-paaryhmittain :tavoitepalkkio)
        tavoitehinnan-ylitys (get rivit-paaryhmittain :tavoitehinnan-ylitys)
        kattohinnan-ylitys (get rivit-paaryhmittain :kattohinnan-ylitys)
        valittu-hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)
        valittu-hoitovuosi-nro (urakka-tiedot/hoitokauden-jarjestysnumero valittu-hoitokauden-alkuvuosi (-> @tila/yleiset :urakka :loppupvm))
        hoitovuosi-nro-menossa (urakka-tiedot/kuluva-hoitokausi-nro (pvm/nyt) (-> @tila/yleiset :urakka :loppupvm))
        hoitovuotta-jaljella (if (= valittu-hoitovuosi-nro hoitovuosi-nro-menossa)
                               (pvm/montako-paivaa-valissa
                                 (pvm/nyt)
                                 (pvm/->pvm (str "30.09." (inc valittu-hoitokauden-alkuvuosi))))
                               nil)
        yht-negatiivinen? (big/gt (big/->big (or (get-in app [:kustannukset-yhteensa :yht-toteutunut-summa]) 0))
                            (big/->big (or (:yht-budjetoitu-summa-indeksikorjattu (get app :kustannukset-yhteensa)) 0)))
        kaikki-vahvistettu? (onko-kaikki-vahvistettu? #{:hankintakustannukset :hoidonjohdonpalkkio
                                                        :erillishankinnat :johto-ja-hallintakorvaus
                                                        :rahavaraukset} rivit-paaryhmittain)]
    [:div.row.sivuelementti
     [:div.col-xs-12
      [:h4 "Hoitovuosi: " valittu-hoitovuosi-nro " (1.10." valittu-hoitokauden-alkuvuosi " - 09.30." (inc valittu-hoitokauden-alkuvuosi) ")"]
      (when hoitovuotta-jaljella
        [:span "Hoitovuotta on jäljellä " hoitovuotta-jaljella " päivää."])]]
    [:div.row.sivuelementti
     [:div.col-xs-12.col-md-9
      [:h2 "Tavoitehintaan kuuluvat"]
      (when-not kaikki-vahvistettu?
        [yleiset/info-laatikko :vahva-ilmoitus "Merkityt indeksikorjatut luvut eivät ole vielä vahvistettuja. Vahvista luvut kustannussuunnitelmassa."])
      [:div.table-default {:style {:padding-top "1rem"}}
       [:table.table-default-header-valkoinen
        [:thead
         [:tr.bottom-border.otsikkorivi
          [:th.paaryhma-center {:style {:width (:caret-paaryhma leveydet)}}]
          [:th.paaryhma-center {:style {:width (:paaryhma-vari leveydet)}}]
          [:th {:style {:width (:tehtava leveydet)}} "Toimenpide"]
          [:th {:style {:width (:suunniteltu leveydet) :text-align "right"}} "Suunniteltu (€)"]
          [:th {:style {:width (:indeksikorjattu leveydet) :text-align "right"}} "Indeksikorjattu (€)"]
          [:th {:style {:width (:toteuma leveydet) :text-align "right"}} "Toteuma (€)"]
          [:th {:style {:width (:erotus leveydet) :text-align "right"}} "Erotus (€) "
           [yleiset/tooltip {} (ikonit/harja-icon-status-info) "Erotus lasketaan indeksikorjatusta ja toteumasta."]]
          [:th {:style {:width (:prosentti leveydet) :text-align "right"}} "%"]]]
        [:tbody
         (paaryhman-rivitys e! app "Suunnitellut hankinnat" :hankintakustannukset hankintakustannusten-toimenpiteet rivit-paaryhmittain)
         (paaryhman-rivitys e! app "Rahavaraukset" :rahavaraukset rahavaraukset-toimenpiteet rivit-paaryhmittain)
         (paaryhman-rivitys e! app "Johto- ja hallintokorvaukset" :johto-ja-hallintakorvaus jjhk-toimenpiteet rivit-paaryhmittain)
         (paaryhman-rivitys e! app "Hoidonjohdonpalkkio" :hoidonjohdonpalkkio hoidonjohdonpalkkiot rivit-paaryhmittain)
         (paaryhman-rivitys e! app "Erillishankinnat" :erillishankinnat erillishankinnat rivit-paaryhmittain)
         ;; Näytetään tavoitehinnanoikaisut vain, jos niitä on oikeasti lisätty ja käytetty
         (when (> (count (get-in rivit-paaryhmittain [:tavoitehinnanoikaisu :tehtavat])) 0)
           (paaryhman-rivitys e! app "Tavoitehinnan oikaisut" :tavoitehinnanoikaisu tavoitehinnanoikaisut rivit-paaryhmittain))
         ;; Siirto rivi
         (when siirtoa-viime-vuodelta?
           [:tr.bottom-border.tummennettu
            [:td.paaryhma-center {:style {:width (:caret-paaryhma leveydet)}}]
            [:td.paaryhma-center {:style {:width (:paaryhma-vari leveydet)}}]
            [:td {:style {:width (:tehtava leveydet)
                          :font-weight "700"}}
             "Siirto edelliseltä vuodelta"]

            [:td.numero {:style {:width (:suunniteltu leveydet)}}]
            [:td.numero {:style {:width (:indeksikorjattu leveydet)}}]
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

          [:td.numero {:style {:width (:suunniteltu leveydet)}} (fmt->big (:yht-budjetoitu-summa (get app :kustannukset-yhteensa)))]
          [:td.numero {:style {:width (:indeksikorjattu leveydet)}} (fmt->big (:yht-budjetoitu-summa-indeksikorjattu (get app :kustannukset-yhteensa)))]
          [:td.numero {:style {:width (:toteuma leveydet)}} (fmt->big (get-in app [:kustannukset-yhteensa :yht-toteutunut-summa]))]
          [:td {:class (if yht-negatiivinen? "negatiivinen-numero" "numero")
                :style {:width (:erotus leveydet)}} (str (when yht-negatiivinen? "+ ") (fmt->big (- (get-in app [:kustannukset-yhteensa :yht-toteutunut-summa])
                                                                                                   (:yht-budjetoitu-summa-indeksikorjattu (get app :kustannukset-yhteensa)))))]
          [:td {:class (if yht-negatiivinen? "negatiivinen-numero" "numero")
                :style {:width (:prosentti leveydet)}} (muotoile-prosentti
                                                         (big/->big (or (get-in app [:kustannukset-yhteensa :yht-toteutunut-summa]) 0))
                                                         (big/->big (or (:yht-budjetoitu-summa-indeksikorjattu (get app :kustannukset-yhteensa)) 0))
                                                         yht-negatiivinen?)]]]]
       [:h2 {:style {:padding-top "2rem"}} "Tavoitehinnan ulkopuoliset"]
       ;; Lisätyöt
       [:table.table-default-header-valkoinen {:style {:margin-top "32px"}}
        [:tbody
         (when (> (:ulkopuoliset-rahavaraukset-budjetoitu ulkopuoliset-rahavaraukset) 0)
           (piirra-taulukko-rivi nil
             {:otsikko "Tavoitehinnan ulkopuoliset rahavaraukset"
              :budjetoitu-summa (str (fmt->big (:ulkopuoliset-rahavaraukset-budjetoitu ulkopuoliset-rahavaraukset)))
              :indeksikorjattu-budjetoitu-summa (str (fmt->big (:ulkopuoliset-rahavaraukset-budjetoitu-indeksikorjattu ulkopuoliset-rahavaraukset)))}))
         (when (> (count (:tehtavat bonukset)) 0)
           (piirra-taulukko-rivi nil
             {:otsikko "Bonukset"
              :toteutunut-summa (str (fmt->big (:bonukset-toteutunut bonukset)))}))
         (when (> (count (:tehtavat sanktiot)) 0)
           (piirra-taulukko-rivi nil
             {:otsikko "Sanktiot"
              :toteutunut-summa (str (fmt->big (:sanktiot-toteutunut sanktiot)))}))
         (when (> (count (get-in rivit-paaryhmittain [:tavoitepalkkio :tehtavat])) 0)
           (vuoden-paattamiskulu-rivi tavoitepalkkio))
         (when (> (count (get-in rivit-paaryhmittain [:tavoitehinnan-ylitys :tehtavat])) 0)
           (vuoden-paattamiskulu-rivi tavoitehinnan-ylitys))
         (when (> (count (get-in rivit-paaryhmittain [:kattohinnan-ylitys :tehtavat])) 0)
           (vuoden-paattamiskulu-rivi kattohinnan-ylitys))
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
          [:td.numero {:style {:width (:suunniteltu leveydet)}}]
          [:td.numero {:style {:width (:indeksikorjattu leveydet)}}]
          [:td.numero {:style {:width (:toteuma leveydet)}} (fmt->big (:lisatyot-summa rivit-paaryhmittain))]
          [:td {:style {:width (:erotus leveydet)}}]
          [:td {:style {:width (:prosentti leveydet)}}]]
         (when (contains? (:avatut-rivit app) :lisatyot)
           (doall
             (for [l lisatyot]
               ^{:key (hash l)}
               l)))]]]]]))


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
    [:div.kustannusten-seuranta.margin-top-16
     ;[debug/debug app]
     [:div
      [:div.row.header
       [:div
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
                                      :format-fn #(fmt/hoitovuoden-jarjestysluku-ja-vuodet % hoitokaudet) #_ (str kustannusten-seuranta-tiedot/fin-hk-alkupvm % "-" kustannusten-seuranta-tiedot/fin-hk-loppupvm (inc %))
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
