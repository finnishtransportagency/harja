(ns harja.views.urakka.kulut.yhteiset
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [harja.tiedot.urakka.kulut.mhu-kustannusten-seuranta :as kustannusten-seuranta-tiedot]
            [harja.tyokalut.big :as big]
            [harja.fmt :as fmt]
            [harja.ui.napit :as napit]
            [harja.domain.kulut.valikatselmus :as valikatselmus]
            [harja.tiedot.urakka.kulut.yhteiset :as t]
            [harja.tiedot.urakka :as urakka-tiedot]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.ikonit :as ikonit]
            [harja.pvm :as pvm]
            [harja.ui.grid :as grid]))

(defn fmt->big
  ([arvo] (fmt->big arvo false))
  ([arvo on-big?]
   (let [arvo (if on-big?
                arvo
                (big/->big arvo))
         fmt-arvo (harja.fmt/desimaaliluku (or (:b arvo) 0) 2 true)]
     fmt-arvo)))

(defn- paatoksen-maksu-prosentit [paatos vertailtava-summa]
  {:urakoitsija (* 100 (/ (::valikatselmus/urakoitsijan-maksu paatos) vertailtava-summa))
   :tilaaja (* 100 (/ (::valikatselmus/tilaajan-maksu paatos) vertailtava-summa))
   :siirto (* 100 (/ (::valikatselmus/siirto paatos) vertailtava-summa))})

(defn kattohinnan-oikaisu-valitulle-vuodelle [app]
  (get-in app [:kattohintojen-oikaisut (:hoitokauden-alkuvuosi app)]))

(defn yhteenveto-laatikko [e! app data sivu]
  (let [valittu-hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)
        valittu-hoitovuosi-nro (urakka-tiedot/hoitokauden-jarjestysnumero valittu-hoitokauden-alkuvuosi (-> @tila/yleiset :urakka :loppupvm))
        {:keys [tavoitehinta] indeksikorjattu-tavoitehinta? :indeksikorjattu?} (t/hoitokauden-tavoitehinta valittu-hoitovuosi-nro app 0)
        {:keys [kattohinta] indeksikorjattu-kattohinta? :indeksikorjattu?} (or (t/hoitokauden-kattohinta valittu-hoitovuosi-nro app) 0)
        oikaistu-kattohinta (or (t/hoitokauden-oikaistu-kattohinta valittu-hoitovuosi-nro app) 0)
        toteuma (or (get-in app [:kustannukset-yhteensa :yht-toteutunut-summa]) 0)
        oikaisujen-summa (t/oikaisujen-summa (:tavoitehinnan-oikaisut app) valittu-hoitokauden-alkuvuosi)
        oikaisuja? (not (or (nil? oikaisujen-summa) (= 0 oikaisujen-summa)))
        oikaistu-tavoitehinta (+ tavoitehinta oikaisujen-summa)
        kattohintaa-oikaistu? (kattohinnan-oikaisu-valitulle-vuodelle app)
        urakan-paatokset (:urakan-paatokset app)
        filtteroi-paatos-fn (fn [paatoksen-tyyppi]
                              (first (filter #(and (= (::valikatselmus/hoitokauden-alkuvuosi %) valittu-hoitokauden-alkuvuosi)
                                                (= (::valikatselmus/tyyppi %) (name paatoksen-tyyppi))) urakan-paatokset)))
        tavoitehinta-alitettu? (> oikaistu-tavoitehinta toteuma)
        tavoitehinta-ylitetty? (> toteuma oikaistu-tavoitehinta)
        kattohinta-ylitetty? (> toteuma oikaistu-kattohinta)
        tavoitehinnan-ylitys (if (> toteuma oikaistu-kattohinta)
                               (- oikaistu-kattohinta oikaistu-tavoitehinta)
                               (- toteuma oikaistu-tavoitehinta))
        kattohinnan-ylitys (- toteuma oikaistu-kattohinta)
        tavoitehinnan-alitus (- oikaistu-tavoitehinta toteuma)
        tavoitehinnan-alitus-paatos (filtteroi-paatos-fn :tavoitehinnan-alitus)
        tavoitehinnan-ylitys-paatos (filtteroi-paatos-fn :tavoitehinnan-ylitys)
        tavoitehhinnan-ylitys-prosentit (paatoksen-maksu-prosentit tavoitehinnan-ylitys-paatos tavoitehinnan-ylitys)
        kattohinnan-ylitys-paatos (filtteroi-paatos-fn :kattohinnan-ylitys)
        kattohinnan-ylitys-prosentit (paatoksen-maksu-prosentit kattohinnan-ylitys-paatos kattohinnan-ylitys)
        lupausbonus-paatos (filtteroi-paatos-fn :lupausbonus)
        lupaussanktio-paatos (filtteroi-paatos-fn :lupaussanktio)
        valikatselmus-tekematta? (t/valikatselmus-tekematta? app)
        lupausbonus (:toteutunut_summa (first (filter #(when (= "lupausbonus" (:maksutyyppi %))
                                                         %) (get-in data [:bonukset :tehtavat]))))
        bonus-maara (:bonukset-toteutunut data)
        toteutunut-bonus (if (and (not (nil? bonus-maara)) (not= 0 bonus-maara))
                           (if (not (nil? lupausbonus))
                             (- bonus-maara lupausbonus)
                             bonus-maara)
                           nil)
        lupaussanktio (:toteutunut_summa (first (filter #(when (= "lupaussanktio" (:maksutyyppi %))
                                                           %) (get-in data [:sanktiot :tehtavat]))))
        sanktio-maara (:sanktiot-toteutunut data)
        toteutunut-sanktio (if (and (not (nil? sanktio-maara)) (not= 0 sanktio-maara))
                             (if (not (nil? lupaussanktio))
                               (- sanktio-maara lupaussanktio)
                               sanktio-maara)
                             nil)]
    [:div.yhteenveto.elevation-2
     [:h2 [:span "Yhteenveto"]]
     (when (and valikatselmus-tekematta? (not= :valikatselmus sivu))
       [:div.valikatselmus
        "Välikatselmus puuttuu"
        [napit/yleinen-ensisijainen
         "Tee välikatselmus"
         #(e! (kustannusten-seuranta-tiedot/->AvaaValikatselmusLomake))]])
     [:div.rivi 
      [:span (if oikaisuja? 
               (str "Alkuperäinen tavoitehinta " (if indeksikorjattu-tavoitehinta? "(indeksikorjattu)" "(indeksikorjaamaton)"))
               (str "Tavoitehinta " (if indeksikorjattu-tavoitehinta? "(indeksikorjattu)" "(indeksikorjaamaton)")))] 
      [:span (fmt/euro-opt tavoitehinta)]]
     (when oikaisuja?
       [:<>
        [:div.rivi [:span "Tavoitehinnan oikaisu"] [:span (str (when (pos? (:b oikaisujen-summa)) "+") (fmt/euro-opt oikaisujen-summa))]]
        [:div.rivi [:span "Oikaistu tavoitehinta "] [:span (fmt/euro-opt oikaistu-tavoitehinta)]]])
     (if (or oikaisuja? kattohintaa-oikaistu?)
       [:<>
        [:div.rivi [:span (str "Alkuperäinen kattohinta " (if indeksikorjattu-kattohinta? "(indeksikorjattu)" "(indeksikorjaamaton)"))] 
         [:span (fmt/euro-opt kattohinta)]]
        [:div.rivi [:span "Oikaistu kattohinta"] [:span (fmt/euro-opt oikaistu-kattohinta)]]]

       [:div.rivi 
        [:span (str "Kattohinta " (if indeksikorjattu-kattohinta? "(indeksikorjattu)" "(indeksikorjaamaton)"))] 
        [:span (fmt/euro-opt kattohinta)]])

     [:div.rivi [:span "Toteuma"] [:span (fmt/euro-opt toteuma)]]
     [:hr]
     (when tavoitehinta-ylitetty?
       [:<>
        [:div.rivi
         [:span "Tavoitehinnan ylitys"]
         [:span.negatiivinen-numero
          (str "+ " (fmt/euro-opt tavoitehinnan-ylitys))]]
        (when tavoitehinnan-ylitys-paatos
          [:<>
           (when (pos? (::valikatselmus/urakoitsijan-maksu tavoitehinnan-ylitys-paatos))
             [:div.rivi-sisempi
              [:span "Urakoitsija maksaa " (fmt/euro-opt (:urakoitsija tavoitehhinnan-ylitys-prosentit)) "%"]
              [:span (fmt/euro-opt (::valikatselmus/urakoitsijan-maksu tavoitehinnan-ylitys-paatos))]])
           (when (pos? (::valikatselmus/tilaajan-maksu tavoitehinnan-ylitys-paatos))
             [:div.rivi-sisempi
              [:span "Tilaaja maksaa " (fmt/euro-opt (:tilaaja tavoitehhinnan-ylitys-prosentit)) "%"]
              [:span (fmt/euro-opt (::valikatselmus/tilaajan-maksu tavoitehinnan-ylitys-paatos))]])])])

     (when kattohinta-ylitetty?
       [:<>
        [:div.rivi
         [:span "Kattohinnan ylitys"]
         [:span.negatiivinen-numero
          (str "+ " (fmt/euro-opt kattohinnan-ylitys))]]
        (when kattohinnan-ylitys-paatos
          [:<>
           (when (pos? (::valikatselmus/urakoitsijan-maksu kattohinnan-ylitys-paatos))
             [:div.rivi-sisempi
              [:span "Urakoitsija maksaa " (fmt/euro-opt (:urakoitsija kattohinnan-ylitys-prosentit)) "%"]
              [:span (fmt/euro-opt (::valikatselmus/urakoitsijan-maksu kattohinnan-ylitys-paatos))]])
           (when (pos? (::valikatselmus/siirto kattohinnan-ylitys-paatos))
             [:div.rivi-sisempi
              [:span "Siirretään seuraavan vuoden kustannuksiin"]
              [:span (fmt/euro-opt (::valikatselmus/siirto kattohinnan-ylitys-paatos))]])])])

     (when tavoitehinta-alitettu?
       [:<>
        [:div.rivi
         [:span "Tavoitehinnan alitus"]
         [:span.positiivinen-numero
          (fmt/euro-opt tavoitehinnan-alitus)]]
        (when tavoitehinnan-alitus-paatos
          [:<>
           (when (neg? (::valikatselmus/siirto tavoitehinnan-alitus-paatos))
             [:div.rivi-sisempi
              [:span "Siirretään seuraavan vuoden lisäbudjetiksi"]
              [:span (fmt/euro-opt (- (::valikatselmus/siirto tavoitehinnan-alitus-paatos)))]])
           (when (neg? (::valikatselmus/urakoitsijan-maksu tavoitehinnan-alitus-paatos))
             [:div.rivi-sisempi
              [:span "Maksetaan tavoitepalkkiona "]
              [:span (fmt/euro-opt (- (::valikatselmus/urakoitsijan-maksu tavoitehinnan-alitus-paatos)))]])

           (when (neg? (::valikatselmus/tilaajan-maksu tavoitehinnan-alitus-paatos))
             [:div.rivi-sisempi
              [:span "Säästö tilaajalle"]
              [:span (fmt/euro-opt (- (::valikatselmus/tilaajan-maksu tavoitehinnan-alitus-paatos)))]])])])

     (when (and (not (nil? (:lisatyot-summa data))) (not= 0 (:lisatyot-summa data)))
       [:div.rivi [:span "Lisätyöt"] [:span (fmt/euro-opt (:lisatyot-summa data))]])
     (when toteutunut-bonus
       [:div.rivi [:span "Bonukset"] [:span (fmt/euro-opt toteutunut-bonus)]])
     (when toteutunut-sanktio
       [:div.rivi [:span "Sanktiot"] [:span (fmt/euro-opt toteutunut-sanktio)]])
     (when lupausbonus-paatos
       [:div.rivi [:span "Lupauksien bonus"] [:span.positiivinen-numero (fmt/euro-opt lupausbonus)]])
     (when lupaussanktio-paatos
       [:div.rivi [:span "Lupauksien sanktio"] [:span.negatiivinen-numero (fmt/euro-opt lupaussanktio)]])
     (when (and (not valikatselmus-tekematta?) (not= :valikatselmus sivu))
       [:div.valikatselmus-tehty
        [napit/yleinen-ensisijainen "Avaa välikatselmus" #(e! (kustannusten-seuranta-tiedot/->AvaaValikatselmusLomake)) {:luokka "napiton-nappi tumma" :ikoni (ikonit/harja-icon-action-show)}]])]))

(defn tavoitehinnan-oikaisut-taulukko
  "Tavoitehinnan oikaisujen taulukko.

  oikaisut-atom on hoitokausikohtainen atom, joka sisältää mapin.
  Välikatselmuksessa käytetään kursoria tuck-tilasta.

  Optiot ottaa vastaan:
  :voi-muokata?           Boolean, joka kertoo voiko muokata. Esim. roolit tai ajankohta voi pakottaa taulukon lukutilaan.
  :poista-oikaisu-fn      Funktio, jolla poistetaan oikaisu, esimerkiksi tuck-funktio joka tekee kutsun bäkkäriin.
  :tallenna-oikaisu-fn    Funktio, jolla tallennetaan oikaisu, esimerkiksi tuck-funktio joka tekee kutsun bäkkäriin.
  :tallenna-oikaisut-fn   Funktio, jolla päivitetään oikaisut, esimerkiksi tuck-funktio joka tekee kutsun bäkkäriin.
                          Kutsutaan jokaisesta muutoksesta."
  [hoitokauden-oikaisut-atom {:keys [voi-muokata? poista-oikaisu-fn tallenna-oikaisu-fn
                                                                 paivita-oikaisu-fn hoitokauden-alkuvuosi]}]
  (let [virheet (atom {})
        uusi-id (if (empty? (keys @hoitokauden-oikaisut-atom))
                  0
                  (inc (apply max (keys @hoitokauden-oikaisut-atom))))]
    [grid/muokkaus-grid
     {:otsikko "Tavoitehinnan oikaisut"
      :tyhja "Ei oikaisuja"
      :voi-kumota? false
      :voi-muokata? voi-muokata?
      ;; Lisää oikaisunappula taulukon yläpuolella oikealla
      :custom-toiminto {:teksti "Lisää oikaisu"
                        :toiminto #(do
                                     (swap! hoitokauden-oikaisut-atom assoc uusi-id
                                       {:id uusi-id :koskematon true :lisays-tai-vahennys :lisays}))
                        :opts {:ikoni (ikonit/livicon-plus)
                               :luokka "nappi-toissijainen"}}
      ;; Roskakorinappula rivin päässä
      :toimintonappi-fn (when voi-muokata?
                              (fn [rivi _muokkaa! id]
                                [napit/poista ""
                                 #(do
                                    (poista-oikaisu-fn rivi id)
                                    #_ (reset! tallennettu-tila hoitokauden-oikaisut))
                                 {:luokka "napiton-nappi"}]))
      :voi-lisata? false ;; Piilotetaan default lisää rivi -nappi. Se on korvattu custom-toiminnolla
      :validoi-uusi-rivi? false
      :on-rivi-blur (fn [oikaisu i]
                      (when-not (or (seq (get @virheet i))
                                  (:koskematon (get @hoitokauden-oikaisut-atom i)))
                        (tallenna-oikaisu-fn oikaisu i)))
      :uusi-id uusi-id
      :virheet virheet
      :nayta-virheikoni? false}
     [{:otsikko "Luokka"
       :nimi ::valikatselmus/otsikko
       :tyyppi :valinta
       :valinnat valikatselmus/luokat ;; TODO: Älä näytä "Alleviivatun fontin vaikutus tavoitehintaan" muissa kuin 19-20 alkaneissa.
       :validoi [[:ei-tyhja "Valitse arvo"]]
       :leveys 2}
      {:otsikko "Selite"
       :nimi ::valikatselmus/selite
       :tyyppi :string
       :validoi [[:ei-tyhja "Täytä arvo"]]
       :leveys 3}
      {:otsikko "Lisäys / Vähennys"
       :nimi :lisays-tai-vahennys
       :hae #(if (neg? (::valikatselmus/summa %)) :vahennys :lisays)
       :aseta (fn [rivi arvo]
                ;; Käännetään summa, jos valittu arvo ei täsmää arvon merkkisyyteen.
                (let [maksu (js/parseFloat (::valikatselmus/summa rivi))
                      rivi (assoc rivi :lisays-tai-vahennys arvo)]
                  (if (or (and (neg? maksu) (= :lisays arvo)) (and (pos? maksu) (= :vahennys arvo)))
                    (update rivi ::valikatselmus/summa -)
                    rivi)))
       :tyyppi :valinta
       :valinnat [:lisays :vahennys]
       :valinta-arvo identity
       :valinta-nayta {:lisays "Lisäys" ;; TODO: Korjaa lukumoodissa
                       :vahennys "Vähennys"}
       :leveys 2}
      {:otsikko "Summa"
       :nimi ::valikatselmus/summa
       :tyyppi :numero
       :tasaa :oikea
       :aseta (fn [rivi arvo]
                (let [vahennys? (= :vahennys (:lisays-tai-vahennys rivi))]
                  (if (and vahennys? (pos? arvo))
                    (assoc rivi ::valikatselmus/summa (- arvo))
                    (assoc rivi ::valikatselmus/summa arvo))))
       :fmt #(str (Math/abs %))
       :validoi [[:ei-tyhja "Täytä arvo"]]
       :leveys 2}]
     hoitokauden-oikaisut-atom]))
