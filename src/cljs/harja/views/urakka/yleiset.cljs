(ns harja.views.urakka.yleiset
  "Urakan 'Yleiset' välilehti: perustiedot ja yhteyshenkilöt"
  (:require [reagent.core :refer [atom] :as reagent]
            [harja.ui.bootstrap :as bs]
            [clojure.string :as string]
            [harja.domain.roolit :as roolit]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.yhteystiedot :as yht]
            [harja.tiedot.urakka.sopimustiedot :as sopimus]
            [harja.tiedot.navigaatio :as navigaatio]
            [harja.loki :refer [log]]
            [harja.views.urakka.yhatuonti :as yha]
            [harja.pvm :as pvm]

            [cljs.core.async :refer [<!]]
            [clojure.string :as str]
            [cljs-time.core :as t]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.modal :as modal]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.komponentti :as komp])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.ui.yleiset :refer [deftk]]))




;; hallintayksikkö myös
;; organisaatio = valinta siitä mitä on tietokannassa
;; sampoid

(defn tallenna-yhteyshenkilot [ur yhteyshenkilot uudet-yhteyshenkilot]
  (go (let [tallennettavat
            (into []
                  ;; Kaikki tiedon mankelointi ennen lähetystä tähän
                  (comp (filter #(not (:poistettu %)))
                        (map #(if-let [nimi (:nimi %)]
                                (let [[_ etu suku] (re-matches #"^ *([^ ]+)( *.*?) *$" nimi)]
                                  (assoc %
                                    :etunimi (str/trim etu)
                                    :sukunimi (str/trim suku)))
                                %)))
                  uudet-yhteyshenkilot)
            poistettavat
            (into []
                  (keep #(when (and (:poistettu %)
                                    (> (:id %) 0))
                           (:id %)))
                  uudet-yhteyshenkilot)
            res (<! (yht/tallenna-urakan-yhteyshenkilot (:id ur) tallennettavat poistettavat))]
        (reset! yhteyshenkilot res)
        true)))

(defn tallenna-paivystajat [ur paivystajat uudet-paivystajat]
  (log "tallenna päivystäjät!" (pr-str uudet-paivystajat))
  (go (let [tallennettavat
            (into []
                  ;; Kaikki tiedon mankelointi ennen lähetystä tähän
                  (comp (filter #(not (:poistettu %)))
                        (map #(if-let [nimi (:nimi %)]
                                (let [[_ etu suku] (re-matches #"^ *([^ ]+)( *.*?) *$" nimi)]
                                  (assoc %
                                    :etunimi (str/trim etu)
                                    :sukunimi (str/trim suku)))
                                %)))
                  uudet-paivystajat)
            poistettavat
            (into []
                  (keep #(when (and (:poistettu %)
                                    (> (:id %) 0))
                           (:id %)))
                  uudet-paivystajat)
            res (<! (yht/tallenna-urakan-paivystajat (:id ur) tallennettavat poistettavat))]
        (reset! paivystajat res)
        true)))

(defn tallenna-sopimustyyppi [ur uusi-sopimustyyppi]
  (go (let [res (<! (sopimus/tallenna-sopimustyyppi (:id ur) uusi-sopimustyyppi))]
        (if-not (k/virhe? res)
          (nav/paivita-urakka (:id ur) assoc :sopimustyyppi res)
          true))))

(defn vahvista-urakkatyypin-vaihtaminen [ur uusi-urakkatyyppi]
  (when-not (= uusi-urakkatyyppi (:tyyppi ur))
    (let [vaihda-urakkatyyppi (fn []
                                (go (let [res (<! (urakka/vaihda-urakkatyyppi
                                                   (:id ur)
                                                   (name uusi-urakkatyyppi)))]
                                      (if-not (k/virhe? res)
                                        (nav/paivita-urakka (:id ur) assoc :tyyppi res)
                                        true))))]
    (modal/nayta!
     {:otsikko "Vaihdetaanko urakkatyyppi?"
      :footer  [:span
                [:button.nappi-toissijainen {:type     "button"
                                             :on-click #(do (.preventDefault %)
                                                            (modal/piilota!))}
                 "Peruuta"]
                [:button.nappi-myonteinen {:type     "button"
                                           :on-click #(do (.preventDefault %)
                                                          (modal/piilota!)
                                                          (vaihda-urakkatyyppi))}
                 "Vaihda"]]}
     [:div
      [:p (str "Haluatko varmasti vaihtaa " (navigaatio/nayta-urakkatyyppi (:tyyppi ur))
               "-tyyppisen urakan ")
       [:strong (str (navigaatio/nayta-urakkatyyppi uusi-urakkatyyppi) "-tyyppiseksi")] "?"]
      [:p (str "Vaihtamisen jälkeen nykyiseen "
               (string/lower-case (navigaatio/nayta-urakkatyyppi (:tyyppi ur)))
               "urakkaan sidotut tiedot, kuten ilmoituslomakkeet, säilytetään, "
               "mutta ne eivät enää ole näkyvissä.")]]))))

(defn urakkaan-liitetyt-kayttajat [urakka-id]
  (let [kayttajat (atom nil)
        hae! (fn [urakka-id]
               (reset! kayttajat nil)
               (go (reset! kayttajat (<! (yht/hae-urakan-kayttajat urakka-id)))))]
    (hae! urakka-id)
    (komp/luo
     (komp/kun-muuttuu hae!)
     (fn [urakka-id]
       [grid/grid
        {:otsikko "Urakkaan liitetyt käyttäjät"
         :tunniste :kayttajatunnus
         :tyhja (if (nil? @kayttajat)
                  [yleiset/ajax-loader "Haetaan urakkaan liitettyjä käyttäjiä"]
                  "Ei urakkaan liitettyjä käyttäjiä.")}

        [{:otsikko "Rooli" :nimi :roolit :fmt #(str/join ", " %) :tyyppi :string :leveys "15%"}
         {:otsikko "Organisaatio" :nimi :organisaatio :tyyppi :string :leveys "15%"}
         {:otsikko "Nimi" :nimi :nimi :hae #(str (:etunimi %) " " (:sukunimi %)) :tyyppi :string
          :leveys "25%"}
         {:otsikko "Puhelin" :nimi :puhelin :tyyppi :string :leveys "20%"}
         {:otsikko "Sähköposti" :nimi :sahkoposti :tyyppi :string :leveys "25%"}]
        @kayttajat]))))

(deftk yleiset [ur]
  [yhteyshenkilot (<! (yht/hae-urakan-yhteyshenkilot (:id ur)))
   paivystajat (reverse (sort-by :loppu (<! (yht/hae-urakan-paivystajat (:id ur)))))
   yhteyshenkilotyypit (<! (yht/hae-yhteyshenkilotyypit))
   sopimustyyppi (:sopimustyyppi ur)]

  (let [kirjoitusoikeus? (oikeudet/voi-kirjoittaa? oikeudet/urakat-yleiset (:id ur))]
    (yha/nayta-tuontidialogi ur) ;; FIXME Näytä vain jos tarvii
    [:div
     [bs/panel {}
      "Yleiset tiedot"
      [yleiset/tietoja {}
       (when false ;; FIXME Kun urakka sidottu
         "YHA-tunnus" "TODO"
         "ELYt" "TODO"
         "Vuodet" "TODO")
       "Urakan nimi:" (:nimi ur)
       "Urakan tunnus:" (:sampoid ur)
       "Sopimuksen tunnus: " (some->> ur :sopimukset vals (str/join ", "))
       "Aikaväli:" [:span.aikavali (pvm/pvm (:alkupvm ur)) " \u2014 " (pvm/pvm (:loppupvm ur))]
       "Tilaaja:" (:nimi (:hallintayksikko ur))
       "Urakoitsija:" (:nimi (:urakoitsija ur))
       ;; valaistus, tiemerkintä --> palvelusopimus
       ;; päällystys --> kokonaisurakka
       "Sopimustyyppi: "
       (when-not (= :hoito (:tyyppi ur))
         [yleiset/livi-pudotusvalikko {:class      "alasveto-yleiset-tiedot"
                                       :valinta    @sopimustyyppi
                                       :format-fn  #(if %
                                                      (str/capitalize (name %))
                                                      "Ei sopimustyyppiä")
                                       :valitse-fn #(tallenna-sopimustyyppi ur %)
                                       :disabled   (not kirjoitusoikeus?)}
          sopimus/+sopimustyypit+])
       "Urakkatyyppi: " ; Päällystysurakan voi muuttaa paikkaukseksi ja vice versa
       (when (or (= :paikkaus (:tyyppi ur))
                 (= :paallystys (:tyyppi ur)))
         [yleiset/livi-pudotusvalikko {:class      "alasveto-yleiset-tiedot"
                                       :valinta    (:tyyppi ur)
                                       :format-fn  #(navigaatio/nayta-urakkatyyppi %)
                                       :valitse-fn #(vahvista-urakkatyypin-vaihtaminen ur %)
                                       :disabled   (not kirjoitusoikeus?)}
          [:paallystys :paikkaus]])]]

     [urakkaan-liitetyt-kayttajat (:id ur)]

     [grid/grid
      {:otsikko "Yhteyshenkilöt"
       :tyhja "Ei yhteyshenkilöitä."
       :tallenna #(tallenna-yhteyshenkilot ur yhteyshenkilot %)}
      [{:otsikko       "Rooli" :nimi :rooli :tyyppi :valinta :leveys "17%"
        :hae #(do (when (:rooli %)
                  (str/capitalize (:rooli %))))
        :valinta-nayta #(if (nil? %) "- valitse -" (str/capitalize %))

        :valinnat      (vec (concat [nil] @yhteyshenkilotyypit))

        :validoi       [[:ei-tyhja "Anna yhteyshenkilön rooli"]]}
       {:otsikko "Organisaatio"
        :nimi :organisaatio
        :fmt :nimi
        :leveys "17%"
        :tyyppi :valinta
        :valinta-nayta #(if % (:nimi %) "- Valitse organisaatio -")
        :valinnat [nil (:urakoitsija ur) (:hallintayksikko ur)]}

       {:otsikko "Nimi" :hae #(if-let [nimi (:nimi %)]
                                nimi
                                (str (:etunimi %)
                                     (when-let [suku (:sukunimi %)]
                                       (str " " suku))))
        :pituus-max 64
        :aseta (fn [yht arvo]
                 (assoc yht :nimi arvo))
        :tyyppi :string :leveys "15%"
        :validoi [[:ei-tyhja "Anna yhteyshenkilön nimi"]]}
       {:otsikko "Puhelin (virka)" :nimi :tyopuhelin :tyyppi :puhelin :leveys "12%" :pituus 16}
       {:otsikko "Puhelin (gsm)" :nimi :matkapuhelin :tyyppi :puhelin :leveys "12%" :pituus 16}
       {:otsikko "Sähköposti" :nimi :sahkoposti :tyyppi :email :leveys "22%"}]
      @yhteyshenkilot]


     [grid/grid
      {:otsikko "Päivystystiedot"
       :tyhja "Ei päivystystietoja."
       :tallenna #(tallenna-paivystajat ur paivystajat %)}
      [{:otsikko "Nimi" :hae #(if-let [nimi (:nimi %)]
                                nimi
                                (str (:etunimi %)
                                     (when-let [suku (:sukunimi %)]
                                       (str " " suku))))
        :aseta (fn [yht arvo]
                 (assoc yht :nimi arvo))


        :tyyppi :string :leveys "20%"
        :validoi [[:ei-tyhja  "Anna päivystäjän nimi"]]}
       {:otsikko "Organisaatio" :nimi :organisaatio :fmt :nimi :leveys "15%"
        :tyyppi :valinta :muokattava? (constantly false)
        :valinta-nayta #(if % (:nimi %) "- Valitse organisaatio -")
        :valinnat [nil (:urakoitsija ur) (:hallintayksikko ur)]}

       {:otsikko "Puhelin (virka)" :nimi :tyopuhelin :tyyppi :puhelin :leveys "10%"
        :pituus 16}
       {:otsikko "Puhelin (gsm)" :nimi :matkapuhelin :tyyppi :puhelin :leveys "10%"
        :pituus 16}
       {:otsikko  "Sähköposti" :nimi :sahkoposti :tyyppi :email :leveys "20%"
        :validoi [[:ei-tyhja "Anna päivystäjän sähköposti"]]}
       {:otsikko "Alkupvm" :nimi :alku :tyyppi :pvm :fmt pvm/pvm :leveys "10%"
        :validoi [[:ei-tyhja "Aseta alkupvm"]
                  (fn [alku rivi]
                    (let [loppu (:loppu rivi)]
                      (when (and alku loppu
                               (t/before? loppu alku))
                        "Alkupvm ei voi olla lopun jälkeen.")))
                  ]}
       {:otsikko "Loppupvm" :nimi :loppu :tyyppi :pvm :fmt pvm/pvm :leveys "10%"
        :validoi [[:ei-tyhja "Aseta loppupvm"]
                  (fn [loppu rivi]
                    (let [alku (:alku rivi)]
                      (when (and alku loppu
                                 (t/before? loppu alku))
                        "Loppupvm ei voi olla alkua ennen.")))]}]
      @paivystajat]]))
