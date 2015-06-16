(ns harja.views.urakka.toteumat.materiaalit
  (:require [harja.views.urakka.valinnat :as valinnat]
            [reagent.core :refer [atom]]
            [harja.loki :refer [log]]
            [harja.ui.lomake :refer [lomake]]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.pvm :as pvm]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.komponentti :as komp]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.urakka :as u]
            [harja.ui.grid :as grid]

            [harja.tiedot.urakka.toteumat :as toteumat]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.materiaalit :as materiaali-tiedot]

            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

(defonce valittu-materiaalin-kaytto (atom nil))

(defonce urakan-materiaalin-kaytot
  (reaction<! (when @materiaali-tiedot/materiaalinakymassa?
                (let [sopimusnumero (first @u/valittu-sopimusnumero)
                      [alku loppu] @u/valittu-hoitokausi
                      ur @nav/valittu-urakka]
                  (when (and sopimusnumero alku loppu ur)
                  (materiaali-tiedot/hae-urakassa-kaytetyt-materiaalit (:id ur)
                                                                       alku
                                                                       loppu
                                                                       sopimusnumero))))))

(defn tallenna-toteuma-ja-toteumamateriaalit!
  [tm m]
  (log "RAAKA TM: "(pr-str tm))
  (log "RAAKA T: "(pr-str m))
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
                 :sopimus (first @u/valittu-sopimusnumero) :tyyppi nil
                 :suorittajan-nimi (:suorittaja m)
                 :suorittajan-ytunnus (:ytunnus m)
                 :lisatieto (:lisatieto m)}
        hoitokausi @u/valittu-hoitokausi
        sopimus-id (first @u/valittu-sopimusnumero)]
    (log "KÄSITELTY TM: " (pr-str toteumamateriaalit))
    (log "KÄSITELTY T: " (pr-str toteuma))
    (toteumat/tallenna-toteuma-ja-toteumamateriaalit! toteuma toteumamateriaalit hoitokausi sopimus-id)))

(def materiaalikoodit (reaction (into []
                                      (comp
                                        (map #(dissoc % :urakkatyyppi))
                                        (map #(dissoc % :kohdistettava)))
                                      @(materiaali-tiedot/hae-materiaalikoodit))))

(defn hae-tiedot-vetolaatikkoon
  [tiedot urakan-id materiaali-id]
  (go
    (reset! tiedot
            (filter
              (fn [kartta]
                (let [hoitokauden-alku (first @u/valittu-hoitokausi)
                      hoitokauden-loppu (second @u/valittu-hoitokausi)
                      toteuman-alku (:alkanut (:toteuma kartta))
                      toteuman-loppu (:paattynyt (:toteuma kartta))]

                  (log "Toteuman alku:" (pvm/pvm toteuman-alku))
                  (log "Hoitokausi:" (pvm/pvm hoitokauden-alku) "-" (pvm/pvm hoitokauden-loppu))
                  (and
                    (pvm/sama-tai-jalkeen? toteuman-alku hoitokauden-alku)
                    (pvm/sama-tai-ennen? toteuman-alku hoitokauden-loppu))))
              (<!(materiaali-tiedot/hae-toteumat-materiaalille urakan-id materiaali-id))))))

(defn tallenna-toteuma-materiaaleja
  [urakka atomi]
  "Tätä funktiota käytetään, kun materiaalitoteuman tietoja muutetaan suoraan pääsivulla,
  kun vetolaatikko on aukaistu. Parametrina saatava atomi sisältää vetolaatikossa näytettävät tiedot,
  ja se päivitetään kun tallennus on saatu tehtyä - eli joudutaan tekemään kaksi kyselyä."
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
      (log (pr-str toteumamateriaalit))
      (go (let [tulos (<!(materiaali-tiedot/tallenna-toteuma-materiaaleja urakka
                                                                          toteumamateriaalit
                                                                          @u/valittu-hoitokausi
                                                                          (first @u/valittu-sopimusnumero)))]
            (log (pr-str tulos))
            ;; fixme: (reset! atomi uudet tiedot)
            (reset! urakan-materiaalin-kaytot tulos)
            ;; Koska vetolaatikon kautta voidaan kerralla muokata vain yhtä materiaalia, voidaan kaivaa vaan
            ;; materiaalikoodi ensimmäisestä materiaalitoteumasta.
            (hae-tiedot-vetolaatikkoon atomi urakka (:materiaalikoodi (first toteumamateriaalit))))))))

(defn materiaalit-ja-maarat
  [materiaalit-atom virheet-atom]


  (log "Materiaalit-ja-maarat, tiedot: " (pr-str @materiaalit-atom))
  (log "Materiaalikoodit:" (pr-str @materiaalikoodit))

  [grid/muokkaus-grid
   {:tyhja "Ei materiaaleja."
    :muutos (fn [g] (reset! virheet-atom (grid/hae-virheet g)))}

   [{:otsikko "Materiaali" :nimi :materiaali :tyyppi :valinta
     :valinnat @materiaalikoodit
     :valinta-nayta #(if % (:nimi %) "- valitse materiaali -")
     :validoi [[:ei-tyhja "Valitse materiaali."]]
     :leveys "50%"}

    {:otsikko "Määrä" :nimi :maara :tyyppi :string :validoi [[:positiivinen-luku "Anna käytetty määrä."]] :leveys "40%"}
    {:otsikko "Yks." :muokattava? (constantly false) :nimi :yksikko :hae (comp :yksikko :materiaali) :leveys "5%"}]
   materiaalit-atom])

(defn materiaalit-tiedot
  [ur]
  "Valitun toteuman tietojen näkymä"
  [ur]
  (let [tiedot (atom nil)
        muokattu (reaction @tiedot)
        ;tallennus-kaynnissa (atom false)
        materiaalitoteumat-mapissa (reaction (into {} (map (juxt :tmid identity) (:toteumamateriaalit @tiedot))))
        uusi-toteuma? (if (:id @valittu-materiaalin-kaytto) true false)
        lomakkeen-virheet (atom {})
        materiaalien-virheet (atom {})]

    (komp/luo
      {:component-will-mount
       (fn [_]
         (log "MATERIAALIT-TIEDOT WILL MOUNT")
         (log (pr-str @valittu-materiaalin-kaytto))
         (when (:id @valittu-materiaalin-kaytto)
           (go
             (reset! tiedot
                     (<!(materiaali-tiedot/hae-toteuman-materiaalitiedot (:id ur) (:id @valittu-materiaalin-kaytto)))))))}

      (fn [ur]
        (log "Lomake, muokattu: " (pr-str @muokattu))
        (log "Uusi toteuma?" uusi-toteuma?)
        [:div.toteuman-tiedot
         [:button.nappi-toissijainen {:on-click #(reset! valittu-materiaalin-kaytto nil)}
          (ikonit/chevron-left) " Takaisin materiaaliluetteloon"]
         (if uusi-toteuma?
           [:h3 "Muokkaa toteumaa"]
           [:h3 "Luo uusi toteuma"])

         [lomake {:luokka   :horizontal
                  :muokkaa! (fn [uusi]
                              (log "MUOKATAAN " (pr-str uusi))
                              (reset! muokattu uusi))
                  :footer   [harja.ui.napit/palvelinkutsu-nappi
                             "Tallenna toteuma"
                             #(tallenna-toteuma-ja-toteumamateriaalit!
                               (vals
                                 ;; Poista rivit joissa negatiivinen id (uusi) ja poistettu = true
                                 (filter
                                   (fn [kartta]
                                     (not
                                       (and
                                         (neg? (key kartta))
                                         (:poistettu (val kartta))))
                                     @materiaalitoteumat-mapissa)))
                                 @muokattu)
                             {:luokka "nappi-ensisijainen"
                              :ikoni (ikonit/envelope)
                              :kun-onnistuu
                                      #(do
                                        (reset! urakan-materiaalin-kaytot %)
                                        (reset! valittu-materiaalin-kaytto nil))
                              :disabled (or
                                          (> (count @lomakkeen-virheet ) 0)
                                          (= (count @muokattu) 0)
                                          (= (count @materiaalitoteumat-mapissa) 0)
                                          (> (count @materiaalien-virheet) 0))}]
                  :virheet lomakkeen-virheet}

          [{:otsikko "Sopimus" :nimi :sopimus :hae (fn [_] (second @u/valittu-sopimusnumero)) :muokattava? (constantly false)}

           {:otsikko "Hoitokausi" :nimi :hoitokausi :hae (fn [_]
                                                           (let [[alku loppu] @u/valittu-hoitokausi]
                                                             [:span (pvm/pvm alku) " \u2014 " (pvm/pvm loppu)]))
            :fmt identity
            :muokattava? (constantly false)}
           {:otsikko     "Aloitus" :tyyppi :pvm :nimi :alkanut :validoi [[:ei-tyhja "Anna aloituspäivämäärä"]]
            :varoita [[:urakan-aikana]]
            :muokattava? (constantly (not uusi-toteuma?)) :aseta (fn [rivi arvo]
                                                                   (assoc
                                                                     (if
                                                                       (or
                                                                         (not (:paattynyt rivi))
                                                                         (pvm/jalkeen? arvo (:paattynyt rivi)))
                                                                       (assoc rivi :paattynyt arvo)
                                                                       rivi)
                                                                     :alkanut
                                                                     arvo))
            :leveys "30%"}
           {:otsikko "Lopetus" :tyyppi :pvm :nimi :paattynyt :validoi [[:ei-tyhja "Anna lopetuspäivämäärä"]
                                                                       [:pvm-kentan-jalkeen :alkanut "Lopetuksen pitää olla aloituksen jälkeen"]]
            :muokattava? (constantly (not uusi-toteuma?)) :leveys "30%"}
           {:otsikko "Suorittaja" :tyyppi :string :nimi :suorittaja :validoi [[:ei-tyhja "Anna suorittaja"]]}
           {:otsikko "Suorittajan y-tunnus" :tyyppi :string :nimi :ytunnus :validoi [[:ei-tyhja "Anna y-tunnus"]]}
           {:otsikko "Lisätietoja" :tyyppi :text :nimi :lisatieto}
           {:otsikko "Materiaalit" :nimi :materiaalit :komponentti [materiaalit-ja-maarat
                                                                    materiaalitoteumat-mapissa
                                                                    materiaalien-virheet] :tyyppi :komponentti}]

          @muokattu]]))))

(defn tarkastele-toteumaa-nappi [rivi]
  [:button.nappi-toissijainen {:on-click #(reset! valittu-materiaalin-kaytto rivi)} (ikonit/eye-open) " Toteuma"])

(defn materiaalinkaytto-vetolaatikko
  [urakan-id mk]
  (let [tiedot (atom nil)]
    (komp/luo
      {:component-will-mount
       (fn [_]
         (hae-tiedot-vetolaatikkoon tiedot urakan-id (:id (:materiaali mk))))}

      (fn [urakan-id vm]
        (log "Vetolaatikko tiedot:" (pr-str @tiedot))
        {:key (:id vm)}
        [:div
         [grid/grid
          {:otsikko (str (get-in mk [:materiaali :nimi]) " toteumat")
           :tyhja   (if (nil? @tiedot) [ajax-loader "Ladataan toteumia"] "Ei toteumia")
           :tallenna (tallenna-toteuma-materiaaleja urakan-id tiedot)
           :voi-lisata? false
           :luokat ["toteumat-haitari"]}

          [{:otsikko "Päivämäärä" :tyyppi :pvm :nimi :aloitus
            :hae (comp pvm/pvm :alkanut :toteuma) :muokattava? (constantly false)}
           {:otsikko "Määrä" :nimi :toteuman_maara :tyyppi :numero :hae (comp :maara :toteuma) :aseta #(assoc-in %1 [:toteuma :maara] %2)
            :validoi [[:positiivinen-luku "Anna käytetty määrä."]]}
           {:otsikko "Suorittaja" :nimi :suorittaja :tyyppi :text :hae (comp :suorittaja :toteuma) :muokattava? (constantly false)}
           {:otsikko "Lisätietoja" :nimi :lisatiedot :tyyppi :text :hae (comp :lisatieto :toteuma) :muokattava? (constantly false)}
           {:otsikko "Tarkastele koko toteumaa" :nimi :tarkastele-toteumaa :tyyppi :komponentti
            :komponentti (fn [rivi] (tarkastele-toteumaa-nappi rivi)) :muokattava? (constantly false)}]
          @tiedot]]))))

(defn materiaalit-paasivu
  [ur]
  (log "Paasivu, urakan-materiaalin-kaytot:" (pr-str @urakan-materiaalin-kaytot))
  [:div
   [valinnat/urakan-sopimus-ja-hoitokausi ur]
   [:button.nappi-ensisijainen {:on-click #(reset! valittu-materiaalin-kaytto {})}
    (ikonit/plus-sign) " Lisää toteuma"]
   [grid/grid
    {:otsikko        "Suunnitellut ja toteutuneet materiaalit"
     :tyhja          (if (nil? @urakan-materiaalin-kaytot) [ajax-loader "Toteuman materiaaleja haetaan."] "Ei löytyneitä tietoja.")
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
                             (fn [rivi] (> (:kokonaismaara rivi) 0))
                             @urakan-materiaalin-kaytot))
     }

    ;; sarakkeet
    [{:tyyppi :vetolaatikon-tila :leveys "5%"}
     {:otsikko "Nimi" :nimi :materiaali_nimi :hae (comp :nimi :materiaali) :leveys "50%"}
     {:otsikko "Yksikkö" :nimi :materiaali_yksikko :hae (comp :yksikko :materiaali) :leveys "10%"}
     {:otsikko "Suunniteltu määrä" :nimi :sovittu_maara :hae :maara :leveys "20%"}
     {:otsikko "Käytetty määrä" :nimi :toteutunut_maara :hae :kokonaismaara :leveys "20%"}
     {:otsikko "Jäljellä" :nimi :materiaalierotus :tyyppi :komponentti
      :muokattava? (constantly false) :leveys "20%"
      :komponentti
      (fn [rivi]
        (let [erotus (-
                       (if (:maara rivi) (:maara rivi) 0)
                       (:kokonaismaara rivi))]
          (if (>= erotus 0)
            [:span.materiaalierotus.materiaalierotus-positiivinen erotus]
            [:span.materiaalierotus.materiaalierotus-negatiivinen erotus])))}
     ]

    (sort-by (comp :nimi :materiaali) @urakan-materiaalin-kaytot)]])

(defn materiaalit-nakyma [ur]
  (komp/luo
    (komp/lippu materiaali-tiedot/materiaalinakymassa?)

    (fn [ur]
      (if @valittu-materiaalin-kaytto
        [materiaalit-tiedot ur]
        [materiaalit-paasivu ur]))))