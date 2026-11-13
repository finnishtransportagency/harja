(ns harja.views.urakkatilanne.kojelauta
  (:require [reagent.core :as r]
            [tuck.core :refer [tuck]]

            [harja.pvm :as pvm]
            [harja.ui.kentat :as kentat]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.valinnat :as valinnat]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.hallintayksikot :as hal]
            [harja.tiedot.palaute :as palaute-tiedot]
            [harja.tiedot.urakkatilanne.kojelauta :as tiedot]
            [harja.views.urakkatilanne.taulukko-hoito :refer [taulukko-hoitourakat]]
            [harja.views.urakkatilanne.taulukko-yllapito :refer [taulukko-paallystysurakat]]))

(def
  ^{:doc "Näytetään uuden ominaisuuden vihjetekstiä jonkin aikaa, että käyttäjät oppivat mistä asiassa on kyse."}
  vihjeteksti-uudesta-ominaisuudesta
  [:p "Tämä on uusi osio, jonka tarkoituksena on parantaa tiedon läpinäkyvyyttä Harjan sisällä.
       Tässä vaiheessa osio näkyy vain pääkäyttäjille sekä Elinvoimakeskusten pääkäyttäjille ja urakanvalvojille.
       Myöhemmin laajennamme mahdollisesti tiedon näkyvyyttä myös urakoitsijoille heidän omien urakoidensa osalta. 
       Jos löydät tiedoista virheitä tai sinulla on muita toiveita tämän osion kehittämiseksi, voit "
   [:a.klikattava.alleviivaa {:href (palaute-tiedot/mailto-kehitystiimi)} "laittaa meille viestiä osoitteeseen harjapalaute@solita.fi"]])


(defn- mahdolliset-hoitokauden-alkuvuodet [pvm-nyt]
  (let [hoitokausia-taaksepain 4
        hoitokausia-eteenpain 6]
    (range (- (pvm/vuosi pvm-nyt) hoitokausia-taaksepain)
      (+ hoitokausia-eteenpain (pvm/vuosi pvm-nyt)))))


(defn suodattimet [e! {:keys [valinnat urakkahaku haku-kaynnissa?]}]
  (let [valitut-evkt (:evkt valinnat)
        valittavat-evkt (into [{:id 0 :nimi "Kaikki" :evknumero 0 :valittu? (empty? valitut-evkt)}]
                          (map (fn [evk]
                                 (assoc evk
                                   :nimi (hal/evknumero-ja-nimi evk)
                                   :valittu? (some #(= (:id evk) (:id %)) valitut-evkt)))
                            (sort-by :evknumero @hal/vaylamuodon-hallintayksikot)))]
    [:div
     [yleiset/pudotusvalikko
      "Urakkatyyppi"
      {:valitse-fn #(do
                      (e! (tiedot/->AsetaSuodatin :urakkatyyppi %)))
       :valinta (:urakkatyyppi valinnat)
       :format-fn :nimi
       :vayla-tyyli? true
       :disabled haku-kaynnissa?}
      (filter (fn [ut]
                (#{:hoito :paallystys} (:arvo ut)))
        nav/+urakkatyypit+)]
     [:div
      [:div.label-ja-alasveto
       [:label.alasvedon-otsikko "Elinvoimakeskus"]
       [valinnat/checkbox-pudotusvalikko
        valittavat-evkt
        (fn [evk valittu?]
          (let [valitut-evkt-uudet (cond
                                     (= 0 (:id evk)) []
                                     valittu? (conj (filterv #(not= (:id evk) (:id %)) valitut-evkt)
                                                    (select-keys evk [:id :nimi :evknumero]))
                                     :else (filterv #(not= (:id evk) (:id %)) valitut-evkt))]
            (e! (tiedot/->AsetaSuodatin :evkt valitut-evkt-uudet))))
        [" Elinvoimakeskus valittu" " Elinvoimakeskusta valittu"]
        {:vayla-tyyli? true :disabled haku-kaynnissa?}]]
      [yleiset/pudotusvalikko
       (if (= :paallystys (get-in valinnat [:urakkatyyppi :arvo]))
         "Vuosi"
         "Hoitokauden alkuvuosi")
       {:valitse-fn #(do
                       (e! (tiedot/->AsetaSuodatin :urakkavuosi %)))
        :valinta (:urakkavuosi valinnat)
        :vayla-tyyli? true
        :disabled haku-kaynnissa?}
       (mahdolliset-hoitokauden-alkuvuodet (pvm/nyt))]

      [:div.label-ja-alasveto
       [:label.alasvedon-otsikko {:for "urakkahaku"} "Hae urakkaa"]
       [kentat/tee-kentta
        {:elementin-id "urakkahaku" :tyyppi :haku
         :nayta :nimi
         :hae-kun-yli-n-merkkia 0
         :lahde urakkahaku
         :monivalinta? true
         :tarkkaile-ulkopuolisia-muutoksia? true
         :hakuikoni? true
         :placeholder "Käytä suurennuslasia tai anna nimi"
         :monivalinta-teksti #(case (count %)
                                0 ""
                                1 (:nimi (first %))
                                (str (count %) " urakkaa valittu"))
         :disabled? haku-kaynnissa?}
       (r/wrap (:urakat valinnat) #(e! (tiedot/->AsetaSuodatin :urakat %)))]]]
      [:div {:style {:margin-top "1rem"}}
       [napit/yleinen-ensisijainen "Hae"
        #(e! (tiedot/->HaeUrakat))
        {:disabled haku-kaynnissa?}]]]))


(defn listaus
  "Listauskomponentti, joka toimii pohjana eri urakkatyypeille, 
  ja hoitaa urakkatyypeille yhteisen suodattamisen elinvoimakeskuksen, vuoden sekä valitun urakan perusteella."
  [e! {:keys [valinnat urakat haku-kaynnissa?] :as app}]
  (let [valitut-urakat (:urakat valinnat)
        valittu-evk (get-in valinnat [:evk :id])
        valittu-hk-alkuvuosi (:urakkavuosi valinnat)
        ;; evk-suodatus
        urakat (if (nil? valittu-evk)
                 urakat
                 (filter #(= valittu-evk (:evk_id %)) urakat))
        ;; hoitokausisuodatus valittu-hk-alkuvuosi
        urakat (if (nil? valittu-hk-alkuvuosi)
                 urakat
                 (filter #(= valittu-hk-alkuvuosi (:hoitokauden_alkuvuosi %)) urakat))
        ;; urakkasuodatus
        urakat (if (empty? valitut-urakat)
                 urakat
                 (filter #((into #{} (map :id valitut-urakat)) (:id %)) urakat))]
    [:div
     (if (= :paallystys (get-in app [:valinnat :urakkatyyppi :arvo]))
       [taulukko-paallystysurakat e! {:urakat urakat
                                      :haku-kaynnissa? haku-kaynnissa?}]
       [taulukko-hoitourakat e! {:urakat urakat
                                 :haku-kaynnissa? haku-kaynnissa?}])]))


(defn kojelauta* [e! app]
  [:div.kojelauta-hallinta
   [:h1 "Urakoiden tilanne"]
   (when (pvm/ennen? (pvm/nyt) (pvm/->pvm "7.12.2024"))
     [yleiset/vihje vihjeteksti-uudesta-ominaisuudesta])
   [suodattimet e! app]
   [listaus e! app]])


(defn kojelauta []
  [tuck tiedot/tila kojelauta*])
