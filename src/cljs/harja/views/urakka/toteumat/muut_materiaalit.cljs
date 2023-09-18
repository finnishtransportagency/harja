(ns harja.views.urakka.toteumat.muut-materiaalit
  (:require [clojure.string :as str]
            [harja.views.kartta.tasot :as kartta-tasot]
            [harja.views.urakka.valinnat :as valinnat]
            [reagent.core :refer [atom wrap]]
            [harja.loki :refer [log]]
            [harja.fmt :as fmt]
            [harja.ui.lomake :refer [lomake] :as lomake]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.pvm :as pvm]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka :as u]
            [harja.ui.grid :as grid]
            [harja.ui.napit :as napit]

            [harja.tiedot.urakka.toteumat :as toteumat]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.suunnittelu.materiaalit :as materiaali-tiedot]

            [cljs.core.async :refer [<!]]
            [harja.views.kartta :as kartta]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.yleiset :as yleiset])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

(defonce
  ^{:doc "Valittu aikaväli materiaalien tarkastelulle"}
  valittu-aikavali (atom nil))

(defonce urakan-materiaalin-kaytot
  (reaction<! [nakymassa? @materiaali-tiedot/materiaalinakymassa?
               sopimusnumero (first @u/valittu-sopimusnumero)
               [alku loppu] @valittu-aikavali
               ur @nav/valittu-urakka
               _ @u/valittu-hoitokauden-kuukausi
               _ @u/valittu-hoitokausi]
              {:nil-kun-haku-kaynnissa? true}
              (when (and nakymassa? sopimusnumero alku loppu ur)
                (materiaali-tiedot/hae-urakassa-kaytetyt-materiaalit
                 (:id ur) alku loppu sopimusnumero))))

(def tr-osoite-taytetty? (every-pred :numero :alkuosa :alkuetaisyys :loppuosa :loppuetaisyys))

(defn tallenna-toteuma-ja-toteumamateriaalit!
  [tm m]
  (let [toteumamateriaalit (into []
                                 (comp
                                   (map #(assoc % :materiaalikoodi (:id (:materiaali %))))
                                   (map #(dissoc % :materiaali))
                                   (map #(assoc % :maara (if (string? (:maara %))
                                                           (js/parseInt (:maara %) 10)
                                                           (:maara %))))
                                   (map #(assoc % :id (:tmid %)))
                                   (map #(dissoc % :tmid))
                                   (map #(assoc % :toteuma (:id m))))
                                 tm)
        toteuma {:id (:id m) :urakka (:id @nav/valittu-urakka)
                 :alkanut (:alkanut m) :paattynyt (:paattynyt m)
                 :sopimus (first @u/valittu-sopimusnumero)
                 :tyyppi "materiaali"
                 :suorittajan-nimi (:suorittaja m)
                 :suorittajan-ytunnus (:ytunnus m)
                 :lisatieto (:lisatieto m)
                 :tierekisteriosoite (if (tr-osoite-taytetty? (:tierekisteriosoite m)) (:tierekisteriosoite m) nil)}
        hoitokausi @u/valittu-hoitokausi
        sopimus-id (first @u/valittu-sopimusnumero)]
    (materiaali-tiedot/tallenna-toteuma-ja-toteumamateriaalit! toteuma toteumamateriaalit hoitokausi sopimus-id)))

(def materiaalikoodit (reaction (into []
                                      (comp
                                        (map #(dissoc % :urakkatyyppi))
                                        (map #(dissoc % :kohdistettava)))
                                      @(materiaali-tiedot/hae-materiaalikoodit))))


(defn- paivita-toteuma-materiaalit
  "Päivittää materiaalien toteumarivit muokattujen rivien perusteella.
Jos muokatuissa on rivi samalla id:llä, korvaa se aiemman rivin. Jos muokattu
rivi on poistettu, poistetaan vastaava rivi toteumariveistä."
  [toteuma-materiaalit muokatut]
  (let [muokatut-rivit (into {}
                             (map (juxt :tmid identity))
                             muokatut)]
    (into []
          (comp (map (fn [toteuma-materiaali]
                       (let [muokattu (get muokatut-rivit (:tmid toteuma-materiaali))]
                         (if (:poistettu muokattu)
                           nil
                           (or muokattu toteuma-materiaali)))))
                (remove nil?))
          toteuma-materiaalit)))

(defn tallenna-toteuma-materiaaleja
  [urakka atomi]
  "Tätä funktiota käytetään, kun materiaalitoteuman tietoja muutetaan suoraan pääsivulla,
  kun vetolaatikko on aukaistu. Parametrina saatava atomi sisältää vetolaatikossa näytettävät tiedot,
  ja se päivitetään kun tallennus on saatu tehtyä."
  (fn [materiaalit]
    (let [toteumamateriaalit (into []
                                   (comp
                                     (map #(assoc % :materiaalikoodi (:id (:materiaali %))))
                                     (map #(dissoc % :materiaali))
                                     (map #(assoc % :maara (if (string? (:maara (:toteuma %)))
                                                             (js/parseInt (:maara (:toteuma %)) 10)
                                                             (:maara (:toteuma %)))))
                                     (map #(assoc % :toteuma (:id %)))
                                     (map #(assoc % :id (:tmid %)))
                                     (map #(dissoc % :tmid)))
                                   materiaalit)]
      (go (let [tulos (<! (materiaali-tiedot/tallenna-toteuma-materiaaleja urakka
                                                                           toteumamateriaalit
                                                                           @u/valittu-hoitokausi
                                                                           (first @u/valittu-sopimusnumero)))]
            (reset! urakan-materiaalin-kaytot tulos)
            (swap! atomi paivita-toteuma-materiaalit materiaalit))))))

(defn materiaalit-ja-maarat
  [materiaalit-atom virheet-atom jarjestelman-luoma?]
  (let [pienin-id (apply min (keys @materiaalit-atom))
        uuden-id (if (neg? pienin-id)
                   (dec pienin-id)
                   -1 ;; muokkaus-gridin toteutuksessa uudet rivit ovat id:llä -1, -2, -3
                   )]
    [grid/muokkaus-grid
     {:tyhja "Ei materiaaleja."
      :voi-lisata? false :voi-kumota? false
      :muutos (fn [g] (reset! virheet-atom (grid/hae-virheet g)))
      :custom-toiminto {:teksti "Lisää materiaali"
                        :toiminto #(do
                                     (swap! materiaalit-atom assoc uuden-id
                                            {:id uuden-id :koskematon true}))
                        :opts {:ikoni (ikonit/livicon-plus)
                               :luokka "nappi-toissijainen"}}
      :voi-muokata? (and (not jarjestelman-luoma?)
                         (oikeudet/voi-kirjoittaa? oikeudet/urakat-toteumat-materiaalit (:id @nav/valittu-urakka)))}
     [{:otsikko "Materiaali" :nimi :materiaali :tyyppi :valinta
       :valinnat @materiaalikoodit :fmt :nimi
       :valinta-nayta #(if % (:nimi %) "- valitse materiaali -")
       :validoi [[:ei-tyhja "Valitse materiaali."]]
       :leveys "50%"}
      {:otsikko "Määrä" :nimi :maara :tyyppi :numero :leveys "40%" :validoi [[:ei-tyhja "Anna määrä."]]}
      {:otsikko "Yks." :muokattava? (constantly false) :nimi :yksikko :hae (comp :yksikko :materiaali) :leveys "5%"}]
     materiaalit-atom]))


(defn- aseta-lomakkeen-tiedot [tiedot urakka-id]
  (if (:id tiedot)
    (go
      (reset! materiaali-tiedot/valitun-materiaalitoteuman-tiedot
              (<! (materiaali-tiedot/hae-toteuman-materiaalitiedot urakka-id (:id tiedot)))))
    (reset! materiaali-tiedot/valitun-materiaalitoteuman-tiedot
            {:suorittaja (:nimi @u/urakan-organisaatio)
             :ytunnus (:ytunnus @u/urakan-organisaatio)
             :alkanut (pvm/nyt)
             :paattynyt (pvm/nyt)})))

(def pakolliset-materiaalit #{"Sorastusmurske" "Kelirikkomurske"})
(defn- sijainti-pakollinen? [valitun-materiaalitoteuman-tiedot]
  (boolean (some #(contains? pakolliset-materiaalit %)
    (map #(:nimi %) (map #(:materiaali %) (:toteumamateriaalit valitun-materiaalitoteuman-tiedot))))))

(defn materiaalit-tiedot
  "Valitun toteuman tietojen näkymä"
  [ur]
  (komp/luo
    (fn [ur]
      (let [muokkaa! #(do (log "MATERIAALI: " (pr-str %)) (reset! materiaali-tiedot/valitun-materiaalitoteuman-tiedot %))
            materiaalitoteumat-mapissa (wrap (into {}
                                                   (map (juxt :tmid identity))
                                                   (:toteumamateriaalit @materiaali-tiedot/valitun-materiaalitoteuman-tiedot))
                                             (fn [rivit]
                                               (swap! materiaali-tiedot/valitun-materiaalitoteuman-tiedot
                                                      assoc :toteumamateriaalit
                                                      (keep
                                                        (fn [[id rivi]]
                                                          (when (not (and (neg? id)
                                                                          (:poistettu rivi)))
                                                            (assoc rivi :tmid id)))
                                                        rivit))))

            materiaalien-virheet (wrap (::materiaalivirheet @materiaali-tiedot/valitun-materiaalitoteuman-tiedot)
                                       #(swap! materiaali-tiedot/valitun-materiaalitoteuman-tiedot assoc ::materiaalivirheet %))
            jarjestelman-luoma? (true? (:jarjestelmanlisaama @materiaali-tiedot/valitun-materiaalitoteuman-tiedot))
            voi-tallentaa? (and (lomake/validi? @materiaali-tiedot/valitun-materiaalitoteuman-tiedot)
                             (> (count @materiaalitoteumat-mapissa) 0)
                             (zero? (count @materiaalien-virheet)))]
        [:div.toteuman-tiedot
         [napit/takaisin "Takaisin materiaaliluetteloon" #(reset! materiaali-tiedot/valitun-materiaalitoteuman-tiedot nil)]
         [lomake {:otsikko (if (:id @materiaali-tiedot/valitun-materiaalitoteuman-tiedot)
                             "Muokkaa toteumaa"
                             "Luo uusi toteuma")
                  :luokka :horizontal
                  :voi-muokata? (and (oikeudet/voi-kirjoittaa? oikeudet/urakat-toteumat-materiaalit (:id @nav/valittu-urakka))
                                  (not jarjestelman-luoma?))
                  :muokkaa! muokkaa!
                  :validoi-alussa? true
                  :kutsu-muokkaa-renderissa? true
                  :footer [napit/palvelinkutsu-nappi
                           "Tallenna toteuma"
                           #(tallenna-toteuma-ja-toteumamateriaalit!
                              (:toteumamateriaalit @materiaali-tiedot/valitun-materiaalitoteuman-tiedot)
                              @materiaali-tiedot/valitun-materiaalitoteuman-tiedot)
                           {:luokka "nappi-ensisijainen"
                            :ikoni (ikonit/tallenna)
                            :kun-onnistuu
                            #(do
                               (reset! urakan-materiaalin-kaytot %)
                               (reset! materiaali-tiedot/valitun-materiaalitoteuman-tiedot nil))
                            :disabled (or (not voi-tallentaa?)
                                        jarjestelman-luoma?
                                        (and (sijainti-pakollinen? @materiaali-tiedot/valitun-materiaalitoteuman-tiedot)
                                          (not (:tierekisteriosoite @materiaali-tiedot/valitun-materiaalitoteuman-tiedot)))
                                        (not (oikeudet/voi-kirjoittaa? oikeudet/urakat-toteumat-materiaalit (:id @nav/valittu-urakka))))}]}

          [(when jarjestelman-luoma?
             {:otsikko "Lähde" :nimi :luoja :tyyppi :string
              :hae (fn [rivi] (str "Järjestelmä (" (:luoja rivi) " / " (:organisaatio rivi) ")"))
              :muokattava? (constantly false)
              :vihje toteumat/ilmoitus-jarjestelman-luoma-toteuma})
           {:otsikko "Sopimus" :nimi :sopimus :hae (fn [_] (second @u/valittu-sopimusnumero)) :muokattava? (constantly false)}
           {:otsikko "Aloitus" :pakollinen? true :uusi-rivi? true
            :tyyppi :pvm :nimi :alkanut :validoi [[:ei-tyhja "Anna aloituspäivämäärä"]]
            :huomauta [[:urakan-aikana-ja-hoitokaudella]]
            :muokattava? (constantly (not jarjestelman-luoma?))
            :aseta (fn [rivi arvo]
                     (assoc
                       (if (or
                             (not (:paattynyt rivi))
                             (pvm/jalkeen? arvo (:paattynyt rivi)))
                         (assoc rivi :paattynyt arvo)
                         rivi)
                       :alkanut arvo))}
           {:otsikko "Lopetus" :nimi :paattynyt
            :pakollinen? true
            :tyyppi :pvm :validoi [[:ei-tyhja "Anna lopetuspäivämäärä"]
                                   [:pvm-kentan-jalkeen :alkanut "Lopetuksen pitää olla aloituksen jälkeen"]]
            :muokattava? (constantly (not jarjestelman-luoma?))}
                      (when jarjestelman-luoma?
             {:otsikko "Lähde" :nimi :luoja :tyyppi :string
              :hae (fn [rivi] (str "Järjestelmä (" (:kayttajanimi rivi) " / " (:organisaatio rivi) ")")) :muokattava? (constantly false)})
           {:label-ja-kentta-samalle-riville? false
            :otsikko "Sijainti"
            :nimi :tierekisteriosoite
            :tyyppi :tierekisteriosoite
            :vayla-tyyli? true
            :alaotsikot? false
            :sijainti (atom nil)
            :pakollinen? (sijainti-pakollinen? @materiaali-tiedot/valitun-materiaalitoteuman-tiedot)
            :tarkkaile-ulkopuolisia-muutoksia? true
            :validoi [(fn [osoite]
                         (when (and (sijainti-pakollinen? @materiaali-tiedot/valitun-materiaalitoteuman-tiedot)
                                (not (tr-osoite-taytetty? osoite)))
                          (str "Sijaintitieto on pakollinen materiaaleille " (str/join " tai " pakolliset-materiaalit))))
                      [:kokonainen-tr-osoite]]}
           {:otsikko "Materiaalit" :nimi :materiaalit :palstoja 2
            :komponentti (fn [_]
                           [materiaalit-ja-maarat
                            materiaalitoteumat-mapissa
                            materiaalien-virheet
                            jarjestelman-luoma?]) :tyyppi :komponentti}
           {:otsikko "Suorittaja" :pakollinen? true :tyyppi :string :pituus-max 256
            :muokattava? (constantly (not jarjestelman-luoma?)) :nimi :suorittaja :validoi [[:ei-tyhja "Anna suorittaja"]]}
           {:otsikko "Suorittajan y-tunnus" :pakollinen? true :tyyppi :string :pituus-max 9
            :nimi :ytunnus :muokattava? (constantly (not jarjestelman-luoma?)) :validoi [[:ei-tyhja "Anna y-tunnus"]
                                                                                         [:ytunnus]]}
           {:otsikko "Lisätietoja" :tyyppi :text :palstoja 2 :koko [80 :auto]
            :nimi :lisatieto :muokattava? (constantly (not jarjestelman-luoma?))}]
          @materiaali-tiedot/valitun-materiaalitoteuman-tiedot]]))))

(defn tarkastele-toteumaa-nappi [rivi urakka-id]
  [:button.nappi-toissijainen.nappi-grid {:on-click #(aseta-lomakkeen-tiedot rivi urakka-id)}
   (ikonit/eye-open) " Toteuma"])

(defn materiaalinkaytto-vetolaatikko
  [urakan-id mk]
  (let [tiedot (reaction<! [aika @valittu-aikavali
                            sop @u/valittu-sopimusnumero]
                           (materiaali-tiedot/hae-toteumat-materiaalille
                            urakan-id (:id (:materiaali mk)) aika (first sop)))
        tallenna (reaction
                   (if (or (not (oikeudet/voi-kirjoittaa? oikeudet/urakat-toteumat-materiaalit
                                                          (:id @nav/valittu-urakka)))
                           (nil? @tiedot)
                           (every? :jarjestelmanlisaama @tiedot))
                     :ei-mahdollinen
                     (tallenna-toteuma-materiaaleja urakan-id tiedot)))]
    (komp/luo
      (fn [urakan-id vm]
        {:key (:id vm)}
        [:div
         [grid/grid
          {:otsikko (str (get-in mk [:materiaali :nimi]) " toteumat")
           :tyhja (if (nil? @tiedot) [ajax-loader "Ladataan toteumia"] "Ei toteumia")
           :tallenna @tallenna
           :voi-lisata? false
           :esta-poistaminen? (comp :jarjestelmanlisaama :toteuma)
           :esta-poistaminen-tooltip (fn [_] "Järjestelmän lisäämää toteumaa ei voi poistaa.")
           :voi-muokata-rivia? (comp not :jarjestelmanlisaama :toteuma)
           :tunniste :tmid}
          [{:otsikko "Päivämäärä"
            :tyyppi :pvm
            :nimi :aloitus
            :leveys "20%"
            :hae (comp pvm/pvm :alkanut :toteuma)
            :muokattava? (constantly false)}
           {:otsikko "Määrä"
            :muokattava? (comp not :jarjestelmanlisaama :toteuma)
            :nimi :toteuman_maara
            :tyyppi :numero
            :hae (comp :maara :toteuma)
            :aseta #(assoc-in %1 [:toteuma :maara] %2)
            :leveys "20%"
            :tasaa :oikea}
           {:otsikko "Suorittaja"
            :nimi :suorittaja
            :pituus-max 256
            :tyyppi :text
            :hae (comp :suorittaja :toteuma)
            :muokattava? (constantly false)
            :leveys "20%"}
           {:otsikko "Lisätietoja"
            :nimi :lisatiedot
            :tyyppi :text
            :hae (comp :lisatieto :toteuma)
            :muokattava? (constantly false)
            :leveys "20%"}
           {:otsikko "Tarkastele koko toteumaa"
            :nimi :tarkastele-toteumaa
            :tyyppi :komponentti
            :komponentti (fn [rivi] (tarkastele-toteumaa-nappi rivi urakan-id))
            :muokattava? (constantly false)
            :leveys "20%"}]
          @tiedot]]))))

(defn materiaalit-paasivu
  [ur]
  [:div
   [valinnat/urakan-sopimus ur]
   [valinnat/aikavali-nykypvm-taakse ur valittu-aikavali]

   (let [oikeus? (oikeudet/voi-kirjoittaa?
                   oikeudet/urakat-toteumat-materiaalit
                   (:id @nav/valittu-urakka))]
     (yleiset/wrap-if
       (not oikeus?)
       [yleiset/tooltip {} :%
        (oikeudet/oikeuden-puute-kuvaus :kirjoitus
                                        oikeudet/urakat-toteumat-materiaalit)]
       [napit/uusi "Lisää toteuma" #(aseta-lomakkeen-tiedot {} (:id ur))
        {:disabled (not oikeus?)}]))
   [grid/grid
    {:otsikko "Materiaalien käyttö"
     :tyhja (if (nil? @urakan-materiaalin-kaytot) [ajax-loader "Materiaaleja haetaan"] "Ei löytyneitä tietoja.")
     :tunniste #(:id (:materiaali %))
     :luokat ["toteumat-paasisalto"]
     :vetolaatikot
     (into {}
           (map
             (juxt
               (comp :id :materiaali)
               (fn [mk] [materiaalinkaytto-vetolaatikko (:id ur) mk]))
             )
           (filter
             (fn [rivi] (not (zero? (:kokonaismaara rivi)))) ;; Ei oteta mukaan toteumarivejä, joissa määrä on nolla. Niitä tulee välillä koneellisessa seurannassa.
             @urakan-materiaalin-kaytot))
     }

    ;; sarakkeet
    [{:tyyppi :vetolaatikon-tila :leveys 1}
     {:otsikko "Nimi" :nimi :materiaali_nimi :hae (comp :nimi :materiaali) :leveys 10}
     {:otsikko "Yksik\u00ADkö" :nimi :materiaali_yksikko :hae (comp :yksikko :materiaali) :leveys 2}
     {:otsikko "Suunniteltu määrä" :nimi :sovittu_maara :hae :maara
      :tyyppi :numero :desimaalien-maara 2 :leveys 4 :tasaa :oikea}
     {:otsikko "Käytetty määrä" :nimi :toteutunut_maara :hae :kokonaismaara
      :tyyppi :numero :desimaalien-maara 2 :leveys 4 :tasaa :oikea}
     {:otsikko "Jäljellä" :nimi :materiaalierotus :tyyppi :komponentti :tasaa :oikea
      :muokattava? (constantly false) :leveys 4
      :komponentti
      (fn [{:keys [maara kokonaismaara]}]
        (if-not maara
          [:span]
          (let [erotus (fmt/desimaaliluku-opt (- maara kokonaismaara) 2)]
            (if (>= erotus 0)
              [:span.materiaalierotus.materiaalierotus-positiivinen erotus]
              [:span.materiaalierotus.materiaalierotus-negatiivinen erotus]))))}]

    (sort-by (comp :nimi :materiaali) @urakan-materiaalin-kaytot)]])

(defn muut-materiaalit-nakyma [ur]
  (komp/luo
    (komp/lippu materiaali-tiedot/materiaalinakymassa?)
    (komp/sisaan-ulos #(do
                         (kartta-tasot/taso-paalle! :materiaalitoteumat))
      #(do
         (kartta-tasot/taso-pois! :materiaalitoteumat)
         (kartta-tasot/poista-geometria! :tr-valittu-osoite)))
    (fn [ur]
      [:span
       [kartta/kartan-paikka]
       (if @materiaali-tiedot/valitun-materiaalitoteuman-tiedot
         [materiaalit-tiedot ur]
         [materiaalit-paasivu ur])])))
