(ns harja.views.urakka.yleiset
  "Urakan 'Yleiset' välilehti: perustiedot ja yhteyshenkilöt"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.bootstrap :as bs]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.hallinta.indeksit :as indeksit]
            [harja.tiedot.urakka.yhteystiedot :as yht]
            [harja.tiedot.urakka.sopimustiedot :as sopimus]
            [harja.tiedot.navigaatio :as navigaatio]
            [harja.tiedot.urakka.yhatuonti :as yhatiedot]
            [harja.views.urakka.yhatuonti :as yha]
            [harja.loki :refer [log tarkkaile!]]
            [harja.pvm :as pvm]

            [cljs.core.async :refer [<!]]
            [clojure.string :as str]
            [cljs-time.core :as t]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.modal :as modal]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.komponentti :as komp]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.fmt :as fmt]
            [harja.ui.ikonit :as ikonit]
            [reagent.core :as r]
            [harja.ui.viesti :as viesti]
            [harja.domain.roolit :as roolit]
            [harja.ui.napit :as napit]
            [harja.tiedot.urakat :as urakat]
            [harja.ui.lomake :as lomake])
  (:require-macros [cljs.core.async.macros :refer [go]]))

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
            vastaus (<! (yht/tallenna-urakan-paivystajat (:id ur) tallennettavat poistettavat))]
        (if (k/virhe? vastaus)
          (viesti/nayta! "Päivystäjien tallennus epäonnistui." :warning viesti/viestin-nayttoaika-keskipitka)
          (do (reset! paivystajat (reverse (sort-by :loppu vastaus)))
              true)))))

(defn tallenna-sopimustyyppi [ur uusi-sopimustyyppi]
  (go (let [res (<! (sopimus/tallenna-sopimustyyppi (:id ur) uusi-sopimustyyppi))]
        (if-not (k/virhe? res)
          (nav/paivita-urakan-tiedot! (:id ur) assoc :sopimustyyppi res)
          true))))

(defn vahvista-urakkatyypin-vaihtaminen [ur uusi-urakkatyyppi]
  (when-not (= uusi-urakkatyyppi (:tyyppi ur))
    (let [vaihda-urakkatyyppi (fn []
                                (go (let [res (<! (urakka/vaihda-urakkatyyppi
                                                    (:id ur)
                                                    (name uusi-urakkatyyppi)))]
                                      (if-not (k/virhe? res)
                                        (nav/paivita-urakan-tiedot! (:id ur) assoc :tyyppi res)
                                        true))))]
      (modal/nayta!
        {:otsikko "Vaihdetaanko urakkatyyppi?"
         :footer [:span
                  [:button.nappi-toissijainen {:type "button"
                                               :on-click #(do (.preventDefault %)
                                                              (modal/piilota!))}
                   "Peruuta"]
                  [:button.nappi-myonteinen {:type "button"
                                             :on-click #(do (.preventDefault %)
                                                            (modal/piilota!)
                                                            (vaihda-urakkatyyppi))}
                   "Vaihda"]]}
        [:div
         [:p (str "Haluatko varmasti vaihtaa " (navigaatio/nayta-urakkatyyppi (:tyyppi ur))
                  "-tyyppisen urakan ")
          [:strong (str (navigaatio/nayta-urakkatyyppi uusi-urakkatyyppi) "-tyyppiseksi")] "?"]]))))

(defn urakkaan-liitetyt-kayttajat [kayttajat]
  [grid/grid
   {:otsikko "Käyttövaltuushallinnassa urakkaan liitetyt käyttäjät"
    :tunniste :kayttajatunnus
    :tyhja (cond
             (nil? kayttajat)
             [yleiset/ajax-loader
              "Haetaan käyttövaltuushallinnassa urakkaan liitettyjä käyttäjiä"]

             (k/virhe? kayttajat)
             "Virhe haettaessa käyttäjiä käyttövaltuushallinnan FIM-palvelusta."

             :default
             "Ei urakkaan liitettyjä käyttäjiä.")}

   [{:otsikko "Rooli" :nimi :roolit :fmt #(str/join ", " %) :tyyppi :string :leveys "15%"}
    {:otsikko "Organisaatio" :nimi :organisaatio :tyyppi :string :leveys "15%"}
    {:otsikko "Nimi" :nimi :nimi :hae #(str (:etunimi %) " " (:sukunimi %)) :tyyppi :string
     :leveys "25%"}
    {:otsikko "Puhelin" :nimi :puhelin :tyyppi :string :leveys "20%"}
    {:otsikko "Sähköposti" :nimi :sahkoposti :tyyppi :string :leveys "25%"}]
   (if (k/virhe? kayttajat)
     []
     kayttajat)])

(defn paivystajalista
  [ur paivystajat tallenna!]
  [:div
   [grid/grid
    {:otsikko "Päivystystiedot"
     :tyhja "Ei päivystystietoja."
     :tallenna tallenna!
     :rivin-luokka #(when (and (< (:alku %) (pvm/nyt))
                               (< (pvm/nyt) (:loppu %)))
                      " bold")}
    [{:otsikko "Nimi" :hae #(if-let [nimi (:nimi %)]
                              nimi
                              (str (:etunimi %)
                                   (when-let [suku (:sukunimi %)]
                                     (str " " suku))))
      :aseta (fn [yht arvo]
               (assoc yht :nimi arvo))


      :tyyppi :string :leveys 15
      :validoi [[:ei-tyhja "Anna päivystäjän nimi"]]}
     {:otsikko "Organisaatio" :nimi :organisaatio :fmt :nimi :leveys 10
      :tyyppi :valinta
      :valinta-nayta #(if % (:nimi %) "- Valitse organisaatio -")
      :valinnat [nil (:urakoitsija ur) (:hallintayksikko ur)]}

     {:otsikko "Puhelin (virka)" :nimi :tyopuhelin :tyyppi :puhelin :leveys 10
      :pituus 16}
     {:otsikko "Puhelin (gsm)" :nimi :matkapuhelin :tyyppi :puhelin :leveys 10
      :pituus 16}
     {:otsikko "Sähköposti" :nimi :sahkoposti :tyyppi :email :leveys 20
      :validoi [[:ei-tyhja "Anna päivystäjän sähköposti"]]}
     {:otsikko "Alkupvm" :nimi :alku :tyyppi :pvm-aika :fmt pvm/pvm-aika :leveys 10
      :validoi [[:ei-tyhja "Aseta alkupvm"]
                (fn [alku rivi]
                  (let [loppu (:loppu rivi)]
                    (when (and alku loppu
                               (t/before? loppu alku))
                      "Alkupvm ei voi olla lopun jälkeen.")))
                ]}
     {:otsikko "Loppupvm" :nimi :loppu :tyyppi :pvm-aika :fmt pvm/pvm-aika :leveys 10
      :validoi [[:ei-tyhja "Aseta loppupvm"]
                (fn [loppu rivi]
                  (let [alku (:alku rivi)]
                    (when (and alku loppu
                               (t/before? loppu alku))
                      "Loppupvm ei voi olla alkua ennen.")))]}
     {:otsikko "Vastuuhenkilö" :nimi :vastuuhenkilo :tyyppi :checkbox
      :leveys 10
      :fmt fmt/totuus :tasaa :keskita}]
    paivystajat]
   [yleiset/vihje "Kaikista ilmoituksista lähetetään aina kaikille vuorossaoleville päivystäjille tieto sähköpostilla.
   Uusista toimenpidepyynnöistä lähetetään tekstiviesti vain vuorossaoleville vastuuhenkilöille"]])

(defn paivystajat [ur]
  (let [paivystajat (atom nil)
        hae! (fn [urakka-id]
               (reset! paivystajat nil)
               (go (reset! paivystajat
                           (reverse (sort-by :loppu
                                             (<! (yht/hae-urakan-paivystajat urakka-id)))))))]
    (hae! (:id ur))
    (komp/luo
      (komp/kun-muuttuu (comp hae! :id))
      (fn [ur]
        [paivystajalista ur @paivystajat
         (when (oikeudet/voi-kirjoittaa? oikeudet/urakat-yleiset (:id ur))
           #(tallenna-paivystajat ur paivystajat %))]))))

(defn takuuaika [ur]
  (let [tallennus-kaynnissa (atom false)]
    (komp/luo
      (komp/kun-muuttuu #(swap! tallennus-kaynnissa
                                (fn [k]
                                  (if (= k (:id %))
                                    k
                                    false))))
      (fn [ur]
        [:span.takuuaika.inline
         (if (oikeudet/voi-kirjoittaa? oikeudet/urakat-yleiset (:id ur))
           [:span
            [tee-kentta {:tyyppi :pvm :placeholder "Ei asetettu"}
             (r/wrap (get-in ur [:takuu :loppupvm])
                     #(do (reset! tallennus-kaynnissa (:id ur))
                          (nav/paivita-urakan-tiedot! (:id ur) assoc-in [:takuu :loppupvm] %)
                          (go (reset! tallennus-kaynnissa
                                      (if (k/virhe? (<! (urakka/aseta-takuu-loppupvm (:id ur) %)))
                                        :virhe
                                        false)))))]
            (cond
              (number? @tallennus-kaynnissa) [yleiset/ajax-loader-pieni]
              (= :virhe @tallennus-kaynnissa) [:span (ikonit/livicon-warning-sign)]
              :default nil)
            (when (and
                    (get-in ur [:takuu :loppupvm])
                    (not (pvm/jalkeen? (get-in ur [:takuu :loppupvm]) (:loppupvm ur))))
              (yleiset/vihje "Takuu päättyy yleensä urakan päättymisen jälkeen, tarkista päivämäärä"))]
           [:span
            (if-let [p (get-in ur [:takuu :loppupvm])]
              (pvm/pvm p)
              "Ei asetettu")])]))))

(defn- yllapitourakan-sopimustyyppi [ur]
  (when-not (= :hoito (:tyyppi ur))
    (let [kirjoitusoikeus? (oikeudet/voi-kirjoittaa? oikeudet/urakat-yleiset (:id ur))
          sopimustyyppi (:sopimustyyppi ur)]
      [yleiset/livi-pudotusvalikko {:class "alasveto-yleiset-tiedot"
                                    :valinta sopimustyyppi
                                    :format-fn #(if %
                                                  (str/capitalize (name %))
                                                  "Ei sopimustyyppiä")
                                    :valitse-fn #(tallenna-sopimustyyppi ur %)
                                    :disabled (not kirjoitusoikeus?)}
       sopimus/+sopimustyypit+])))

(defn yha-tiedot [ur]
  {:yha-tuontioikeus? (yhatiedot/yha-tuontioikeus? ur)
   :paallystys-tai-paikkausurakka? (or (= (:tyyppi ur) :paallystys)
                                       (= (:tyyppi ur) :paikkaus))
   :paallystys-tai-paikkausurakka-sidottu? (some? (:yhatiedot ur))
   :sidonta-lukittu? (get-in ur [:yhatiedot :sidonta-lukittu?])
   :sidonta-lukittu-vihje "Urakan sidontaa ei voi enää muuttaa, sillä sen tietoja on muutettu."})

(defn- yha-sidonta [ur {:keys [paallystys-tai-paikkausurakka? paallystys-tai-paikkausurakka-sidottu?
                               yha-tuontioikeus?
                               sidonta-lukittu? sidonta-lukittu-vihje]}]
  (cond
    (and paallystys-tai-paikkausurakka? (not yha-tuontioikeus?) (not paallystys-tai-paikkausurakka-sidottu?))
    [:span.bold "Urakanvalvojan täytyy sitoa urakka YHA-urakkaan"]
    (and paallystys-tai-paikkausurakka? yha-tuontioikeus? (not paallystys-tai-paikkausurakka-sidottu?))
    [:span (when sidonta-lukittu? {:title sidonta-lukittu-vihje})
     [:button.nappi-ensisijainen {:on-click #(yha/nayta-tuontidialogi ur)
                                  :disabled sidonta-lukittu?}
      "Sido YHA-urakkaan"]]
    (and paallystys-tai-paikkausurakka? (not yha-tuontioikeus?) paallystys-tai-paikkausurakka-sidottu?)
    [:span "Sidottu YHA-urakkaan. Vain urakanvalvoja voi muuttaa sidontaa."]
    (and paallystys-tai-paikkausurakka? yha-tuontioikeus? paallystys-tai-paikkausurakka-sidottu?)
    [:span (when sidonta-lukittu? {:title sidonta-lukittu-vihje})
     [:button.nappi-ensisijainen {:on-click #(yha/nayta-tuontidialogi ur)
                                  :disabled sidonta-lukittu?}
      "Vaihda sidottu urakka"]]
    :default nil))

(defn- yllapidon-urakkatyypin-vaihto [ur {:keys [paallystys-tai-paikkausurakka? sidonta-lukittu?
                                                 yha-tuontioikeus?]}]
  (when paallystys-tai-paikkausurakka?
    [:span {:title (cond sidonta-lukittu?
                         "Urakan sidonta on lukittu, urakkatyyppiä ei voi enää muuttaa."
                         (not yha-tuontioikeus?)
                         "Vain urakanvalvoja voi muuttaa urakan tyyppiä"
                         :default nil)}
     [yleiset/livi-pudotusvalikko {:class "alasveto-yleiset-tiedot"
                                   :valinta (:tyyppi ur)
                                   :format-fn #(navigaatio/nayta-urakkatyyppi %)
                                   :valitse-fn #(vahvista-urakkatyypin-vaihtaminen ur %)
                                   ;; todo: Urakkatyypin vaihto on toiseseksi estetty,
                                   ;; sillä paikkausurakoiden toteutus on kesken.
                                   :disabled (constantly true)
                                   #_(or (not yha-tuontioikeus?)
                                         sidonta-lukittu?)}
      [:paallystys :paikkaus]]]))

(defn- urakan-indeksi [ur]
  (let [auki? (atom false)]
    (fn [ur]
      [:span
       (or (:indeksi ur)
           "Ei käytössä")
       (when (and (:indeksi ur)
                  (roolit/tilaajan-kayttaja? @istunto/kayttaja)
                  (oikeudet/voi-kirjoittaa? oikeudet/urakat-yleiset (:id ur)))
         [:span
          [:span.klikattava {:on-click #(swap! auki? not)}
           " "
           (ikonit/livicon-wrench)
           " "]
          (when @auki?
            [napit/yleinen "Poista indeksi käytöstä"
             (fn []
               (modal/nayta! {}
                             [:div
                              [:b "Haluatko poistaa indeksin käytön tässä urakassa?"]
                              [yleiset/tietoja {}
                               "Urakka: " (:nimi ur)
                               "Nykyinen indeksi: " (:indeksi ur)]
                              [:div
                               [:br]
                               [napit/palvelinkutsu-nappi "Poista indeksi käytöstä"
                                #(urakat/poista-indeksi-kaytosta! ur)
                                {:luokka "nappi-kielteinen"
                                 :kun-onnistuu #(do (nav/paivita-urakan-tiedot! (:id ur)
                                                                                assoc :indeksi nil)
                                                    (modal/piilota!)
                                                    (reset! auki? false))}]
                               [napit/yleinen "Peruuta" #(do (modal/piilota!)
                                                             (reset! auki? false))
                                {:luokka "nappi-toissijainen pull-right"}]]]))
             {:luokka "nappi-kielteinen btn-xs"}])])])))


(defn- aseta-vastuuhenkilo [paivita-vastuuhenkilot!
                            urakka-id kayttaja kayttajat vastuuhenkilot rooli
                            ensisijainen varalla]
  (r/with-let [henkilot (atom {:ensisijainen :ei-muutosta
                               :varalla :ei-muutosta})]
    ;; FIXME: valitse oletushenkilöksi nykyinen käyttäjänimen perusteella
    (let [mahdolliset-henkilot (filter #(some (partial = rooli) (:roolinimet %)) kayttajat)]

      [:div.vastuuhenkilo-muokkaus
       [lomake/lomake {:muokkaa! #(reset! henkilot %)
                       :footer-fn (fn [data]
                                    [napit/palvelinkutsu-nappi "Tallenna yhteyshenkilöt"
                                     #(let [{uusi-ensisijainen :ensisijainen
                                             uusi-varalla :varalla} @henkilot]
                                        (yht/tallenna-urakan-vastuuhenkilot-roolille
                                          urakka-id rooli
                                          (if (= :ei-muutosta uusi-ensisijainen)
                                            ensisijainen
                                            uusi-ensisijainen)
                                          (if (= :ei-muutosta uusi-varalla)
                                            varalla
                                            uusi-varalla)))
                                     {:kun-onnistuu #(do
                                                       (paivita-vastuuhenkilot! %)
                                                       (modal/piilota!))}])}
        [{:otsikko "Ensisijainen"
          :nimi :ensisijainen
          :leveys 2
          :tyyppi :valinta
          :valinta-nayta #(if (= :ei-muutosta %)
                            (fmt/kayttaja ensisijainen)
                            (fmt/kayttaja %))
          :valinnat mahdolliset-henkilot}
         {:otsikko "Varalla"
          :nimi :varalla
          :leveys 2
          :tyyppi :valinta
          :valinta-nayta #(if (= :ei-muutosta %)
                            (fmt/kayttaja varalla)
                            (fmt/kayttaja %))
          :valinnat mahdolliset-henkilot}]
        @henkilot]])))

(defn- vastuuhenkilo-tooltip [vastuuhenkilo]
  [:span
   [:div "Puhelin: " (:puhelin vastuuhenkilo)]
   [:div "Sähköposti: " (:sahkoposti vastuuhenkilo)]])

(defn- nayta-vastuuhenkilo [paivita-vastuuhenkilot!
                            urakka-id kayttaja kayttajat vastuuhenkilot rooli]
  (let [roolin-henkilot (filter #(= rooli (:rooli %)) vastuuhenkilot)
        ensisijainen (first (filter :ensisijainen roolin-henkilot))
        varalla (first (filter (comp not :ensisijainen) roolin-henkilot))
        voi-muokata? (and (not (k/virhe? kayttajat))
                          (not (empty? kayttajat))
                          (oikeudet/voi-kirjoittaa? oikeudet/urakat-yleiset urakka-id)
                          (or (not= rooli "ELY_Urakanvalvoja")
                              (= :tilaaja (roolit/osapuoli kayttaja))))]
    [:div.vastuuhenkilo.inline-block
     [:span
      (if ensisijainen
        [yleiset/tooltip {}
         [:span.vastuuhenkilo-ensisijainen (fmt/kayttaja ensisijainen)]
         [vastuuhenkilo-tooltip ensisijainen]]
        [:span.vastuuhenkilo-ei-tiedossa "Ei tiedossa"])
      " "
      (when varalla
        [yleiset/tooltip {}
         [:span.vastuuhenkilo-varalla "(sijainen " (fmt/kayttaja varalla) ")"]
         [vastuuhenkilo-tooltip varalla]])
      (when voi-muokata?
        [:span.klikattava {:on-click #(modal/nayta!
                                        {:otsikko (str "Urakan ensisijainen "
                                                       (case rooli
                                                         "ELY_Urakanvalvoja" "urakanvalvoja"
                                                         "vastuuhenkilo" "vastuuhenkilö"))}
                                        [aseta-vastuuhenkilo
                                         paivita-vastuuhenkilot!
                                         urakka-id kayttaja kayttajat
                                         vastuuhenkilot rooli
                                         ensisijainen varalla])}
         " "
         (ikonit/livicon-wrench)
         " "])]]))

(defn yleiset-tiedot [paivita-vastuuhenkilot! ur kayttajat vastuuhenkilot]
  (let [{:keys [paallystys-tai-paikkausurakka? paallystys-tai-paikkausurakka-sidottu?]
         :as yha-tiedot} (yha-tiedot ur)]
    [bs/panel {}
     "Yleiset tiedot"
     [yleiset/tietoja {}
      "Urakan nimi:" (:nimi ur)
      "Urakan tunnus:" (:sampoid ur)

      "YHA:n urakkatunnus:"
      (when (and paallystys-tai-paikkausurakka? paallystys-tai-paikkausurakka-sidottu?)
        (get-in ur [:yhatiedot :yhatunnus]))
      "YHA:n ELY:t"
      (when (and paallystys-tai-paikkausurakka? paallystys-tai-paikkausurakka-sidottu?)
        (str/join ", " (get-in ur [:yhatiedot :elyt])))
      "YHA:n vuodet:"
      (when (and paallystys-tai-paikkausurakka? paallystys-tai-paikkausurakka-sidottu?)
        (str/join ", " (get-in ur [:yhatiedot :vuodet])))
      "YHA-sidonta:" (yha-sidonta ur yha-tiedot)

      "Sopimuksen tunnus: " (some->> ur :sopimukset vals (str/join ", "))
      "Aikaväli:" [:span.aikavali (pvm/pvm (:alkupvm ur)) " \u2014 " (pvm/pvm (:loppupvm ur))]
      "Takuu päättyy:" (when paallystys-tai-paikkausurakka?
                         [takuuaika ur])
      "Tilaaja:" (:nimi (:hallintayksikko ur))
      "Urakanvalvoja: " [nayta-vastuuhenkilo paivita-vastuuhenkilot!
                         (:id ur) @istunto/kayttaja kayttajat vastuuhenkilot "ELY_Urakanvalvoja"]

      "Urakoitsija:" (:nimi (:urakoitsija ur))
      "Urakan vastuuhenkilö: " [nayta-vastuuhenkilo paivita-vastuuhenkilot!
                                (:id ur) @istunto/kayttaja kayttajat vastuuhenkilot "vastuuhenkilo"]

      ;; valaistus, tiemerkintä --> palvelusopimus
      ;; päällystys --> kokonaisurakka
      "Sopimustyyppi: " (yllapitourakan-sopimustyyppi ur)

      "Urakkatyyppi: " ; Päällystysurakan voi muuttaa paikkaukseksi ja vice versa
      (yllapidon-urakkatyypin-vaihto ur yha-tiedot)

      "Indeksi: " [urakan-indeksi ur]]]))

(defn yhteyshenkilot [ur]
  (let [yhteyshenkilot (atom nil)
        yhteyshenkilotyypit (yht/urakkatyypin-mukaiset-yhteyshenkilotyypit (:tyyppi ur))
        hae! (fn [ur]
               (reset! yhteyshenkilot nil)
               (go (reset! yhteyshenkilot
                           (filter
                             #(not= "urakoitsijan paivystaja" (:rooli %))
                             (<! (yht/hae-urakan-yhteyshenkilot (:id ur)))))))]
    (hae! ur)
    (komp/luo
      (komp/kun-muuttuu hae!)
      (fn [ur]
        [grid/grid
         {:otsikko "Yhteyshenkilöt"
          :tyhja "Ei yhteyshenkilöitä."
          :tallenna (when (oikeudet/voi-kirjoittaa? oikeudet/urakat-yleiset (:id ur))
                      #(tallenna-yhteyshenkilot ur yhteyshenkilot %))}
         [{:otsikko "Rooli" :nimi :rooli :tyyppi :valinta :leveys 17
           :hae #(do (when (:rooli %)
                       (str/capitalize (:rooli %))))
           :valinta-nayta #(if (nil? %) "- valitse -" (str/capitalize %))

           :valinnat (vec (concat [nil] yhteyshenkilotyypit))

           :validoi [[:ei-tyhja "Anna yhteyshenkilön rooli"]]}
          {:otsikko "Organisaatio"
           :nimi :organisaatio
           :fmt :nimi
           :leveys 17
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
           :tyyppi :string :leveys 15
           :validoi [[:ei-tyhja "Anna yhteyshenkilön nimi"]]}
          {:otsikko "Puhelin (virka)" :nimi :tyopuhelin :tyyppi :puhelin :leveys 12 :pituus 16}
          {:otsikko "Puhelin (gsm)" :nimi :matkapuhelin :tyyppi :puhelin :leveys 12 :pituus 16}
          {:otsikko "Sähköposti" :nimi :sahkoposti :tyyppi :email :leveys 22}]
         @yhteyshenkilot]))))

(defn- nayta-yha-tuontidialogi-tarvittaessa
  "Näyttää YHA-tuontidialogin, jos tarvii."
  [ur]
  (let [yha-tuontioikeus? (yhatiedot/yha-tuontioikeus? ur)
        paallystys-tai-paikkausurakka? (or (= (:tyyppi ur) :paallystys)
                                           (= (:tyyppi ur) :paikkaus))
        paallystys-tai-paikkausurakka-sidottu? (some? (:yhatiedot ur))
        sidonta-lukittu? (get-in ur [:yhatiedot :sidonta-lukittu?])
        palvelusopimus? (= :palvelusopimus (:sopimustyyppi ur))]
    (when (and yha-tuontioikeus?
               paallystys-tai-paikkausurakka?
               (not paallystys-tai-paikkausurakka-sidottu?)
               (not sidonta-lukittu?)
               (not palvelusopimus?))
      (yha/nayta-tuontidialogi ur))))

(defn- indeksinimi-ja-lahtotaso-fmt
  [rivi]
  (str (:indeksinimi rivi)
       (when (:lahtotason-arvo rivi)
         (str " (lähtötaso: "
              (:lahtotason-arvo rivi) " €/t)"))))

(defn- indeksinimen-kentta
  [nimi otsikko valinnat]
  {:otsikko otsikko :nimi nimi :tyyppi :valinta :leveys 12
   :valinta-nayta #(if (:id %) (:indeksinimi %) "- valitse -")
   :fmt indeksinimi-ja-lahtotaso-fmt
   :valinnat valinnat})

(defn paallystysurakan-indeksit
  "Käyttöliittymä päällystysurakassa käytettävien indeksien valintaan."
  [ur]
  (komp/luo
    (fn [ur]
      (let [varatut-vuodet (into #{} (map :urakkavuosi @urakka/paallystysurakan-indeksitiedot))
            indeksivalinnat (indeksit/urakkatyypin-indeksit :paallystys)
            indeksivalinnat-raskas-po (indeksit/raakaaineen-indeksit "raskas_polttooljy" indeksivalinnat)
            indeksivalinnat-kevyt-po (indeksit/raakaaineen-indeksit "kevyt_polttooljy" indeksivalinnat)
            indeksivalinnat-nestekaasu (indeksit/raakaaineen-indeksit "nestekaasu" indeksivalinnat)]
        [grid/grid
         {:otsikko "Urakan käyttämät indeksit"
          :tyhja "Ei indeksejä."
          :tallenna (when (oikeudet/voi-kirjoittaa? oikeudet/urakat-yleiset (:id ur))
                      #(indeksit/tallenna-paallystysurakan-indeksit {:urakka-id (:id ur) :tiedot %}))}
         [{:otsikko "Vuosi" :nimi :urakkavuosi :tyyppi :valinta :leveys 6
           :muokattava? #(neg? (:id %))
           :valinta-nayta #(if (nil? %) "- valitse -" %)

           :valinnat (vec (filter #(not (varatut-vuodet %))
                                  (range (pvm/vuosi (:alkupvm ur))
                                         (+ 1 (pvm/vuosi (:loppupvm ur))))))
           :validoi [[:ei-tyhja "Anna urakkavuosi"] [:uniikki "Yksi rivi per vuosi"]]}

          (indeksinimen-kentta :raskas "Raskas polttoöljy" indeksivalinnat-raskas-po)
          (indeksinimen-kentta :kevyt "Kevyt polttoöljy" indeksivalinnat-kevyt-po)
          (indeksinimen-kentta :nestekaasu "Nestekaasu" indeksivalinnat-nestekaasu)

          {:otsikko "Lähtö\u00ADtason vuosi" :nimi :lahtotason-vuosi :tyyppi :positiivinen-numero :leveys 6
           :validoi [[:rajattu-numero-tai-tyhja nil 2000 2030 "Anna vuosiluku"]]}
          {:otsikko "Lähtö\u00ADtason kk" :nimi :lahtotason-kuukausi :tyyppi :positiivinen-numero :leveys 6
           :validoi [[:rajattu-numero-tai-tyhja nil 1 12"Anna kuukausi"]]}]
         (reverse (sort-by :urakkavuosi @urakka/paallystysurakan-indeksitiedot))]))))

(defn yleiset [ur]
  (let [kayttajat (atom nil)
        vastuuhenkilot (atom nil)
        hae! (fn [urakan-tiedot]
               (reset! kayttajat nil)
               (reset! vastuuhenkilot nil)
               (go (reset! kayttajat (<! (yht/hae-urakan-kayttajat (:id urakan-tiedot)))))
               (go (reset! vastuuhenkilot (<! (yht/hae-urakan-vastuuhenkilot (:id urakan-tiedot)))))
               (when (= :paallystys (:tyyppi ur))
                 (reset! urakka/paallystysurakan-indeksitiedot nil)
                 (go (reset! urakka/paallystysurakan-indeksitiedot (<! (indeksit/hae-paallystysurakan-indeksitiedot (:id urakan-tiedot)))))))]
    (hae! ur)
    (komp/luo
      (komp/kun-muuttuu hae!)
      (komp/sisaan (fn [_]
                     (nayta-yha-tuontidialogi-tarvittaessa ur)))
      (fn [ur]
        [:div
         [yleiset-tiedot #(reset! vastuuhenkilot %) ur @kayttajat @vastuuhenkilot]
         (when (= :paallystys (:tyyppi ur))
           [paallystysurakan-indeksit ur])
         [urakkaan-liitetyt-kayttajat @kayttajat]
         [yhteyshenkilot ur]
         (when (urakka/paivystys-kaytossa? ur)
           [paivystajat ur])]))))
