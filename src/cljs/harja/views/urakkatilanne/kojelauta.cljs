(ns harja.views.urakkatilanne.kojelauta
  (:require [harja.pvm :as pvm]
            [harja.tiedot.hallintayksikot :as hal]
            [harja.domain.kulut.kustannusten-seuranta :as kustannusten-seuranta-tiedot]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.palaute :as palaute-tiedot]
            [harja.tiedot.urakka.siirtymat :as siirtymat]
            [harja.ui.grid :as grid]
            [harja.ui.kentat :as kentat]
            [reagent.core :as r]
            [tuck.core :refer [tuck]]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.debug :as debug]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakkatilanne.kojelauta :as tiedot])
  (:require-macros [harja.tyokalut.ui :refer [for*]]))

(def hoitokausia-taaksepain 4)
(def hoitokausia-eteenpain 6)

(defn- mahdolliset-hoitokauden-alkuvuodet [pvm-nyt]
  (range (- (pvm/vuosi pvm-nyt) hoitokausia-taaksepain)
    (+ hoitokausia-eteenpain (pvm/vuosi pvm-nyt))))

(defn suodattimet [e! {:keys [valinnat elyhaku urakkahaku haku-kaynnissa?] :as app}]
  [:div
   [yleiset/pudotusvalikko
    "Urakkatyyppi"
    {:valitse-fn #(do
                    (e! (tiedot/->AsetaSuodatin :urakkatyyppi %))
                    (e! (tiedot/->HaeUrakat)))
     :valinta (:urakkatyyppi valinnat)
     :format-fn :nimi
     :vayla-tyyli? true
     :disabled haku-kaynnissa?}
    (filter (fn [ut]
              (#{:hoito :paallystys} (:arvo ut)))
      nav/+urakkatyypit+)]
   [:div
    [:div.label-ja-alasveto
     [:label.alasvedon-otsikko-vayla {:for "elyhaku"} "Hallintayksikkö"]
     [kentat/tee-kentta
      {:input-id "elyhaku" :tyyppi :haku
       :nayta #(hal/elynumero-ja-nimi %)
       :lahde elyhaku
       :hakuikoni? true
       :hae-kun-yli-n-merkkia 0
       :tarkkaile-ulkopuolisia-muutoksia? true
       :placeholder "Käytä suurennuslasia tai anna nimi"
       :monivalinta? true
       :monivalinta-teksti #(case (count %)
                              0 "Kaikki"
                              1 (hal/elynumero-ja-nimi (first %))
                              (str (count %) " hallintayksikköä valittu"))
       :disabled? haku-kaynnissa?}
      (r/wrap (:elyt valinnat) #(do
                                  (e! (tiedot/->AsetaSuodatin :elyt %))
                                  (e! (tiedot/->HaeUrakat))))]]
    [yleiset/pudotusvalikko
     (if (= :paallystys (get-in valinnat [:urakkatyyppi :arvo]))
       "Vuosi"
       "Hoitokauden alkuvuosi")
     {:valitse-fn #(do
                     (e! (tiedot/->AsetaSuodatin :urakkavuosi %))
                     (e! (tiedot/->HaeUrakat)))
      :valinta (:urakkavuosi valinnat)
      :vayla-tyyli? true
      :disabled haku-kaynnissa?}
     (mahdolliset-hoitokauden-alkuvuodet (pvm/nyt))]

    [:div.label-ja-alasveto
     [:label.alasvedon-otsikko-vayla {:for "urakkahaku"} "Hae urakkaa"]
     [kentat/tee-kentta
      {:input-id "urakkahaku" :tyyppi :haku
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
      (r/wrap (:urakat valinnat) #(e! (tiedot/->AsetaSuodatin :urakat %)))]]]])

(defn avoimet-poikkeamat-sarake
  [rivi]
  (let [{:keys [avoimet_laatupoikkeamat avoimet_turvallisuuspoikkeamat]} rivi]
    [:span.avoimet-poikkeamat
     [yleiset/wrap-if true
      [yleiset/tooltip {} :% "Siirry laatupoikkeamiin"]
      [:a.klikattava {:href "#"
                      :on-click #(siirtymat/siirry-annettuun-valilehteen (:ely_id rivi) (:id rivi) {:taso1 :urakat
                                                                                                    :taso2 :laadunseuranta
                                                                                                    :taso3 :laatupoikkeamat})}
       (if (> avoimet_laatupoikkeamat 0)
         (yleiset/tila-indikaattori "hylatty" {:fmt-fn (constantly (str "Avoimia laatupoikkeamia: " avoimet_laatupoikkeamat))})
         (yleiset/tila-indikaattori "valmis" {:fmt-fn (constantly "Ei avoimia laatupoikkeamia")}))]]
     [yleiset/wrap-if true
      [yleiset/tooltip {} :% "Siirry turvallisuuspoikkeamiin"]
      [:a.klikattava {:href "#"
                      :on-click #(siirtymat/siirry-annettuun-valilehteen (:ely_id rivi) (:id rivi) {:taso1 :urakat
                                                                                                    :taso2 :turvallisuuspoikkeamat
                                                                                                    :taso3 nil})}
       (if (> avoimet_turvallisuuspoikkeamat 0)
         (yleiset/tila-indikaattori "hylatty" {:fmt-fn (constantly (str "Avoimia turvallisuuspoikkeamia: " avoimet_turvallisuuspoikkeamat))})
         (yleiset/tila-indikaattori "valmis" {:fmt-fn (constantly "Ei avoimia turvallisuuspoikkeamia")}))]]]))

(defn lupauspisteet-sarake
  [rivi]
  (let [{:keys [lupaus_tavoitepisteet hoitokauden_alkuvuosi]} rivi]
    [yleiset/wrap-if true
     [yleiset/tooltip {} :% "Siirry lupausnäkymään"]
     [:a.klikattava {:href "#"
                     :on-click #(siirtymat/avaa-lupaukset-valitussa-urakassa (:ely_id rivi) (:id rivi) hoitokauden_alkuvuosi)}
      [:div.lupauspisteet
       (if (nil? lupaus_tavoitepisteet)
         (yleiset/tila-indikaattori "hylatty" {:fmt-fn (constantly "Ei tavoitepistemäärää")})
         (yleiset/tila-indikaattori "valmis" {:fmt-fn (constantly "Ok")}))]]]))

(defn valikatselmus-sarake
  [rivi]
  (let [{:keys [urakan_alkuvuosi tavoitehintapaatos kattohintapaatos lupauspaatokset hoitokauden_alkuvuosi]} rivi
        edellisen-hoitokauden-alkuvuosi (- (pvm/vuosi (first (pvm/paivamaaran-hoitokausi (pvm/nyt)))) 1)
        ;; 15.11. on takaraja, milloin edellisen hoitokauden välikatselmus pitää olla tehtynä (edellinen --> kuluva hk -1)
        valikatselmuksen-takaraja-ohi? (or
                                         (< hoitokauden_alkuvuosi edellisen-hoitokauden-alkuvuosi)
                                         (and
                                           (= hoitokauden_alkuvuosi edellisen-hoitokauden-alkuvuosi)
                                           (> (pvm/nyt)
                                             (kustannusten-seuranta-tiedot/valikatselmuksen-takarajapvm (+ hoitokauden_alkuvuosi 1)))))]
    [yleiset/wrap-if true
     [yleiset/tooltip {} :% "Siirry kustannusten seurantaan"]
     [:a.klikattava {:href "#"
                     :on-click #(siirtymat/siirry-annettuun-valilehteen (:ely_id rivi) (:id rivi) {:taso1 :urakat
                                                                                                   :taso2 :laskutus
                                                                                                   :taso3 :kustannusten-seuranta})}
      [:div.tavoitehintapaatos
       (if (nil? tavoitehintapaatos)
         (yleiset/tila-indikaattori (if valikatselmuksen-takaraja-ohi? "hylatty" "kesken")
           {:fmt-fn (constantly "Ei tavoitehinta\u00ADpäätöstä")})
         (yleiset/tila-indikaattori "valmis"
           {:fmt-fn (constantly (kustannusten-seuranta-tiedot/valikatselmuksen-paatostyypin-nimi tavoitehintapaatos))}))]
      ;; ennen vuotta 2021 alkaneissa urakoissa kattohintapäätös ei ollut kytköksissä tavoitehintapäätökseen, niin näytetään tämä tieto vain silloin
      ;; 2021 ja jälkeen riittää kertoa onko tavoitehintapäätös tehty
      [:div.kattohintapaatos
       (when (< urakan_alkuvuosi tiedot/+kattohintapaatos-kynnysvuosi+)
         (if (nil? kattohintapaatos)
           (yleiset/tila-indikaattori (if valikatselmuksen-takaraja-ohi? "hylatty" "kesken")
             {:fmt-fn (constantly "Ei kattohinta\u00ADpäätöstä")})
           (yleiset/tila-indikaattori "valmis"
             {:fmt-fn (constantly (kustannusten-seuranta-tiedot/valikatselmuksen-paatostyypin-nimi kattohintapaatos))})))]

      [:div.lupauspaatokset
       (if (nil? lupauspaatokset)
         (yleiset/tila-indikaattori (if valikatselmuksen-takaraja-ohi? "hylatty" "kesken")
           {:fmt-fn (constantly "Ei lupaus\u00ADpäätöksiä")})
         (for* [lp lupauspaatokset]
           [:span
            (yleiset/tila-indikaattori "valmis"
              {:fmt-fn (constantly (kustannusten-seuranta-tiedot/valikatselmuksen-paatostyypin-nimi lp))})]))]]]))

(defn kustannussuunitelman-tila-sarake
  [rivi]
  (let [indeksi-saatavilla? (boolean (:indeksikerroin rivi))
        {:keys [aloittamattomia vahvistamattomia vahvistettuja suunnitelman_tila]} (:ks_tila rivi)]
    [yleiset/wrap-if true
     [yleiset/tooltip {} :% "Siirry kustannussuunnitelmaan"]
     [:a.klikattava {:href "#"
                     :on-click #(siirtymat/siirry-annettuun-valilehteen (:ely_id rivi) (:id rivi) {:taso1 :urakat
                                                                                                   :taso2 :suunnittelu
                                                                                                   :taso3 :kustannussuunnitelma})}
      (cond
        (= "aloittamatta" suunnitelman_tila)
        (yleiset/tila-indikaattori "hylatty" {:fmt-fn (constantly "Aloittamatta")})

        (= "aloitettu" suunnitelman_tila)
        (yleiset/tila-indikaattori (if indeksi-saatavilla?
                                     "hylatty"
                                     "kesken")
          {:fmt-fn #(str
                      (when indeksi-saatavilla? "Indeksi saatavilla. ")
                      "Aloittamatta: " aloittamattomia
                      ", kesken: " vahvistamattomia
                      ", vahvistettuja: " vahvistettuja)})

        (= "vahvistettu" suunnitelman_tila)
        (yleiset/tila-indikaattori "valmis" {:fmt-fn (constantly "Valmis")}))]]))

(def urakoiden-maara-per-sivu 20)

(defn otsikko-tila-sarake
  [rivi]
  (println "rivi: " rivi)
  [yleiset/wrap-if true
   [yleiset/tooltip {} :% "Siirry ilmoitukseen"]
   [:a.klikattava {:href "#"
                   :on-click #(siirtymat/siirry-annettuun-valilehteen (:ely_id rivi) (:id rivi) {})}]])

(defn taulukko-paallystysurakat [e! {:keys [urakat haku-kaynnissa?]}]
  (println "DEBUG lauri urakat " urakat)
  [grid/grid
   {:otsikko (str "")
    :tyhja (if haku-kaynnissa?
             [ajax-loader "Ladataan tietoja"]
             "Ei tietoja, tarkistathan valitut suodattimet.")
    :luokat ["paallystysurakat"]
    :rivi-jalkeen-fn (fn [urakat]
                       (let [yhteenveto (tiedot/paallystystietojen-yhteenveto urakat)
                             valmiit-kohteet (tiedot/valmiit-yhteenveto urakat)
                             lahetetty (tiedot/lahetetyt-yhteenveto urakat)
                             valmiit-ei-lahetetty (tiedot/valmiit-ei-lahetetty-yhteenveto urakat)
                             epaonnistuneet-lahetetty (tiedot/epaonnistuneet-lahetetyt-yhteenveto urakat)
                             aloittamatta (tiedot/aloittamatta-yhteenveto urakat)
                             virheelliset (tiedot/virheelliset-yhteenveto urakat)
                             ]
                         (when-not (empty? urakat)
                           [{:teksti "Yhteensä" :luokka "lihavoitu"}
                            {:teksti (str (count urakat) " kpl urakoita") :luokka "lihavoitu"}
                            {:teksti yhteenveto :luokka "lihavoitu"}
                            {:teksti valmiit-kohteet :luokka "lihavoitu"}
                            {:teksti lahetetty :luokka "lihavoitu"}
                            {:teksti valmiit-ei-lahetetty :luokka "lihavoitu"}
                            {:teksti epaonnistuneet-lahetetty :luokka "lihavoitu"}
                            {:teksti aloittamatta :luokka "lihavoitu"}
                            {:teksti virheelliset :luokka "lihavoitu"}])))}
   [{:otsikko "Urakka"
     :tyyppi :string
     :nimi :nimi
     :leveys 7
     :muokattava? (constantly false)}
    {:otsikko "Vuosi"
     :muokattava? (constantly false)
     :nimi :hoitokauden_alkuvuosi :leveys 3
     :tyyppi :positiivinen-numero :kokonaisluku? true
     :tasaa :oikea}
    {:otsikko "Kohteiden lkm."
     :muokattava? (constantly false)
     :nimi :yllapitokohteiden_lkm :leveys 4
     :tyyppi :positiivinen-numero :kokonaisluku? true
     :tasaa :oikea}
    {:otsikko "Valmis/hyväksytty"
     :muokattava? (constantly false)
     :nimi :valmis_hyvaksytty :leveys 6
     :tyyppi :positiivinen-numero :kokonaisluku? true
     :tasaa :oikea}
    {:otsikko "Lähetetty onnistuneesti YHA:an"
     :muokattava? (constantly false)
     :nimi :lahetetty_onnistuneesti :leveys 6
     :tyyppi :positiivinen-numero :kokonaisluku? true
     :tasaa :oikea}
    {:otsikko "Valmiit, ei vielä lähetetty"
     :muokattava? (constantly false)
     :nimi :valmiit_ei_lahetetty :leveys 6
     :tyyppi :positiivinen-numero :kokonaisluku? true
     :tasaa :oikea}
    {:otsikko "Epäonnistuneet YHA-lähetykset"
     :muokattava? (constantly false)
     :nimi :epaonnistuneet_lahetetyt :leveys 6
     :tyyppi :positiivinen-numero :kokonaisluku? true
     :tasaa :oikea}
    {:otsikko "Aloittamatta"
     :muokattava? (constantly false)
     :nimi :aloittamatta :leveys 6
     :tyyppi :positiivinen-numero :kokonaisluku? true
     :tasaa :oikea}
    {:otsikko "Virheelliset"
     :muokattava? (constantly false)
     :nimi :virheelliset_kohteet :leveys 6
     :tyyppi :komponentti :kokonaisluku? true
     :komponentti (fn [rivi] (str rivi)
                    #_(for [kohde ] )
                    )
     :tasaa :oikea}]
   urakat])

(defn taulukko-hoitourakat [e! {:keys [urakat haku-kaynnissa?]}]
  [grid/grid
   {:otsikko (str "")
    :sivuta urakoiden-maara-per-sivu
    :tyhja (if haku-kaynnissa?
             [ajax-loader "Ladataan tietoja"]
             "Ei tietoja, tarkistathan valitut suodattimet.")
    :rivi-jalkeen-fn (fn [urakat]
                       (let [ks-tilojen-yhteenveto (tiedot/ks-tilojen-yhteenveto urakat)
                             valikatselmus-tilojen-yhteenveto (tiedot/valikatselmus-tilojen-yhteenveto urakat)
                             lupaustietojen-yhteenveto (tiedot/lupaustietojen-yhteenveto urakat)
                             poikkeusten-yhteenveto (tiedot/poikkeusten-yhteenveto urakat)]
                         (when-not (empty? urakat)
                           [{:teksti "Yhteensä" :luokka "lihavoitu"}
                            {:teksti (str (count urakat) " kpl urakoita") :luokka "lihavoitu"}
                            {:teksti ks-tilojen-yhteenveto :luokka "lihavoitu"}
                            {:teksti valikatselmus-tilojen-yhteenveto :luokka "lihavoitu"}
                            {:teksti lupaustietojen-yhteenveto :luokka "lihavoitu"}
                            {:teksti poikkeusten-yhteenveto :luokka "lihavoitu"}])))}
   [{:otsikko "Urakka"
     :tyyppi :string
     :nimi :nimi
     :leveys 8
     :muokattava? (constantly false)}
    {:otsikko "Hoito\u00ADvuosi"
     :muokattava? (constantly false)
     :nimi :hoitokauden_alkuvuosi :leveys 7
     :tyyppi :string :fmt #(pvm/hoitokausi-str-alkuvuodesta-vuodet %)}
    {:otsikko "Kustannus\u00ADsuunnitelma"
     :muokattava? (constantly false)
     :nimi :ks_tila :leveys 15
     :tyyppi :komponentti
     :komponentti (fn [rivi] [kustannussuunitelman-tila-sarake rivi])}
    {:otsikko "Väli\u00ADkatselmus"
     :muokattava? (constantly false)
     :nimi :ks_tila :leveys 15
     :tyyppi :komponentti
     :komponentti (fn [rivi] [valikatselmus-sarake rivi])}
    {:otsikko "Lupausten tavoite\u00ADpiste\u00ADmäärä"
     :muokattava? (constantly false)
     :nimi :ks_tila :leveys 15
     :tyyppi :komponentti
     :komponentti (fn [rivi] [lupauspisteet-sarake rivi])}
    {:otsikko "Avoimet poikkeamat"
     :muokattava? (constantly false)
     :nimi :ks_tila :leveys 15
     :tyyppi :komponentti
     :komponentti (fn [rivi] [avoimet-poikkeamat-sarake rivi])}]
   urakat])

(defn listaus
  "Listauskomponentti, joka toimii pohjana eri urakkatyypeille, ja hoitaa urakkatyypeille yhteisen suodattamisen elyn, vuoden sekä valitun urakan perusteella."
  [e! {:keys [valinnat urakat haku-kaynnissa?] :as app}]
  (let [valitut-urakat (:urakat valinnat)
        valittu-ely (get-in valinnat [:ely :id])
        valittu-hk-alkuvuosi (:urakkavuosi valinnat)
        ;; ely-suodatus
        urakat (if (nil? valittu-ely)
                 urakat
                 (filter #(= valittu-ely (:ely_id %)) urakat))
        ;; hoitokausisuodatus valittu-hk-alkuvuosi
        urakat (if (nil? valittu-hk-alkuvuosi)
                 urakat
                 (filter #(= valittu-hk-alkuvuosi (:hoitokauden_alkuvuosi %)) urakat))
        ;; urakkasuodatus
        urakat (if (empty? valitut-urakat)
                 urakat
                 (filter #((into #{} (map :id valitut-urakat)) (:id %)) urakat))]
    [:div
     ;; [debug/debug urakat]
     (if (= :paallystys (get-in app [:valinnat :urakkatyyppi :arvo]))
       [taulukko-paallystysurakat e! {:urakat urakat
                                      :haku-kaynnissa? haku-kaynnissa?}]
       [taulukko-hoitourakat e! {:urakat urakat
                                 :haku-kaynnissa? haku-kaynnissa?}])]))


;; Näytetään uuden ominaisuuden vihjetekstiä jonkin aikaa, että käyttäjät oppivat mistä asiassa on kyse
(def vihjeteksti-uudesta-ominaisuudesta
  [:p "Tämä on uusi osio, jonka tarkoituksena on parantaa tiedon läpinäkyvyyttä Harjan sisällä.
       Tässä vaiheessa osio näkyy vain pääkäyttäjille sekä ELY:jen pääkäyttäjille ja urakanvalvojille.
       Myöhemmin laajennamme mahdollisesti tiedon näkyvyyttä myös urakoitsijoille heidän omien urakoidensa osalta. Jos löydät tiedoista virheitä tai sinulla
       on muita toiveita tämän osion kehittämiseksi, voit "
   [:a {:href (palaute-tiedot/mailto-kehitystiimi)} "laittaa meille viestiä osoitteeseen harjapalaute@solita.fi"]])

(defn kojelauta* [e! app]
  (komp/luo
    (komp/sisaan #(do
                    (e! (tiedot/->AlustaHallintayksikkoHaku (into []
                                                              (map (fn [ely] (select-keys ely [:id :nimi :elynumero]))
                                                                @hal/vaylamuodon-hallintayksikot))))
                    (e! (tiedot/->HaeUrakat))))
    (fn [e! app]
      [:div.kojelauta-hallinta
       [:h1 "Urakoiden tilanne"]
       (when (< (pvm/nyt) (pvm/->pvm "7.12.2024"))
         [yleiset/vihje vihjeteksti-uudesta-ominaisuudesta])
       [suodattimet e! app]
       ;; [debug/debug app]
       [listaus e! app]])))

(defn kojelauta []
  [tuck tiedot/tila kojelauta*])
