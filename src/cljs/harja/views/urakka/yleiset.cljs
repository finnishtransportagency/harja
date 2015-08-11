(ns harja.views.urakka.yleiset
  "Urakan 'Yleiset' välilehti: perustiedot ja yhteyshenkilöt"
  (:require [reagent.core :refer [atom] :as reagent]
            [bootstrap :as bs]
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
            [harja.pvm :as pvm]

            [cljs.core.async :refer [<!]]
            [clojure.string :as str]
            [cljs-time.core :as t]
            [harja.domain.roolit :as roolit]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.modal :as modal])
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
  (go (let [res (<! (sopimus/tallenna-sopimustyyppi (:id ur) (name uusi-sopimustyyppi)))]
        (if-not (k/virhe? res)
          (nav/paivita-urakka (:id ur) assoc :sopimustyyppi res)
          true))))

(defn vahvista-urakkatyypin-vaihtaminen [ur uusi-urakkatyyppi]
  (let [vaihda-urakkatyyppi (fn [] (go (let [res (<! (urakka/vaihda-urakkatyyppi (:id ur) (name uusi-urakkatyyppi)))]
                                     (if-not (k/virhe? res)
                                       (nav/paivita-urakka (:id ur) assoc :tyyppi res) ; FIXME Tiedot päivittyy vasta kun Harja ladataan uudelleen?
                                       true))))]
  (modal/nayta! {:otsikko "Vaihdetaanko urakkatyyppi?"
                 :footer  [:span
                           [:button.nappi-toissijainen {:type     "button"
                                                        :on-click #(do (.preventDefault %)
                                                                       (modal/piilota!))}
                            "Peruuta"]
                           [:button.nappi-kielteinen {:type     "button"
                                                      :on-click #(do (.preventDefault %)
                                                                     (modal/piilota!)
                                                                     (vaihda-urakkatyyppi))}
                            "Vaihda"]
                           ]}
                [:div (str "Haluatko varmasti vaihtaa " (navigaatio/nayta-urakkatyyppi (:tyyppi ur)) "-tyyppisen urakan "
                           (navigaatio/nayta-urakkatyyppi uusi-urakkatyyppi) "-tyyppiseksi?")])))

(deftk yleiset [ur]
  [yhteyshenkilot (<! (yht/hae-urakan-yhteyshenkilot (:id ur)))
   kayttajat (<! (yht/hae-urakan-kayttajat (:id ur)))
   paivystajat (<! (yht/hae-urakan-paivystajat (:id ur)))
   yhteyshenkilotyypit (<! (yht/hae-yhteyshenkilotyypit))
   sopimustyyppi (:sopimustyyppi ur)]

  (do
    [:div
     [bs/panel {}
      "Yleiset tiedot" 
      [yleiset/tietoja {} 
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
                                       :format-fn  #(if % (str/capitalize (name %)) "Ei sopimustyyppiä")
                                       :valitse-fn #(tallenna-sopimustyyppi ur %)
                                       :disabled   (not (roolit/rooli-urakassa? roolit/urakanvalvoja (:id ur)))}
          sopimus/+sopimustyypit+])
       "Urakkatyyppi: " ; Päällystysurakan voi muuttaa paikkaukseksi ja vice versa
       (when (or (= :paikkaus (:tyyppi ur))
                 (= :paallystys (:tyyppi ur)))
         [yleiset/livi-pudotusvalikko {:class      "alasveto-yleiset-tiedot"
                                       :valinta    (:tyyppi ur)
                                       :format-fn  #(navigaatio/nayta-urakkatyyppi %)
                                       :valitse-fn #(vahvista-urakkatyypin-vaihtaminen ur %)
                                       :disabled   (not (roolit/rooli-urakassa? roolit/urakanvalvoja (:id ur)))}
          [:paallystys :paikkaus]])]]

     [grid/grid
      {:otsikko "Urakkaan liitetyt käyttäjät"
       :tyhja "Ei urakkaan liitettyjä käyttäjiä."
       :tunniste (or :sahkoposti #((juxt :etunimi :sukunimi) %))}
      
      [{:otsikko "Rooli" :nimi :rooli :fmt roolit/rooli->kuvaus :tyyppi :string :leveys "15%"}
       {:otsikko "Organisaatio" :nimi :org :hae (comp :nimi :organisaatio) :tyyppi :string :leveys "15%"}
       {:otsikko "Nimi" :nimi :nimi :hae #(str (:etunimi %) " " (:sukunimi %)) :tyyppi :string :leveys "25%"}
       {:otsikko "Puhelin" :nimi :puhelin :tyyppi :string :leveys "20%"}
       {:otsikko "Sähköposti" :nimi :sahkoposti :tyyppi :string :leveys "25%"}]

      @kayttajat]
       
     [grid/grid 
      {:otsikko "Yhteyshenkilöt"
       :tyhja "Ei yhteyshenkilöitä."
       :tallenna #(tallenna-yhteyshenkilot ur yhteyshenkilot %)}
      [{:otsikko "Rooli" :nimi :rooli :tyyppi :valinta  :leveys "17%"
        :valinta-nayta #(if (nil? %) "- valitse -" %)
        
        :valinnat (vec (concat [nil] @yhteyshenkilotyypit))
        
        :validoi [[:ei-tyhja  "Anna yhteyshenkilön rooli"]]}
       {:otsikko "Organisaatio" :nimi :organisaatio :fmt :nimi :leveys "17%"
        :tyyppi :valinta
        ; :validoi [[:ei-tyhja "Anna yhteyshenkilön organisaatio"]] FIXME Vaaditaan, mutta e2e:ssä on tällä hetkellä sellainen bugi, että gridin riviltä ei voi valita toista pudotusvalikkoa.
        :valinta-arvo :id
        :valinta-nayta #(if % (:nimi %) "- valitse -")
        :valinnat [nil (:urakoitsija ur) (:hallintayksikko ur)]}
       
       {:otsikko "Nimi" :hae #(if-let [nimi (:nimi %)]
                                nimi
                                (str (:etunimi %)
                                     (when-let [suku (:sukunimi %)]
                                       (str " " suku))))
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
        :tyyppi :valinta
        :valinta-arvo :id
        :valinta-nayta #(if % (:nimi %) "- valitse -")
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
                        "Loppupvm ei voi olla alkua ennen.")))]} ]
      @paivystajat ]
       
     ]))



